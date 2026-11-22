package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PackAuthoredDefenseDutyContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void definitionFormatIsClosedBoundedAndRotationAware() throws IOException {
        String definitions = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingDefinitions.java");
        String slotDefinition = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "defense/duty/VillageDefenseDutySlotDefinition.java");

        assertAll(
                () -> assertTrue(definitions.contains("\"duty_slots\"")),
                () -> assertTrue(definitions.contains("DUTY_SLOT_FIELDS")),
                () -> assertTrue(definitions.contains(
                        "VillageDefenseDutyLayout.MAXIMUM_SLOTS_PER_BUILDING")),
                () -> assertTrue(definitions.contains("MAXIMUM_SLOT_OFFSET")),
                () -> assertTrue(definitions.contains("rejectUnknownFields(slot")),
                () -> assertTrue(slotDefinition.contains("rotation.rotate(facing)")),
                () -> assertTrue(slotDefinition.contains("case CLOCKWISE_90")),
                () -> assertFalse(slotDefinition.contains("getBlockState")),
                () -> assertFalse(slotDefinition.contains("getChunk(")),
                () -> assertFalse(slotDefinition.contains("getEntities")));
    }

    @Test
    void generationPinsStartupDefinitionAndPendingStatePinsExactLayout()
            throws IOException {
        String lifecycle = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingAnchorLifecycle.java");
        String codec = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingRecordCodec.java");

        assertAll(
                () -> assertTrue(lifecycle.contains(
                        "VillageBuildingPoolIntegration.installationSnapshot()")),
                () -> assertTrue(lifecycle.contains(
                        "VillageBuildingPoolIntegration.startupDefinition")),
                () -> assertTrue(lifecycle.contains(".claimLayout(pending.dutyLayout())")),
                () -> assertTrue(codec.contains("PENDING_DUTY_LAYOUT_TAG")),
                () -> assertTrue(codec.contains("LEGACY_PENDING_DATA_VERSION")),
                () -> assertTrue(codec.contains("VillageDefenseDutyLayoutCodec.encode")),
                () -> assertTrue(codec.contains("VillageDefenseDutyLayoutCodec.decode")));
    }

    @Test
    void packGuideDiagnosticsAndArtifactBoundaryExposeTheHook() throws IOException {
        String guide = read("docs/examples/village-buildings-datapack/README.md");
        String example = read("docs/examples/village-buildings-datapack/data/vgg_example/"
                + "village_buildings/plains_sentry_post.json");
        String commands = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "command/VillageBuildingCommands.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(guide.contains("one to four")),
                () -> assertTrue(guide.contains("-16 through 16")),
                () -> assertTrue(guide.contains("do not place blocks")),
                () -> assertTrue(example.contains("\"duty_slots\"")),
                () -> assertTrue(commands.contains("duties=")),
                () -> assertTrue(build.contains(
                        "VillageDefenseDutySlotDefinition.class")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
