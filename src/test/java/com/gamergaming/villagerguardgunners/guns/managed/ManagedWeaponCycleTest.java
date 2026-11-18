package com.gamergaming.villagerguardgunners.guns.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.combat.NativeFireCadence;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedWeaponCycle.Shot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManagedWeaponCycleTest {
    private final ManagedWeaponCycle cycle = new ManagedWeaponCycle();
    private final FakeWeapon weapon = new FakeWeapon();

    @Test
    void firesAtTheGunsInterval() {
        weapon.loaded = 5;
        weapon.capacity = 5;
        weapon.intervalMillis = 100;

        fireFor(0, 9);

        assertEquals(List.of(0L, 2L, 4L, 6L, 8L), weapon.shotTicks);
    }

    @Test
    void semiAutomaticGunFiresAtMostOncePerTick() {
        weapon.loaded = 50;
        weapon.capacity = 50;
        weapon.intervalMillis = 20;
        weapon.automatic = false;

        fireFor(0, 3);

        assertEquals(List.of(0L, 1L, 2L, 3L), weapon.shotTicks);
    }

    @Test
    void automaticGunKeepsItsRateBetweenTicks() {
        weapon.loaded = 100;
        weapon.capacity = 100;
        weapon.intervalMillis = 20;
        weapon.automatic = true;

        fireFor(0, 9);

        // One round every 20 ms from 0 ms to 440 ms; the round due at 460 ms waits for the next tick.
        assertEquals(23, weapon.shotTicks.size());
        assertEquals(List.of(true, false), weapon.firstRoundFlags.subList(1, 3),
                "Later rounds of one tick must be marked so the per-round safety check runs");
    }

    @Test
    void veryFastGunIsCappedPerTick() {
        weapon.loaded = 1_000;
        weapon.capacity = 1_000;
        weapon.intervalMillis = 5;
        weapon.automatic = true;

        fireFor(0, 19);

        for (long tick = 0; tick <= 19; tick++) {
            long current = tick;
            long shots = weapon.shotTicks.stream().filter(shotTick -> shotTick == current).count();
            assertTrue(shots <= NativeFireCadence.MAX_SHOTS_PER_TICK, "Tick " + tick + " fired " + shots);
        }
        assertTrue(weapon.shotTicks.size() > 20, "Sub-tick fire should exceed one round per tick");
    }

    @Test
    void releasingAndPressingTheTriggerCannotBeatTheInterval() {
        weapon.loaded = 5;
        weapon.capacity = 5;
        weapon.intervalMillis = 500;

        tick(0, true);
        tick(1, false);
        fireFor(2, 10);

        assertEquals(List.of(0L, 10L), weapon.shotTicks);
    }

    @Test
    void emptyMagazineReloadsFromTheReserveAfterTheReloadTime() {
        weapon.capacity = 5;
        weapon.reserve = 7;
        weapon.reloadTicks = 10;

        tick(0, true);
        assertEquals(WeaponReadiness.RELOADING, cycle.readiness(weapon));
        fireFor(1, 9);
        assertEquals(0, weapon.loaded, "Ammo moves only when the reload completes");
        assertEquals(7, weapon.reserve);

        tick(10, true);

        assertEquals(5, weapon.loaded);
        assertEquals(2, weapon.reserve);
        assertEquals(WeaponReadiness.READY, cycle.readiness(weapon));
        assertTrue(weapon.shotTicks.isEmpty(), "The tick that completes a reload does not also fire");
        tick(11, true);
        assertEquals(List.of(11L), weapon.shotTicks);
    }

    @Test
    void reloadCompletesWhileTheGuardMayNotFire() {
        weapon.capacity = 5;
        weapon.reserve = 5;
        weapon.reloadTicks = 10;

        for (long tick = 0; tick <= 10; tick++) {
            tick(tick, false);
        }

        assertEquals(5, weapon.loaded);
        assertFalse(cycle.isReloading());
    }

    @Test
    void roundByRoundReloadRunsUntilTheMagazineIsFull() {
        weapon.capacity = 3;
        weapon.reserve = 10;
        weapon.reloadTicks = 4;
        weapon.roundsPerReloadStep = 1;

        fireFor(0, 4);
        assertEquals(1, weapon.loaded);
        assertTrue(cycle.isReloading());
        fireFor(5, 8);
        assertEquals(2, weapon.loaded);
        fireFor(9, 12);

        assertEquals(3, weapon.loaded);
        assertEquals(7, weapon.reserve);
        assertFalse(cycle.isReloading());
        assertTrue(weapon.shotTicks.isEmpty());
    }

    @Test
    void roundByRoundReloadStopsWhenTheReserveRunsOut() {
        weapon.capacity = 5;
        weapon.reserve = 2;
        weapon.reloadTicks = 4;
        weapon.roundsPerReloadStep = 1;

        fireFor(0, 8);

        assertEquals(2, weapon.loaded);
        assertEquals(0, weapon.reserve);
        assertFalse(cycle.isReloading());
        assertEquals(WeaponReadiness.READY, cycle.readiness(weapon));
    }

    @Test
    void lastRoundStartsTheReloadOnTheNextTick() {
        weapon.loaded = 1;
        weapon.capacity = 5;
        weapon.reserve = 5;

        tick(0, true);
        assertEquals(WeaponReadiness.NEEDS_RELOAD, cycle.readiness(weapon));
        tick(1, true);

        assertEquals(List.of(0L), weapon.shotTicks);
        assertTrue(cycle.isReloading());
    }

    @Test
    void cancelledReloadMovesNoAmmo() {
        weapon.capacity = 5;
        weapon.reserve = 7;
        weapon.reloadTicks = 10;

        fireFor(0, 5);
        cycle.cancelReload();

        assertFalse(cycle.isReloading());
        assertEquals(0, weapon.loaded);
        assertEquals(7, weapon.reserve);
    }

    @Test
    void loweringAimDuringAnActiveReloadStillAllowsItToFinish() {
        weapon.capacity = 5;
        weapon.reserve = 7;
        weapon.reloadTicks = 10;
        tick(0, false);
        cycle.releaseTrigger();
        tick(10, false);
        assertFalse(cycle.isReloading());
        assertEquals(5, weapon.loaded);
        assertEquals(2, weapon.reserve);
        assertTrue(weapon.shotTicks.isEmpty());
    }

    @Test
    void emptyReserveAsksOtherGuardsBeforeGoingDry() {
        weapon.capacity = 5;
        weapon.sharedOnRequest = 5;

        tick(0, true);
        assertEquals(1, weapon.ammoRequests);
        assertFalse(cycle.isDryConfirmed());
        tick(1, true);

        assertTrue(cycle.isReloading());
        assertEquals(1, weapon.ammoRequests);
    }

    @Test
    void noAmmoAnywhereConfirmsDryAndRetriesOnATimer() {
        weapon.capacity = 5;

        tick(0, true);
        assertTrue(cycle.isDryConfirmed());
        assertEquals(WeaponReadiness.DRY, cycle.readiness(weapon));
        assertEquals(ManagedWeaponCycle.DRY_RETRY_TICKS, cycle.remainingDryTicks(0));

        fireFor(1, ManagedWeaponCycle.DRY_RETRY_TICKS - 1);
        assertEquals(1, weapon.ammoRequests, "A dry guard must not ask for ammo every tick");

        tick(ManagedWeaponCycle.DRY_RETRY_TICKS, true);
        assertEquals(2, weapon.ammoRequests);
        assertTrue(cycle.isDryConfirmed());
    }

    @Test
    void deliveredAmmoEndsTheDrySpellAtTheNextRetry() {
        weapon.capacity = 5;
        tick(0, true);
        weapon.reserve = 5;

        tick(ManagedWeaponCycle.DRY_RETRY_TICKS, true);

        assertFalse(cycle.isDryConfirmed());
        assertTrue(cycle.isReloading());
    }

    @Test
    void magazineLoadedInPlaceEndsTheDrySpell() {
        weapon.capacity = 5;
        tick(0, true);
        weapon.loaded = 3;

        tick(ManagedWeaponCycle.DRY_RETRY_TICKS, true);

        assertFalse(cycle.isDryConfirmed());
        assertEquals(List.of((long) ManagedWeaponCycle.DRY_RETRY_TICKS), weapon.shotTicks);
    }

    @Test
    void clearingDryLetsAVirtualReserveReloadImmediately() {
        weapon.capacity = 5;
        tick(0, true);
        weapon.reserve = 5;

        cycle.clearDry();
        tick(1, true);

        assertTrue(cycle.isReloading());
    }

    @Test
    void declinedRoundBacksOffBriefly() {
        weapon.loaded = 5;
        weapon.capacity = 5;
        weapon.outcomes.add(Shot.HELD);

        fireFor(0, ManagedWeaponCycle.HELD_RETRY_TICKS);

        assertEquals(List.of(0L, (long) ManagedWeaponCycle.HELD_RETRY_TICKS), weapon.attemptTicks);
        assertEquals(List.of((long) ManagedWeaponCycle.HELD_RETRY_TICKS), weapon.shotTicks);
        assertEquals(4, weapon.loaded, "A declined round spends no ammo");
    }

    @Test
    void failedDischargeBacksOffLonger() {
        weapon.loaded = 5;
        weapon.capacity = 5;
        weapon.outcomes.add(Shot.FAILED);

        fireFor(0, ManagedWeaponCycle.FAILED_RETRY_TICKS);

        assertEquals(List.of(0L, (long) ManagedWeaponCycle.FAILED_RETRY_TICKS), weapon.attemptTicks);
    }

    @Test
    void declinedLaterRoundEndsThatTicksFire() {
        weapon.loaded = 100;
        weapon.capacity = 100;
        weapon.intervalMillis = 20;
        weapon.automatic = true;
        weapon.outcomes.add(Shot.FIRED);
        weapon.outcomes.add(Shot.FIRED);
        weapon.outcomes.add(Shot.HELD);

        fireFor(0, 1);

        assertEquals(List.of(0L, 1L), weapon.shotTicks);
        assertEquals(List.of(0L, 1L, 1L), weapon.attemptTicks);
    }

    @Test
    void suspendedGunDoesNothingUntilResumed() {
        weapon.loaded = 5;
        weapon.capacity = 5;
        cycle.suspend();

        fireFor(0, 5);
        assertTrue(weapon.attemptTicks.isEmpty());
        assertTrue(cycle.isSuspended());

        cycle.resume();
        tick(6, true);
        assertEquals(List.of(6L), weapon.shotTicks);
    }

    @Test
    void busyWhileReloadingBackingOffOrBetweenRounds() {
        weapon.loaded = 1;
        weapon.capacity = 5;
        weapon.reserve = 5;
        weapon.intervalMillis = 200;
        weapon.reloadTicks = 10;
        assertFalse(cycle.isBusy(0));

        tick(0, true);
        assertTrue(cycle.isBusy(1), "Between rounds");
        assertTrue(cycle.isCoolingDown(1));
        assertFalse(cycle.isCoolingDown(4));

        tick(4, true);
        assertTrue(cycle.isBusy(5), "Reloading");
        fireFor(5, 14);
        assertFalse(cycle.isBusy(15));
    }

    @Test
    void weaponChangeForgetsReloadDryStateAndCooldown() {
        weapon.capacity = 5;
        tick(0, true);
        assertTrue(cycle.isDryConfirmed());

        cycle.resetForWeaponChange();
        weapon.loaded = 5;
        tick(1, true);

        assertFalse(cycle.isDryConfirmed());
        assertEquals(List.of(1L), weapon.shotTicks);

        weapon.loaded = 0;
        weapon.reserve = 5;
        tick(2, true);
        assertTrue(cycle.isReloading());
        cycle.resetForWeaponChange();
        assertFalse(cycle.isReloading());
        assertFalse(cycle.isBusy(2));
    }

    private void tick(long gameTime, boolean mayFire) {
        weapon.currentTick = gameTime;
        cycle.tick(gameTime, mayFire, weapon);
    }

    private void fireFor(long firstTick, long lastTick) {
        for (long tick = firstTick; tick <= lastTick; tick++) {
            tick(tick, true);
        }
    }

    private static final class FakeWeapon implements ManagedWeaponCycle.Weapon {
        int loaded;
        int capacity = 1;
        long intervalMillis = 50;
        boolean automatic;
        int reloadTicks = 1;
        int roundsPerReloadStep = Integer.MAX_VALUE;
        int reserve;
        int sharedOnRequest;
        int ammoRequests;
        long currentTick;
        final Deque<Shot> outcomes = new ArrayDeque<>();
        final List<Long> attemptTicks = new ArrayList<>();
        final List<Long> shotTicks = new ArrayList<>();
        final List<Boolean> firstRoundFlags = new ArrayList<>();

        @Override
        public int loaded() {
            return loaded;
        }

        @Override
        public int capacity() {
            return capacity;
        }

        @Override
        public long fireIntervalMillis() {
            return intervalMillis;
        }

        @Override
        public boolean automatic() {
            return automatic;
        }

        @Override
        public int reloadTicks() {
            return reloadTicks;
        }

        @Override
        public int roundsPerReloadStep() {
            return roundsPerReloadStep;
        }

        @Override
        public boolean hasReserve() {
            return reserve > 0;
        }

        @Override
        public int loadFromReserve(int rounds) {
            int moved = Math.min(rounds, reserve);
            reserve -= moved;
            loaded += moved;
            return moved;
        }

        @Override
        public boolean requestAmmo() {
            ammoRequests++;
            if (sharedOnRequest <= 0) {
                return false;
            }
            reserve += sharedOnRequest;
            sharedOnRequest = 0;
            return true;
        }

        @Override
        public Shot fire(boolean firstRoundThisTick) {
            if (loaded <= 0) {
                throw new AssertionError("The cycle fired an empty gun");
            }
            attemptTicks.add(currentTick);
            firstRoundFlags.add(firstRoundThisTick);
            Shot outcome = outcomes.isEmpty() ? Shot.FIRED : outcomes.removeFirst();
            if (outcome == Shot.FIRED) {
                loaded--;
                shotTicks.add(currentTick);
            }
            return outcome;
        }
    }
}
