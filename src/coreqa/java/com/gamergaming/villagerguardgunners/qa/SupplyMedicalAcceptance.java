package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.inventory.ItemCustomData;
import com.gamergaming.villagerguardgunners.medical.*;
import com.gamergaming.villagerguardgunners.quartermaster.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** Real installed-server workflows; no replacement AI or test code in the production JAR. */
final class SupplyMedicalAcceptance {
    static final BlockPos ORIGIN = new BlockPos(0, 160, 0);
    static final Path EXPECTED = Path.of("workflow-qa-expected.nbt");
    static final String[] CASES = {"armory_resupply", "quartermaster_job", "quartermaster_service", "medic_healing", "player_casualty_care", "transport_blocked_fallback", "transport_and_cot"};
    ServerLevel level;
    FakePlayer owner;
    Guard guard, medic;
    Villager operator;
    ArmoryBlockEntity armory;
    QuartermasterBlockEntity station;
    BlockPos activeCot;
    final List<Entity> actors = new ArrayList<>();
    int index = -1, ticks;
    boolean done, restoring, started, carried, cotSeen, healed;
    CompoundTag expected;

    SupplyMedicalAcceptance() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static ResourceLocation id(String name) { return ResourceLocation.parse(name.contains(":") ? name : "villagerguardgunners:" + name); }
    static void passed(String name) { LogUtils.getLogger().info("VGG_WORKFLOW_QA_CASE_PASS case={}", name); }
    static ItemStack bandages(int count) { return new ItemStack(ModItems.BANDAGE.get(), count); }
    static ItemStack ammo(int count) { return AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(count).build(); }
    ItemStack gun(String name) { return GunItemBuilder.create().setId(id("tacz:" + name)).setAmmoCount(0).setAmmoInBarrel(false).build(level.registryAccess()); }
    Guard guard(BlockPos position, boolean ai) {
        Guard g = GuardEntityType.GUARD.get().create(level);
        g.setPersistenceRequired(); g.setPos(Vec3.atBottomCenterOf(position)); g.setOwnerId(owner.getUUID()); g.setNoAi(!ai);
        var nativeData = IGunOperator.fromLivingEntity(g).getDataHolder();
        nativeData.drawTimestamp = nativeData.shootTimestamp = -1; nativeData.meleePrepTickCount = -1;
        require(level.addFreshEntity(g), "Guard spawn rejected"); actors.add(g); return g;
    }
    void clearActors() {
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (Entity e : actors) {
            if (e instanceof Guard g) { TransportRuntime.cancel(g); PlayerTreatmentRuntime.cancel(g); MedicRuntime.cancel(g, MedicState.Status.INTERRUPTED); CotRestRuntime.release(g, false); }
            e.discard();
        }
        actors.clear(); guard = medic = null;
    }
    void placeCot(BlockPos head) {
        var state = ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING, Direction.EAST);
        level.setBlock(head.west(), state.setValue(RecoveryCotBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, state.setValue(RecoveryCotBlock.PART, BedPart.HEAD), 3);
        require(level.getBlockEntity(head) instanceof RecoveryCotBlockEntity, "Cot head missing");
    }
    private void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) { level.setChunkForced(x, z, true); level.getChunk(x,z); }
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            level.setDayTime(3000);
            ModConfigs.GUARD_GUNNERS.medical.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.medical.medicAi.accept(true);
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);
            ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);
            if (Files.exists(EXPECTED)) { expected = NbtIo.readCompressed(EXPECTED, NbtAccounter.create(4_194_304)); restoring = true; return; }
            for (int x = -24; x <= 28; x++) for (int z = -16; z <= 20; z++) {
                level.setBlock(ORIGIN.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y < 6; y++) level.setBlock(ORIGIN.offset(x,y,z), Blocks.AIR.defaultBlockState(), 3);
            }
            owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VGG supply QA"));
            owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,24000));
            owner.setGameMode(GameType.SURVIVAL); owner.setPos(.5,160,2.5);
        } catch (Throwable error) { fail(error); }
    }
    private void begin() throws Exception {
        clearActors(); ticks = 0; index++; carried = cotSeen = healed = false;
        if (index == CASES.length) { preparePersistence(); return; }
        switch (CASES[index]) {
            case "armory_resupply" -> {
                BlockPos pos = ORIGIN.offset(8,0,0); level.setBlock(pos, ModBlocks.ARMORY.get().defaultBlockState(),3);
                armory = (ArmoryBlockEntity)level.getBlockEntity(pos); armory.getInventory().setStackInSlot(0,ammo(48));
                guard = guard(ORIGIN,true); guard.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));
            }
            case "quartermaster_job" -> {
                level.removeBlock(armory.getBlockPos(),false);
                BlockPos pos=ORIGIN.offset(8,0,0); level.setBlock(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
                station=(QuartermasterBlockEntity)level.getBlockEntity(pos); owner.setPos(8.5,160,2.5); station.placedBy(owner);
                level.setBlock(ORIGIN.offset(8,0,3),ModBlocks.ARMORY.get().defaultBlockState(),3);
                operator=EntityType.VILLAGER.create(level); operator.setPersistenceRequired(); operator.moveTo(8.5,160,1.5);
                require(level.addFreshEntity(operator),"Villager spawn rejected");
            }
            case "quartermaster_service" -> {
                WorkflowSupplyChecks.service(this);
                guard=guard(ORIGIN.offset(2,0,0),true); guard.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));
                GuardMedicalAccess.get(guard).inventory().setStackInSlot(0,bandages(1));
                var settings=new ServiceSettings(GuardServiceState.Policy.RESTOCK,null,false,
                        new ServiceStationAssignment(station.stationId(),level.dimension().location(),station.getBlockPos()));
                require(ServiceControls.applyAtStation(owner,station,ServiceManagementPermissions.Context.of(station),guard,settings)
                        ==ServiceManagementPermissions.Result.ALLOWED,"Station assignment rejected");
                station.inventory().setStackInSlot(0,ammo(23)); station.inventory().setStackInSlot(1,bandages(7));
            }
            case "medic_healing" -> {
                guard=guard(ORIGIN,true); guard.setHealth(8);
                medic=guard(ORIGIN.offset(6,0,0),true); GuardMedicAccess.get(medic).medic(true);
                GuardMedicalAccess.get(medic).inventory().setStackInSlot(0,bandages(4));
            }
            case "player_casualty_care" -> {
                guard=guard(ORIGIN,false); guard.setHealth(2); guard.hurt(level.damageSources().generic(),100);
                require(GuardLifeAccess.get(guard).stage()==GuardLifeState.Stage.DOWNED,"Lethal damage did not down guard");
                owner.setPos(.5,160,1.5); owner.setItemInHand(InteractionHand.MAIN_HAND,bandages(4));
                require(MedicalTreatment.fromHand(owner,guard,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.HEALED,"Stabilization failed");
                require(GuardLifeAccess.get(guard).stage()==GuardLifeState.Stage.STABILIZED,"Not stabilized");
            }
            case "transport_blocked_fallback", "transport_and_cot" -> {
                int z=CASES[index].equals("transport_and_cot")?-8:0;
                guard=guard(ORIGIN.offset(1,0,z),true); guard.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));
                guard.setHealth(2); guard.hurt(level.damageSources().generic(),100); GuardLifeRuntime.completeTreatment(guard,4,40);
                GuardMedicalAccess.get(guard).readyAt(0); activeCot=ORIGIN.offset(12,0,z); placeCot(activeCot);
                medic=guard(ORIGIN.offset(0,0,z),true); GuardMedicAccess.get(medic).medic(true);
                GuardMedicalAccess.get(medic).inventory().setStackInSlot(0,bandages(4));
                // Let the normal medic scheduler discover the newly loaded cot and start transport.
            }
        }
        LogUtils.getLogger().info("VGG_WORKFLOW_QA_BEGIN case={}",CASES[index]);
    }
    private void tick(ServerTickEvent.Post event) {
        if (level==null||done) return;
        try {
            ticks++;
            if (restoring) { restart(); return; }
            if (!started) {
                if (ticks<40) return;
                WorkflowSupplyChecks.resources(this); WorkflowSupplyChecks.storage(this); WorkflowMedicalChecks.run(this);
                started=true; begin(); return;
            }
            if (ticks%100==0) LogUtils.getLogger().info("VGG_WORKFLOW_QA_PROGRESS case={} ticks={} service={} life={} medic={}",
                    CASES[index],ticks,station==null?null:ServiceEligibility.station(station),
                    guard==null?null:GuardLifeAccess.get(guard).stage(),medic==null?null:GuardMedicAccess.get(medic).status);
            boolean success=false;
            switch (CASES[index]) {
                case "armory_resupply" -> {
                    int reserve=reserve(guard); int stock=armory.getInventory().getStackInSlot(0).getCount();
                    require(reserve+stock+loaded(guard)==48,"Armory ammo conservation failed");
                    success=reserve+loaded(guard)==48;
                }
                case "quartermaster_job" -> success=operator.getVillagerData().getProfession()==BuiltInRegistries.VILLAGER_PROFESSION.get(QuartermasterProfession.ID)
                        && ServiceEligibility.station(station)==ServiceStatus.READY;
                case "quartermaster_service" -> {
                    int reserve=reserve(guard), stock=station.inventory().getStackInSlot(0).getCount();
                    require(reserve+loaded(guard)+stock==23,"Service ammo conservation failed");
                    int pouch=GuardMedicalAccess.get(guard).inventory().getStackInSlot(0).getCount();
                    require(pouch+station.inventory().getStackInSlot(1).getCount()==8,"Service bandage conservation failed");
                    success=reserve+loaded(guard)==23 && pouch==8;
                }
                case "medic_healing" -> {
                    if (guard.getHealth()>8) { require(GuardMedicalAccess.get(medic).inventory().getStackInSlot(0).getCount()<4,"Medic healed without spending supplies"); success=true; }
                }
                case "player_casualty_care" -> {
                    if (ticks==60) require(MedicalTreatment.fromHand(owner,guard,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.STARTED,"Field treatment did not start");
                    if (ticks==80) owner.setItemInHand(InteractionHand.MAIN_HAND,bandages(2));
                    if (ticks==85) {
                        require(!PlayerTreatmentRuntime.busy(guard)&&GuardLifeRuntime.casualty(guard)&&owner.getMainHandItem().getCount()==2,"Interrupted treatment spent supplies");
                        require(MedicalTreatment.fromHand(owner,guard,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.STARTED,"Treatment retry failed");
                    }
                    if (ticks>85 && GuardLifeAccess.get(guard).stage()==GuardLifeState.Stage.RECOVERING) {
                        require(owner.getMainHandItem().getCount()==1,"Revival did not consume exactly one bandage"); success=true;
                    }
                }
                case "transport_blocked_fallback", "transport_and_cot" -> {
                    if(ticks%20==0 && ticks<=200) LogUtils.getLogger().info("VGG_WORKFLOW_QA_TRANSPORT tick={} carrier={} patient={} carried={} cot={} reason={} goals={} point={}",
                            ticks,medic.position(),guard.position(),carried,cotSeen,GuardMedicAccess.get(medic).transportReason,
                            medic.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(TransportMovementGoal.class::isInstance).map(TransportMovementGoal.class::cast).map(TransportMovementGoal::status).toList(),
                            TransportRuntime.cotPosition(level,activeCot));
                    carried |= guard.isPassenger(); cotSeen |= TransportRuntime.onCot(guard);
                    healed |= GuardLifeAccess.get(guard).stage()==GuardLifeState.Stage.RECOVERING;
                    if(healed && CASES[index].equals("transport_blocked_fallback")) {
                        require(!carried&&!cotSeen&&GuardMedicAccess.get(medic).transportReason.equals("route_blocked")
                                &&GuardLifeAccess.get(guard).remaining()>=300
                                &&GuardMedicalAccess.get(medic).inventory().getStackInSlot(0).getCount()==3,"Rejected carry route did not use safe field care");
                        success=true;
                    } else if(healed) require(carried&&cotSeen,"Revival happened without transport: carried="+carried+" cot="+cotSeen+" reason="+GuardMedicAccess.get(medic).transportReason);
                    if (healed&&!GuardLifeRuntime.blocked(guard)) {
                        require(carried&&cotSeen&&!guard.isPassenger()&&!CotRestRuntime.anchored(guard),"Transport/recovery skipped a stage");
                        require(GuardMedicalAccess.get(medic).inventory().getStackInSlot(0).getCount()==3,"Cot revival dose count changed");
                        success=true;
                    }
                }
            }
            if (success) {
                passed(CASES[index]);
                if(CASES[index].startsWith("transport_")) { level.removeBlock(activeCot,false); level.removeBlock(activeCot.west(),false); }
                begin();
            }
            else require(ticks<1200,"Workflow timed out: "+CASES[index]+" guard="+(guard==null?null:guard.position()));
        } catch (Throwable error) { fail(error); }
    }
    static int reserve(Guard g) { int total=0; var items=GuardAmmoAccess.getAmmoHandler(g); for(int i=0;i<items.getSlots();i++)total+=items.getStackInSlot(i).getCount();return total; }
    static int loaded(Guard g) { ItemStack s=g.getMainHandItem(); return s.getItem() instanceof IGun gun ? gun.getCurrentAmmoCount(s)+(gun.hasBulletInBarrel(s)?1:0):0; }
    private void preparePersistence() throws Exception {
        guard=guard(ORIGIN.offset(7,0,0),false); guard.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));
        GuardMedicalAccess.get(guard).inventory().setStackInSlot(2,bandages(13)); GuardMedicalAccess.get(guard).readyAt(level.getGameTime()+1000);
        var state=GuardEquipmentServiceAccess.get(guard);
        state.update(new ServiceSettings(GuardServiceState.Policy.RESTOCK,null,false,new ServiceStationAssignment(station.stationId(),level.dimension().location(),station.getBlockPos())));
        station.inventory().setStackInSlot(0,ammo(19));
        // Commit through the production service and journal, then freeze ordinary AI for the save.
        GuardMedicalAccess.get(guard).readyAt(0);
        var plan=ServicePlanner.plan(guard,station,QuartermasterServiceRuntime.sources(station));
        require(plan!=null&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED,"Persistence transfer failed");
        guard.setHealth(2); guard.hurt(level.damageSources().generic(),100); GuardLifeRuntime.completeTreatment(guard,4,40);
        placeCot(ORIGIN.offset(12,0,0)); require(CotRestRuntime.attach(guard,ORIGIN.offset(12,0,0)),"Persistence cot attachment failed");
        GuardMedicalAccess.get(guard).readyAt(level.getGameTime()+1000);
        ItemCustomData.update(guard.getMainHandItem(),tag->tag.putString("workflow","saved-components"));
        expected=new CompoundTag(); expected.putUUID("Guard",guard.getUUID()); expected.putUUID("Station",station.stationId());
        expected.putUUID("Operator",operator.getUUID()); expected.put("GuardData",guard.saveWithoutId(new CompoundTag()));
        expected.put("StationData",station.saveWithoutMetadata(level.registryAccess()));
        NbtIo.writeCompressed(expected,EXPECTED); done=true;
        LogUtils.getLogger().info("VGG_WORKFLOW_QA_READY_TO_RESTART uuid={}",guard.getUUID());
    }
    private void restart() {
        if (!(level.getEntity(expected.getUUID("Guard")) instanceof Guard g)) { require(ticks<400,"Saved guard missing"); return; }
        station=(QuartermasterBlockEntity)level.getBlockEntity(ORIGIN.offset(8,0,0));
        require(station!=null&&station.stationId().equals(expected.getUUID("Station")),"Station identity lost");
        Guard snapshot=GuardEntityType.GUARD.get().create(level); snapshot.load(expected.getCompound("GuardData"));
        require(ItemStack.matches(g.getMainHandItem(),snapshot.getMainHandItem()),"Gun components changed");
        require(GuardMedicalAccess.get(g).save().equals(GuardMedicalAccess.get(snapshot).save()),"Pouch/cooldown changed");
        require(GuardEquipmentServiceAccess.get(g).settings().equals(GuardEquipmentServiceAccess.get(snapshot).settings()),"Service settings/assignment changed");
        require(ServiceJournal.get(level.getServer()).verify(GuardEquipmentServiceAccess.get(g).receipt)
                &&ServiceJournal.get(level.getServer()).verify(station.serviceReceipt()),"Ownership journal lost committed receipts");
        require(station.saveWithoutMetadata(level.registryAccess()).equals(expected.getCompound("StationData")),"Station contents/permissions changed");
        require(level.getEntity(expected.getUUID("Operator")) instanceof Villager v
                &&v.getVillagerData().getProfession()==BuiltInRegistries.VILLAGER_PROFESSION.get(QuartermasterProfession.ID),"Quartermaster profession lost");
        CotRestRuntime.tick(g);
        require(GuardLifeAccess.get(g).stage()==GuardLifeState.Stage.STABILIZED&&CotRestRuntime.valid(g),"Saved casualty/cot relationship lost");
        done=true; LogUtils.getLogger().info("VGG_WORKFLOW_QA_RESTART_PASS uuid={} receipt={}",g.getUUID(),GuardEquipmentServiceAccess.get(g).receipt.revision());
    }
    private void fail(Throwable error) { done=true; LogUtils.getLogger().error("VGG_WORKFLOW_QA_FAIL case={} tick={}",index<0?"static":index<CASES.length?CASES[index]:"restart",ticks,error); }
}
