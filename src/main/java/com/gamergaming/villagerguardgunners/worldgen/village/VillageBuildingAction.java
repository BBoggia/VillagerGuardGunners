package com.gamergaming.villagerguardgunners.worldgen.village;

/** Stable at-most-once action claims owned by one village building. */
public enum VillageBuildingAction {
    ANCHOR(1, 0),
    GARRISON(1 << 1, 1),
    ARMORY(1 << 2, 1),
    INITIAL_STOCK(1 << 3, 1 << 2);

    private static final int SUPPORTED_MASK = java.util.Arrays.stream(values())
            .mapToInt(VillageBuildingAction::mask)
            .reduce(0, (left, right) -> left | right);

    private final int mask;
    private final int prerequisiteMask;

    VillageBuildingAction(int mask, int prerequisiteMask) {
        this.mask = mask;
        this.prerequisiteMask = prerequisiteMask;
    }

    public int mask() {
        return mask;
    }

    public int prerequisiteMask() {
        return prerequisiteMask;
    }

    public static int supportedMask() {
        return SUPPORTED_MASK;
    }
}
