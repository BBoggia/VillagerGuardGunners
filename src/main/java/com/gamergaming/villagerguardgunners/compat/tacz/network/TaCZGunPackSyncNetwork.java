package com.gamergaming.villagerguardgunners.compat.tacz.network;

import com.gamergaming.villagerguardgunners.compat.tacz.network.client.TaCZGunPackSyncClient;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Overflow-only TaCZ common data, registered with NeoForge's play payload API. */
public final class TaCZGunPackSyncNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TaCZGunPackSyncPacket.TYPE,
                StreamCodec.of((buffer, value) -> TaCZGunPackSyncPacket.encode(value, buffer), TaCZGunPackSyncPacket::decode),
                (packet, context) -> TaCZGunPackSyncClient.accept(packet, !context.connection().isMemoryConnection()));
    }
    static void send(net.minecraft.server.level.ServerPlayer player, TaCZGunPackSyncPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
    private TaCZGunPackSyncNetwork() {}
}
