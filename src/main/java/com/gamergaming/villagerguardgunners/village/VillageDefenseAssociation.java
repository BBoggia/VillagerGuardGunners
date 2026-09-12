package com.gamergaming.villagerguardgunners.village;

import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import java.util.UUID;

/** Buildings retain the existing readiness owner even when network IDs merge or become aliases. */
public final class VillageDefenseAssociation {
    public static Association resolve(VillageRegistrySavedData villages, VillageDefenseNetworkSavedData networks, UUID building) {
        UUID village = villages.buildingVillage(building);
        var network = networks.networkForBuilding(building);
        return village == null || network == null ? null : new Association(village, building, network.networkId());
    }
    public record Association(UUID village, UUID building, UUID readinessNetwork) {}
    private VillageDefenseAssociation() {}
}
