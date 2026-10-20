package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ManagementNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("6");
        registrar.playToServer(ManagementProtocol.Action.TYPE,
                StreamCodec.of((buffer, value) -> value.encode(buffer), ManagementProtocol.Action::decode), (message, context) -> {
                    if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof QuartermasterManagementMenu menu)
                        menu.act(player, message);
                });
        registrar.playToServer(BulkProtocol.Action.TYPE,
                StreamCodec.of((buffer, value) -> value.encode(buffer), BulkProtocol.Action::decode), (message, context) -> {
                    if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof QuartermasterManagementMenu menu)
                        menu.act(player, message);
                });
        registrar.playToClient(ManagementProtocol.Summary.TYPE, StreamCodec.of((buffer, value) -> value.encode(buffer), ManagementProtocol.Summary::decode),
                (message, context) -> com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message));
        registrar.playToClient(BulkProtocol.State.TYPE, StreamCodec.of((buffer, value) -> value.encode(buffer), BulkProtocol.State::decode),
                (message, context) -> com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message));
        registrar.playToClient(BulkProtocol.Navigation.TYPE, StreamCodec.of((buffer, value) -> value.encode(buffer), BulkProtocol.Navigation::decode),
                (message, context) -> com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message));
        registrar.playToClient(OverviewProtocol.Page.TYPE, StreamCodec.of((buffer, value) -> value.encode(buffer), OverviewProtocol.Page::decode),
                (message, context) -> com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message));

    }
    public static void send(ServerPlayer player, ManagementProtocol.Summary value) { PacketDistributor.sendToPlayer(player, value); }
    public static void send(ServerPlayer player, BulkProtocol.State value) { PacketDistributor.sendToPlayer(player, value); }
    public static void send(ServerPlayer player, BulkProtocol.Navigation value) { PacketDistributor.sendToPlayer(player, value); }
    public static void send(ServerPlayer player, OverviewProtocol.Page value) { PacketDistributor.sendToPlayer(player, value); }
    public static void request(ManagementProtocol.Action value) { PacketDistributor.sendToServer(value); }
    public static void request(BulkProtocol.Action value) { PacketDistributor.sendToServer(value); }
    private ManagementNetwork() {}
}
