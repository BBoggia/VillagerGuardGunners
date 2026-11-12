package com.gamergaming.villagerguardgunners.qa.multiplayer;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import com.gamergaming.villagerguardgunners.client.*;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.menu.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.IGun;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/** Drives production menu/interaction packets in a full installed graphical client. */
@EventBusSubscriber(modid="vgg_multiplayer_qa", value=Dist.CLIENT)
public final class MultiplayerClient {
    private static int tick, stage=-2, step, last;
    private static boolean connecting, done;
    private static UUID token;
    private static BulkProtocol.Action oldAction;
    private static int stockMenu;
    private static int shots, reloads;
    @SubscribeEvent public static void shot(com.tacz.guns.api.event.common.GunShootEvent event) {
        if(event.getLogicalSide()==net.neoforged.fml.LogicalSide.CLIENT && event.getShooter() instanceof Guard)shots++;
    }
    @SubscribeEvent public static void reload(com.tacz.guns.api.event.common.GunReloadEvent event) {
        if(event.getLogicalSide()==net.neoforged.fml.LogicalSide.CLIENT && event.getEntity() instanceof Guard)reloads++;
    }
    static String role() { return System.getProperty("vgg.multiplayer.role"); }
    static Path shared() { return Path.of(System.getProperty("vgg.multiplayer.shared")); }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void ack() throws Exception {
        var mc=Minecraft.getInstance();
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(shared().resolve(role()+"-"+stage+".png"));
        }
        Files.writeString(shared().resolve(role()+"-"+stage+".done"),"passed\n"); step=99;
    }
    static void connect(Minecraft mc) {
        String address=System.getProperty("vgg.multiplayer.address");
        ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),
                new ServerData("Installed VGG acceptance",address,ServerData.Type.OTHER),false,null);
        connecting=true;
    }
    static void sendNativeCommands(int id) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new tallestegg.guardvillagers.networking.GuardFollowPacket(id));
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new tallestegg.guardvillagers.networking.GuardSetPatrolPosPacket(id, false));
    }
    static void face(Minecraft mc, Guard guard) {
        var d=guard.position().add(0,.7,0).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(role()==null || done) return;
        var mc=Minecraft.getInstance(); tick++;
        try {
            if(Files.exists(shared().resolve("server-result.json"))) { done=true; mc.stop(); return; }
            if(tick>14000)throw new AssertionError("Installed client timeout");
            if(!connecting && mc.player==null && mc.screen instanceof TitleScreen) { mc.options.pauseOnLostFocus=false; connect(mc); return; }
            var control=shared().resolve("control.json"); if(!Files.exists(control))return;
            var json=JsonParser.parseString(Files.readString(control)).getAsJsonObject();
            int next=json.get("stage").getAsInt();
            if(next!=stage) { stage=next;step=0;last=tick;System.out.println("VGG_MULTIPLAYER_CLIENT "+role()+" stage="+stage); }
            if(tick-last<12 || step==99)return;last=tick;
            if(stage==29) {
                String scope = "villagerguardgunners.guard_gunners.ammunition";
                if(step==0) {
                    me.fzzyhmstrs.fzzy_config.api.ConfigApiJava.INSTANCE.openScreen(scope); step=1;
                } else {
                    check(me.fzzyhmstrs.fzzy_config.api.ConfigApiJava.isScreenOpen(scope), "Fzzy ammunition screen did not open");
                    ack();
                }
                return;
            }
            if(stage==13 && role().equals("A")) {
                if(mc.player==null) { if(step==0){connect(mc);step=1;} return; }
                if(mc.player.getInventory().countItem(ModItems.BANDAGE.get())==4)ack();return;
            }
            if(mc.player==null || mc.level==null || mc.gameMode==null)return;
            var entity=mc.level.getEntity(json.get("entity").getAsInt());
            if(!(entity instanceof Guard guard))return;
            if(stage==12 && role().equals("A")) {
                mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("QA reconnect"));
                mc.disconnect(new TitleScreen()); ack(); return;
            }
            if(stage==5) {
                if(!(mc.screen instanceof QuartermasterScreen)||!(mc.player.containerMenu instanceof QuartermasterMenu m))return;
                stockMenu=m.containerId;
                if(role().equals("A") && step==0) {check(m.getSlot(0).getItem().getCount()==12,"Stock payload missing");mc.gameMode.handleInventoryMouseClick(m.containerId,0,0,ClickType.QUICK_MOVE,mc.player);step++;}
                else if(m.getSlot(0).getItem().isEmpty()) {if(role().equals("A"))check(mc.player.getInventory().countItem(Items.IRON_INGOT)==12,"Stock transfer client inventory");ack();}
                return;
            }
            if(stage==6) {
                if(role().equals("B")) {
                    if(mc.player.containerMenu!=mc.player.inventoryMenu)return;
                    if(step==0) {mc.gameMode.handleInventoryButtonClick(stockMenu,0);step++;return;}
                    check(mc.player.getInventory().countItem(Items.IRON_INGOT)==0,"Revoked peer received stock");
                }
                ack();return;
            }
            if(stage==7) {
                if(!(mc.screen instanceof ArmoryScreen)||!(mc.player.containerMenu instanceof ArmoryMenu m))return;
                if(role().equals("A")&&step==0){check(m.getSlot(0).getItem().getCount()==32,"Armory ammo sync");mc.gameMode.handleInventoryMouseClick(m.containerId,0,0,ClickType.QUICK_MOVE,mc.player);step++;}
                else if(m.getSlot(0).getItem().isEmpty())ack();return;
            }
            if(stage==8||stage==9) {
                if(stage==8&&role().equals("B")) {check(guard.getMainHandItem().getItem() instanceof IGun,"Remote guard gun missing");face(mc,guard);if(step++>1)ack();return;}
                if(stage==9&&role().equals("A")){ack();return;}
                if(!(mc.screen instanceof GuardGunnerInventoryScreen)||!(mc.player.containerMenu instanceof GuardGunnerContainer m))return;
                if(step==0){
                    check(m.servicePolicy()==1,"Service policy data slot missing");
                    check(m.getSlot(GuardGunnerContainer.MEDICAL_SLOT_START).getItem().getCount()==4,"Medical pouch not synchronized");
                    mc.gameMode.handleInventoryButtonClick(m.containerId,GuardOrders.OrderField.ENGAGEMENT.buttonId());
                    if(stage==8)mc.gameMode.handleInventoryMouseClick(m.containerId,GuardGunnerContainer.SIDEARM_SLOT_INDEX,0,ClickType.QUICK_MOVE,mc.player);
                    if(stage==9)sendNativeCommands(guard.getId());
                    step++;return;
                }
                if(!m.getOrders().equals(GuardOrders.DEFAULT.cycle(GuardOrders.OrderField.ENGAGEMENT)))return;
                if(stage==8&&mc.player.getInventory().items.stream().filter(s->s.getItem() instanceof IGun g && g.getGunId(s).toString().equals("tacz:glock_17")).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum()!=1)return;
                if(stage==8) {
                    var buttons=mc.screen.children().stream().filter(c->c instanceof Button && c.getClass().getSimpleName().equals("GuardGuiButton")).map(c->(Button)c).toList();
                    check(buttons.size()==2,"Follow/patrol buttons missing");
                    if(step==1){buttons.get(0).onPress();step++;return;}
                    if(step==2){if(!guard.isFollowing())return;buttons.get(0).onPress();step++;return;}
                    if(step==3){if(guard.isFollowing())return;buttons.get(1).onPress();step++;return;}
                    if(step==4){if(!guard.isPatrolling())return;buttons.get(1).onPress();step++;return;}
                    if(guard.isPatrolling())return;
                }
                ack();return;
            }
            if(stage==27 || stage==28) {
                if(!role().equals("A"))return;
                if(step==0){sendNativeCommands(guard.getId());step++;return;}
                check(!guard.isFollowing() && !guard.isPatrolling(),"Unauthorized native command reached client");
                ack();return;
            }
            if(stage>=22) {
                face(mc,guard);
                if((stage<26&&!role().equals("A"))||(stage==26&&!role().equals("B")))return;
                if(stage==22||stage==23) {
                    if(!mc.player.getMainHandItem().is(ModItems.ORDERS_LEDGER.get()))return;
                    if(step==0){mc.gameMode.interact(mc.player,guard,InteractionHand.MAIN_HAND);step++;return;}
                    var copied=com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.readFrom(com.gamergaming.villagerguardgunners.inventory.ItemCustomData.read(mc.player.getMainHandItem()));
                    if(copied.orders()==null)return;ack();return;
                }
                if(!mc.player.getMainHandItem().is(ModItems.SQUAD_COMMAND_LEDGER.get()))return;
                if(stage==24) {
                    if(step==0){mc.options.keyShift.setDown(true);step++;return;}
                    if(step==1){mc.gameMode.interact(mc.player,guard,InteractionHand.MAIN_HAND);step++;return;}
                    var binding=com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec.readFrom(com.gamergaming.villagerguardgunners.inventory.ItemCustomData.read(mc.player.getMainHandItem()));
                    if(binding.binding()==null)return;mc.options.keyShift.setDown(false);ack();return;
                }
                if(step==0) {
                    var mode=com.gamergaming.villagerguardgunners.squad.SquadLedgerModeCodec.readFrom(com.gamergaming.villagerguardgunners.inventory.ItemCustomData.read(mc.player.getMainHandItem()));
                    if(mode.modeOrDefault()!=com.gamergaming.villagerguardgunners.squad.SquadCommandType.HOLD)return;
                    mc.player.setXRot(65);step++;return;
                }
                if(step==1){mc.player.setXRot(65);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);step++;return;}
                ack();return;
            }
            if(stage>=15) {
                check(mc.screen==null,"World view retained a stale screen");face(mc,guard);
                int visual=((GuardLifeAccess)guard).villagerGuardGunners$getLifeVisual();
                if(stage==15) {if(visual!=1)return; if(step++>1)ack();}
                else if(stage==16) {
                    if(role().equals("A")&&step==0){check(mc.player.getMainHandItem().is(ModItems.BANDAGE.get()),"Bandage not synced");mc.gameMode.interact(mc.player,guard,InteractionHand.MAIN_HAND);step++;return;}
                    if(visual!=2)return; if(role().equals("A"))check(mc.player.getMainHandItem().getCount()==3,"Client bandage count not synced");ack();
                } else if(stage==17) {if(((GuardLifeAccess)guard).villagerGuardGunners$getCotVisual().isEmpty())return;if(step++>2)ack();}
                else if(stage==18) {if(!com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).getSynIsAiming())return;if(step++>2)ack();}
                else if(stage==19) {if(shots>0)ack();}
                else if(stage==20) {if(reloads>0)ack();}
                else if(stage==21) {
                    if(com.tacz.guns.resource.network.CommonNetworkCache.INSTANCE.getGunData(net.minecraft.resources.ResourceLocation.parse("vgg_multiplayer_qa:sync_probe"))==null)return;
                    check(com.tacz.guns.api.TimelessAPI.getCommonGunIndex(net.minecraft.resources.ResourceLocation.parse("tacz:ak47")).isPresent(),"Large cache discarded existing gun index");ack();
                }
                return;
            }
            if(stage==10&&role().equals("A")&&mc.screen instanceof MerchantScreen) {
                var m=(MerchantMenu)mc.player.containerMenu;
                if(step<=1) {
                    var buttons=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getWidth()==88).map(c->(Button)c).toList();
                    if(buttons.isEmpty())return;var b=buttons.get(0);b.mouseClicked(b.getX()+5,b.getY()+5,0);step=2;
                } else if(step==2&&!m.getSlot(2).getItem().isEmpty()) {
                    check(m.getSlot(2).getItem().is(ModItems.BANDAGE.get()),"First offer must be bandages");
                    mc.gameMode.handleInventoryMouseClick(m.containerId,2,0,ClickType.PICKUP,mc.player);step=3;
                } else if(step==3) {
                    check(m.getCarried().getCount()==4,"Expected four purchased bandages");
                    mc.gameMode.handleInventoryMouseClick(m.containerId,30,0,ClickType.PICKUP,mc.player);step=4;
                } else if(step==4) {check(m.getSlot(0).getItem().getCount()==18,"Expected 18 remaining emeralds");ack();}
                return;
            }
            if(!(mc.player.containerMenu instanceof QuartermasterManagementMenu m)||m.summary()==null)return;
            if(stage==1) {
                check(mc.screen instanceof QuartermasterManagementScreen,"Management screen factory missing");
                check(com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.combat.aiming.errorDegrees.get()==3.5,"Server accuracy config did not synchronize");
                check(com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.get()==com.gamergaming.villagerguardgunners.ammunition.InfiniteAmmoMode.CREATIVE_AMMO_BOX,"Server infinite ammo mode did not synchronize");
                if(role().equals("A")){check(m.summary().stock()&&m.summary().manage(),"Owner permissions missing");ack();}
                else if(step==0){check(!m.summary().stock()&&!m.summary().manage(),"Peer has unauthorized permissions");ManagementNetwork.request(m.requestBulk(BulkProtocol.OPEN,0,null,null));ManagementNetwork.request(m.request(ManagementProtocol.STOCK));step++;}
                else{check(m.bulkState()==null,"Denied peer opened bulk session");ack();}
            } else if(stage==2) {
                switch(step) {
                    case 0 -> {check(m.summary().stock()&&m.summary().manage(),"Authorized permissions missing");ManagementNetwork.request(m.requestBulk(BulkProtocol.OPEN,0,null,null));step++;}
                    case 1 -> {if(m.bulkState()==null)return;check(mc.screen instanceof QuartermasterBulkScreen,"Bulk navigation packet failed");ManagementNetwork.request(m.requestBulk(BulkProtocol.TOGGLE,0,UUID.fromString(json.get("guard").getAsString()),null));step++;}
                    case 2 -> {ManagementNetwork.request(m.requestBulk(BulkProtocol.POLICY,1,null,null));step++;}
                    case 3 -> {ManagementNetwork.request(m.requestBulk(BulkProtocol.ASSIGN,1,null,null));step++;}
                    case 4 -> {ManagementNetwork.request(m.requestBulk(BulkProtocol.PREVIEW,0,null,null));step++;}
                    default -> {if(m.bulkState().token()==null||m.details()==null)return;token=m.bulkState().token();ack();}
                }
            } else if(stage==3&&role().equals("A")) {
                if(step==0){oldAction=m.requestBulk(BulkProtocol.APPLY,0,null,token);ManagementNetwork.request(oldAction);step++;}
                else if(m.bulkState().status().equals("applied"))ack();
            } else if(stage==4&&role().equals("B")) {
                if(step==0){ManagementNetwork.request(m.requestBulk(BulkProtocol.APPLY,0,null,token));step++;}
                else {check(!m.bulkState().status().equals("applied")&&m.bulkState().token()==null,"Stale preview not refused");ack();}
            } else if(stage==10&&role().equals("A")&&step==0) {ManagementNetwork.request(m.request(ManagementProtocol.TRADE));step=1;}
            else if(stage==11&&role().equals("B")) {
                if(step==0){ManagementNetwork.request(m.request(ManagementProtocol.TRADE));step++;}
                else {check(m.summary().tradeStatus()==ManagementProtocol.BUSY,"Other trader should be busy");ack();}
            } else if(stage==14&&role().equals("A")) {
                if(step==0){ManagementNetwork.request(oldAction);step++;}
                else {check(m.bulkState()==null,"Reconnected menu accepted old session");ack();}
            }
        } catch(Throwable error) {
            error.printStackTrace();done=true;
            try{Files.writeString(shared().resolve(role()+"-error.txt"),error.toString());}catch(Exception ignored){}
            mc.stop();
        }
    }
}
