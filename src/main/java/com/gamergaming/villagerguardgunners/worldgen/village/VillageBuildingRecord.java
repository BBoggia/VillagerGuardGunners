package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Objects;

/** Persisted identity plus fail-closed at-most-once action claims. */
public record VillageBuildingRecord(
        VillageBuildingIdentity identity,
        int claimedActions) {

    public VillageBuildingRecord {
        Objects.requireNonNull(identity, "identity");
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
    }

    public static VillageBuildingRecord anchored(VillageBuildingIdentity identity) {
        return new VillageBuildingRecord(
                identity,
                VillageBuildingAction.ANCHOR.mask());
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
        return new VillageBuildingRecord(identity, claimedActions | action.mask());
    }

    public boolean canClaim(VillageBuildingAction action) {
        Objects.requireNonNull(action, "action");
        return (claimedActions & action.prerequisiteMask()) == action.prerequisiteMask();
    }
}
