package quartermasterprobe;

import com.gamergaming.villagerguardgunners.client.QuartermasterManagementScreen;
import com.gamergaming.villagerguardgunners.client.QuartermasterScreen;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Packaged integrated client fixture: real interaction packets, GUI buttons and rendered captures. */
@Mod.EventBusSubscriber(modid="vggquartermasterprobe",value=Dist.CLIENT)
public final class ManagementClientProbe {
    private static int tick,stage,entered,inspection,bulkInspection,tradeInspection;private static boolean done,opening;private static volatile int npcId;
    private static volatile boolean tradeReturned;
    private static volatile UUID unavailableGuard;
    private static volatile boolean forgotten;
    private static volatile String error;private static String capture;private static final List<String> cases=new ArrayList<>();
    private static Path out(){return Path.of(System.getProperty("vgg.management.client"));}
    private static boolean enabled(){return System.getProperty("vgg.management.client")!=null;}
    private static void check(String name,boolean pass){if(!pass)throw new AssertionError(name);cases.add(name);}
    private static void advance(){stage++;entered=tick;System.out.println("VGG_MANAGEMENT_CLIENT stage="+stage);}
    private static void button(String title) {
        var screen=Minecraft.getInstance().screen;
        var b=screen.children().stream().filter(c->c instanceof Button button&&button.getMessage().getString().equals(title)).map(c->(Button)c).findFirst().orElseThrow();
        check("button-active-"+title+"-"+stage,b.active);
        b.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,0);
    }
    private static void finish(String failure) {
        done=true;
        try{Files.writeString(out().resolve("client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",failure==null,"cases",cases,"failure",failure==null?"":failure)));}catch(Exception e){throw new RuntimeException(e);}
        Minecraft.getInstance().stop();
    }
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> task) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();error=e.toString();}});
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(!enabled()||event.phase!=TickEvent.Phase.END||capture==null)return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out().resolve(capture));capture=null;}
        catch(Exception e){finish(e.toString());}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!enabled()||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();tick++;if(tick>2400){finish("Timeout at stage "+stage);return;}
        if(tick%200==0)System.out.println("VGG_MANAGEMENT_CLIENT waiting stage="+stage+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName()+" "+mc.screen.getTitle().getString()));
        if(stage==0&&tick==200)capture="startup.png";
        try {
            if(error!=null)throw new AssertionError(error);
            if(stage==0&&!opening&&tick>40&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opening=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"QuartermasterQA");
            }
            if(stage==0&&mc.player!=null&&mc.getSingleplayerServer()!=null) {
                mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(100);mc.resizeDisplay();mc.setScreen(null);
                server(player->{
                    var level=player.serverLevel();level.setDayTime(3000);level.setWeatherParameters(6000,0,false,false);player.setGameMode(GameType.CREATIVE);
                    for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
                    var pos=new BlockPos(0,100,0);level.setBlockAndUpdate(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState());
                    var station=(QuartermasterBlockEntity)level.getBlockEntity(pos);station.placedBy(player);station.inventory().setStackInSlot(0,new ItemStack(Items.IRON_HELMET));
                    station.inventory().setStackInSlot(1,com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:762x39")).setCount(60).build());
                    station.inventory().setStackInSlot(2,com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:762x39")).setCount(20).build());
                    var enchanted=new ItemStack(Items.IRON_HELMET);enchanted.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION,3);enchanted.setHoverName(net.minecraft.network.chat.Component.literal("Patrol Helmet"));
                    station.inventory().setStackInSlot(3,enchanted);station.inventory().setStackInSlot(4,new ItemStack(Items.IRON_BOOTS));station.inventory().setStackInSlot(5,new ItemStack(Items.IRON_CHESTPLATE));station.inventory().setStackInSlot(6,new ItemStack(Items.IRON_LEGGINGS));
                    for(int i=0;i<22;i++) {
                        var guard=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);guard.setPos(2.5,100,2.5);guard.setNoAi(true);guard.setOwnerId(player.getUUID());
                        guard.setCustomName(net.minecraft.network.chat.Component.literal("Guard "+(i+1)));level.addFreshEntity(guard);
                        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:ak47")).build());
                        var state=com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard);state.policy(com.gamergaming.villagerguardgunners.quartermaster.service.GuardServiceState.Policy.RESTOCK);
                        if(i<21)state.assignment(new com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStationAssignment(station.stationId(),level.dimension().location(),pos));
                    }
                    player.teleportTo(.5,100,2.5);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                    var npc=new Villager(EntityType.VILLAGER,level);npc.setPos(1.5,100,1.5);
                    npc.setVillagerData(npc.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
                    npc.setNoAi(true);npc.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));
                    npc.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BREAD),12,1,.05f));
                    level.addFreshEntity(npc);npcId=npc.getId();station.operator=npc.getUUID();
                });advance();
            } else if(stage==1&&tick-entered>25&&mc.level!=null&&mc.level.getEntity(npcId)!=null) {
                server(player->{var level=player.serverLevel();var pos=new BlockPos(0,100,0);level.getPoiManager().take(t->t.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(t,p)->p.equals(pos),pos,1);});advance();
            } else if(stage==2&&tick-entered>10) {
                mc.gameMode.interact(mc.player,mc.level.getEntity(npcId),InteractionHand.MAIN_HAND);advance();
            } else if(stage==3&&mc.screen instanceof QuartermasterManagementScreen&&mc.player.containerMenu instanceof QuartermasterManagementMenu menu&&menu.summary()!=null&&menu.details()!=null&&!menu.details().refreshing()&&menu.details().rows().size()>=6) {
                check("npc-packet-opens-owner-summary",menu.summary().manage()&&menu.summary().occupied()==7);
                check("minimum-gui-fits",mc.getWindow().getGuiScaledWidth()>=320&&mc.getWindow().getGuiScaledHeight()>=240);
                capture="management-supplies-small.png";advance();
            } else if(stage==4&&capture==null&&tick-entered>12&&mc.player.containerMenu instanceof QuartermasterManagementMenu menu&&menu.details()!=null) {
                var p=menu.details();
                switch(inspection) {
                    case 0 -> {button("Needs");inspection++;}
                    case 1 -> {if(p.view()!=ManagementProtocol.NEEDS||p.rows().isEmpty())return;
                        check("client-scarce-stock-totals",p.rows().stream().mapToInt(OverviewProtocol.Row::requested).sum()==1344&&p.rows().stream().mapToInt(OverviewProtocol.Row::available).sum()==80);
                        check("client-incoming-column",p.rows().stream().allMatch(r->r.incoming()==0)
                                &&mc.screen.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().contains(" / 0 / ")));
                        capture="management-needs-small.png";inspection++;}
                    case 2 -> {var b=mc.screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().startsWith("tacz:762x39")).map(c->(Button)c).findFirst().orElseThrow();
                        b.mouseClicked(b.getX()+4,b.getY()+4,0);inspection++;}
                    case 3 -> {if(p.view()!=ManagementProtocol.GUARDS)return;check("client-shortage-filter",p.rows().size()==20&&p.pages()==1);capture="management-filtered-small.png";inspection++;}
                    case 4 -> {button("All guards");inspection++;}
                    case 5 -> {if(p.pages()!=2)return;button(">");inspection++;}
                    case 6 -> {if(p.page()!=1)return;check("client-roster-second-page",p.rows().size()==2);capture="management-roster-page2-small.png";inspection++;}
                    case 7 -> {button("Service");inspection++;}
                    case 8 -> {if(p.view()!=ManagementProtocol.SERVICE)return;capture="management-service-small.png";inspection++;}
                    case 9 -> {button("Stock");inspection++;}
                    case 10 -> {if(p.view()!=ManagementProtocol.SUPPLIES)return;
                        var b=mc.screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().startsWith("Patrol Helmet")).map(c->(Button)c).findFirst().orElseThrow();
                        var w=org.lwjgl.BufferUtils.createIntBuffer(1);var h=org.lwjgl.BufferUtils.createIntBuffer(1);org.lwjgl.glfw.GLFW.glfwGetWindowSize(mc.getWindow().getWindow(),w,h);
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),(b.getX()+6.0)*w.get(0)/mc.screen.width,(b.getY()+6.0)*h.get(0)/mc.screen.height);inspection++;}
                    case 11 -> {capture="management-tooltip-small.png";check("client-stock-variants-distinct",p.rows().size()==6);inspection++;}
                    case 12 -> {var b=mc.screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().startsWith("Patrol Helmet")).map(c->(Button)c).findFirst().orElseThrow();
                        check("client-scroll-handled",mc.screen.mouseScrolled(b.getX()+4,b.getY()+4,-1));capture="management-scrolled-small.png";inspection++;}
                    default -> {button("Guards");advance();}
                }
                entered=tick;
            }
            else if(stage==5&&mc.player.containerMenu instanceof QuartermasterManagementMenu menu&&menu.summary().tab()==ManagementProtocol.GUARDS) {
                capture="management-guards-small.png";advance();
            } else if(stage==6&&capture==null&&tick-entered>12&&mc.player.containerMenu instanceof QuartermasterManagementMenu menu) {
                var bulk=menu.bulkState();
                switch(bulkInspection) {
                    case 0 -> {
                        button("Needs");button("Manage");
                        check("bulk-open-waits-for-server",mc.screen instanceof QuartermasterManagementScreen);bulkInspection=-2;
                    }
                    case -2 -> {
                        check("rate-limited-open-keeps-overview",mc.screen instanceof QuartermasterManagementScreen);
                        button("Manage");bulkInspection=1;
                    }
                    case 1 -> {if(!(mc.screen instanceof com.gamergaming.villagerguardgunners.client.QuartermasterBulkScreen)||bulk==null)return;
                        check("bulk-defaults-keep",bulk.policy()==-1&&bulk.replacement()==-1&&bulk.assignment()==-1);
                        var row=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("[ ] ")).map(c->(Button)c).findFirst().orElseThrow();
                        row.mouseClicked(row.getX()+4,row.getY()+4,0);bulkInspection++;}
                    case 2 -> {if(bulk.selected()!=1)return;button("Policy: Keep");bulkInspection++;}
                    case 3 -> {if(bulk.policy()!=0)return;capture="management-bulk-selection-small.png";button("Preview");bulkInspection++;}
                    case 4 -> {if(bulk.token()==null||bulk.view()!=1)return;
                        check("bulk-preview-identifies-guard",bulk.rows().size()==1&&bulk.rows().get(0).detail().contains("After: Manual"));
                        capture="management-bulk-preview-small.png";bulkInspection++;}
                    case 5 -> {button("Needs");bulkInspection++;}
                    case 6 -> {if(bulk.view()!=2)return;capture="management-bulk-needs-small.png";button("Apply");bulkInspection++;}
                    case 7 -> {if(!bulk.status().equals("applied"))return;check("bulk-native-packet-applied",bulk.applied()==1&&bulk.currentPolicy()==0&&bulk.token()==null);
                        capture="management-bulk-applied-small.png";bulkInspection++;}
                    case 8 -> {button("Clear");button("Back");check("bulk-close-waits-for-server",mc.screen instanceof com.gamergaming.villagerguardgunners.client.QuartermasterBulkScreen);bulkInspection=-3;}
                    case -3 -> {check("rate-limited-close-keeps-editor",mc.screen instanceof com.gamergaming.villagerguardgunners.client.QuartermasterBulkScreen);button("Back");bulkInspection=9;}
                    case 9 -> {if(!(mc.screen instanceof QuartermasterManagementScreen))return;
                        server(player->{
                            var level=player.serverLevel();var pos=new BlockPos(0,100,0);var station=(QuartermasterBlockEntity)level.getBlockEntity(pos);
                            var guard=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);guard.setUUID(new UUID(Long.MIN_VALUE,3));guard.setPos(2.5,100,2.5);guard.setNoAi(true);guard.setOwnerId(player.getUUID());level.addFreshEntity(guard);
                            com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).assignment(new com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStationAssignment(station.stationId(),level.dimension().location(),pos));
                            guard.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);unavailableGuard=guard.getUUID();
                        });button("Guards");bulkInspection++;}
                    case 10 -> {var p=menu.details();if(unavailableGuard==null||p==null||p.view()!=ManagementProtocol.GUARDS||p.rows().stream().noneMatch(r->unavailableGuard.equals(r.guard())))return;
                        check("client-unavailable-record-action",p.rows().stream().anyMatch(r->unavailableGuard.equals(r.guard())&&r.selection()>=0));
                        capture="roster-forget-small.png";bulkInspection++;}
                    case 11 -> {var row=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().contains("Forget record")).map(c->(Button)c).findFirst().orElseThrow();
                        row.mouseClicked(row.getX()+4,row.getY()+4,0);bulkInspection++;}
                    case 12 -> {server(player->{
                        if(com.gamergaming.villagerguardgunners.quartermaster.service.ServiceRoster.get(player.serverLevel()).find(unavailableGuard)!=null)throw new AssertionError("Forget record packet did not remove roster hint");forgotten=true;
                    });bulkInspection++;}
                    case 13 -> {if(!forgotten||menu.details()==null||menu.details().refreshing())return;
                        check("client-forget-record-packet",menu.details().rows().stream().noneMatch(r->unavailableGuard.equals(r.guard())));bulkInspection++;}
                    default -> {if(!(mc.screen instanceof QuartermasterManagementScreen))return;button("Open Stock");advance();}
                }
                entered=tick;
            }
            else if(stage==7&&mc.screen instanceof QuartermasterScreen){capture="management-stock-small.png";advance();}
            else if(stage==8&&capture==null&&tick-entered>10){button("Overview");advance();}
            else if(stage==9&&mc.screen instanceof QuartermasterManagementScreen&&mc.player.containerMenu instanceof QuartermasterManagementMenu menu&&menu.summary()!=null) {
                check("station-round-trip",menu.summary().stock());server(player->player.getInventory().setItem(10,new ItemStack(Items.EMERALD,20)));button("Trade");advance();
            } else if(stage==10&&mc.screen instanceof MerchantScreen) {
                if(capture!=null||tick-entered<=12)return;
                var merchant=(net.minecraft.world.inventory.MerchantMenu)mc.player.containerMenu;
                switch(tradeInspection) {
                    case 0 -> {check("native-merchant-screen",true);check("new-supply-offers-visible",merchant.getOffers().stream().anyMatch(o->o.getResult().getItem()==com.gamergaming.villagerguardgunners.init.ModItems.BANDAGE.get()));capture="management-native-trade.png";tradeInspection++;}
                    case 1 -> {var buttons=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getWidth()==88).map(c->(Button)c).toList();
                        check("native-trade-row-available",!buttons.isEmpty());var b=buttons.get(0);b.mouseClicked(b.getX()+5,b.getY()+5,0);tradeInspection++;}
                    case 2 -> {if(merchant.getSlot(2).getItem().isEmpty())return;check("native-client-trade-result",merchant.getSlot(2).getItem().getCount()==4);
                        capture="management-trade-result.png";tradeInspection++;}
                    case 3 -> {mc.gameMode.handleInventoryMouseClick(merchant.containerId,2,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);tradeInspection++;}
                    case 4 -> {if(merchant.getCarried().isEmpty())return;check("native-client-purchase",merchant.getCarried().getCount()==4&&merchant.getSlot(0).getItem().getCount()==18);
                        mc.gameMode.handleInventoryMouseClick(merchant.containerId,3,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);capture="management-trade-purchased.png";tradeInspection++;}
                    case 5 -> {server(player->com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.quartermasterTrades.suppliesEnabled.accept(false));tradeInspection++;}
                    case 6 -> {if(!merchant.getSlot(2).getItem().isEmpty())return;check("open-client-trade-disabled",merchant.getOffers().get(0).isOutOfStock()&&merchant.getSlot(0).getItem().getCount()==18);capture="management-trade-disabled.png";tradeInspection++;}
                    case 7 -> {mc.gameMode.handleInventoryMouseClick(merchant.containerId,2,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player);tradeInspection++;}
                    default -> {check("disabled-click-no-input-loss",merchant.getSlot(0).getItem().getCount()==18&&merchant.getCarried().isEmpty());
                        server(player->com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.quartermasterTrades.suppliesEnabled.accept(true));advance();}
                }
                entered=tick;
            } else if(stage==11&&capture==null&&tick-entered>10) {
                mc.player.closeContainer();
                server(player->{
                    var level=player.serverLevel();((Villager)level.getEntity(npcId)).setTradingPlayer(null);
                    // A separate private station avoids placement recovery and dropped items in this UI fixture.
                    var pos=new BlockPos(-2,100,0);level.setBlockAndUpdate(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState());
                    var station=(QuartermasterBlockEntity)level.getBlockEntity(pos);
                    var other=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PrivateOwner"));station.placedBy(other);
                    station.inventory().setStackInSlot(0,new ItemStack(Items.DIAMOND,12));
                    var npc=new Villager(EntityType.VILLAGER,level);npc.setPos(-1.5,100,1.5);
                    npc.setVillagerData(npc.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
                    npc.setNoAi(true);npc.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));
                    npc.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BREAD),12,1,.05f));
                    level.addFreshEntity(npc);npcId=npc.getId();station.operator=npc.getUUID();
                });advance();
            } else if(stage==12&&tick-entered>25) {
                server(player->{
                    var level=player.serverLevel();var pos=new BlockPos(-2,100,0);
                    level.getPoiManager().take(t->t.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(t,p)->p.equals(pos),pos,1);
                    var npc=(Villager)level.getEntity(npcId);
                    if(player.getInventory().countItem(Items.EMERALD)!=18||player.getInventory().countItem(com.gamergaming.villagerguardgunners.init.ModItems.BANDAGE.get())!=4)throw new AssertionError("merchant close did not preserve purchase and remaining payment");
                    if(!player.getMainHandItem().isEmpty()){player.getInventory().setItem(11,player.getMainHandItem().copy());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
                    tradeReturned=true;
                    if(!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())throw new AssertionError("visitor fixture must have empty hands");
                    if(QuartermasterManagement.station(npc)!=level.getBlockEntity(pos))throw new AssertionError("visitor fixture has no claimed station");
                    if(!new ManagementSession(player,(QuartermasterBlockEntity)level.getBlockEntity(pos),npc).valid(player))throw new AssertionError("visitor fixture cannot open a valid session");
                });advance();
            } else if(stage==13&&tick-entered>10){check("client-trade-close-returns-payment",tradeReturned);mc.gameMode.interact(mc.player,mc.level.getEntity(npcId),InteractionHand.MAIN_HAND);advance();}
            else if(stage==14&&mc.screen instanceof QuartermasterManagementScreen&&mc.player.containerMenu instanceof QuartermasterManagementMenu menu&&menu.summary()!=null) {
                check("visitor-no-private-summary",!menu.summary().stock()&&!menu.summary().manage()&&menu.summary().occupied()==-1);capture="management-visitor-small.png";advance();
            } else if(stage==15&&capture==null&&tick-entered>10){server(player->player.teleportTo(40,100,40));advance();}
            else if(stage==16&&!(mc.screen instanceof QuartermasterManagementScreen)&&tick-entered>10){check("movement-closes-client-menu",true);finish(null);}
        }catch(Throwable e){e.printStackTrace();finish(e.toString());}
    }
}
