package com.gamergaming.villagerguardgunners.qa.multiplayer;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.medical.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.quartermaster.*;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.google.gson.Gson;
import com.tacz.guns.api.item.builder.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** Test fixtures and server assertions only. All tested actions arrive from two real clients. */
@Mod("vgg_multiplayer_qa")
public final class MultiplayerAcceptance {
    static final Gson JSON = new Gson();
    static Path shared() { return Path.of(System.getProperty("vgg.multiplayer.shared")); }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void write(String name, Object data) throws Exception {
        Path temp = shared().resolve(name + ".tmp");
        Files.writeString(temp, JSON.toJson(data));
        Files.move(temp, shared().resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    final List<String> passed = new ArrayList<>();
    ServerLevel level;
    QuartermasterBlockEntity station;
    ArmoryBlockEntity armory;
    Guard guard;
    Villager operator;
    long revision;
    int stage = -1, ticks, entered;
    int shots, commandRevision;
    boolean done;
    final BlockPos origin = new BlockPos(0, 160, 0);
    public MultiplayerAcceptance() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    void passed(String name) { passed.add(name); System.out.println("VGG_MULTIPLAYER_QA_CASE_PASS " + name); }
    boolean ack(String role, int value) { return Files.exists(shared().resolve(role + "-" + value + ".done")); }
    boolean both() { return ack("A", stage) && ack("B", stage); }
    void advance(int next) throws Exception {
        stage = next; entered = ticks;
        write("control.json", Map.of("stage", stage, "guard", guard.getUUID().toString(), "entity", guard.getId(), "operator", operator.getId()));
        System.out.println("VGG_MULTIPLAYER_QA_STAGE " + stage);
    }
    void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) level.setBlock(origin.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 3);
            level.setDefaultSpawnPos(origin.offset(0,0,3),180);
            level.setDayTime(3000);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
            ModConfigs.GUARD_GUNNERS.combat.aiming.errorDegrees.accept(3.5);
            ModConfigs.GUARD_GUNNERS.medical.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);
            ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);
        } catch (Throwable error) { fail(error); }
    }
    void openGuard(ServerPlayer p) {
        p.closeContainer();
        p.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new GuardGunnerContainer(id, inventory,
                guard.guardInventory, GuardAmmoAccess.getAmmoHandler(guard), guard), Component.literal("QA Guard")), data -> data.writeInt(guard.getId()));
    }
    void openManagement(ServerPlayer p) {
        p.closeContainer();
        require(QuartermasterManagement.open(p,station,station.canInteract(p)?null:operator),"Management opening rejected");
    }
    void tick(ServerTickEvent.Post event) {
        if (level == null || done) return;
        ticks++;
        try {
            for (String role : List.of("A","B")) if (Files.exists(shared().resolve(role + "-error.txt")))
                throw new AssertionError(Files.readString(shared().resolve(role + "-error.txt")));
            var players = level.getServer().getPlayerList();
            var a = players.getPlayerByName("VggOwner"); var b = players.getPlayerByName("VggPeer");
            if (stage == -1) {
                if (ticks > 6000) throw new AssertionError("Two installed clients did not join");
                if (a == null || b == null) return;
                require(!a.getUUID().equals(b.getUUID()) && !a.hasDisconnected() && !b.hasDisconnected(), "Need two distinct connections");
                passed("two-real-connected-clients");
                for (var p : List.of(a,b)) {
                    players.deop(p.getGameProfile()); p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
                    p.teleportTo(p==a?1.5:-1.5,160,3.5); p.setYRot(180); p.setXRot(12);
                }
                level.setBlock(origin,ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
                station=(QuartermasterBlockEntity)level.getBlockEntity(origin); station.placedBy(a);
                station.inventory().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,12));
                level.setBlock(origin.offset(4,0,0),ModBlocks.ARMORY.get().defaultBlockState(),3);
                armory=(ArmoryBlockEntity)level.getBlockEntity(origin.offset(4,0,0));
                armory.getInventory().setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(32).build());
                a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,24000));
                guard=GuardEntityType.GUARD.get().create(level); guard.setPersistenceRequired(); guard.setNoAi(true);
                guard.setPos(2.5,160,1.5); guard.setOwnerId(a.getUUID()); guard.setCustomName(Component.literal("QA Guard")); guard.setCustomNameVisible(false);
                guard.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setAmmoCount(20).build(level.registryAccess()));
                GuardMedicalAccess.get(guard).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),4));
                require(GuardSidearmAccess.getConfiguredSidearmInventory(guard).insertItem(0,
                        GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).build(level.registryAccess()),false).isEmpty(),"Pistol sidearm fixture rejected");
                require(level.addFreshEntity(guard),"Guard spawn");
                operator=EntityType.VILLAGER.create(level); operator.setPersistenceRequired(); operator.setNoAi(true); operator.setPos(.5,160,1.5);
                operator.setVillagerData(operator.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(QuartermasterProfession.ID)));
                operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),origin)); level.addFreshEntity(operator);
                station.operator=operator.getUUID(); advance(0); return;
            }
            if (ticks-entered > 1600) throw new AssertionError("Stage timed out: " + stage);
            if (stage == 0 && ticks-entered > 30) {
                level.getPoiManager().take(t->t.value()==BuiltInRegistries.POINT_OF_INTEREST_TYPE.get(QuartermasterProfession.ID),(t,p)->p.equals(origin),origin,1);
                openManagement(a); openManagement(b); advance(1);
            } else if(stage==1 && both()) {
                require(GuardEquipmentServiceAccess.get(guard).settingsRevision()==0,"Denied peer changed guard service settings");
                passed("unauthorized-peer-management-denied");
                players.op(b.getGameProfile()); b.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,24000));
                openManagement(a); openManagement(b); advance(2);
            } else if(stage==2 && both()) { passed("two-client-summary-overview-bulk-roundtrip"); advance(3); }
            else if(stage==3 && ack("A",3)) {
                require(GuardEquipmentServiceAccess.get(guard).policy()==GuardServiceState.Policy.RESTOCK,"Owner bulk action missing");
                revision=GuardEquipmentServiceAccess.get(guard).settingsRevision(); passed("owner-bulk-apply"); advance(4);
            } else if(stage==4 && ack("B",4)) {
                require(GuardEquipmentServiceAccess.get(guard).settingsRevision()==revision,"Stale peer overwrote settings");
                passed("concurrent-stale-preview-refused");
                a.closeContainer(); b.closeContainer(); a.openMenu(station,origin); b.openMenu(station,origin); advance(5);
            } else if(stage==5 && both()) {
                require(station.inventory().getStackInSlot(0).isEmpty() && a.getInventory().countItem(Items.IRON_INGOT)==12,"Stock transfer not conserved");
                passed("quartermaster-stock-two-viewer-sync");
                players.deop(b.getGameProfile()); b.removeAllEffects(); advance(6);
            } else if(stage==6 && both()) {
                require(b.containerMenu==b.inventoryMenu,"Revoked stock menu stayed open");
                require(station.inventory().getStackInSlot(0).isEmpty() && b.getInventory().countItem(Items.IRON_INGOT)==0,"Revoked stock access leaked items");
                passed("live-permission-revocation-closes-peer-menu");
                a.closeContainer(); b.closeContainer(); a.teleportTo(3.5,160,2.5); b.teleportTo(4.5,160,2.5);
                a.openMenu(armory,armory.getBlockPos()); b.openMenu(armory,armory.getBlockPos()); advance(7);
            } else if(stage==7 && both()) {
                require(armory.getInventory().getStackInSlot(0).isEmpty(),"Armory transfer missing"); passed("armory-ammo-two-viewer-sync");
                a.closeContainer(); b.closeContainer(); a.teleportTo(1.5,160,3.5); b.teleportTo(-1.5,160,3.5);
                openGuard(a); advance(8);
            } else if(stage==8 && both()) {
                require(!GuardOrdersAccess.getOrders(guard).equals(GuardOrders.DEFAULT),"Order button not applied");
                require(GuardSidearmAccess.getConfiguredSidearmInventory(guard).getStackInSlot(0).isEmpty() && a.getInventory().items.stream().filter(s->s.getItem() instanceof com.tacz.guns.api.item.IGun g && g.getGunId(s).toString().equals("tacz:glock_17")).mapToInt(ItemStack::getCount).sum()==1,"Sidearm transfer not conserved");
                passed("guard-menu-orders-sidearm-medical-service-sync");
                openGuard(b); advance(9);
            } else if(stage==9 && both()) {
                require(GuardOrdersAccess.getOrders(guard).equals(GuardOrders.DEFAULT.cycle(GuardOrders.OrderField.ENGAGEMENT)),"Peer changed guard orders");
                passed("unauthorized-peer-order-button-denied");
                require(a.getUUID().equals(guard.getOwnerId()) && !guard.isFollowing() && !guard.isPatrolling(), "Forged peer packets changed owner or patrol");
                passed("unauthorized-peer-native-follow-and-patrol-denied");
                a.closeContainer(); b.closeContainer(); a.removeAllEffects(); a.getInventory().clearContent(); a.getInventory().setItem(10,new ItemStack(Items.EMERALD,20));
                players.op(b.getGameProfile()); a.getInventory().selected=0; openManagement(a); openManagement(b); advance(10);
            } else if(stage==10 && ack("A",10)) {
                require(operator.getTradingPlayer()==a && a.containerMenu instanceof MerchantMenu,"Owner trading missing");
                passed("real-merchant-click-payment-and-output"); advance(11);
            } else if(stage==11 && ack("B",11)) {
                require(operator.getTradingPlayer()==a && !(b.containerMenu instanceof MerchantMenu),"Peer stole merchant");
                passed("concurrent-trading-lock"); advance(12);
            } else if(stage==12 && a==null && ack("A",12)) {
                require(operator.getTradingPlayer()==null,"Disconnect retained merchant lock"); passed("disconnect-releases-merchant"); advance(13);
            } else if(stage==13 && a!=null && ack("A",13)) {
                int emeralds=a.getInventory().countItem(Items.EMERALD);
                for(var drop:level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(12))) if(drop.getItem().is(Items.EMERALD))emeralds+=drop.getItem().getCount();
                require(emeralds==18 && a.getInventory().countItem(ModItems.BANDAGE.get())==4,"Reconnect lost/duplicated trade items");
                passed("reconnect-conserves-payment-and-purchase"); a.teleportTo(1.5,160,3.5); openManagement(a); advance(14);
            } else if(stage==14 && ack("A",14)) {
                require(GuardEquipmentServiceAccess.get(guard).settingsRevision()==revision,"Old session replay applied"); passed("reconnect-rejects-old-session-payload");
                a.closeContainer(); b.closeContainer();
                guard.setHealth(2); guard.hurt(level.damageSources().generic(),100);
                require(GuardLifeAccess.get(guard).stage()==GuardLifeState.Stage.DOWNED,"Guard failed to enter downed state");
                a.getInventory().selected=0; a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.BANDAGE.get(),4));
                advance(15);
            } else if(stage==15 && both()) {
                passed("both-clients-render-downed-guard"); advance(16);
            } else if(stage==16 && both()) {
                require(GuardLifeAccess.get(guard).stage()==GuardLifeState.Stage.STABILIZED,"Real treatment interaction failed");
                require(a.getMainHandItem().getCount()==3,"Treatment did not consume exactly one bandage");
                passed("client-interaction-treatment-and-peer-state-sync");
                BlockPos head=origin.offset(4,0,2);
                var state=ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING,Direction.EAST);
                level.setBlock(head.west(),state.setValue(RecoveryCotBlock.PART,BedPart.FOOT),3);
                level.setBlock(head,state.setValue(RecoveryCotBlock.PART,BedPart.HEAD),3);
                guard.setPos(4.5,160,2.5); require(CotRestRuntime.attach(guard,head),"Cot fixture attach failed");
                GuardLifeAccess.get(guard).recover(1200,1800,true); CotRestRuntime.synchronize(guard);
                a.teleportTo(3.5,160,6.5); b.teleportTo(5.5,160,6.5); advance(17);
            } else if(stage==17 && both()) {
                passed("both-clients-render-cot-pose-and-name-tag");
                CotRestRuntime.release(guard,false);
                GuardLifeAccess.get(guard).load(new net.minecraft.nbt.CompoundTag());
                ((GuardLifeAccess)guard).villagerGuardGunners$setLifeVisual(0);
                guard.setPos(2.5,160,1.5); a.teleportTo(1.5,160,4.5); b.teleportTo(4.5,160,4.5);
                var op=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard);
                op.getDataHolder().drawTimestamp=op.getDataHolder().shootTimestamp=-1;
                op.getDataHolder().meleePrepTickCount=-1; op.aim(true); advance(18);
            } else if(stage==18 && both()) {
                passed("native-aim-state-reaches-both-clients"); advance(19);
            } else if(stage==19) {
                if(ticks-entered>10 && (ticks-entered)%10==0 && shots<3) {
                    var result=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).shoot(()->-15f,()->180f);
                    if(result==com.tacz.guns.api.entity.ShootResult.SUCCESS)shots++;
                }
                if(both()) {
                    require(shots>0,"Native shot did not execute"); passed("native-gunshot-event-and-render-on-both-clients");
                    GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(32).build());
                    com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).reload(); advance(20);
                }
            } else if(stage==20 && both()) {
                passed("native-reload-event-reaches-both-clients");
                sendLargeCache(a,b); advance(21);
            } else if(stage==21 && both()) {
                passed("oversized-gun-cache-fragments-applied-on-both-clients");
                com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).aim(false);
                a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.ORDERS_LEDGER.get()));
                advance(22);
            } else if(stage==22 && ack("A",22)) {
                var copy=com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.readFrom(com.gamergaming.villagerguardgunners.inventory.ItemCustomData.read(a.getMainHandItem()));
                require(copy.orders()!=null && copy.orders().equals(GuardOrdersAccess.getOrders(guard)),"Orders Ledger components did not capture orders");
                passed("orders-ledger-capture-item-components-sync"); GuardOrdersAccess.setOrders(guard,GuardOrders.DEFAULT); advance(23);
            } else if(stage==23 && ack("A",23)) {
                require(GuardOrdersAccess.getOrders(guard).equals(GuardOrders.DEFAULT.cycle(GuardOrders.OrderField.ENGAGEMENT)),"Orders Ledger apply did not restore orders");
                passed("orders-ledger-client-apply");
                a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.SQUAD_COMMAND_LEDGER.get())); advance(24);
            } else if(stage==24 && ack("A",24)) {
                require(GuardSquadAccess.getSquadId(guard)!=null,"Squad Ledger interaction did not assign a squad");
                passed("squad-ledger-binding-components-sync");
                com.gamergaming.villagerguardgunners.inventory.ItemCustomData.update(a.getMainHandItem(),tag->com.gamergaming.villagerguardgunners.squad.SquadLedgerModeCodec.writeTo(tag,com.gamergaming.villagerguardgunners.squad.SquadCommandType.HOLD));
                players.deop(b.getGameProfile()); b.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,a.getMainHandItem().copy()); advance(25);
            } else if(stage==25 && ack("A",25)) {
                var command=GuardSquadCommandAccess.getCommand(guard);
                require(command!=null && command.issuedBy().equals(a.getUUID()) && command.type()==com.gamergaming.villagerguardgunners.squad.SquadCommandType.HOLD,"Owner squad command did not arrive");
                commandRevision=GuardSquadCommandAccess.getRevision(guard); passed("owner-squad-command-from-client-item-use"); advance(26);
            } else if(stage==26 && ack("B",26)) {
                require(GuardSquadCommandAccess.getRevision(guard)==commandRevision,"Peer with copied ledger overwrote squad command");
                passed("peer-copied-squad-ledger-command-denied");
                a.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,24000));
                a.closeContainer(); advance(27);
            } else if(stage==27 && ack("A",27)) {
                require(!guard.isFollowing() && !guard.isPatrolling(), "Owner sent commands without menu");
                passed("native-commands-without-menu-denied");
                tallestegg.guardvillagers.configuration.GuardConfig.COMMON.followHero.set(true);
                tallestegg.guardvillagers.configuration.GuardConfig.COMMON.setGuardPatrolHotv.set(true);
                a.teleportTo(1.5,160,3.5); openGuard(a); a.removeAllEffects(); advance(28);
            } else if(stage==28 && ack("A",28)) {
                require(!guard.isFollowing() && !guard.isPatrolling(), "Owner bypassed live Hero requirement");
                passed("native-commands-revoked-hero-denied");
                done=true; write("server-result.json",Map.of("passed",true,"cases",passed));
                System.out.println("VGG_MULTIPLAYER_QA_PASS cases="+passed.size());
            }
        } catch(Throwable error) { fail(error); }
    }
    void sendLargeCache(ServerPlayer... players) {
        var cache=new java.util.EnumMap<com.tacz.guns.resource.network.DataType,Map<ResourceLocation,String>>(com.tacz.guns.resource.network.DataType.class);
        com.tacz.guns.resource.CommonAssetsManager.getInstance().getNetworkCache().forEach((type,values)->cache.put(type,new HashMap<>(values)));
        var guns=cache.get(com.tacz.guns.resource.network.DataType.GUN_DATA);
        String sample=guns.values().iterator().next();
        guns.put(ResourceLocation.parse("vgg_multiplayer_qa:sync_probe"),sample);
        // Legal JSON whitespace increases wire size without changing any gun behavior.
        cache.forEach((type,values)->values.replaceAll((id,json)->json+" ".repeat(Math.max(0,28000-json.length()))));
        byte[] data=com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncProtocol.encodeCache(cache);
        require(data.length>1_048_576,"Fixture did not exercise the custom-payload size boundary");
        var chunks=com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncProtocol.split(UUID.randomUUID(),data);
        for(var player:players)for(var chunk:chunks)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,chunk);
        System.out.println("VGG_MULTIPLAYER_QA_LARGE_CACHE bytes="+data.length+" chunks="+chunks.size());
    }
    void fail(Throwable error) {
        done=true; error.printStackTrace();
        try { write("server-result.json",Map.of("passed",false,"cases",passed,"failure",error.toString())); }
        catch(Exception ignored) {}
    }
}
