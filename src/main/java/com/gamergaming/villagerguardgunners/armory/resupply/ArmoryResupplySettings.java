package com.gamergaming.villagerguardgunners.armory.resupply;

/**
 * Immutable, synchronized schema-9 policy snapshot for one Armory resupply
 * decision. The snapshot contains values only; it owns no entity, level,
 * inventory, navigation, or index reference.
 */
public record ArmoryResupplySettings(
        boolean enabled,
        int checkIntervalTicks,
        double searchRadius,
        int maximumCandidateArmories,
        int lowReserveMagazines,
        int targetReserveMagazines,
        double movementSpeed,
        int requestLifetimeTicks,
        int reservationLifetimeTicks,
        int successCooldownTicks,
        int noSupplyRetryTicks,
        int unreachableRetryTicks) {

    public ArmoryResupplySettings {
        if (checkIntervalTicks < ArmoryResupplyLimits.MIN_CHECK_INTERVAL_TICKS
                || checkIntervalTicks > ArmoryResupplyLimits.MAX_CHECK_INTERVAL_TICKS) {
            throw new IllegalArgumentException("checkIntervalTicks is outside the hard bounds");
        }
        if (!Double.isFinite(searchRadius)
                || searchRadius < ArmoryResupplyLimits.MIN_SEARCH_RADIUS
                || searchRadius > ArmoryResupplyLimits.HARD_MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("searchRadius is outside the hard bounds");
        }
        if (maximumCandidateArmories <= 0
                || maximumCandidateArmories
                        > ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES) {
            throw new IllegalArgumentException(
                    "maximumCandidateArmories is outside the hard bounds");
        }
        if (lowReserveMagazines < 0
                || lowReserveMagazines > ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES
                || targetReserveMagazines < 0
                || targetReserveMagazines > ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES) {
            throw new IllegalArgumentException("reserve thresholds are outside the hard bounds");
        }
        if (!Double.isFinite(movementSpeed)
                || movementSpeed < ArmoryResupplyLimits.MIN_RESUPPLY_MOVEMENT_SPEED
                || movementSpeed > ArmoryResupplyLimits.HARD_MAX_RESUPPLY_MOVEMENT_SPEED) {
            throw new IllegalArgumentException("movementSpeed is outside the hard bounds");
        }
        requireLifetime(
                requestLifetimeTicks,
                ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS,
                "requestLifetimeTicks");
        requireLifetime(
                reservationLifetimeTicks,
                ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS,
                "reservationLifetimeTicks");
        requireRetry(successCooldownTicks, "successCooldownTicks");
        requireRetry(noSupplyRetryTicks, "noSupplyRetryTicks");
        requireRetry(unreachableRetryTicks, "unreachableRetryTicks");
    }

    /**
     * Cross-field threshold validity is deliberately explicit. Fzzy validates
     * each field's hard range, while the request boundary fails closed when a
     * target lower than the trigger is configured.
     */
    public boolean validReserveThresholds() {
        return targetReserveMagazines >= lowReserveMagazines;
    }

    public double searchRadiusSquared() {
        return searchRadius * searchRadius;
    }

    private static void requireLifetime(int ticks, int maximum, String name) {
        if (ticks < ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS || ticks > maximum) {
            throw new IllegalArgumentException(name + " is outside the hard bounds");
        }
    }

    private static void requireRetry(int ticks, String name) {
        if (ticks < ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS
                || ticks > ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS) {
            throw new IllegalArgumentException(name + " is outside the hard bounds");
        }
    }
}
