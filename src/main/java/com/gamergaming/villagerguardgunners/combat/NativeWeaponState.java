package com.gamergaming.villagerguardgunners.combat;

/** Tracks goal-level availability separately from TaCZ's native gun state. */
public final class NativeWeaponState {
    private long dryUntilGameTick = Long.MIN_VALUE;
    private boolean suspended;

    public boolean mayEngage(long gameTick) {
        return !suspended && gameTick >= dryUntilGameTick;
    }

    public void markDry(long gameTick, int retryTicks) {
        dryUntilGameTick = gameTick + Math.max(1, retryTicks);
    }

    public void clearDry() {
        dryUntilGameTick = Long.MIN_VALUE;
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
        return dryUntilGameTick <= gameTick ? 0L : dryUntilGameTick - gameTick;
    }

    public void resetForWeaponChange() {
        dryUntilGameTick = Long.MIN_VALUE;
        suspended = false;
    }
}
