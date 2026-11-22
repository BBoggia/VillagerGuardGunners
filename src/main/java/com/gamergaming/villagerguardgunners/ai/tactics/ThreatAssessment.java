package com.gamergaming.villagerguardgunners.ai.tactics;

/** Observed danger is independent of permission to shoot any particular entity. */
public record ThreatAssessment(long sampledAt, int threats, int visibleThreats,
        double nearestDistance, double closingPressure, double rangedExposure,
        boolean explosiveDanger, int occupiedSectors, double support,
        double recentDamageFraction, boolean complete) {
    public static final ThreatAssessment EMPTY = new ThreatAssessment(Long.MIN_VALUE,
            0, 0, Double.POSITIVE_INFINITY, 0, 0, false, 0, 0, 0, false);

    public double pressure() { return closingPressure + rangedExposure; }
    public boolean surrounded() { return occupiedSectors >= 3; }
}
