package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Objects;
import java.util.Optional;

/** Persisted identity plus fail-closed at-most-once action claims. */
public record VillageBuildingRecord(
        VillageBuildingIdentity identity,
        int claimedActions,
        Optional<VillageBuildingPendingActivation> pendingActivation) {

    public VillageBuildingRecord {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(pendingActivation, "pendingActivation");
        if ((claimedActions & ~VillageBuildingAction.supportedMask()) != 0
                || (claimedActions & VillageBuildingAction.ANCHOR.mask()) == 0) {
            throw new IllegalArgumentException(
                    "Village-building record has an unsupported action state");
        }
        for (VillageBuildingAction action : VillageBuildingAction.values()) {
            if ((claimedActions & action.mask()) != 0
                    && (claimedActions & action.prerequisiteMask())
                            != action.prerequisiteMask()) {
                throw new IllegalArgumentException(
                        "Village-building record is missing an action prerequisite");
            }
        }
        pendingActivation.ifPresent(pending -> {
            VillageBuildingAction expected = identity.buildingType()
                    == VillageBuildingType.ARMORY
                    ? VillageBuildingAction.INITIAL_STOCK
                    : VillageBuildingAction.GARRISON;
            if (pending.requiredAction() != expected
                    || !identity.equals(pending.dutyLayout().building())
                    || (claimedActions & expected.mask()) != 0) {
                throw new IllegalArgumentException(
                        "Village-building pending action does not match its identity or claims");
            }
        });
    }

    public VillageBuildingRecord(
            VillageBuildingIdentity identity,
            int claimedActions) {
        this(identity, claimedActions, Optional.empty());
    }

    public static VillageBuildingRecord anchored(VillageBuildingIdentity identity) {
        return new VillageBuildingRecord(
                identity,
                VillageBuildingAction.ANCHOR.mask());
    }

    public static VillageBuildingRecord anchored(
            VillageBuildingIdentity identity,
            VillageBuildingPendingActivation pendingActivation) {
        return new VillageBuildingRecord(
                identity,
                VillageBuildingAction.ANCHOR.mask(),
                Optional.of(Objects.requireNonNull(
                        pendingActivation, "pendingActivation")));
    }

    public boolean hasClaimed(VillageBuildingAction action) {
        Objects.requireNonNull(action, "action");
        return (claimedActions & action.mask()) != 0;
    }

    public VillageBuildingRecord claim(VillageBuildingAction action) {
        Objects.requireNonNull(action, "action");
        if (hasClaimed(action)) {
            return this;
        }
        if (!canClaim(action)) {
            throw new IllegalStateException(
                    "Village-building action prerequisites have not been claimed");
        }
        Optional<VillageBuildingPendingActivation> updatedPending = pendingActivation
                .filter(pending -> pending.requiredAction() != action);
        return new VillageBuildingRecord(
                identity,
                claimedActions | action.mask(),
                updatedPending);
    }

    public boolean canClaim(VillageBuildingAction action) {
        Objects.requireNonNull(action, "action");
        return (claimedActions & action.prerequisiteMask()) == action.prerequisiteMask();
    }

    public VillageBuildingRecord withPendingActivation(
            VillageBuildingPendingActivation pending) {
        Objects.requireNonNull(pending, "pending");
        if (hasClaimed(pending.requiredAction())) {
            return this;
        }
        return new VillageBuildingRecord(identity, claimedActions, Optional.of(pending));
    }

    public VillageBuildingRecord withoutPendingActivation() {
        return pendingActivation.isEmpty()
                ? this
                : new VillageBuildingRecord(identity, claimedActions, Optional.empty());
    }
}
