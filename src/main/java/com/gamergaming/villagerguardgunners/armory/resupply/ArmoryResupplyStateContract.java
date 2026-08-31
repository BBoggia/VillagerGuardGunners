package com.gamergaming.villagerguardgunners.armory.resupply;

import java.util.Objects;

/** Pure transition contract used by the later server runtime controller. */
public final class ArmoryResupplyStateContract {
    private ArmoryResupplyStateContract() {
    }

    /**
     * Resolves one state-changing event. Invalid transitions are programming
     * errors and fail immediately instead of silently retaining stale claims.
     */
    public static ArmoryResupplyState transition(
            ArmoryResupplyState current,
            ArmoryResupplyEvent event) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(event, "event");

        ArmoryResupplyState next = switch (current) {
            case IDLE -> event == ArmoryResupplyEvent.REQUEST_CREATED
                    ? ArmoryResupplyState.SEARCHING
                    : null;
            case SEARCHING -> switch (event) {
                case CANDIDATE_RESERVED -> ArmoryResupplyState.RESERVED;
                case NO_SUPPLY -> ArmoryResupplyState.COOLDOWN;
                case CANCELLED -> ArmoryResupplyState.IDLE;
                default -> null;
            };
            case RESERVED -> switch (event) {
                case TRAVEL_STARTED -> ArmoryResupplyState.TRAVELLING;
                case WITHDRAWAL_REACHED -> ArmoryResupplyState.WITHDRAWING;
                case NO_SUPPLY -> ArmoryResupplyState.COOLDOWN;
                case PATH_FAILED -> ArmoryResupplyState.UNREACHABLE;
                case CANCELLED -> ArmoryResupplyState.IDLE;
                default -> null;
            };
            case TRAVELLING -> switch (event) {
                case WITHDRAWAL_REACHED -> ArmoryResupplyState.WITHDRAWING;
                case NO_SUPPLY -> ArmoryResupplyState.COOLDOWN;
                case PATH_FAILED -> ArmoryResupplyState.UNREACHABLE;
                case CANCELLED -> ArmoryResupplyState.IDLE;
                default -> null;
            };
            case WITHDRAWING -> switch (event) {
                case TRANSFER_SUCCEEDED, NO_SUPPLY -> ArmoryResupplyState.COOLDOWN;
                case CANCELLED -> ArmoryResupplyState.IDLE;
                default -> null;
            };
            case COOLDOWN, UNREACHABLE -> switch (event) {
                case RETRY_READY, CANCELLED -> ArmoryResupplyState.IDLE;
                default -> null;
            };
        };
        if (next == null) {
            throw new IllegalStateException(
                    "Invalid Armory resupply transition: " + current + " + " + event);
        }
        return next;
    }

    /** True when a transition must release any candidate reservation first. */
    public static boolean releasesReservation(
            ArmoryResupplyState current,
            ArmoryResupplyEvent event) {
        ArmoryResupplyState next = transition(current, event);
        return current.ownsReservation() && !next.ownsReservation();
    }

    /** True when a transition must stop navigation owned by resupply first. */
    public static boolean releasesNavigation(
            ArmoryResupplyState current,
            ArmoryResupplyEvent event) {
        ArmoryResupplyState next = transition(current, event);
        return current.ownsNavigation() && !next.ownsNavigation();
    }
}
