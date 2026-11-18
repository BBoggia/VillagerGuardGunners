package com.gamergaming.villagerguardgunners.guns;

/** Mod identifiers of the supported gun mods. Each is used when installed; none is required. */
public final class OptionalGunMods {
    public static final String TACZ = "tacz";
    public static final String CGM = "cgm";
    /** Shared by Vic's Point Blank and Point Blank: Jelly. */
    public static final String POINT_BLANK = "pointblank";

    private static final java.util.Set<String> ALL = java.util.Set.of(TACZ, CGM, POINT_BLANK);

    private OptionalGunMods() {
    }

    /**
     * True when the namespace belongs to a supported gun mod that is not
     * installed. Shipped data for such a mod is skipped quietly.
     */
    public static boolean isAbsent(String namespace) {
        if (!ALL.contains(namespace)) {
            return false;
        }
        net.neoforged.fml.ModList mods = net.neoforged.fml.ModList.get();
        // Before mods are loaded, as in unit tests, nothing is known to be absent.
        return mods != null && !mods.isLoaded(namespace);
    }
}
