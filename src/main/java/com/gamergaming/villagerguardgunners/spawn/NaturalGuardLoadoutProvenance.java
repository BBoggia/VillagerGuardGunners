package com.gamergaming.villagerguardgunners.spawn;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable versioned identity shared by a naturally issued guard and gun. */
public record NaturalGuardLoadoutProvenance(
        int schemaVersion,
        ResourceLocation poolId,
        ResourceLocation gunId,
        UUID issuanceId,
        NaturalGuardDifficulty difficulty,
        NaturalGuardSpawnSource spawnSource) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public NaturalGuardLoadoutProvenance {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Natural loadout schema version must be positive");
        }
        poolId = Objects.requireNonNull(poolId, "poolId");
        gunId = Objects.requireNonNull(gunId, "gunId");
        issuanceId = Objects.requireNonNull(issuanceId, "issuanceId");
        difficulty = Objects.requireNonNull(difficulty, "difficulty");
        spawnSource = Objects.requireNonNull(spawnSource, "spawnSource");
    }

    public static NaturalGuardLoadoutProvenance issued(
            ResourceLocation poolId,
            ResourceLocation gunId,
            UUID issuanceId,
            NaturalGuardDifficulty difficulty,
            NaturalGuardSpawnSource spawnSource) {
        return new NaturalGuardLoadoutProvenance(
                CURRENT_SCHEMA_VERSION,
                poolId,
                gunId,
                issuanceId,
                difficulty,
                spawnSource);
    }
}
