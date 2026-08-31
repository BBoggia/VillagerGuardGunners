package com.gamergaming.villagerguardgunners.compat.tacz.network;

import com.mojang.logging.LogUtils;
import com.tacz.guns.resource.CommonAssetsManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;

/** Overflow-only replacement for TaCZ 1.1.8's single login payload. */
public final class TaCZGunPackSync {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TaCZGunPackSync() {
    }

    /**
     * Returns true only when the native event body must be cancelled.
     * Small payloads retain TaCZ's original synchronization unchanged.
     */
    public static boolean replaceIfOversized(OnDatapackSyncEvent event) {
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager == null) {
            return false;
        }

        byte[] encoded;
        try {
            encoded = TaCZGunPackSyncProtocol.encodeCache(manager.getNetworkCache());
        } catch (RuntimeException exception) {
            // Preserve login even if a future TaCZ format exceeds our explicit
            // compatibility boundary; publishing no replacement is safer than
            // re-entering the known oversized native path.
            LOGGER.error(
                    "Suppressed TaCZ's native common-data sync after its cache could not be "
                            + "encoded safely; the player may retain stale gun-pack data",
                    exception);
            return true;
        }
        if (encoded.length <= TaCZGunPackSyncProtocol.NATIVE_SAFE_PAYLOAD_BYTES) {
            return false;
        }

        List<ServerPlayer> targets = event.getPlayer() == null
                ? List.copyOf(event.getPlayerList().getPlayers())
                : List.of(event.getPlayer());
        if (targets.isEmpty()) {
            return true;
        }

        try {
            List<TaCZGunPackSyncPacket> chunks = TaCZGunPackSyncProtocol.split(
                    UUID.randomUUID(),
                    encoded);
            LOGGER.info(
                    "Replacing oversized TaCZ common-data sync: {} bytes in {} chunks for {} "
                            + "player(s)",
                    encoded.length,
                    chunks.size(),
                    targets.size());
            for (ServerPlayer target : targets) {
                for (TaCZGunPackSyncPacket chunk : chunks) {
                    TaCZGunPackSyncNetwork.send(target, chunk);
                }
            }
        } catch (RuntimeException exception) {
            // A failed bounded transfer stays absent; never fall back to the
            // known login-breaking native oversized packet.
            LOGGER.error(
                    "Chunked TaCZ common-data sync failed; the oversized native packet remains "
                            + "suppressed so affected players can still join",
                    exception);
        }
        return true;
    }
}
