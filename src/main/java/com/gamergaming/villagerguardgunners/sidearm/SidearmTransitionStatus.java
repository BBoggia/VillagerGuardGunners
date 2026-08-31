package com.gamergaming.villagerguardgunners.sidearm;

/** Outcome of an equipment transaction, separate from its policy decision. */
public enum SidearmTransitionStatus {
    ACTIVATED,
    RETURNED,
    ROLLED_BACK,
    RECOVERY_REQUIRED,
    ROLLBACK_FAILED
}
