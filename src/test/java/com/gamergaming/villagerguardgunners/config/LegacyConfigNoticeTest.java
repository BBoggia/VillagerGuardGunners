package com.gamergaming.villagerguardgunners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LegacyConfigNoticeTest {

    @TempDir
    Path configDirectory;

    @Test
    void reportsEveryRemovedLegacyConfigButNotTheCurrentConfig() throws IOException {
        Path modDirectory = Files.createDirectories(configDirectory.resolve(VillagerGuardGunners.MODID));
        Path general = Files.writeString(modDirectory.resolve("general.toml"), "debugMode = true");
        Path guardGunner = Files.writeString(
                modDirectory.resolve("guard_gunner.toml"),
                "[reloadSettings]\ninfiniteGuardAmmo = true");
        Path tacz = Files.writeString(modDirectory.resolve("tacz.toml"), "enableGunBalancing = true");
        Path villager = Files.writeString(modDirectory.resolve("villager.toml"), "enableGunsmithVillager = true");
        Path blueprint = Files.writeString(modDirectory.resolve("blueprint.toml"), "enableBlueprints = true");
        Files.writeString(modDirectory.resolve("guard_gunners.toml"), "version = 1");
        Files.writeString(modDirectory.resolve("unrelated.toml"), "enabled = true");

        assertEquals(
                List.of(general, guardGunner, tacz, villager, blueprint),
                LegacyConfigNotice.findLegacyFiles(configDirectory));
    }

    @Test
    void doesNothingForAFirstInstallOrOnlyTheCurrentConfig() throws IOException {
        assertTrue(LegacyConfigNotice.findLegacyFiles(configDirectory).isEmpty());

        Path modDirectory = Files.createDirectories(configDirectory.resolve(VillagerGuardGunners.MODID));
        Files.writeString(modDirectory.resolve("guard_gunners.toml"), "version = 1");

        assertTrue(LegacyConfigNotice.findLegacyFiles(configDirectory).isEmpty());
    }
}
