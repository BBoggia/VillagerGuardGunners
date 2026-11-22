package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerStepSixteenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void futurePostDataIsQuarantinedPreservedAndNeverReinterpreted() throws IOException {
        String identityCodec = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostIdentityCodec.java");
        String assignmentCodec = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostAssignmentCodec.java");
        String armoryCodec = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostArmoryIdentityCodec.java");
        String integration = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "spawn/NaturalGuardSpawnIntegration.java");
        String armory = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "armory/ArmoryBlockEntity.java");

        assertAll(
                () -> assertTrue(identityCodec.contains("copyEncodedRecord(")),
                () -> assertTrue(identityCodec.contains("writeGuardRecordPreservingUnreadable(")),
                () -> assertTrue(assignmentCodec.contains("copyEncodedRecord(")),
                () -> assertTrue(assignmentCodec.contains("writeGuardRecordPreservingUnreadable(")),
                () -> assertTrue(armoryCodec.contains("copyEncodedRecord(")),
                () -> assertTrue(armoryCodec.contains("writePreservingUnreadable(")),
                () -> assertTrue(integration.contains(
                        "GuardPostAccess.hasUnreadableIdentity(guard)")),
                () -> assertTrue(integration.contains(
                        "GuardPostAssignmentAccess.hasUnreadableAssignment(guard)")),
                () -> assertTrue(armory.contains("unreadableGeneratedIdentity != null")),
                () -> assertTrue(armory.contains(
                        "GuardPostArmoryIdentityCodec.writePreservingUnreadable(")));
    }

    @Test
    void compatibilitySurfaceUsesStandardDataAndHasNoVanillaPoolMutation()
            throws IOException {
        String structure = read("src/main/resources/data/villagerguardgunners/"
                + "worldgen/structure/watchtower.json");
        String structureSet = read("src/main/resources/data/villagerguardgunners/"
                + "worldgen/structure_set/watchtowers.json");
        String biomeTag = read("src/main/resources/data/villagerguardgunners/"
                + "tags/worldgen/biome/has_structure/watchtower.json");
        String production;
        try (Stream<Path> files = Files.walk(PROJECT.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/structure"))) {
            production = files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        assertAll(
                () -> assertTrue(structure.contains(
                        "#villagerguardgunners:has_structure/watchtower")),
                () -> assertTrue(structureSet.contains(
                        "\"structure\": \"villagerguardgunners:watchtower\"")),
                () -> assertTrue(biomeTag.contains("#minecraft:has_structure/village_plains")),
                () -> assertFalse(production.contains("StructureTemplatePool")),
                () -> assertFalse(production.contains("JigsawPlacement")),
                () -> assertFalse(production.contains("BuiltInRegistries.TEMPLATE_POOL")),
                () -> assertFalse(production.contains("net.minecraft.client")));
    }

    @Test
    void compatibilityErrorsAreBoundedAndOperatorVisible() throws IOException {
        String groups = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostGroups.java");
        String commands = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "command/WatchtowerCommands.java");
        String documentation = read("docs/village-watchtowers.md");

        assertAll(
                () -> assertTrue(groups.contains("safe.length() <= 512")),
                () -> assertTrue(commands.contains("Commands.literal(\"errors\")")),
                () -> assertTrue(commands.contains("errors.subList(from, to)")),
                () -> assertTrue(commands.contains("GROUPS_PER_PAGE = 8")),
                () -> assertTrue(documentation.contains(
                        "/vgg watchtowers errors [page]")));
    }

    @Test
    void interruptedHandoffsAndCorruptPieceRotationsFailWithoutDataLoss()
            throws IOException {
        String integration = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "spawn/NaturalGuardSpawnIntegration.java");
        String armory = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "armory/ArmoryBlockEntity.java");
        String piece = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/structure/VillageWatchtowerPiece.java");
        int queueStart = integration.indexOf("private static void queueSelection(");
        int queuedStart = integration.indexOf("private static void selectQueuedAfterJoin(");
        int postStart = integration.indexOf("private static void selectPostAfterJoin(");

        assertAll(
                () -> assertTrue(queueStart >= 0 && queuedStart > queueStart),
                () -> assertFalse(integration.substring(queueStart, queuedStart)
                        .contains("claimNaturalLoadoutSpawnSource")),
                () -> assertFalse(integration.substring(queueStart, queuedStart)
                        .contains("GuardPostAssignmentAccess.claim")),
                () -> assertTrue(integration.substring(queuedStart, postStart)
                        .contains("isLiveIn(level, guard)")),
                () -> assertTrue(integration.substring(queuedStart, postStart)
                        .contains("GuardPostAssignmentAccess.claim(guard)")),
                () -> assertTrue(armory.contains("GENERATED_SQUAD_PENDING_TAG")),
                () -> assertTrue(armory.contains("resolveGeneratedSquad(serverLevel)")),
                () -> assertTrue(armory.contains("GuardPostSquads.ensureRegistered(")),
                () -> assertTrue(armory.contains("setSquadLock(null)")),
                () -> assertTrue(piece.contains("hasValidPersistedRotation(tag)")),
                () -> assertTrue(piece.contains("if (!placementEnabled)")),
                () -> assertTrue(piece.contains("tag.putBoolean(PLACEMENT_ENABLED_TAG")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
