package com.gamergaming.villagerguardgunners.sidearm;

/**
 * Unforgeable ordinary-code key used only by the internal sidearm package.
 * The public guard bridge accepts this type so its mixin can live in a separate
 * package without making the complete two-slot storage generally obtainable.
 */
public final class SidearmStorageAccessKey {
    private static final SidearmStorageAccessKey INTERNAL = new SidearmStorageAccessKey();

    private SidearmStorageAccessKey() {
    }

    static SidearmStorageAccessKey internal() {
        return INTERNAL;
    }

    public static boolean authorizes(SidearmStorageAccessKey key) {
        return key == INTERNAL;
    }
}
