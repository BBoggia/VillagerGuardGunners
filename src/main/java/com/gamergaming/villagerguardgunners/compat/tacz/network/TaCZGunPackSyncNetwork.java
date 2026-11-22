package com.gamergaming.villagerguardgunners.compat.tacz.network;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.compat.tacz.network.client.TaCZGunPackSyncClient;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Dedicated add-on channel for the overflow-only TaCZ common-data path. */
public final class TaCZGunPackSyncNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(
                    VillagerGuardGunners.MODID,
                    "tacz_gun_pack_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private TaCZGunPackSyncNetwork() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        CHANNEL.registerMessage(
                0,
                TaCZGunPackSyncPacket.class,
                TaCZGunPackSyncPacket::encode,
                TaCZGunPackSyncPacket::decode,
                (packet, contextSupplier) -> {
                    var context = contextSupplier.get();
                    if (context.getDirection().getReceptionSide().isClient()) {
                        boolean remoteConnection = context.getNetworkManager() != null
                                && !context.getNetworkManager().isMemoryConnection();
                        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                                Dist.CLIENT,
                                () -> () -> TaCZGunPackSyncClient.accept(
                                        packet,
                                        remoteConnection)));
                    }
                    context.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    static void send(net.minecraft.server.level.ServerPlayer player, TaCZGunPackSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
