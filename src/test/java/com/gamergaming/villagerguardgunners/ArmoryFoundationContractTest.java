package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArmoryFoundationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void armoryRegistersItsCompletePlayerFacingSurface() throws IOException {
        String mod = read("src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String blocks = read("src/main/java/com/gamergaming/villagerguardgunners/init/ModBlocks.java");
        String entities = read("src/main/java/com/gamergaming/villagerguardgunners/init/ModBlockEntities.java");
        String items = read("src/main/java/com/gamergaming/villagerguardgunners/init/ModItems.java");
        String menus = read("src/main/java/com/gamergaming/villagerguardgunners/init/ModMenus.java");
        String client = read("src/main/java/com/gamergaming/villagerguardgunners/client/ClientEventHandler.java");

        assertAll(
                () -> assertTrue(mod.contains("ModBlocks.register(modEventBus)")),
                () -> assertTrue(mod.contains("ModBlockEntities.register(modEventBus)")),
                () -> assertTrue(blocks.contains("new ArmoryBlock")),
                () -> assertTrue(entities.contains("ArmoryBlockEntity::new")),
                () -> assertTrue(items.contains("new BlockItem(ModBlocks.ARMORY.get()")),
                () -> assertTrue(menus.contains("RegistryObject<MenuType<ArmoryMenu>> ARMORY")),
                () -> assertTrue(client.contains("ArmoryScreen::new")),
                () -> assertTrue(exists("src/main/resources/assets/villagerguardgunners/blockstates/armory.json")),
                () -> assertTrue(exists("src/main/resources/assets/villagerguardgunners/models/block/armory.json")),
                () -> assertTrue(exists("src/main/resources/assets/villagerguardgunners/models/item/armory.json")),
                () -> assertTrue(exists("src/main/resources/data/villagerguardgunners/recipes/armory.json")),
                () -> assertTrue(exists("src/main/resources/data/villagerguardgunners/loot_tables/blocks/armory.json")));
    }

    @Test
    void storageIsBoundedAutomatablePersistentAndUnticked() throws IOException {
        String block = read("src/main/java/com/gamergaming/villagerguardgunners/armory/ArmoryBlock.java");
        String entity = read("src/main/java/com/gamergaming/villagerguardgunners/armory/ArmoryBlockEntity.java");
        String handler = read("src/main/java/com/gamergaming/villagerguardgunners/armory/ArmoryItemHandler.java");
        String ledger = read("src/main/java/com/gamergaming/villagerguardgunners/item/SquadCommandLedgerItem.java");
        String menu = read("src/main/java/com/gamergaming/villagerguardgunners/menu/ArmoryMenu.java");

        assertAll(
                () -> assertTrue(handler.contains("SLOT_COUNT = 9")),
                () -> assertTrue(handler.contains("GuardAmmoHandler.isAmmo(stack)")),
                () -> assertTrue(entity.contains("ForgeCapabilities.ITEM_HANDLER")),
                () -> assertTrue(entity.contains("inventory.serializeNBT()")),
                () -> assertTrue(entity.contains("inventory.loadSafely(")),
                () -> assertTrue(entity.contains("ArmorySquadLockCodec.writeTo")),
                () -> assertTrue(entity.contains("public boolean maySupply(")),
                () -> assertTrue(entity.contains("unreadableLockTag.copy()")),
                () -> assertTrue(entity.contains("private void lockChanged()")),
                () -> assertTrue(entity.contains("private void inventoryChanged()")),
                () -> assertTrue(menu.contains(
                        "level.getBlockEntity(position) == armory")),
                () -> assertTrue(menu.contains("armory.isRemoved()")),
                () -> assertTrue(block.contains("armory.dropContents()")),
                () -> assertTrue(block.contains("hasAnalogOutputSignal")),
                () -> assertTrue(block.contains("return null;")),
                () -> assertTrue(ledger.contains("instanceof ArmoryBlockEntity")),
                () -> assertFalse(entity.contains("getEntities")),
                () -> assertFalse(entity.contains("BlockEntityTicker<ArmoryBlockEntity>")),
                () -> assertFalse(entity.contains("forceChunk")));

        String inventoryChanged = entity.substring(
                entity.indexOf("private void inventoryChanged()"),
                entity.indexOf("private void lockChanged()"));
        assertFalse(inventoryChanged.contains("sendBlockUpdated"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static boolean exists(String relativePath) {
        return Files.isRegularFile(PROJECT_DIRECTORY.resolve(relativePath));
    }
}
