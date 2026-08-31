package com.gamergaming.villagerguardgunners.spawn;

/** Severity contract shared by future validation, inspection, and preview reports. */
public enum NaturalGuardLoadoutDiagnosticSeverity {
    /** Context or compatibility detail that does not prevent issuance. */
    INFO,
    /** Entry may be skipped, config-blocked, or require a live guard context. */
    WARNING,
    /** Entry cannot produce a valid natural loadout in the evaluated context. */
    ERROR
}
