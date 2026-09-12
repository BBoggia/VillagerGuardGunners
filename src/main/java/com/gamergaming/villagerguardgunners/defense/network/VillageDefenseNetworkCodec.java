package com.gamergaming.villagerguardgunners.defense.network;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseStatus;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseStatusCodec;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict, versioned network payload codec used by per-dimension SavedData. */
final class VillageDefenseNetworkCodec {
    private static final String VERSION_TAG = "Version";
    private static final String NETWORK_ID_TAG = "NetworkId";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String BUILDINGS_TAG = "Buildings";
    private static final String DEFENSE_STATUS_TAG = "DefenseStatus";
    private static final String BUILDING_ID_TAG = "BuildingId";
    private static final String ENTRY_ID_TAG = "EntryId";
    private static final String BUILDING_TYPE_TAG = "BuildingType";
    private static final String ANCHOR_TAG = "Anchor";

    private static final Set<String> LEGACY_NETWORK_KEYS = Set.of(
            VERSION_TAG, NETWORK_ID_TAG, DIMENSION_TAG, BUILDINGS_TAG);
    private static final Set<String> NETWORK_KEYS = Set.of(
            VERSION_TAG,
            NETWORK_ID_TAG,
            DIMENSION_TAG,
            BUILDINGS_TAG,
            DEFENSE_STATUS_TAG);
    private static final Set<String> BUILDING_KEYS = Set.of(
            VERSION_TAG,
            BUILDING_ID_TAG,
            ENTRY_ID_TAG,
            BUILDING_TYPE_TAG,
            ANCHOR_TAG);

    static CompoundTag encode(VillageDefenseNetwork network) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, network.schemaVersion());
        encoded.putUUID(NETWORK_ID_TAG, network.networkId());
        encoded.putString(DIMENSION_TAG, network.dimensionId().toString());
        ListTag buildings = new ListTag();
        for (VillageBuildingIdentity identity : network.buildings()) {
            CompoundTag building = new CompoundTag();
            building.putInt(VERSION_TAG, identity.schemaVersion());
            building.putUUID(BUILDING_ID_TAG, identity.buildingId());
            building.putString(ENTRY_ID_TAG, identity.entryId().toString());
            building.putString(BUILDING_TYPE_TAG, identity.buildingType().serializedName());
            building.putLong(ANCHOR_TAG, identity.anchorPosition().asLong());
            buildings.add(building);
        }
        encoded.put(BUILDINGS_TAG, buildings);
        encoded.put(DEFENSE_STATUS_TAG,
                VillageDefenseStatusCodec.encode(network.defenseStatus()));
        return encoded;
    }

    static @Nullable VillageDefenseNetwork decode(CompoundTag encoded) {
        if (encoded == null
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || !encoded.hasUUID(NETWORK_ID_TAG)
                || !encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                || !encoded.contains(BUILDINGS_TAG, Tag.TAG_LIST)) {
            return null;
        }
        int schemaVersion = encoded.getInt(VERSION_TAG);
        boolean legacy = schemaVersion == VillageDefenseNetwork.LEGACY_SCHEMA_VERSION;
        if (legacy
                ? !encoded.getAllKeys().equals(LEGACY_NETWORK_KEYS)
                : schemaVersion != VillageDefenseNetwork.CURRENT_SCHEMA_VERSION
                        || !encoded.getAllKeys().equals(NETWORK_KEYS)
                        || !encoded.contains(DEFENSE_STATUS_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(
                encoded.getString(DIMENSION_TAG));
        Tag rawBuildings = encoded.get(BUILDINGS_TAG);
        if (dimension == null
                || !(rawBuildings instanceof ListTag buildingList)
                || buildingList.isEmpty()
                || buildingList.size() > VillageDefenseNetwork.MAXIMUM_BUILDINGS
                || buildingList.getElementType() != Tag.TAG_COMPOUND) {
            return null;
        }
        List<VillageBuildingIdentity> buildings = new ArrayList<>(buildingList.size());
        for (Tag rawBuilding : buildingList) {
            if (!(rawBuilding instanceof CompoundTag building)
                    || !building.getAllKeys().equals(BUILDING_KEYS)
                    || !building.contains(VERSION_TAG, Tag.TAG_INT)
                    || building.getInt(VERSION_TAG)
                            != VillageBuildingIdentity.CURRENT_SCHEMA_VERSION
                    || !building.hasUUID(BUILDING_ID_TAG)
                    || !building.contains(ENTRY_ID_TAG, Tag.TAG_STRING)
                    || !building.contains(BUILDING_TYPE_TAG, Tag.TAG_STRING)
                    || !building.contains(ANCHOR_TAG, Tag.TAG_LONG)) {
                return null;
            }
            ResourceLocation entryId = ResourceLocation.tryParse(
                    building.getString(ENTRY_ID_TAG));
            VillageBuildingType buildingType = VillageBuildingType.byName(
                    building.getString(BUILDING_TYPE_TAG)).orElse(null);
            if (entryId == null || buildingType == null) {
                return null;
            }
            try {
                buildings.add(new VillageBuildingIdentity(
                        VillageBuildingIdentity.CURRENT_SCHEMA_VERSION,
                        building.getUUID(BUILDING_ID_TAG),
                        entryId,
                        buildingType,
                        dimension,
                        BlockPos.of(building.getLong(ANCHOR_TAG))));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        VillageDefenseStatus defenseStatus = legacy
                ? VillageDefenseStatus.peaceful()
                : VillageDefenseStatusCodec.decode(
                        encoded.getCompound(DEFENSE_STATUS_TAG));
        if (defenseStatus == null) {
            return null;
        }
        try {
            return new VillageDefenseNetwork(
                    VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                    encoded.getUUID(NETWORK_ID_TAG),
                    dimension,
                    buildings,
                    defenseStatus);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private VillageDefenseNetworkCodec() {
    }
}
