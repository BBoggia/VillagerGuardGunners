package com.gamergaming.villagerguardgunners.guns.managed;

import com.gamergaming.villagerguardgunners.combat.NativeFireCadence;
import com.gamergaming.villagerguardgunners.combat.NativeTriggerState;
import com.gamergaming.villagerguardgunners.combat.NativeWeaponState;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;

/**
 * Firing rate, reloading, and running dry for one managed gun.
 *
 * <p>Holds no Minecraft objects. {@link ManagedWeaponDriver} supplies the live
 * magazine, reserve, and trigger through {@link Weapon}, so these rules can be
 * tested without a server.</p>
 */
final class ManagedWeaponCycle {
    static final int HELD_RETRY_TICKS = 2;
    static final int FAILED_RETRY_TICKS = 5;
    static final int DRY_RETRY_TICKS = 20;

    private static final long NONE = Long.MIN_VALUE;

    /** The live gun as the cycle sees it. */
    interface Weapon {
        int loaded();

        int capacity();

        long fireIntervalMillis();

        boolean automatic();

        int reloadTicks();

        int roundsPerReloadStep();

        /** True when the guard carries ammo this gun can load. */
        boolean hasReserve();

        /** Moves up to {@code rounds} from the reserve into the magazine and returns how many moved. */
        int loadFromReserve(int rounds);

        /** Asks nearby guards for ammo. True when some arrived. */
        boolean requestAmmo();

        /**
         * Checks safety and aim, then discharges and spends one round.
         *
         * @param firstRoundThisTick false for further rounds of the same tick
         */
        Shot fire(boolean firstRoundThisTick);
    }

    enum Shot {
        FIRED,
        /** A safety or aim check declined the round. */
        HELD,
        /** The gun mod did not discharge. */
        FAILED
    }

    private final NativeTriggerState trigger = new NativeTriggerState();
    private final NativeWeaponState state = new NativeWeaponState();
    private final NativeFireCadence cadence = new NativeFireCadence();
    private long reloadCompleteGameTick = NONE;
    private long lastShotMillis = NONE;
    private long lastShotIntervalMillis;

    /** Advances by one server tick. Reloading continues whether or not the guard may fire. */
    void tick(long gameTime, boolean mayFire, Weapon weapon) {
        if (!state.mayAttemptAction(gameTime)) {
            cadence.reset();
            return;
        }
        trigger.tick();

        if (reloadCompleteGameTick != NONE) {
            if (gameTime >= reloadCompleteGameTick) {
                finishReloadStep(gameTime, weapon);
            }
            cadence.reset();
            return;
        }

        if (weapon.loaded() <= 0) {
            beginReload(gameTime, weapon);
            cadence.reset();
            return;
        }
        // Rounds in the magazine end a dry spell however they got there.
        state.clearDry();

        if (!mayFire) {
            cadence.reset();
            return;
        }
        if (!trigger.canAttemptAction()) {
            return;
        }

        long interval = weapon.fireIntervalMillis();
        long now = gameTime * NativeFireCadence.SERVER_TICK_MILLIS;
        cadence.beginTick(now, interval, weapon.automatic() ? NativeFireCadence.MAX_SHOTS_PER_TICK : 1);
        boolean firstRound = true;
        while (cadence.hasShotDue()) {
            long shotMillis = cadence.getDueTimestamp();
            if (coolingDown(shotMillis)) {
                cadence.reset();
                return;
            }
            Shot shot = weapon.fire(firstRound);
            if (shot != Shot.FIRED) {
                trigger.defer(shot == Shot.HELD ? HELD_RETRY_TICKS : FAILED_RETRY_TICKS);
                cadence.reset();
                return;
            }
            lastShotMillis = shotMillis;
            lastShotIntervalMillis = interval;
            cadence.recordSuccessfulShot();
            firstRound = false;
            if (weapon.loaded() <= 0) {
                // The reload starts on the next tick.
                cadence.reset();
                return;
            }
        }
    }

    private void beginReload(long gameTime, Weapon weapon) {
        if (!trigger.canAttemptAction()) {
            return;
        }
        if (weapon.hasReserve()) {
            // Ammo moves only when the step completes, so an interrupted reload loses nothing.
            reloadCompleteGameTick = gameTime + Math.max(1, weapon.reloadTicks());
            state.clearDry();
            return;
        }
        if (weapon.requestAmmo()) {
            state.clearDry();
            trigger.defer(1);
            return;
        }
        state.markDry(gameTime, DRY_RETRY_TICKS);
        trigger.reset();
    }

    private void finishReloadStep(long gameTime, Weapon weapon) {
        reloadCompleteGameTick = NONE;
        int room = weapon.capacity() - weapon.loaded();
        if (room <= 0) {
            return;
        }
        int moved = weapon.loadFromReserve(Math.min(room, Math.max(1, weapon.roundsPerReloadStep())));
        if (moved <= 0) {
            // The reserve was taken mid-reload. The next tick decides again from an empty magazine.
            return;
        }
        state.clearDry();
        if (weapon.loaded() < weapon.capacity() && weapon.hasReserve()) {
            reloadCompleteGameTick = gameTime + Math.max(1, weapon.reloadTicks());
        }
    }

    private boolean coolingDown(long timestampMillis) {
        if (lastShotMillis == NONE) {
            return false;
        }
        if (timestampMillis < lastShotMillis) {
            // Game time never runs backwards within a world; treat a jump as a fresh start.
            lastShotMillis = NONE;
            return false;
        }
        return timestampMillis - lastShotMillis
                < lastShotIntervalMillis - NativeFireCadence.TACZ_COOLDOWN_TOLERANCE_MILLIS;
    }

    /** State of a bound, readable gun. The driver reports unbound and suspended guns itself. */
    WeaponReadiness readiness(Weapon weapon) {
        if (isReloading()) {
            return WeaponReadiness.RELOADING;
        }
        if (state.isDryConfirmed()) {
            return WeaponReadiness.DRY;
        }
        return weapon.loaded() <= 0 ? WeaponReadiness.NEEDS_RELOAD : WeaponReadiness.READY;
    }

    boolean isReloading() {
        return reloadCompleteGameTick != NONE;
    }

    boolean isCoolingDown(long gameTime) {
        return coolingDown(gameTime * NativeFireCadence.SERVER_TICK_MILLIS);
    }

    /** Read-only diagnostic clock; never resets state on a backwards time jump. */
    long remainingCooldownMillis(long gameTime) {
        long now = gameTime * NativeFireCadence.SERVER_TICK_MILLIS;
        if (lastShotMillis == NONE || now < lastShotMillis) return 0L;
        return Math.max(0L, lastShotIntervalMillis - NativeFireCadence.TACZ_COOLDOWN_TOLERANCE_MILLIS
                - (now - lastShotMillis));
    }

    /** True while a reload, a retry delay, or the gap between rounds is still running. */
    boolean isBusy(long gameTime) {
        return isReloading() || !trigger.canAttemptAction() || isCoolingDown(gameTime);
    }

    boolean isDryConfirmed() {
        return state.isDryConfirmed();
    }

    long remainingDryTicks(long gameTime) {
        return state.remainingDryTicks(gameTime);
    }

    /** A virtual reserve or a delivery arrived; stop waiting out the dry retry. */
    void clearDry() {
        state.clearDry();
    }

    void suspend() {
        state.suspend();
        releaseTrigger();
    }

    void resume() {
        state.resume();
    }

    boolean isSuspended() {
        return state.isSuspended();
    }

    /** The guard lowered the gun. An in-progress reload keeps running. */
    void releaseTrigger() {
        trigger.reset();
        cadence.reset();
    }

    /** Abandons a reload that has not yet moved any ammo. */
    void cancelReload() {
        reloadCompleteGameTick = NONE;
    }

    void resetForWeaponChange() {
        cancelReload();
        releaseTrigger();
        state.resetForWeaponChange();
        lastShotMillis = NONE;
        lastShotIntervalMillis = 0L;
    }
}
