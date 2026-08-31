package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadCommandLedgerRegistrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void ledgerIsSingleStackRegisteredAndAvailableWithBoundedPresentation()
            throws IOException {
        String registry = read(
                "src/main/java/com/gamergaming/villagerguardgunners/init/ModItems.java");
        String item = read(
                "src/main/java/com/gamergaming/villagerguardgunners/item/"
                        + "SquadCommandLedgerItem.java");

        assertAll(
                () -> assertTrue(registry.contains("SQUAD_COMMAND_LEDGER")),
                () -> assertTrue(registry.contains("\"squad_command_ledger\"")),
                () -> assertTrue(registry.contains(
                        "new SquadCommandLedgerItem(new Item.Properties().stacksTo(1))")),
                () -> assertTrue(registry.contains(
                        "event.accept(SQUAD_COMMAND_LEDGER.get())")),
                () -> assertTrue(item.contains("public boolean isFoil(ItemStack stack)")),
                () -> assertTrue(item.contains(
                        "SquadLedgerBindingCodec.readFrom(stack.getTag())")),
                () -> assertFalse(item.contains("getEntitiesOfClass(")),
                () -> assertFalse(item.contains("Packet")),
                () -> assertFalse(item.contains("tick(")));
    }

    @Test
    void ledgerHasRecipeModelAndCompleteEnglishFeedback() throws IOException {
        String recipe = read(
                "src/main/resources/data/villagerguardgunners/recipes/"
                        + "squad_command_ledger.json");
        String model = read(
                "src/main/resources/assets/villagerguardgunners/models/item/"
                        + "squad_command_ledger.json");
        String language = read(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json");

        assertAll(
                () -> assertTrue(recipe.contains("minecraft:crafting_shapeless")),
                () -> assertTrue(recipe.contains("minecraft:writable_book")),
                () -> assertTrue(recipe.contains("minecraft:goat_horn")),
                () -> assertTrue(recipe.contains("minecraft:emerald")),
                () -> assertTrue(recipe.contains(
                        "villagerguardgunners:squad_command_ledger")),
                () -> assertTrue(model.contains("minecraft:item/written_book")),
                () -> assertTrue(language.contains(
                        "\"item.villagerguardgunners.squad_command_ledger\": "
                                + "\"Squad Command Ledger\"")),
                () -> assertTrue(language.contains("squad_command_ledger.tooltip.blank")),
                () -> assertTrue(language.contains("squad_command_ledger.tooltip.bound")),
                () -> assertTrue(language.contains("squad_command_ledger.tooltip.corrupt")),
                () -> assertTrue(language.contains("squad_ledger.confirm_reassignment")),
                () -> assertTrue(language.contains("squad_ledger.unauthorized")));
    }

    @Test
    void releaseArtifactRequiresTheCompleteSquadLedgerSurface() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains(
                        "assets/villagerguardgunners/models/item/squad_command_ledger.json")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/recipes/squad_command_ledger.json")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/api/GuardSquadAccess.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/item/SquadCommandLedgerItem.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/squad/SquadLedgerBindingCodec.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/squad/SquadLedgerInteractions.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/squad/SquadLedgerService.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/squad/SquadRegistrySavedData.class")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
