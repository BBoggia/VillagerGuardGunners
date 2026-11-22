package com.gamergaming.villagerguardgunners.compat.cgm;

import com.gamergaming.villagerguardgunners.guns.GunProviders;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only class outside this package's own files that touches MrCrayfish's
 * Gun Mod. It is loaded only after the mod is known to be installed.
 */
public final class CgmBootstrap {
    public static final String MOD_ID = "cgm";

    private static final Logger LOGGER = LoggerFactory.getLogger("villagerguardgunners/cgm");
    private static boolean warnedImprovedHitboxes;
    private static boolean warnedDischargeFailure;

    static void warnDischargeFailureOnce(RuntimeException failure) {
        if (!warnedDischargeFailure) {
            warnedDischargeFailure = true;
            LOGGER.warn("A CGM guard shot encountered an error after firing; accepted projectiles still consume one round", failure);
        }
    }

    private CgmBootstrap() {
    }

    public static void register() {
        GunProviders.register(new CgmGunProvider());
        MinecraftForge.EVENT_BUS.addListener(CgmAllyProtection::onProjectileHit);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                CgmAllyProtection::onExplosionDetonate);
    }

    static void warnImprovedHitboxesOnce() {
        if (!warnedImprovedHitboxes) {
            warnedImprovedHitboxes = true;
            LOGGER.warn("Guards will not fire MrCrayfish's Gun Mod guns while its improvedHitboxes setting is on: "
                    + "that setting assumes every shooter is a player and would crash the server.");
        }
    }
}
