package com.gamergaming.villagerguardgunners.defense.network;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseStatus;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Immutable identity and bounded building membership for one defense network. */
public record VillageDefenseNetwork(
        int schemaVersion,
        UUID networkId,
        ResourceLocation dimensionId,
        List<VillageBuildingIdentity> buildings,
        VillageDefenseStatus defenseStatus) {
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAXIMUM_BUILDINGS = 4_096;

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public VillageDefenseNetwork {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported defense-network schema");
        }
        networkId = Objects.requireNonNull(networkId, "networkId");
        if (NIL_UUID.equals(networkId)) {
            throw new IllegalArgumentException("Defense-network ID cannot be the nil UUID");
        }
        dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        defenseStatus = Objects.requireNonNull(defenseStatus, "defenseStatus");
        if (buildings == null || buildings.isEmpty()
                || buildings.size() > MAXIMUM_BUILDINGS) {
            throw new IllegalArgumentException(
                    "Defense-network building count is outside its hard bound");
        }

        ArrayList<VillageBuildingIdentity> normalized = new ArrayList<>(buildings);
        normalized.sort(Comparator.comparing(VillageBuildingIdentity::buildingId));
        Set<UUID> buildingIds = new HashSet<>();
        Set<BlockPos> anchors = new HashSet<>();
        boolean networkIdDerivedFromMember = false;
        for (VillageBuildingIdentity identity : normalized) {
            Objects.requireNonNull(identity, "building identity");
            if (!dimensionId.equals(identity.dimensionId())
                    || !buildingIds.add(identity.buildingId())
                    || !anchors.add(identity.anchorPosition())) {
                throw new IllegalArgumentException(
                        "Defense-network buildings conflict or cross dimensions");
            }
            networkIdDerivedFromMember |= networkId.equals(
                    VillageDefenseNetworkIds.forBuilding(identity.buildingId()));
        }
        if (!networkIdDerivedFromMember) {
            throw new IllegalArgumentException(
                    "Defense-network ID is not derived from one of its buildings");
        }
        buildings = List.copyOf(normalized);
    }

    /** Source-compatible constructor for callers creating a peaceful network. */
    public VillageDefenseNetwork(
            int schemaVersion,
            UUID networkId,
            ResourceLocation dimensionId,
            List<VillageBuildingIdentity> buildings) {
        this(schemaVersion, networkId, dimensionId, buildings,
                VillageDefenseStatus.peaceful());
    }

    public static VillageDefenseNetwork forBuilding(VillageBuildingIdentity identity) {
        VillageBuildingIdentity normalized = Objects.requireNonNull(identity, "identity");
        return new VillageDefenseNetwork(
                CURRENT_SCHEMA_VERSION,
                VillageDefenseNetworkIds.forBuilding(normalized.buildingId()),
                normalized.dimensionId(),
                List.of(normalized),
                VillageDefenseStatus.peaceful());
    }

    public VillageDefenseNetwork withBuilding(VillageBuildingIdentity identity) {
        VillageBuildingIdentity normalized = Objects.requireNonNull(identity, "identity");
        for (VillageBuildingIdentity current : buildings) {
            if (current.buildingId().equals(normalized.buildingId())) {
                if (!current.equals(normalized)) {
                    throw new IllegalArgumentException(
                            "A defense-network building ID has conflicting identity data");
                }
                return this;
            }
            if (current.anchorPosition().equals(normalized.anchorPosition())) {
                throw new IllegalArgumentException(
                        "A defense-network physical anchor is already occupied");
            }
        }
        if (buildings.size() >= MAXIMUM_BUILDINGS) {
            throw new IllegalStateException("Defense-network member limit reached");
        }
        ArrayList<VillageBuildingIdentity> replacement = new ArrayList<>(buildings);
        replacement.add(normalized);
        return new VillageDefenseNetwork(
                CURRENT_SCHEMA_VERSION,
                networkId,
                dimensionId,
                replacement,
                defenseStatus);
    }

    public VillageDefenseNetwork withDefenseStatus(
            VillageDefenseStatus replacement) {
        VillageDefenseStatus normalized = Objects.requireNonNull(
                replacement, "replacement");
        if (defenseStatus.equals(normalized)) {
            return this;
        }
        return new VillageDefenseNetwork(
                CURRENT_SCHEMA_VERSION,
                networkId,
                dimensionId,
                buildings,
                normalized);
    }
}
