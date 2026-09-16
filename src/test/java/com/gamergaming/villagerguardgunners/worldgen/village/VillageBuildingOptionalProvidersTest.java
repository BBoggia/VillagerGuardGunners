package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageBuildingOptionalProvidersTest {
    @Test
    void absentProvidersAreOptionalButInstalledProvidersStillGetPoolValidation() {
        Map<String, String> examples = Map.of(
                "ctov:village/plains/terminator", "ctov",
                "mmv:village/jungle/terminators", "mmv",
                "joshie:village/ocean/roads", "mr_tidal_towns",
                "skyvillages:skyvillage_houses", "skyvillages");
        examples.forEach((pool, mod) -> {
            assertTrue(absent(pool, Set.of(), Set.of("minecraft")));
            assertFalse(absent(pool, Set.of(mod), Set.of("minecraft")));
        });
    }

    @Test
    void datapackProvidedPoolsRemainActiveWithoutModWrapper() {
        assertFalse(absent("joshie:village/ocean/roads", Set.of(), Set.of("minecraft", "joshie")));
        assertFalse(absent("ctov:village/plains/terminator", Set.of(), Set.of("ctov")));
    }

    @Test
    void unknownAndVanillaPoolsAreNeverSilentlySkipped() {
        assertFalse(absent("minecraft:village/plains/houses", Set.of(), Set.of()));
        assertFalse(absent("example:missing_pool", Set.of(), Set.of()));
        assertFalse(absent("ctov:village/plains/typo", Set.of(), Set.of()));
    }

    @Test
    void unrelatedProviderDoesNotActivateMissingIntegration() {
        assertTrue(absent("mmv:village/swamp/terminators", Set.of("ctov"), Set.of("ctov")));
    }

    private static boolean absent(String pool, Set<String> mods, Set<String> namespaces) {
        return VillageBuildingOptionalProviders.isAbsent(ResourceLocation.parse(pool), mods::contains, namespaces);
    }
}
