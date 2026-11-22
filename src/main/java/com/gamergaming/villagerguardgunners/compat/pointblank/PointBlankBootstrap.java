package com.gamergaming.villagerguardgunners.compat.pointblank;

import com.gamergaming.villagerguardgunners.guns.GunProviders;

/**
 * Registers whichever Point Blank build is installed. The build is recognized
 * by a resource lookup, so neither build's classes are loaded to decide.
 */
public final class PointBlankBootstrap {
    public static final String MOD_ID = "pointblank";

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static boolean warnedAfterShot;

    public static void warnAfterShotOnce(RuntimeException failure) {
        if (!warnedAfterShot) {
            warnedAfterShot = true;
            LOGGER.warn("A Point Blank impact or sound callback failed after a guard fired", failure);
        }
    }

    private PointBlankBootstrap() {
    }

    public static void register() {
        var variant = PointBlankApi.INSTALLED;
        if (variant == PointBlankApi.VPB) {
            GunProviders.register(new PointBlankGunProvider(VpbHolder.create()));
        } else if (variant == PointBlankApi.PBJ) {
            GunProviders.register(new PointBlankGunProvider(PbjHolder.create()));
        } else {
            LOGGER.warn("This Point Blank build has an incompatible gun API. Guards will skip its guns.");
        }
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
