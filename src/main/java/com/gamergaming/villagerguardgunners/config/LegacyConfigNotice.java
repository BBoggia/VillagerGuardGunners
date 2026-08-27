package com.gamergaming.villagerguardgunners.config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Reports legacy Fzzy Config files without reading, rewriting, or deleting them.
 *
 * <p>The old settings controlled the removed custom combat and rebalance
 * implementation. Automatically translating similarly named values would change
 * TaCZ weapon behavior or reinterpret squared distances as linear distances, so
 * the native rewrite deliberately starts from its compatibility-safe defaults.</p>
 */
public final class LegacyConfigNotice {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final List<String> LEGACY_FILE_NAMES = List.of(
            "general.toml",
            "guard_gunner.toml",
            "tacz.toml",
            "villager.toml",
            "blueprint.toml");

    private LegacyConfigNotice() {
    }

    public static void warnIfPresent() {
        List<Path> legacyFiles = findLegacyFiles(FMLPaths.CONFIGDIR.get());
        if (legacyFiles.isEmpty()) {
            return;
        }

        String names = legacyFiles.stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        LOGGER.warn(
                "Found legacy Villager Guard Gunners config file(s): {}. "
                        + "The native TaCZ rewrite intentionally leaves these removed legacy settings "
                        + "untouched and does not read them. Use guard_gunners.toml for the supported settings.",
                names);
    }

    static List<Path> findLegacyFiles(Path configDirectory) {
        Path modDirectory = configDirectory.resolve(VillagerGuardGunners.MODID);
        return LEGACY_FILE_NAMES.stream()
                .map(modDirectory::resolve)
                .filter(Files::isRegularFile)
                .toList();
    }
}
