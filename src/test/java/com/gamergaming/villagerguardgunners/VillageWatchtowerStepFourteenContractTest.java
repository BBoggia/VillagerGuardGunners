package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerStepFourteenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void generatedMembersUseTheExistingOneShotLoadoutRoleAndSquadSystems()
            throws IOException {
        String spawner = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostSpawner.java");
        String integration = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "spawn/NaturalGuardSpawnIntegration.java");
        String assignment = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostAssignmentCodec.java");

        assertAll(
                () -> assertTrue(spawner.contains(
                        "GuardPostAssignment.pending(member, generationDifficulty)")),
                () -> assertFalse(spawner.contains("ItemStack")),
                () -> assertFalse(spawner.contains("setItemSlot")),
                () -> assertTrue(integration.contains("selectPostAfterJoin")),
                () -> assertTrue(integration.contains("NaturalGuardLoadoutSelector.select(")),
                () -> assertTrue(integration.contains("NaturalGuardLoadoutIssuance.issue(")),
                () -> assertTrue(integration.contains("GuardPostIds.memberLoadoutSeed")),
                () -> assertTrue(integration.contains("assignment.generationDifficulty()")),
                () -> assertTrue(integration.contains(
                        "config.watchtowers.guardGunUpgradeChance(configured.upgradeChance())")),
                () -> assertTrue(integration.contains("countNearbyCandidates")),
                () -> assertTrue(integration.contains("GuardRoles.get(assignment.roleId())")),
                () -> assertTrue(integration.contains("SquadMembership.assigned(squadId)")),
                () -> assertTrue(assignment.contains(
                        "encoded.getAllKeys()")),
                () -> assertFalse(assignment.contains("ItemStack")),
                () -> assertFalse(assignment.contains("Class.forName")));
    }

    @Test
    void armoryMarkerIsClosedBoundedDeterministicAndUnticked() throws IOException {
        String marker = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostMarker.java");
        String placer = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostArmoryPlacer.java");
        String loot = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostArmoryLoot.java");
        String armory = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "armory/ArmoryBlockEntity.java");
        String armoryBlock = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "armory/ArmoryBlock.java");
        String piece = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/structure/VillageWatchtowerPiece.java");

        assertAll(
                () -> assertTrue(marker.contains("ARMORY_PREFIX = \"armory:\"")),
                () -> assertTrue(marker.contains("SQUAD_LOCKED(\"squad\")")),
                () -> assertTrue(marker.contains("PUBLIC(\"public\")")),
                () -> assertTrue(marker.contains("\"empty\".equals(loot)")),
                () -> assertTrue(placer.contains("GuardPostIds.armoryLootSeed(postId)")),
                () -> assertTrue(placer.contains("prepareGenerated(")),
                () -> assertFalse(placer.contains("LootTable")),
                () -> assertTrue(loot.contains("MAXIMUM_LOOT_STACKS = 64")),
                () -> assertTrue(loot.contains("LootContextParamSets.CHEST")),
                () -> assertTrue(loot.contains("getRandomItemsRaw(")),
                () -> assertTrue(loot.contains("isSameThread()")),
                () -> assertTrue(loot.contains("hasBoundedDefinition(")),
                () -> assertTrue(loot.contains("instanceof ConstantValue")),
                () -> assertTrue(loot.contains("instanceof LootItem")),
                () -> assertTrue(loot.contains("TimelessAPI.getCommonAmmoIndex")),
                () -> assertTrue(armory.contains("initializeGenerated(")),
                () -> assertTrue(armory.contains("queueGeneratedInitialization()")),
                () -> assertTrue(armory.contains("generatedIdentity != null")),
                () -> assertTrue(piece.contains("GuardPostArmoryPlacer.place(")),
                () -> assertFalse(placer.contains("getChunk(")),
                () -> assertFalse(placer.contains("getEntitiesOfClass(")),
                () -> assertFalse(placer.contains("TickTask")),
                () -> assertFalse(loot.contains("ForgeHooks.modifyLoot")),
                () -> assertFalse(placer.contains("LOGGER")),
                () -> assertTrue(armoryBlock.contains("return null;")));
    }

    @Test
    void builtInLootIsDeclaredAmmoOnlyAndArtifactGateIncludesIt() throws IOException {
        JsonObject loot = JsonParser.parseString(read(
                "src/main/resources/data/villagerguardgunners/loot_tables/chests/"
                        + "watchtower_armory.json")).getAsJsonObject();
        String serialized = loot.toString();
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(serialized.contains("tacz:ammo")),
                () -> assertTrue(serialized.contains("AmmoId")),
                () -> assertFalse(serialized.contains("tacz:modern_kinetic_gun")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/loot_tables/chests/watchtower_armory.json")),
                () -> assertTrue(build.contains("startsWith('armory:')")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
