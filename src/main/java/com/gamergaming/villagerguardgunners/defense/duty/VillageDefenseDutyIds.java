package com.gamergaming.villagerguardgunners.defense.duty;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Deterministic identities for stable, building-owned duty cells. */
public final class VillageDefenseDutyIds {
    private static final String SLOT_DOMAIN =
            "villagerguardgunners:village_defense_duty_slot:v1";

    public static UUID slotId(UUID buildingId, String slotKey) {
        Objects.requireNonNull(buildingId, "buildingId");
        Objects.requireNonNull(slotKey, "slotKey");
        String input = SLOT_DOMAIN + '\n' + buildingId + '\n' + slotKey;
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private VillageDefenseDutyIds() {
    }
}
