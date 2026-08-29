package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

final class NaturalGuardSpawnIntegrationTest {

    @Test
    void mapsSupportedMinecraftSpawnReasonsWithoutBroadeningOtherOrigins() {
        assertAll(
                () -> assertEquals(
                        NaturalGuardSpawnSource.STRUCTURE,
                        NaturalGuardSpawnIntegration.mapSpawnType(MobSpawnType.STRUCTURE)),
                () -> assertEquals(
                        NaturalGuardSpawnSource.CONVERSION,
                        NaturalGuardSpawnIntegration.mapSpawnType(MobSpawnType.CONVERSION)),
                () -> assertEquals(
                        NaturalGuardSpawnSource.SPAWN_EGG,
                        NaturalGuardSpawnIntegration.mapSpawnType(MobSpawnType.SPAWN_EGG)),
                () -> assertEquals(
                        NaturalGuardSpawnSource.COMMAND,
                        NaturalGuardSpawnIntegration.mapSpawnType(MobSpawnType.COMMAND)),
                () -> assertEquals(
                        NaturalGuardSpawnSource.OTHER,
                        NaturalGuardSpawnIntegration.mapSpawnType(MobSpawnType.NATURAL)),
                () -> assertEquals(
                        NaturalGuardSpawnSource.OTHER,
                        NaturalGuardSpawnIntegration.mapSpawnType(MobSpawnType.SPAWNER)));
    }

    @Test
    void mapsEveryWorldDifficultyExplicitly() {
        assertAll(
                () -> assertEquals(
                        NaturalGuardDifficulty.PEACEFUL,
                        NaturalGuardSpawnIntegration.mapDifficulty(Difficulty.PEACEFUL)),
                () -> assertEquals(
                        NaturalGuardDifficulty.EASY,
                        NaturalGuardSpawnIntegration.mapDifficulty(Difficulty.EASY)),
                () -> assertEquals(
                        NaturalGuardDifficulty.NORMAL,
                        NaturalGuardSpawnIntegration.mapDifficulty(Difficulty.NORMAL)),
                () -> assertEquals(
                        NaturalGuardDifficulty.HARD,
                        NaturalGuardSpawnIntegration.mapDifficulty(Difficulty.HARD)));
    }

    @Test
    void selectedCandidateCarriesOnlyTheIssuanceInputs() {
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                id("hard"),
                id("rifle"),
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);
        NaturalGuardLoadoutProvenance provenance = NaturalGuardLoadoutProvenance.issued(
                candidate.poolId(),
                candidate.gunId(),
                UUID.randomUUID(),
                candidate.difficulty(),
                candidate.spawnSource());

        assertAll(
                () -> assertEquals(id("hard"), candidate.poolId()),
                () -> assertEquals(id("rifle"), candidate.gunId()),
                () -> assertEquals(NaturalGuardDifficulty.HARD, candidate.difficulty()),
                () -> assertEquals(
                        NaturalGuardSpawnSource.STRUCTURE,
                        candidate.spawnSource()),
                () -> assertNotNull(provenance.issuanceId()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
