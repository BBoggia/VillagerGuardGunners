package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SidearmStorageContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void guardOwnsVersionedPrivateStorageAcrossItsLifecycle() throws IOException {
        String storage = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/GuardSidearmStorage.java");
        String api = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/GuardSidearmAccess.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String accessKey = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "SidearmStorageAccessKey.java");

        assertAll(
                () -> assertTrue(storage.contains("SLOT_COUNT = 2")),
                () -> assertTrue(storage.contains("SIDEARM_SLOT = 0")),
                () -> assertTrue(storage.contains("STOWED_PRIMARY_SLOT = 1")),
                () -> assertTrue(storage.contains("CURRENT_SCHEMA_VERSION = 3")),
                () -> assertTrue(storage.contains("version < 1 || version > CURRENT_SCHEMA_VERSION")),
                () -> assertTrue(storage.contains("PRIMARY_STANDOFF_TAG")),
                () -> assertTrue(storage.contains("ACTIVE_TOKEN_TAG")),
                () -> assertTrue(storage.contains("unreadableRecord.copy()")),
                () -> assertTrue(storage.contains("recoverKnownStacks")),
                () -> assertFalse(api.contains("static GuardSidearmStorage getStorage")),
                () -> assertTrue(api.contains("getConfiguredSidearmInventory")),
                () -> assertTrue(api.contains(
                        "villagerGuardGunners$getSidearmStorage(\n"
                                + "            SidearmStorageAccessKey accessKey)")),
                () -> assertTrue(accessKey.contains(
                        "private SidearmStorageAccessKey()")),
                () -> assertTrue(storage.contains("ConfiguredSidearmView")),
                () -> assertTrue(storage.contains("getSlots() {\n            return 1;")),
                () -> assertTrue(storage.contains(
                        "GuardSidearmStorage.this\n"
                                + "                            .getStackInSlot(STOWED_PRIMARY_SLOT)")),
                () -> assertTrue(mixin.contains("implements GuardAmmoAccess")),
                () -> assertTrue(mixin.contains("GuardSidearmAccess")),
                () -> assertTrue(mixin.contains("sidearmStorage.loadFrom(compound)")),
                () -> assertTrue(mixin.contains(
                        "sidearmStorage.saveTo(compound, getMainHandItem())")),
                () -> assertTrue(mixin.contains("sidearmStorage.recoverableStacks()")),
                () -> assertTrue(mixin.contains("sidearmStorage.drainRecoverableStacks()")));
    }

    @Test
    void sidearmStorageIsNotAddedToTheTaCZItemCapability() throws IOException {
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        int combinedStart = mixin.indexOf("IItemHandler combined = new CombinedInvWrapper(");
        int combinedEnd = mixin.indexOf(
                "villagerGuardGunners$combinedItemHandler = LazyOptional.of",
                combinedStart);
        String combinedConstruction = mixin.substring(combinedStart, combinedEnd);

        assertTrue(combinedConstruction.contains("new InvWrapper(guard.guardInventory)"));
        assertTrue(combinedConstruction.contains("villagerGuardGunners$ammoHandler"));
        assertFalse(combinedConstruction.contains("sidearmStorage"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
