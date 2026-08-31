package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GuardPostGroupsContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void compositionRegistryIsBoundedAtomicAndHasNoSpawnOrEquipmentAuthority()
            throws IOException {
        String registry = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/post/GuardPostGroups.java");
        String pool = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/post/GuardPostGroupPool.java");
        String format = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/post/GuardPostGroupFormat.java");
        String main = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(main.contains("GuardPostGroups::registerReloadListener")),
                () -> assertTrue(registry.contains("previous.groups()")),
                () -> assertTrue(registry.contains("volatile GuardPostGroupReloadSnapshot")),
                () -> assertTrue(registry.contains("readBoundedJson")),
                () -> assertTrue(format.contains("MAXIMUM_DOCUMENTS = 128")),
                () -> assertTrue(format.contains("MAXIMUM_DOCUMENT_CHARACTERS = 65_536")),
                () -> assertTrue(format.contains("MAXIMUM_JSON_DEPTH = 32")),
                () -> assertTrue(format.contains("MAXIMUM_MEMBERS_PER_VARIANT = 4")),
                () -> assertTrue(pool.contains("MAXIMUM_VARIANTS_PER_GROUP")),
                () -> assertTrue(pool.contains("NaturalGuardDifficulty.PEACEFUL")),
                () -> assertFalse(registry.contains("addFreshEntity")),
                () -> assertFalse(registry.contains("setItem")),
                () -> assertFalse(registry.contains("ItemStack")),
                () -> assertFalse(registry.contains("getEntitiesOfClass")),
                () -> assertFalse(registry.contains("getChunk(")),
                () -> assertFalse(registry.contains("tick(")),
                () -> assertFalse(pool.contains("RandomSource")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/guard_post_groups/watchtower.json")),
                () -> assertTrue(build.contains(
                        "worldgen/post/GuardPostGroups.class")));
    }

    @Test
    void stepTwelveResourceAndDocumentationArePackaged() throws IOException {
        assertAll(
                () -> assertTrue(Files.isRegularFile(PROJECT.resolve(
                        "src/main/resources/data/villagerguardgunners/guard_post_groups/watchtower.json"))),
                () -> assertTrue(Files.isRegularFile(PROJECT.resolve(
                        "docs/village-watchtowers-step-12.md"))),
                () -> assertTrue(read("README.md").contains(
                        "docs/village-watchtowers-step-12.md")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
