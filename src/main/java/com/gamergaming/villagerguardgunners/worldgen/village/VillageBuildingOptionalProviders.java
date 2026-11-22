package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Expected absences for the supported companion packs; unknown pools still warn. */
final class VillageBuildingOptionalProviders {
    private static final Map<String, String> POOL_MODS = Map.of(
            "ctov:village/desert/terminator", "ctov",
            "ctov:village/jungle/roads", "ctov",
            "ctov:village/plains/terminator", "ctov",
            "ctov:village/snowy_igloo/terminator", "ctov",
            "ctov:village/swamp/terminator", "ctov",
            "ctov:village/taiga/terminator", "ctov",
            "mmv:village/jungle/terminators", "mmv",
            "mmv:village/swamp/terminators", "mmv",
            "joshie:village/ocean/roads", "mr_tidal_towns",
            "skyvillages:skyvillage_houses", "skyvillages");

    private VillageBuildingOptionalProviders() {
    }

    static boolean isAbsent(
            ResourceLocation pool, Predicate<String> modLoaded, Set<String> poolNamespaces) {
        String mod = POOL_MODS.get(pool.toString());
        // A datapack can supply the provider's pools without a mod wrapper. Keep
        // those integrations active, including diagnostics for partially supplied pools.
        return mod != null && !modLoaded.test(mod) && !poolNamespaces.contains(pool.getNamespace());
    }
}
