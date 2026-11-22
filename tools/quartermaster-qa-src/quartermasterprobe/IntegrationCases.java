package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.quartermaster.trade.QuartermasterTrades;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Cross-feature journeys on installed jars, including ordinary villager job acquisition. */
final class IntegrationCases {
    private static final List<String> CASES = new ArrayList<>();
    private static final BlockPos SITE = new BlockPos(24,80,20);
    private static QuartermasterBlockEntity station;
    private static Villager villager;
    private static Guard guard;
    private static FakePlayer owner;
    private static int stage, ticks, sequence;
    private static UUID firstOperator;
    private static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    private static ItemStack ammo(int count) { return AmmoItemBuilder.create().setId(id("tacz:9mm")).setCount(count).build(); }
    private static void check(String name, boolean value) {
        System.out.println("VGG integration " + name + "=" + value);
        if (!value) throw new AssertionError("integration-" + name);
        CASES.add("integration-" + name);
    }
    static List<String> results() { return List.copyOf(CASES); }
    private static Guard guard(ServerLevel level, FakePlayer player, double x, double z) {
        var g=GuardEntityType.GUARD.get().create(level);g.setPos(x,80,z);g.setOwnerId(player.getUUID());g.setNoAi(true);level.addFreshEntity(g);
        g.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(id("tacz:glock_17")).build());
        var data=IGunOperator.fromLivingEntity(g).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
        return g;
    }
    static void interruptions(ServerLevel level, QuartermasterBlockEntity s, Villager operator, FakePlayer player) {
        var g=guard(level,player,.5,2.5);var state=GuardEquipmentServiceAccess.get(g);
        var assignment=new ServiceStationAssignment(s.stationId(),level.dimension().location(),((BlockEntity)s).getBlockPos());
        state.update(new ServiceSettings(GuardServiceState.Policy.RESTOCK,null,false,assignment));
        var before=new ArrayList<ItemStack>();for(int i=0;i<36;i++)before.add(s.inventory().getStackInSlot(i).copy());
        try {
            s.inventory().setStackInSlot(0,ammo(20));
            var plan=ServicePlanner.plan(g,s,QuartermasterServiceRuntime.sources(s));
            check("delivery-reserved",plan!=null&&QuartermasterServiceRuntime.reserve(plan));
            var menu=new GuardGunnerContainer(1,player.getInventory(),g.guardInventory,GuardAmmoAccess.getAmmoHandler(g),g);
            check("guard-menu-edit-keeps-assignment",((AbstractContainerMenu)menu).clickMenuButton(player,ServiceControls.REPLACE_BUTTON)&&assignment.equals(state.assignment()));
            check("guard-menu-edit-cancels-delivery",QuartermasterServiceRuntime.plan(g)==null&&!ServiceTransactions.valid(plan));
            ((AbstractContainerMenu)menu).removed(player);
            plan=ServicePlanner.plan(g,s,QuartermasterServiceRuntime.sources(s));check("fresh-plan-after-menu-edit",ServiceTransactions.valid(plan));
            GuardMedicalAccess.get(g).busy(true);
            check("medical-interruption-no-transfer",ServiceTransactions.transfer(plan)==ServiceStatus.RETRY&&s.inventory().getStackInSlot(0).getCount()==20);
            GuardMedicalAccess.get(g).busy(false);
            operator.getBrain().setActiveActivityIfPossible(Activity.PANIC);
            check("panic-interruption-no-transfer",!ServiceTransactions.valid(plan));
            operator.getBrain().setActiveActivityIfPossible(Activity.RAID);
            check("raid-activity-interruption-no-transfer",!ServiceTransactions.valid(plan));
            operator.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            check("safe-operator-resumes",ServiceTransactions.valid(plan));
            var raids=((com.gamergaming.villagerguardgunners.mixin.accessor.RaidsAccessor)level.getRaids()).villagerGuardGunners$getRaidMap();
            int raidId=raids.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)+1;
            raids.put(raidId,new net.minecraft.world.entity.raid.Raid(raidId,level,((BlockEntity)s).getBlockPos()));
            try { check("active-raid-blocks-station",ServiceEligibility.station(s)==ServiceStatus.RAID&&!ServiceTransactions.valid(plan)); }
            finally { raids.remove(raidId); }
            var pipe=s.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("transport"));
            check("automation-disabled-by-default",pipe.insertItem(0,ammo(5),false).getCount()==5);
            s.changeSetting(player,1);
            check("automation-inserts-real-stock",pipe.insertItem(0,ammo(5),false).isEmpty()&&s.inventory().getStackInSlot(0).getCount()==25);
            check("automation-invalidates-old-delivery",!ServiceTransactions.valid(plan));
            s.changeSetting(player,1);
            check("cached-pipe-obeys-disable",pipe.insertItem(0,ammo(5),false).getCount()==5);
            ServiceEquipment.set(g,0,GunItemBuilder.create().setId(id("tacz:ak47")).build());
            check("configure-sidearm-through-inventory",GuardSidearmAccess.getConfiguredSidearmInventory(g).insertItem(0,GunItemBuilder.create().setId(id("tacz:glock_17")).build(),false).isEmpty());
            s.inventory().setStackInSlot(1,AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(30).build());
            var report=ServiceAssessment.preview(player,s,ServiceManagementPermissions.Context.of(s),List.of(new ServiceAssessment.Request(g.getUUID(),null)));
            check("dual-caliber-preview-keeps-both-reserves",report.guards().get(0).rows().stream().filter(r->r.category().equals("ammo")&&r.available()>0).map(ServiceNeeds.Row::item).collect(java.util.stream.Collectors.toSet()).equals(Set.of("tacz:9mm","tacz:762x39")));
            for(int i=0;i<2;i++)check("dual-caliber-transfer-"+i,ServiceTransactions.transfer(ServicePlanner.plan(g,s,QuartermasterServiceRuntime.sources(s)))==ServiceStatus.SUPPLIED);
            check("dual-caliber-stock-conserved",java.util.stream.IntStream.range(6,9).map(i->ServiceEquipment.get(g,i).getCount()).sum()==55&&s.inventory().getStackInSlot(0).isEmpty()&&s.inventory().getStackInSlot(1).isEmpty());
            s.inventory().setStackInSlot(0,ammo(25));
            plan=ServicePlanner.plan(g,s,QuartermasterServiceRuntime.sources(s));
            state.receipt.quarantine();
            check("guard-recovery-lock-blocks-delivery",ServiceTransactions.transfer(plan)==ServiceStatus.RETRY&&s.inventory().getStackInSlot(0).getCount()==25);
            var packed=s.packedStack();var restored=new QuartermasterBlockEntity(((BlockEntity)s).getBlockPos(),((BlockEntity)s).getBlockState());
            ((BlockEntity)restored).load(packed.getTag().getCompound("BlockEntityTag"));
            check("packing-retains-station-and-stock",restored.stationId().equals(s.stationId())&&ItemStack.matches(restored.inventory().getStackInSlot(0),s.inventory().getStackInSlot(0)));
            s.serviceReceipt().quarantine();
            check("station-recovery-lock-blocks-management-and-pipes",!new ManagementSession(player,s,null).valid(player)&&pipe.insertItem(0,ammo(5),false).getCount()==5);
            var receipt=new net.minecraft.nbt.CompoundTag();s.serviceReceipt().save(receipt);receipt.getCompound(ServiceReceipt.KEY).putBoolean("Quarantined",false);s.serviceReceipt().load(receipt,s.serviceReceipt().id());
        } finally {
            QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);g.discard();
            for(int i=0;i<36;i++)s.inventory().setStackInSlot(i,before.get(i));
        }
    }
    private static Villager unemployed(ServerLevel level) {
        var v=new Villager(EntityType.VILLAGER,level);v.setPos(24.5,80,22.5);v.setPersistenceRequired();level.addFreshEntity(v);return v;
    }
    private static void begin(ServerLevel level) {
        level.setDayTime(3000);
        for(int x=19;x<=29;x++)for(int z=15;z<=25;z++) {
            level.getChunk(new BlockPos(x,80,z));level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState());
            if(x==19||x==29||z==15||z==25)for(int y=80;y<=82;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
        }
        owner=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"IntegrationOwner"));owner.setPos(24.5,80,21.5);
        level.setBlockAndUpdate(SITE,ModBlocks.QUARTERMASTER.get().defaultBlockState());station=(QuartermasterBlockEntity)level.getBlockEntity(SITE);station.placedBy(owner);
        villager=unemployed(level);firstOperator=villager.getUUID();stage=1;
    }
    static boolean tick(ServerLevel level) {
        if(stage==0)begin(level);
        if(++ticks>1800)throw new AssertionError("integration journey timeout stage="+stage+" profession="+villager.getVillagerData()+" site="+villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)+" service="+ServiceEligibility.station(station));
        if(stage==1) {
            if(QuartermasterManagement.station(villager)!=station||ServiceEligibility.station(station)!=ServiceStatus.READY)return false;
            check("natural-adult-claims-and-operates-station",!villager.isNoAi()&&station.operator.equals(villager.getUUID()));
            owner.setPos(villager.position());
            var session=new ManagementSession(owner,station,villager);check("natural-operator-opens-trade",QuartermasterManagement.trade(owner,session));
            var menu=(MerchantMenu)owner.containerMenu;
            var offer=villager.getOffers().stream().filter(o->id("villagerguardgunners:bandages").equals(QuartermasterTrades.id(o))).findFirst().orElseThrow();
            for(int i=0;i<5;i++) {
                menu.setSelectionHint(villager.getOffers().indexOf(offer));menu.getSlot(0).set(offer.getCostA().copy());
                menu.clicked(2,0,ClickType.QUICK_MOVE,owner);
            }
            check("trade-pauses-service-and-buys-supplies",owner.getInventory().countItem(offer.getResult().getItem())==20&&ServiceEligibility.station(station)==ServiceStatus.OPERATOR_UNAVAILABLE);
            owner.closeContainer();stage=2;
        }
        if(stage==2) {
            if(villager.getVillagerData().getLevel()<2)return false;
            check("natural-operator-unlocks-ammo-trade",villager.getOffers().size()==4);
            owner.setPos(villager.position());check("reopen-trade-after-level-up",QuartermasterManagement.trade(owner,new ManagementSession(owner,station,villager)));
            var menu=(MerchantMenu)owner.containerMenu;var offer=villager.getOffers().stream().filter(o->id("villagerguardgunners:pistol_ammo").equals(QuartermasterTrades.id(o))).findFirst().orElseThrow();
            menu.setSelectionHint(villager.getOffers().indexOf(offer));menu.getSlot(0).set(offer.getCostA().copy());menu.clicked(2,0,ClickType.QUICK_MOVE,owner);owner.closeContainer();
            owner.setPos(24.5,80,21.5);
            var stock=((net.minecraft.world.MenuProvider)station).createMenu(1,owner.getInventory(),owner);owner.containerMenu=stock;
            for(int i=36;i<stock.slots.size();i++)if(!stock.getSlot(i).getItem().isEmpty())stock.quickMoveStack(owner,i);
            check("purchases-deposited-through-stock-menu",java.util.stream.IntStream.range(0,36).map(i->station.inventory().getStackInSlot(i).getCount()).sum()==36);
            owner.closeContainer();
            guard=guard(level,owner,25.5,22.5);ServiceEquipment.set(guard,9,new ItemStack(offerBandage()));
            var session=new ManagementSession(owner,station,null);var bulk=new BulkSession(owner,session);
            bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.OPEN,0,null,null));
            bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.TOGGLE,0,guard.getUUID(),null));
            bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.POLICY,1,null,null));
            bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.ASSIGN,1,null,null));
            check("purchased-stock-preview",bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.PREVIEW,0,null,null)));
            var preview=bulk.state(1,1);check("bulk-assigns-natural-service",bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.APPLY,0,null,preview.token())));
            guard.setNoAi(false);GuardEquipmentServiceAccess.get(guard).nextAttempt=0;stage=3;
        }
        if(stage==3) {
            if(GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0).getCount()!=16||ServiceEquipment.get(guard,9).getCount()!=16)return false;
            check("purchased-ammo-and-medical-delivered",java.util.stream.IntStream.range(0,36).map(i->station.inventory().getStackInSlot(i).getCount()).sum()==5);
            owner.setPos(24.5,80,21.5);
            check("overview-after-physical-service",QuartermasterManagement.open(owner,station,null));
            var overview=(com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu)owner.containerMenu;
            ((AbstractContainerMenu)overview).broadcastChanges();
            for(int i=0;i<4;i++)OverviewRuntime.advance(level);
            ((AbstractContainerMenu)overview).broadcastChanges();
            check("overview-reflects-remaining-purchased-stock",!overview.details().refreshing()&&overview.details().rows().stream().mapToInt(OverviewProtocol.Row::available).sum()==5);
            check("history-reflects-purchased-supplies",ServiceHistory.recent(level,station.dropId()).stream().filter(e->e.guard().equals(guard.getUUID())).mapToInt(ServiceHistory.Entry::amount).sum()==31);
            owner.closeContainer();
            guard.setNoAi(true);var session=new ManagementSession(owner,station,villager);
            villager.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
            check("actual-operator-death-closes-session",!session.valid(owner)&&ServiceEligibility.station(station)==ServiceStatus.OPERATOR_UNAVAILABLE);
            villager=unemployed(level);stage=4;
        }
        if(stage==4) {
            if(QuartermasterManagement.station(villager)!=station||ServiceEligibility.station(station)!=ServiceStatus.READY)return false;
            check("replacement-naturally-claims-existing-station",!firstOperator.equals(station.operator)&&villager.getUUID().equals(station.operator));
            check("replacement-preserves-assignment-and-stock",GuardEquipmentServiceAccess.get(guard).assignment().stationId().equals(station.stationId())&&java.util.stream.IntStream.range(0,36).map(i->station.inventory().getStackInSlot(i).getCount()).sum()==5);
            level.setDayTime(9000);check("modified-ai-does-not-extend-service-hours",ServiceEligibility.station(station)==ServiceStatus.OUTSIDE_WORK_HOURS);level.setDayTime(3000);
            stage=5;return true;
        }
        return stage==5;
    }
    private static Item offerBandage() { return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id("villagerguardgunners:bandage")); }
}
