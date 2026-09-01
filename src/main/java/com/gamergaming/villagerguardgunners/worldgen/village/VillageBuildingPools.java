package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import net.minecraft.resources.ResourceLocation;

/** Built-in pool identities reserved by the 1.5 village-building contract. */
public final class VillageBuildingPools {
    public static final int MAXIMUM_TARGET_POOLS = 5;
    public static final int MAXIMUM_INJECTED_ENTRIES = 25;

    public static final ResourceLocation PLAINS_HOUSES = minecraft("village/plains/houses");
    public static final ResourceLocation DESERT_HOUSES = minecraft("village/desert/houses");
    public static final ResourceLocation SAVANNA_HOUSES = minecraft("village/savanna/houses");
    public static final ResourceLocation SNOWY_HOUSES = minecraft("village/snowy/houses");
    public static final ResourceLocation TAIGA_HOUSES = minecraft("village/taiga/houses");
    public static final ResourceLocation EMPTY_PROCESSORS = minecraft("empty");
    public static final ResourceLocation PLAINS_INTEGRATION_PROBE_ID =
            vgg("integration_probe/plains");

    private static ResourceLocation minecraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    private static ResourceLocation vgg(String path) {
        return ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, path);
    }

    private VillageBuildingPools() {
    }
}
