package com.gamergaming.villagerguardgunners.compat.tacz;

import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireEvents;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncNetwork;
import com.gamergaming.villagerguardgunners.compat.tacz.provider.TaczGunProvider;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.tacz.guns.util.HitboxHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Everything that connects this mod to Timeless and Classics Zero. The mod
 * entry point reaches it only after TaCZ is known to be installed.
 */
public final class TaczBootstrap {
    public static final String MOD_ID = "tacz";

    private TaczBootstrap() {
    }

    public static void register(IEventBus modEventBus) {
        GunProviders.register(new TaczGunProvider());
        GuardFriendlyFireSafety.setHitboxSource(HitboxHelper::getFixedBoundingBox);
        NeoForge.EVENT_BUS.register(GuardFriendlyFireEvents.class);
        NeoForge.EVENT_BUS.addListener(TaczCasualtyEvents::reload);
        NeoForge.EVENT_BUS.addListener(TaczCasualtyEvents::melee);
        modEventBus.addListener(TaCZGunPackSyncNetwork::register);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            ClientHooks.register();
        }
    }

    /** Keeps client-only classes out of the server's class loading. */
    private static final class ClientHooks {
        static void register() {
            NeoForge.EVENT_BUS.register(
                    com.gamergaming.villagerguardgunners.compat.tacz.network.client.TaCZGunPackSyncClient.class);
        }
    }
}
