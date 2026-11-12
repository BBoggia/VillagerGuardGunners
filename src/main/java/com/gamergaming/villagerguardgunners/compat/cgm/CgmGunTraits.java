package com.gamergaming.villagerguardgunners.compat.cgm;

/**
 * Turns MrCrayfish's Gun Mod's numbers into the terms guard tactics and shot
 * safety use. No game classes, so the rules are unit tested directly.
 */
final class CgmGunTraits {
    /** Farthest travel the pre-fire ally check covers. Later hits on allies are cancelled as they happen. */
    static final double MAX_CHECKED_TRAVEL = 192.0D;
    static final float MAX_EFFECTIVE_RANGE = 128.0F;

    // Vanilla projectile spread: each direction component moves by at most this much per unit of inaccuracy.
    private static final double VANILLA_SPREAD_COMPONENT_SCALE = 0.0172275D;

    private CgmGunTraits() {
    }

    /**
     * The mod has no weapon classes, so one is inferred. Explosive comes
     * first: a type mapping would otherwise hide the explosive trait from
     * tactical profile selection.
     */
    static String inferType(String gripType, boolean automatic, int rateTicks, int pellets, boolean explosive) {
        if (explosive || "bazooka".equals(gripType)) {
            return "rpg";
        }
        if ("mini_gun".equals(gripType)) {
            return "mg";
        }
        if (pellets > 1) {
            return "shotgun";
        }
        if ("one_handed".equals(gripType)) {
            return automatic ? "smg" : "pistol";
        }
        if (!automatic && rateTicks >= 20) {
            return "sniper";
        }
        return "rifle";
    }

    /**
     * The mod turns a non-player's shot by up to half the gun's spread on
     * pitch and on yaw. This returns the vanilla inaccuracy value whose
     * safety cone contains every such shot.
     */
    static double safetyInaccuracy(float spreadDegrees) {
        if (!Float.isFinite(spreadDegrees) || spreadDegrees <= 0.0F) {
            return 0.0D;
        }
        double worstAngle = Math.toRadians(Math.sqrt(2.0D) * spreadDegrees / 2.0D);
        return worstAngle / (Math.sqrt(3.0D) * VANILLA_SPREAD_COMPONENT_SCALE);
    }

    /** Ticks of flight the pre-fire check follows: the projectile's life, cut off at {@link #MAX_CHECKED_TRAVEL}. */
    static int safetyLifeTicks(double speed, int lifeTicks) {
        if (!Double.isFinite(speed) || speed <= 0.0D || lifeTicks < 1) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(lifeTicks, (long) Math.ceil(MAX_CHECKED_TRAVEL / speed)));
    }

    static float effectiveRange(double speed, int lifeTicks) {
        double reach = speed * lifeTicks;
        if (!Double.isFinite(reach) || reach <= 0.0D) {
            return Float.NaN;
        }
        return (float) Math.min(reach, MAX_EFFECTIVE_RANGE);
    }

    static int fireIntervalMillis(int rateTicks) {
        return Math.max(1, rateTicks) * 50;
    }
}
