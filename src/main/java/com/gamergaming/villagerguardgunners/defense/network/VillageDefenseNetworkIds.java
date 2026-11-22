package com.gamergaming.villagerguardgunners.defense.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Domain-separated deterministic identities for persistent defense networks. */
public final class VillageDefenseNetworkIds {
    private static final String NETWORK_DOMAIN =
            "villagerguardgunners:village_defense_network:v1";

    public static UUID forBuilding(UUID buildingId) {
        UUID normalized = Objects.requireNonNull(buildingId, "buildingId");
        return UUID.nameUUIDFromBytes(
                (NETWORK_DOMAIN + '\n' + normalized)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private VillageDefenseNetworkIds() {
    }
}
