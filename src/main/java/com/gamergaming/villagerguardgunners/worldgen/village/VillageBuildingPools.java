package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import java.util.List;
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

    /**
     * Step 2 uses a vanilla road-connected house as an inert compatibility
     * probe. It performs no marker action and is disabled by default. The
     * reviewed VGG templates replace this entry before 1.5 release.
     */
    public static final VillageBuildingPoolEntry PLAINS_INTEGRATION_PROBE =
            new VillageBuildingPoolEntry(
                    vgg("integration_probe/plains"),
                    PLAINS_HOUSES,
                    minecraft("village/plains/houses/plains_small_house_1"),
                    EMPTY_PROCESSORS,
                    1);

    private static final List<VillageBuildingPoolEntry> STEP_TWO_ENTRIES =
            List.of(PLAINS_INTEGRATION_PROBE);

    public static List<VillageBuildingPoolEntry> stepTwoEntries() {
        return STEP_TWO_ENTRIES;
    }

    private static ResourceLocation minecraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    private static ResourceLocation vgg(String path) {
        return ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, path);
    }

    private VillageBuildingPools() {
    }
}
