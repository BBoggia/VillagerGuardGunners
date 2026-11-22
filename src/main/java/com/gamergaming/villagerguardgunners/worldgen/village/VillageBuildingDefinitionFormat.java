package com.gamergaming.villagerguardgunners.worldgen.village;

/** Hard bounds and the on-disk version for village-building definitions. */
public final class VillageBuildingDefinitionFormat {
    public static final int VERSION = 1;
    public static final int MAXIMUM_DOCUMENTS =
            VillageBuildingPools.MAXIMUM_INJECTED_ENTRIES;
    public static final int MAXIMUM_DOCUMENT_CHARACTERS = 16_384;
    public static final int MAXIMUM_JSON_DEPTH = 16;
    public static final int MAXIMUM_ERROR_CHARACTERS = 512;

    private VillageBuildingDefinitionFormat() {
    }
}
