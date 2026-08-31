package com.gamergaming.villagerguardgunners.sidearm;

import java.util.Objects;

/**
 * Verified postcondition of an atomic weapon swap.
 *
 * <p>A successful transition must reach the exact opposite stable state. A
 * successful rollback must restore the same stable state. Any incomplete
 * rollback must surface {@link SidearmState#RECOVERY_REQUIRED}; it may never be
 * reported as a normal active layout.</p>
 */
public record SidearmTransitionResult(
        SidearmState before,
        SidearmState after,
        SidearmTransitionStatus status) {
    public SidearmTransitionResult {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(status, "status");

        boolean valid = switch (status) {
            case ACTIVATED -> before == SidearmState.PRIMARY_ACTIVE
                    && after == SidearmState.SIDEARM_ACTIVE;
            case RETURNED -> before == SidearmState.SIDEARM_ACTIVE
                    && after == SidearmState.PRIMARY_ACTIVE;
            case ROLLED_BACK -> before == after
                    && before != SidearmState.RECOVERY_REQUIRED;
            case RECOVERY_REQUIRED, ROLLBACK_FAILED ->
                    after == SidearmState.RECOVERY_REQUIRED;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Sidearm transition status does not match its state postcondition");
        }
    }

    public static SidearmTransitionResult activated() {
        return new SidearmTransitionResult(
                SidearmState.PRIMARY_ACTIVE,
                SidearmState.SIDEARM_ACTIVE,
                SidearmTransitionStatus.ACTIVATED);
    }

    public static SidearmTransitionResult returned() {
        return new SidearmTransitionResult(
                SidearmState.SIDEARM_ACTIVE,
                SidearmState.PRIMARY_ACTIVE,
                SidearmTransitionStatus.RETURNED);
    }

    public static SidearmTransitionResult rolledBack(SidearmState restoredState) {
        return new SidearmTransitionResult(
                restoredState,
                restoredState,
                SidearmTransitionStatus.ROLLED_BACK);
    }

    public static SidearmTransitionResult recoveryRequired(SidearmState before) {
        return new SidearmTransitionResult(
                before,
                SidearmState.RECOVERY_REQUIRED,
                SidearmTransitionStatus.RECOVERY_REQUIRED);
    }

    public static SidearmTransitionResult rollbackFailed(SidearmState before) {
        return new SidearmTransitionResult(
                before,
                SidearmState.RECOVERY_REQUIRED,
                SidearmTransitionStatus.ROLLBACK_FAILED);
    }
}
