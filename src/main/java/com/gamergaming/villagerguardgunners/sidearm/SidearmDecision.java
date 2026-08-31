package com.gamergaming.villagerguardgunners.sidearm;

/**
 * Allocation-free result of a sidearm policy check.
 *
 * <p>The later runtime controller will perform a transaction only for
 * {@link #ACTIVATE} or {@link #RETURN}. Deferred and blocked decisions never
 * mutate equipment.</p>
 */
public enum SidearmDecision {
    NO_TRANSITION,
    ACTIVATE,
    RETURN,
    BLOCKED_FEATURE_DISABLED,
    BLOCKED_SIDEARM_INELIGIBLE,
    BLOCKED_SIDEARM_NOT_READY,
    DEFERRED_NATIVE_BUSY
}
