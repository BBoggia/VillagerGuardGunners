package com.gamergaming.villagerguardgunners.sidearm;

/**
 * Pure, allocation-free classification for the eventual runtime controller.
 * It never reads entities, inventories, TaCZ data, config, or world state.
 */
public final class SidearmDecisionPolicy {
    public static final int DEFAULT_MINIMUM_ACTIVE_TICKS = 40;
    public static final double DEFAULT_RETURN_MARGIN_BLOCKS = 2.0D;

    private SidearmDecisionPolicy() {
    }

    /**
     * Classifies only deliberate activation triggers. A dry primary wins over
     * explosive reasons so it cannot be restored mid-engagement merely because
     * an order or range condition changes.
     */
    public static SidearmActivationReason activationReason(
            boolean targetActive,
            boolean primaryDryConfirmed,
            boolean primaryExplosive,
            boolean primaryAllowedByOrders,
            boolean targetInsideExplosiveStandoff) {
        if (!targetActive) {
            return SidearmActivationReason.NONE;
        }
        if (primaryDryConfirmed) {
            return SidearmActivationReason.PRIMARY_DRY;
        }
        if (primaryExplosive && !primaryAllowedByOrders) {
            return SidearmActivationReason.EXPLOSIVE_DISALLOWED;
        }
        if (primaryExplosive && targetInsideExplosiveStandoff) {
            return SidearmActivationReason.EXPLOSIVE_STANDOFF;
        }
        return SidearmActivationReason.NONE;
    }

    /** Applies fail-closed feature, eligibility, readiness, and native-state gates. */
    public static SidearmDecision activationDecision(
            SidearmActivationReason reason,
            boolean featureEnabled,
            boolean sidearmEligible,
            boolean sidearmReady,
            boolean nativeSwapSafe) {
        if (reason == null || reason == SidearmActivationReason.NONE) {
            return SidearmDecision.NO_TRANSITION;
        }
        if (!featureEnabled) {
            return SidearmDecision.BLOCKED_FEATURE_DISABLED;
        }
        if (!sidearmEligible) {
            return SidearmDecision.BLOCKED_SIDEARM_INELIGIBLE;
        }
        if (!sidearmReady) {
            return SidearmDecision.BLOCKED_SIDEARM_NOT_READY;
        }
        return nativeSwapSafe
                ? SidearmDecision.ACTIVATE
                : SidearmDecision.DEFERRED_NATIVE_BUSY;
    }

    /**
     * Determines when the primary may be restored. Feature disablement,
     * engagement completion, and target replacement bypass the minimum active
     * time but still pass through the native-safety gate in
     * {@link #returnDecision}.
     */
    public static SidearmReturnReason returnReason(
            boolean featureEnabled,
            boolean targetActive,
            boolean sameTarget,
            boolean sidearmUsable,
            SidearmActivationReason activationReason,
            long activeTicks,
            int minimumActiveTicks,
            boolean primaryExplosive,
            boolean primaryAllowedByOrders,
            double targetDistance,
            double primaryStandoffDistance,
            double returnMarginBlocks) {
        if (!featureEnabled) {
            return SidearmReturnReason.FEATURE_DISABLED;
        }
        if (!targetActive) {
            return SidearmReturnReason.ENGAGEMENT_ENDED;
        }
        if (!sameTarget) {
            return SidearmReturnReason.TARGET_CHANGED;
        }
        if (!sidearmUsable) {
            return SidearmReturnReason.SIDEARM_UNUSABLE;
        }
        if (activeTicks < Math.max(0, minimumActiveTicks)) {
            return SidearmReturnReason.NONE;
        }
        if (activationReason == null
                || activationReason == SidearmActivationReason.NONE
                || activationReason == SidearmActivationReason.PRIMARY_DRY) {
            return SidearmReturnReason.NONE;
        }
        if (activationReason == SidearmActivationReason.EXPLOSIVE_DISALLOWED) {
            return primaryAllowedByOrders
                    ? SidearmReturnReason.PRIMARY_ALLOWED_AGAIN
                    : SidearmReturnReason.NONE;
        }
        if (activationReason == SidearmActivationReason.EXPLOSIVE_STANDOFF
                && primaryExplosive
                && primaryAllowedByOrders
                && hasClearedStandoff(
                        targetDistance,
                        primaryStandoffDistance,
                        returnMarginBlocks)) {
            return SidearmReturnReason.PRIMARY_STANDOFF_CLEARED;
        }
        return SidearmReturnReason.NONE;
    }

    public static SidearmDecision returnDecision(
            SidearmReturnReason reason,
            boolean nativeSwapSafe) {
        if (reason == null || reason == SidearmReturnReason.NONE) {
            return SidearmDecision.NO_TRANSITION;
        }
        return nativeSwapSafe
                ? SidearmDecision.RETURN
                : SidearmDecision.DEFERRED_NATIVE_BUSY;
    }

    static boolean hasClearedStandoff(
            double targetDistance,
            double primaryStandoffDistance,
            double returnMarginBlocks) {
        if (!Double.isFinite(targetDistance)
                || targetDistance < 0.0D
                || !Double.isFinite(primaryStandoffDistance)
                || primaryStandoffDistance < 0.0D
                || !Double.isFinite(returnMarginBlocks)
                || returnMarginBlocks < 0.0D) {
            return false;
        }
        double returnDistance = primaryStandoffDistance + returnMarginBlocks;
        return Double.isFinite(returnDistance) && targetDistance >= returnDistance;
    }
}
