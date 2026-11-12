package com.gamergaming.villagerguardgunners.compat.pointblank;

import com.gamergaming.villagerguardgunners.guns.GunProviders;

/**
 * Registers whichever Point Blank build is installed. The build is recognized
 * by a resource lookup, so neither build's classes are loaded to decide.
 */
public final class PointBlankBootstrap {
    public static final String MOD_ID = "pointblank";

    private static final String VPB_MARKER = "com/vicmatskiv/pointblank/item/GunItem.class";
    private static final String PBJ_MARKER = "mod/pbj/item/GunItem.class";

    private PointBlankBootstrap() {
    }

    public static void register() {
        Variant variant = installedVariant(PointBlankBootstrap.class.getClassLoader());
        if (variant == Variant.VPB) {
            GunProviders.register(new PointBlankGunProvider(VpbHolder.create()));
        } else if (variant == Variant.PBJ) {
            GunProviders.register(new PointBlankGunProvider(PbjHolder.create()));
        }
        // An unrecognized build registers nothing; its guns are then ordinary items to a guard.
    }

    public enum Variant { VPB, PBJ, NONE }

    public static Variant installedVariant(ClassLoader loader) {
        if (loader.getResource(VPB_MARKER) != null) {
            return Variant.VPB;
        }
        return loader.getResource(PBJ_MARKER) != null ? Variant.PBJ : Variant.NONE;
    }

    // Holder classes keep each build's adapter from loading unless that build was found.
    private static final class VpbHolder {
        static PointBlankAccess create() {
            return new com.gamergaming.villagerguardgunners.compat.pointblank.vpb.VpbAccess();
        }
    }

    private static final class PbjHolder {
        static PointBlankAccess create() {
            return new com.gamergaming.villagerguardgunners.compat.pointblank.pbj.PbjAccess();
        }
    }
}
