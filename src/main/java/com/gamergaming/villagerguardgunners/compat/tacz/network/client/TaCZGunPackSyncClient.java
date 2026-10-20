package com.gamergaming.villagerguardgunners.compat.tacz.network.client;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncAssembly;
import com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncPacket;
import com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncProtocol;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Client-only atomic publication for a completed chunked TaCZ cache. */
@net.neoforged.fml.common.EventBusSubscriber(
        modid = VillagerGuardGunners.MODID,
        bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT)
public final class TaCZGunPackSyncClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TaCZGunPackSyncAssembly ASSEMBLY = new TaCZGunPackSyncAssembly();

    private TaCZGunPackSyncClient() {
    }

    public static void accept(TaCZGunPackSyncPacket packet, boolean remoteConnection) {
        ASSEMBLY.accept(packet).ifPresent(encoded -> {
            try {
                var cache = TaCZGunPackSyncProtocol.decodeCache(encoded);
                if (remoteConnection) {
                    CommonAssetsManager.clearInstance();
                }
                CommonNetworkCache.INSTANCE.fromNetwork(cache);
                ClientIndexManager.reload();
                LOGGER.info("Applied {} bytes of chunked TaCZ common data", encoded.length);
            } catch (RuntimeException exception) {
                // Preserve the last complete client cache when a future or
                // malformed transfer cannot be decoded atomically.
                LOGGER.error("Rejected a completed chunked TaCZ common-data transfer", exception);
            }
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ASSEMBLY.clear();
    }
}
