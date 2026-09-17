package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@Mod("vggquartermasterprobe")
public final class Probe {
    private static final List<String> CASES = new ArrayList<>();
    private static final BlockPos A = new BlockPos(0, 80, 0), B = new BlockPos(6, 80, 0);
    private int tick; private boolean done, tradesComplete, deliveryChecked;
    private Guard guard; private QuartermasterBlockEntity station, other; private Villager operator; private FakePlayer owner;
    public Probe() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(new AcceptanceProbe()::tick);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,PerformanceCases::beforeTick);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,PerformanceCases::afterTick);
    }
    private static void check(String name, boolean okay) {
        if (!okay) throw new AssertionError(name);
        CASES.add(name);
    }
    private ServiceStationAssignment assignment() { return new ServiceStationAssignment(station.stationId(), ((net.minecraft.world.level.block.entity.BlockEntity) station).getLevel().dimension().location(), A); }
    private ServiceSettings settings(GuardServiceState.Policy policy, ServiceStationAssignment assignment) {
        return new ServiceSettings(policy, ResourceLocation.parse("villagerguardgunners:rifleman"), false, assignment);
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || System.getProperty("vgg.quartermaster.output") == null) return;
        try {
            var level = event.getServer().overworld(); tick++;
            if (Boolean.getBoolean("vgg.quartermaster.restart")) {
                if (tick == 40) for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.getChunk(x,z);
                if (tick == 60) restart(level,event);
                return;
            }
            if (tick == 40) setup(level);
            if (tick == 60) run(level);
            if(tick>100&&!tradesComplete&&TradeCases.tick(level)){tradesComplete=true;CASES.addAll(TradeCases.results());}
            if (tick == 80) {
                // Block replacement queues POI updates; claim the new site after those updates settle.
                operator.setPos(.5,80,1.5);
                operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
                operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),A));
                station.operator=operator.getUUID();
                level.getPoiManager().take(type -> type.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(type,pos)->pos.equals(A),A,1);
                check("replacement-site-reclaimed", ServiceEligibility.station(station)==ServiceStatus.READY);
                CASES.addAll(ManagementFixCases.dimensionCheck(level,station,owner));
            }
            if (tick == 170) {
                check("assigned-empty-station-never-falls-back-to-stocked-neighbor", guard.getMainHandItem().isEmpty());
                station.inventory().setStackInSlot(0, gun());
            }
            if(tick>=1200&&!tradesComplete)throw new AssertionError("Trade reload/level-up timeout");
            // Native pathing and listener order can still have a valid trip in progress at tick 300.
            // Require actual delivery within a bounded window instead of one exact scheduler tick.
            if (tick >= 300 && tradesComplete && (!guard.getMainHandItem().isEmpty() || tick >= 600)) {
                if (!deliveryChecked) {
                System.out.println("VGG service="+GuardEquipmentServiceAccess.get(guard).status+" guardGate="+ServiceEligibility.guard(guard)+" stationGate="+ServiceEligibility.station(station)+" position="+guard.position()+" operator="+operator.position());
                check("scheduled-service-delivers-from-assigned-station", !guard.getMainHandItem().isEmpty() && station.inventory().getStackInSlot(0).isEmpty());
                check("neighbor-stock-preserved", !other.inventory().getStackInSlot(0).isEmpty());
                guard.setNoAi(true);
                deliveryChecked=true;
                }
                if (!IntegrationCases.tick(level)) return;
                CASES.addAll(IntegrationCases.results());
                finish(event, null);
            }
        } catch (Throwable error) { error.printStackTrace(); finish(event, error.toString()); }
    }
    private static ItemStack gun() { return GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build(); }
    private void setup(ServerLevel level) {
        level.setDayTime(3000);
        // Give the cross-dimension roster fixture a genuinely loaded destination.
        var nether=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        nether.getChunk(0,0);nether.setChunkForced(0,0,true);
        for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++) { level.getChunk(x,z); level.setChunkForced(x,z,true); }
        for (int x=-8; x<=15; x++) for (int z=-8; z<=8; z++) level.setBlockAndUpdate(new BlockPos(x,79,z), Blocks.STONE.defaultBlockState());
        owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "QuartermasterOwner")); owner.setPos(.5,80,1.5);
        level.setBlockAndUpdate(A, ModBlocks.QUARTERMASTER.get().defaultBlockState());
        level.setBlockAndUpdate(B, ModBlocks.QUARTERMASTER.get().defaultBlockState());
        station = (QuartermasterBlockEntity)level.getBlockEntity(A); station.placedBy(owner);
        other = (QuartermasterBlockEntity)level.getBlockEntity(B); other.placedBy(owner);
        QuartermasterServiceRuntime.loaded(station); QuartermasterServiceRuntime.loaded(other);
        guard = GuardEntityType.GUARD.get().create(level); guard.setPos(.5,80,2.5); guard.setOwnerId(owner.getUUID()); guard.setNoAi(true);
        level.addFreshEntity(guard);
        var data = IGunOperator.fromLivingEntity(guard).getDataHolder(); data.drawTimestamp=-1; data.shootTimestamp=-1; data.meleePrepTickCount=-1;
        operator = new Villager(EntityType.VILLAGER,level); operator.setPos(.5,80,1.5);
        operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
        operator.refreshBrain(level); operator.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(),A));
        level.addFreshEntity(operator); station.operator=operator.getUUID();
        level.getPoiManager().take(type -> type.value() == ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID), (type,pos)->pos.equals(A), A,1);
    }
    private void run(ServerLevel level) throws Exception {
        var state = GuardEquipmentServiceAccess.get(guard); var context = ServiceManagementPermissions.Context.of(station);
        var requested = settings(GuardServiceState.Policy.PROFILE, assignment());
        check("native-operator-and-poi-ready", ServiceEligibility.station(station)==ServiceStatus.READY);
        CASES.addAll(AssessmentCases.run(level,station,owner));
        CASES.addAll(ManagementCases.run(level,station,operator,owner));
        CASES.addAll(OverviewCases.run(level,station,owner));
        CASES.addAll(OverviewRegressionCases.run(level,station,owner));
        CASES.addAll(BulkCases.run(level,station,owner));
        CASES.addAll(ManagementFixCases.run(level,station,owner));
        IntegrationCases.interruptions(level,station,operator,owner);
        TradeCases.run(level,station,guard,operator);
        context=ServiceManagementPermissions.Context.of(station);
        check("assignment-authorized", ServiceControls.applyAtStation(owner,station,context,guard,requested)==ServiceManagementPermissions.Result.ALLOWED);
        check("roster-reconciled", ServiceRoster.get(level).find(guard.getUUID()) != null);
        check("loaded-hint-cannot-be-forgotten", !ServiceControls.forgetUnavailable(owner,station,context,guard.getUUID()));
        check("wrong-station-planner-refused", ServicePlanner.plan(guard,other,QuartermasterServiceRuntime.sources(other))==null);
        station.inventory().setStackInSlot(0,gun());
        var plan = ServicePlanner.plan(guard,station,QuartermasterServiceRuntime.sources(station));
        check("native-transfer-plan-valid", plan!=null && ServiceTransactions.valid(plan) && QuartermasterServiceRuntime.reserve(plan));
        state.assignment(null);
        check("assignment-change-cancels-reservation", QuartermasterServiceRuntime.plan(guard)==null && !ServiceTransactions.valid(plan));
        state.assignment(assignment());
        check("restoring-settings-does-not-revive-old-plan", !ServiceTransactions.valid(plan));
        plan = ServicePlanner.plan(guard,station,QuartermasterServiceRuntime.sources(station));
        level.getPoiManager().release(A);
        check("released-poi-stops-transfer", !ServiceTransactions.valid(plan));
        level.getPoiManager().take(type -> type.value() == ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID), (type,pos)->pos.equals(A), A,1);
        level.setDayTime(15000);
        check("assignment-outside-work-hours", ServiceControls.applyAtStation(owner,station,context,guard,requested)==ServiceManagementPermissions.Result.ALLOWED);
        check("work-hours-still-block-transfer", !ServiceTransactions.valid(plan)); level.setDayTime(3000);
        var stranger = new FakePlayer(level,new GameProfile(UUID.randomUUID(),"Visitor")); stranger.setPos(owner.position());
        check("stranger-cannot-manage", ServiceControls.applyAtStation(stranger,station,context,guard,requested)!=ServiceManagementPermissions.Result.ALLOWED);
        UUID originalOwner=guard.getOwnerId(); guard.setOwnerId(stranger.getUUID());
        check("station-owner-cannot-control-other-guard", ServiceControls.applyAtStation(owner,station,context,guard,requested)==ServiceManagementPermissions.Result.GUARD_DENIED);
        guard.setOwnerId(originalOwner);
        station.changeSetting(owner,0); station.changeSetting(owner,0);
        var access=ServiceManagementPermissions.station(stranger,station,context);
        check("public-stock-does-not-grant-management", access.viewStock() && !access.manage());
        stranger.setPos(100,80,100);
        check("distant-inventory-access-denied", !ServiceManagementPermissions.station(stranger,station,context).viewStock());
        station.changeSetting(owner,3); // Owner -> bound squad (none)
        check("service-permission-independent", ServiceControls.applyAtStation(owner,station,context,guard,requested)==ServiceManagementPermissions.Result.SERVICE_DENIED);
        check("revoked-service-can-be-cleared", ServiceControls.applyAtStation(owner,station,context,guard,settings(GuardServiceState.Policy.MANUAL,null))==ServiceManagementPermissions.Result.ALLOWED);
        station.changeSetting(owner,3); station.changeSetting(owner,3); station.changeSetting(owner,3);
        ServiceControls.applyAtStation(owner,station,context,guard,requested);
        guard.setPos(55,80,0);
        check("assigned-range-limit", QuartermasterServiceRuntime.assignedStation(level,assignment(),guard.blockPosition()).status()==ServiceStatus.ASSIGNED_STATION_OUT_OF_RANGE);
        guard.setPos(.5,80,2.5);
        var wrongDimension=new ServiceStationAssignment(station.stationId(),ResourceLocation.parse("minecraft:the_nether"),A);
        check("cross-dimension-refused", QuartermasterServiceRuntime.assignedStation(level,wrongDimension,guard.blockPosition()).station()==null);
        CompoundTag saved = ((net.minecraft.world.level.block.entity.BlockEntity) station).saveWithFullMetadata(); UUID identity=station.stationId();
        ((net.minecraft.world.level.block.entity.BlockEntity) other).load(saved); other.placedBy(owner);
        check("duplicate-identity-refused", QuartermasterServiceRuntime.assignedStation(level,assignment(),guard.blockPosition()).status()==ServiceStatus.ASSIGNED_STATION_AMBIGUOUS);
        var replacementSession=new com.gamergaming.villagerguardgunners.quartermaster.management.ManagementSession(owner,station,null);
        check("management-before-block-replacement-valid",replacementSession.valid(owner));
        level.removeBlock(A,false);
        check("relocated-station-found-by-identity", QuartermasterServiceRuntime.assignedStation(level,new ServiceStationAssignment(identity,level.dimension().location(),A),guard.blockPosition()).station()==other);
        check("old-station-context-invalidated", !ServiceManagementPermissions.station(owner,other,context).manage());
        level.setBlockAndUpdate(A,ModBlocks.QUARTERMASTER.get().defaultBlockState());
        station=(QuartermasterBlockEntity)level.getBlockEntity(A); station.placedBy(owner); QuartermasterServiceRuntime.loaded(station);
        check("management-real-block-replacement-invalidates",!replacementSession.valid(owner));
        check("replacement-at-old-position-not-selected", QuartermasterServiceRuntime.assignedStation(level,new ServiceStationAssignment(identity,level.dimension().location(),A),guard.blockPosition()).station()==other);
        QuartermasterServiceRuntime.removed(other);
        check("unloaded-assignment-has-no-fallback", QuartermasterServiceRuntime.assignedStation(level,new ServiceStationAssignment(identity,level.dimension().location(),A),guard.blockPosition()).status()==ServiceStatus.ASSIGNED_STATION_UNAVAILABLE);
        QuartermasterServiceRuntime.loaded(other);
        // Restore a clean work site and test actual scheduler selection/transfers.
        station.operator=operator.getUUID(); operator.setPos(.5,80,1.5);
        operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),A));
        level.getPoiManager().take(type -> type.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(type,pos)->pos.equals(A),A,1);
        check("new-station-assignment", ServiceControls.applyAtStation(owner,station,ServiceManagementPermissions.Context.of(station),guard,settings(GuardServiceState.Policy.PROFILE,assignment()))==ServiceManagementPermissions.Result.ALLOWED);
        CompoundTag guardSave = new CompoundTag(); guard.saveWithoutId(guardSave);
        Guard restored = GuardEntityType.GUARD.get().create(level); restored.load(guardSave);
        check("native-guard-assignment-round-trip", GuardEquipmentServiceAccess.get(restored).assignment().equals(state.assignment()));
        // Do not add the duplicate saved entity; verify metadata survives unload independently.
        QuartermasterServiceRuntime.left(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(guard,level));
        check("unload-keeps-roster", ServiceRoster.get(level).find(guard.getUUID())!=null);
        QuartermasterServiceRuntime.joined(new net.minecraftforge.event.entity.EntityJoinLevelEvent(guard,level));
        int before=level.getChunkSource().getLoadedChunksCount();
        QuartermasterServiceRuntime.assignedStation(level,new ServiceStationAssignment(UUID.randomUUID(),level.dimension().location(),new BlockPos(100000,80,100000)),guard.blockPosition());
        check("assignment-query-loads-no-chunks", before==level.getChunkSource().getLoadedChunksCount());
        var doomed = GuardEntityType.GUARD.get().create(level); doomed.setPos(.5,80,3.5); doomed.setNoAi(true); level.addFreshEntity(doomed);
        GuardEquipmentServiceAccess.get(doomed).assignment(assignment());
        check("second-guard-roster-recorded",ServiceRoster.get(level).find(doomed.getUUID())!=null);
        doomed.discard(); check("known-removal-clears-roster",ServiceRoster.get(level).find(doomed.getUUID())==null);
        // No immediate equipment move resulted from any settings action.
        check("settings-never-transfer-items", guard.getMainHandItem().isEmpty());
        station.inventory().setStackInSlot(0,ItemStack.EMPTY); other.inventory().setStackInSlot(0,gun());
        guard.setNoAi(false); state.nextAttempt=0;
    }
    private void restart(ServerLevel level, TickEvent.ServerTickEvent event) throws Exception {
        var previous=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("vgg.quartermaster.output")).resolveSibling("result.json"))).getAsJsonObject();
        guard=(Guard)level.getEntity(UUID.fromString(previous.get("guardId").getAsString()));
        check("restart-guard-loaded",guard!=null);
        station=(QuartermasterBlockEntity)level.getBlockEntity(A); other=(QuartermasterBlockEntity)level.getBlockEntity(B);
        var state=GuardEquipmentServiceAccess.get(guard);
        check("restart-assignment-preserved",state.readable() && state.assignment()!=null && state.assignment().stationId().equals(station.stationId()));
        check("restart-policy-preserved",state.policy()==GuardServiceState.Policy.PROFILE && !state.replaceExisting());
        check("restart-roster-reconciled",ServiceRoster.get(level).find(guard.getUUID()).assignment().equals(state.assignment()));
        check("restart-physical-items-preserved",ItemStack.matches(guard.getMainHandItem(), ItemStack.of(net.minecraft.nbt.TagParser.parseTag(previous.get("guardStack").getAsString())))
                && station.inventory().getStackInSlot(0).isEmpty() && ItemStack.isSameItemSameTags(other.inventory().getStackInSlot(0),gun()));
        check("restart-assignment-resolves",QuartermasterServiceRuntime.assignedStation(level,state.assignment(),guard.blockPosition()).station()==station);
        CASES.addAll(TradeCases.restart(level));
        finish(event,null);
    }
    private void finish(TickEvent.ServerTickEvent event,String failure) {
        done=true;
        if(!Boolean.getBoolean("vgg.quartermaster.restart"))ManagementFixCases.cleanup(event.getServer().overworld());
        try { Files.writeString(Path.of(System.getProperty("vgg.quartermaster.output")), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("passed",failure==null,"cases",CASES,"guardId",guard==null?"":guard.getUUID().toString(),"guardStack",guard==null?"":guard.getMainHandItem().save(new CompoundTag()).toString(),"failure",failure==null?"":failure))); }
        catch(Exception e){throw new RuntimeException(e);}
        event.getServer().halt(false);
    }
}
