package com.gamergaming.villagerguardgunners.combat;

/** Tracks confirmed exhaustion independently of the next native retry. */
public final class NativeWeaponState {
    private long nextDryRetryGameTick = Long.MIN_VALUE;
    private boolean suspended;
    private boolean dryConfirmed;

    public boolean mayAttemptAction(long gameTick) {
        return !suspended && gameTick >= nextDryRetryGameTick;
    }

    public void markDry(long gameTick, int retryTicks) {
        dryConfirmed = true;
        nextDryRetryGameTick = gameTick + Math.max(1, retryTicks);
    }

    public boolean isDryConfirmed() {
        return dryConfirmed;
    }

    public void clearDry() {
        dryConfirmed = false;
        nextDryRetryGameTick = Long.MIN_VALUE;
    }

    public void suspend() {
        suspended = true;
    }

    public void resume() {
        suspended = false;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public long remainingDryTicks(long gameTick) {
        return nextDryRetryGameTick <= gameTick ? 0L : nextDryRetryGameTick - gameTick;
    }

    public void resetForWeaponChange() {
        clearDry();
        suspended = false;
    }
}
