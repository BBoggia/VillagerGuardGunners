package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OrdersLedgerRegistrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void ledgerIsRegisteredOnceAndPlacedInTheToolsTab() throws IOException {
        String bootstrap = read("src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String registry = read("src/main/java/com/gamergaming/villagerguardgunners/init/ModItems.java");
        String item = read("src/main/java/com/gamergaming/villagerguardgunners/item/OrdersLedgerItem.java");

        assertAll(
                () -> assertTrue(bootstrap.contains("ModItems.register(modEventBus)")),
                () -> assertTrue(registry.contains("DeferredRegister<Item> ITEMS")),
                () -> assertTrue(registry.contains("\"orders_ledger\"")),
                () -> assertTrue(registry.contains("new Item.Properties().stacksTo(1)")),
                () -> assertTrue(registry.contains("CreativeModeTabs.TOOLS_AND_UTILITIES")),
                () -> assertTrue(item.contains("GuardOrderTemplate.readFrom(stack.getTag())")),
                () -> assertTrue(item.contains("public boolean isFoil(ItemStack stack)")),
                () -> assertFalse(item.contains("getEntitiesOfClass(")),
                () -> assertFalse(item.contains("Packet")),
                () -> assertFalse(item.contains("tick(")));
    }

    @Test
    void ledgerHasARecipeModelAndCompleteEnglishTooltipText() throws IOException {
        String recipe = read("src/main/resources/data/villagerguardgunners/recipes/orders_ledger.json");
        String model = read("src/main/resources/assets/villagerguardgunners/models/item/orders_ledger.json");
        String language = read("src/main/resources/assets/villagerguardgunners/lang/en_us.json");

        assertAll(
                () -> assertTrue(recipe.contains("minecraft:crafting_shapeless")),
                () -> assertTrue(recipe.contains("minecraft:writable_book")),
                () -> assertTrue(recipe.contains("minecraft:emerald")),
                () -> assertTrue(recipe.contains("villagerguardgunners:orders_ledger")),
                () -> assertTrue(model.contains("minecraft:item/generated")),
                () -> assertTrue(model.contains("minecraft:item/writable_book")),
                () -> assertTrue(language.contains(
                        "\"item.villagerguardgunners.orders_ledger\": \"Orders Ledger\"")),
                () -> assertTrue(language.contains(
                        "item.villagerguardgunners.orders_ledger.tooltip.blank")),
                () -> assertTrue(language.contains(
                        "item.villagerguardgunners.orders_ledger.tooltip.stored")),
                () -> assertTrue(language.contains(
                        "item.villagerguardgunners.orders_ledger.tooltip.corrupt")),
                () -> assertTrue(language.contains(
                        "item.villagerguardgunners.orders_ledger.tooltip.unsupported")));
    }

    @Test
    void releaseArtifactRequiresTheCompleteLedgerSurface() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains(
                        "assets/villagerguardgunners/models/item/orders_ledger.json")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/recipes/orders_ledger.json")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/init/ModItems.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/item/OrdersLedgerItem.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/orders/GuardOrderPermissions.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/orders/GuardOrderTemplate.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/orders/GuardOrderTemplateInteractions.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/orders/GuardOrderTemplateService.class")));
    }

    @Test
    void ledgerWorkflowAndManualReleaseBoundaryAreDocumented() throws IOException {
        String guide = read("docs/per-guard-orders.md");
        String readme = read("README.md");
        String changelog = read("CHANGELOG.md");
        String curseForgeDescription = read("docs/curseforge-description.md");
        String releaseNotes = read("docs/curseforge-release-notes-1.1.md");
        String checklist = read("docs/release-checklist.md");
        String uploadGuide = read("docs/curseforge-upload-1.1.md");

        assertAll(
                () -> assertTrue(guide.contains("Book and Quill")),
                () -> assertTrue(guide.contains("Use a blank Orders Ledger")),
                () -> assertTrue(guide.contains("Sneak-use")),
                () -> assertTrue(guide.contains("never copies or transfers weapons")),
                () -> assertTrue(guide.contains("strict versioned format")),
                () -> assertTrue(readme.contains("Craft an **Orders Ledger**")),
                () -> assertTrue(changelog.contains("A reusable Orders Ledger")),
                () -> assertTrue(curseForgeDescription.contains("reusable Orders Ledger")),
                () -> assertTrue(releaseNotes.contains("A reusable Orders Ledger")),
                () -> assertTrue(checklist.contains("ORDER-LEDGER-01")),
                () -> assertTrue(checklist.contains("ORDER-LEDGER-02")),
                () -> assertTrue(checklist.contains("ORDER-LEDGER-03")),
                () -> assertTrue(checklist.contains("ORDER-LEDGER-04")),
                () -> assertTrue(uploadGuide.contains("docs/curseforge-description.md")),
                () -> assertTrue(uploadGuide.contains(
                        "docs/curseforge-release-notes-1.1.md")),
                () -> assertFalse(uploadGuide.contains("`DESCRIPTION.md`")),
                () -> assertFalse(uploadGuide.contains("`CHANGELOG-1.1.md`")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
