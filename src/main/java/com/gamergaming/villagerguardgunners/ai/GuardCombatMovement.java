package com.gamergaming.villagerguardgunners.ai;

/**
 * Pure decision helpers for the bounded guard-gunner movement loop.
 *
 * <p>Keeping this separate from navigation makes the state transitions cheap
 * to test without constructing a Minecraft world. The caller owns the actual
 * path or strafe command and applies it at its independently bounded cadence.</p>
 */
final class GuardCombatMovement {
    private GuardCombatMovement() {
    }

    static State selectState(
            boolean patrolling,
            boolean canSeeTarget,
            int unseenSamples,
            int unseenSamplesBeforeApproach,
            double distanceSquared,
            double closeRangeSquared,
            double effectiveRangeSquared,
            boolean friendlyBlockingShot) {
        if (patrolling) {
            return State.HOLD;
        }
        if (distanceSquared <= Math.max(0.0D, closeRangeSquared)) {
            return State.RETREAT;
        }
        if (canSeeTarget && friendlyBlockingShot) {
            return State.REPOSITION;
        }
        if (unseenSamples >= unseenSamplesBeforeApproach
                || distanceSquared > effectiveRangeSquared) {
            return State.APPROACH;
        }
        return State.HOLD;
    }

    static boolean isWithinEngagementRange(double distanceSquared, double engagementRange) {
        if (!Double.isFinite(distanceSquared)
                || distanceSquared < 0.0D
                || !Double.isFinite(engagementRange)
                || engagementRange < 0.0D) {
            return false;
        }
        double engagementRangeSquared = engagementRange * engagementRange;
        return Double.isFinite(engagementRangeSquared)
                && distanceSquared <= engagementRangeSquared;
    }

    static long firstDueTick(long gameTime, int entityId, int intervalTicks) {
        if (intervalTicks <= 1) {
            return gameTime;
        }

        int currentPhase = Math.floorMod(gameTime, intervalTicks);
        int entityPhase = Math.floorMod(entityId, intervalTicks);
        return gameTime + Math.floorMod(entityPhase - currentPhase, intervalTicks);
    }

    static float lateralDirection(int entityId) {
        return (entityId & 1) == 0 ? 1.0F : -1.0F;
    }

    static boolean madeLateralProgress(
            double startX,
            double startZ,
            double currentX,
            double currentZ,
            double minimumProgressSquared) {
        double deltaX = currentX - startX;
        double deltaZ = currentZ - startZ;
        return deltaX * deltaX + deltaZ * deltaZ >= Math.max(0.0D, minimumProgressSquared);
    }

    enum State {
        APPROACH,
        HOLD,
        RETREAT,
        REPOSITION
    }
}
