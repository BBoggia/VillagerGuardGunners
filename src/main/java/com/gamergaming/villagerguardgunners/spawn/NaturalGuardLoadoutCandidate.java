package com.gamergaming.villagerguardgunners.spawn;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable result of one successful natural-loadout selection.
 *
 * <p>The candidate is deliberately only policy metadata. TaCZ item resolution
 * and atomic weapon/ammunition insertion belong to the issuance stage.</p>
 */
public record NaturalGuardLoadoutCandidate(
        ResourceLocation poolId,
        ResourceLocation gunId,
        @Nullable ResourceLocation sidearmId,
        NaturalGuardDifficulty difficulty,
        NaturalGuardSpawnSource spawnSource) {

    public NaturalGuardLoadoutCandidate(
            ResourceLocation poolId,
            ResourceLocation gunId,
            NaturalGuardDifficulty difficulty,
            NaturalGuardSpawnSource spawnSource) {
        this(poolId, gunId, null, difficulty, spawnSource);
    }

    public NaturalGuardLoadoutCandidate {
        poolId = Objects.requireNonNull(poolId, "poolId");
        gunId = Objects.requireNonNull(gunId, "gunId");
        difficulty = Objects.requireNonNull(difficulty, "difficulty");
        spawnSource = Objects.requireNonNull(spawnSource, "spawnSource");
    }
}
