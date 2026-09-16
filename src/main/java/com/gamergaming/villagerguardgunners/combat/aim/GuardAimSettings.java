package com.gamergaming.villagerguardgunners.combat.aim;

import java.util.Objects;

/** Resolved settings for a target; independent of permission to attack it. */
public record GuardAimSettings(double errorDegrees, GuardAimPoint aimPoint) {
    public static final double MAX_ERROR_DEGREES = 10.0D;
    public static final GuardAimSettings DEFAULT = new GuardAimSettings(0.0D, GuardAimPoint.EYE_LEVEL);

    public GuardAimSettings {
        Objects.requireNonNull(aimPoint, "aimPoint");
        if (!Double.isFinite(errorDegrees) || errorDegrees < 0 || errorDegrees > MAX_ERROR_DEGREES) {
            throw new IllegalArgumentException("Aim error must be finite and between 0 and 10 degrees");
        }
    }
}
