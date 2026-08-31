package com.gamergaming.villagerguardgunners.sidearm;

/** Conditions that may return an active sidearm to its private storage. */
public enum SidearmReturnReason {
    NONE,
    FEATURE_DISABLED,
    ENGAGEMENT_ENDED,
    TARGET_CHANGED,
    SIDEARM_UNUSABLE,
    PRIMARY_ALLOWED_AGAIN,
    PRIMARY_STANDOFF_CLEARED
}
