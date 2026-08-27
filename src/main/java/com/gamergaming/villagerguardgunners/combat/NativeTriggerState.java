package com.gamergaming.villagerguardgunners.combat;

/**
 * Small, allocation-free state holder for an AI-controlled trigger.
 *
 * <p>TaCZ owns the actual gun state. This class only models input that a player
 * would normally provide: holding a charge trigger and briefly backing off
 * after a failed action.</p>
 */
public final class NativeTriggerState {
    private int retryTicks;
    private float chargeProgress;

    public void tick() {
        if (retryTicks > 0) {
            retryTicks--;
        }
    }

    public boolean canAttemptAction() {
        return retryTicks == 0;
    }

    public void defer(int ticks) {
        retryTicks = Math.max(retryTicks, Math.max(0, ticks));
    }

    public boolean advanceCharge(float increasePerTick, float maxCharge) {
        if (!hasValidChargeProgression(increasePerTick, maxCharge)) {
            chargeProgress = 0.0F;
            return false;
        }
        if (maxCharge == 0.0F) {
            chargeProgress = 0.0F;
            return true;
        }
        chargeProgress = Math.min(maxCharge, chargeProgress + increasePerTick);
        return chargeProgress >= maxCharge;
    }

    static boolean hasValidChargeDefinition(
            float increasePerTick,
            float decreasePerTick,
            float decreaseOnFire,
            float maxCharge,
            float fireThreshold) {
        return hasValidChargeProgression(increasePerTick, maxCharge)
                && Float.isFinite(decreasePerTick)
                && decreasePerTick >= 0.0F
                && Float.isFinite(decreaseOnFire)
                && decreaseOnFire >= 0.0F
                && Float.isFinite(fireThreshold)
                && fireThreshold >= 0.0F;
    }

    private static boolean hasValidChargeProgression(float increasePerTick, float maxCharge) {
        return Float.isFinite(increasePerTick)
                && Float.isFinite(maxCharge)
                && maxCharge >= 0.0F
                && (maxCharge == 0.0F || increasePerTick > 0.0F);
    }

    public void decayCharge(float decreasePerTick) {
        if (decreasePerTick > 0.0F) {
            chargeProgress = Math.max(0.0F, chargeProgress - decreasePerTick);
        }
    }

    public void finishChargedShot(float decreaseOnFire, boolean resetCompletely) {
        chargeProgress = resetCompletely
                ? 0.0F
                : Math.max(0.0F, chargeProgress - Math.max(0.0F, decreaseOnFire));
    }

    public float getChargeProgress() {
        return chargeProgress;
    }

    public void reset() {
        retryTicks = 0;
        chargeProgress = 0.0F;
    }
}
