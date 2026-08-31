package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerStepThirteenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void markerBoundaryIsClosedBoundedAndOneShot() throws IOException {
        String marker = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostMarker.java");
        String state = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostMarkerState.java");
        String piece = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/structure/VillageWatchtowerPiece.java");

        assertAll(
                () -> assertTrue(marker.contains("MAXIMUM_METADATA_LENGTH = 128")),
                () -> assertTrue(marker.contains("GUARD_POST_PREFIX = \"guard_post:\"")),
                () -> assertFalse(marker.contains("Command")),
                () -> assertFalse(marker.contains("ItemStack")),
                () -> assertEquals(8, integerConstant(state, "MAXIMUM_RECOGNIZED_MARKERS")),
                () -> assertTrue(state.contains("ClaimResult.ALREADY_CLAIMED")),
                () -> assertTrue(state.contains("ClaimResult.DUPLICATE_ACTION")),
                () -> assertTrue(state.contains("ClaimResult.LIMIT_REACHED")),
                () -> assertTrue(piece.contains("markerState.claim(position, marker.action())")),
                () -> assertTrue(piece.indexOf("markerState.claim(position, marker.action())")
                        < piece.indexOf("GuardPostSpawner.spawnMember(")),
                () -> assertTrue(piece.contains("Blocks.AIR.defaultBlockState()")),
                () -> assertFalse(piece.contains("getChunk(")),
                () -> assertFalse(piece.contains("getEntitiesOfClass(")));
    }

    @Test
    void spawnBoundaryUsesVanillaStructureFinalizationAndNoRecurringWork()
            throws IOException {
        String spawner = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostSpawner.java");
        String ids = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostIds.java");

        assertAll(
                () -> assertEquals(1, count(spawner, "addFreshEntity(")),
                () -> assertTrue(spawner.contains("MobSpawnType.STRUCTURE")),
                () -> assertTrue(spawner.contains("setPersistenceRequired()")),
                () -> assertTrue(spawner.contains("GuardPostIds.guardId(postId, memberIndex)")),
                () -> assertTrue(spawner.contains("placementBounds.isInside")),
                () -> assertFalse(spawner.contains("getEntitiesOfClass(")),
                () -> assertFalse(spawner.contains("getChunk(")),
                () -> assertFalse(spawner.contains("TickEvent")),
                () -> assertFalse(spawner.contains("TickTask")),
                () -> assertFalse(spawner.contains("ItemStack")),
                () -> assertFalse(spawner.contains("setItemSlot")),
                () -> assertFalse(spawner.contains("Commands")),
                () -> assertTrue(ids.contains("UUID.nameUUIDFromBytes")),
                () -> assertFalse(ids.contains("Random")));
    }

    @Test
    void identityPersistsStrictlyWithoutMakingTheGuardRecordExecutable()
            throws IOException {
        String codec = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostIdentityCodec.java");
        String mixin = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "mixin/GuardMixin.java");

        assertAll(
                () -> assertTrue(codec.contains("CURRENT_SCHEMA_VERSION")),
                () -> assertTrue(codec.contains("encoded.getAllKeys().equals(RECORD_KEYS)")),
                () -> assertFalse(codec.contains("Class.forName")),
                () -> assertFalse(codec.contains("ItemStack")),
                () -> assertTrue(mixin.contains("GuardPostIdentityCodec.readGuardRecord(compound)")),
                () -> assertTrue(mixin.contains("GuardPostIdentityCodec.writeGuardRecord(")));
    }

    private static int integerConstant(String source, String name) {
        String marker = name + " = ";
        int start = source.indexOf(marker) + marker.length();
        int end = source.indexOf(';', start);
        return Integer.parseInt(source.substring(start, end).trim());
    }

    private static int count(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
