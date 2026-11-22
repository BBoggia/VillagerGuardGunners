package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ManagementNetwork {
    private static final String VERSION="5";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse("villagerguardgunners:quartermaster_management"),()->VERSION,VERSION::equals,VERSION::equals);
    private static boolean initialized;
    public static void init() {
        if(initialized)return;initialized=true;
        CHANNEL.registerMessage(5,BulkProtocol.Navigation.class,BulkProtocol.Navigation::encode,BulkProtocol.Navigation::decode,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
                    com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message)));context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,BulkProtocol.Action.class,BulkProtocol.Action::encode,BulkProtocol.Action::decode,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{
                var player=context.getSender();if(player!=null&&player.containerMenu instanceof QuartermasterManagementMenu menu)menu.act(player,message);
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4,BulkProtocol.State.class,BulkProtocol.State::encode,BulkProtocol.State::decode,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
                    com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message)));context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,OverviewProtocol.Page.class,OverviewProtocol.Page::encode,OverviewProtocol.Page::decode,ManagementNetwork::pageHandler,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(0,ManagementProtocol.Action.class,ManagementProtocol.Action::encode,ManagementProtocol.Action::decode,(message,supplier)->{
            var context=supplier.get();
            context.enqueueWork(()->{
                var player=context.getSender();
                if(player!=null&&player.containerMenu instanceof QuartermasterManagementMenu menu)menu.act(player,message);
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,ManagementProtocol.Summary.class,ManagementProtocol.Summary::encode,ManagementProtocol.Summary::decode,(message,supplier)->{
            var context=supplier.get();
            context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
                    com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message)));
            context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private static void pageHandler(OverviewProtocol.Page message,java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
                com.gamergaming.villagerguardgunners.client.QuartermasterManagementClient.receive(message)));context.setPacketHandled(true);
    }
    public static void send(ServerPlayer player,OverviewProtocol.Page page){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),page);}
    public static void request(ManagementProtocol.Action action){CHANNEL.sendToServer(action);}
    public static void send(ServerPlayer player,ManagementProtocol.Summary summary){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),summary);}
    public static void request(BulkProtocol.Action action){CHANNEL.sendToServer(action);}
    public static void send(ServerPlayer player,BulkProtocol.State state){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),state);}
    public static void send(ServerPlayer player,BulkProtocol.Navigation value){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),value);}
    private ManagementNetwork() {}
}
