package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.armory.*;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Runs against the reobfuscated addon and installed Forge/TaCZ, never the dev runtime. */
final class AssessmentCases {
    private final List<String> cases=new ArrayList<>();
    private final ServerLevel level;
    private final QuartermasterBlockEntity station;
    private final FakePlayer owner;
    private final List<Guard> guards=new ArrayList<>();
    private AssessmentCases(ServerLevel level,QuartermasterBlockEntity station,FakePlayer owner) {
        this.level=level;this.station=station;this.owner=owner;
    }
    static List<String> run(ServerLevel level,QuartermasterBlockEntity station,FakePlayer owner) throws Exception {
        var test=new AssessmentCases(level,station,owner);
        try { test.run(); return test.cases; }
        finally {
            test.guards.forEach(g->{QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);g.discard();});
            for(int i=0;i<36;i++)station.inventory().setStackInSlot(i,ItemStack.EMPTY);
            station.changeSetting(owner,5);
            level.removeBlock(new BlockPos(3,80,0),false);
        }
    }
    private void check(String name,boolean pass) {
        System.out.println("VGG assessment "+name+"="+pass);
        if(!pass)throw new AssertionError("assessment-"+name);
        cases.add("assessment-"+name);
    }
    private static ResourceLocation id(String value) {return ResourceLocation.parse(value);}
    private static ItemStack gun(String name) {return GunItemBuilder.create().setId(id("tacz:"+name)).build();}
    private static ItemStack ammo(String name,int count) {return AmmoItemBuilder.create().setId(id("tacz:"+name)).setCount(count).build();}
    private Guard guard() {
        Guard guard=GuardEntityType.GUARD.get().create(level);
        guard.setPos(.5,80,2.5);guard.setOwnerId(owner.getUUID());guard.setNoAi(true);level.addFreshEntity(guard);
        var data=IGunOperator.fromLivingEntity(guard).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
        GuardEquipmentServiceAccess.get(guard).policy(GuardServiceState.Policy.RESTOCK);
        guards.add(guard);return guard;
    }
    private ServiceAssessment.Report preview(Guard... selected) {
        return ServiceAssessment.preview(owner,station,ServiceManagementPermissions.Context.of(station),
                Arrays.stream(selected).map(g->new ServiceAssessment.Request(g.getUUID(),null)).toList());
    }
    private ServiceAssessment.Report proposed(Guard guard,ServiceSettings settings) {
        return ServiceAssessment.preview(owner,station,ServiceManagementPermissions.Context.of(station),List.of(new ServiceAssessment.Request(guard.getUUID(),settings)));
    }
    private static int supplied(ServiceAssessment.Report report,String category) {
        return report.guards().stream().flatMap(g->g.rows().stream()).filter(r->r.category().equals(category)).mapToInt(ServiceNeeds.Row::available).sum();
    }
    private static boolean reason(ServiceAssessment.Report report,ServiceNeeds.Reason reason) {
        return report.guards().stream().flatMap(g->g.rows().stream()).anyMatch(r->r.reasons().contains(reason));
    }
    private static CompoundTag saved(Guard guard) {var tag=new CompoundTag();guard.saveWithoutId(tag);return tag;}
    private void unchanged(Guard guard,List<ServiceAssessment.Request> requests) throws Exception {
        var state=GuardEquipmentServiceAccess.get(guard);
        var inventory=ServiceEquipment.snapshot(guard);var entity=saved(guard);var block=((BlockEntity)station).saveWithFullMetadata();
        var status=state.status;long revision=state.settingsRevision(),next=state.nextAttempt;
        var path=guard.getNavigation().getPath();var plan=QuartermasterServiceRuntime.plan(guard);
        Path journal=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/villagerguardgunners_equipment_service.dat");
        byte[] before=Files.exists(journal)?Files.readAllBytes(journal):null;
        for(int i=0;i<4;i++)ServiceAssessment.preview(owner,station,ServiceManagementPermissions.Context.of(station),requests);
        check("preview-preserves-exact-items-and-nbt-"+cases.size(),ServiceEquipment.same(guard,inventory)&&entity.equals(saved(guard))&&block.equals(((BlockEntity)station).saveWithFullMetadata()));
        check("preview-preserves-status-navigation-reservation-"+cases.size(),status==state.status&&revision==state.settingsRevision()&&next==state.nextAttempt&&path==guard.getNavigation().getPath()&&plan==QuartermasterServiceRuntime.plan(guard));
        check("preview-preserves-journal-bytes-"+cases.size(),Arrays.equals(before,Files.exists(journal)?Files.readAllBytes(journal):null));
    }
    private void run() throws Exception {
        Guard a=guard(),b=guard();
        a.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));b.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));
        station.inventory().setStackInSlot(0,ammo("762x39",60));station.inventory().setStackInSlot(1,ammo("762x39",20));
        var report=preview(b,a);
        check("scarce-stock-counted-once",report.inspected()==2&&supplied(report,"ammo")==80);
        check("stable-guard-order",report.equals(preview(a,b)));
        check("coverage-and-revision",report.observedAt()==level.getGameTime()&&report.profileRevision()==EquipmentProfiles.revision()&&report.unavailable()==0&&!report.truncated());
        check("empty-restock-medical-unknown",reason(report,ServiceNeeds.Reason.NOT_CONFIGURED));
        unchanged(a,List.of(new ServiceAssessment.Request(a.getUUID(),null)));

        var plan=ServicePlanner.plan(a,station,QuartermasterServiceRuntime.sources(station));
        check("preview-planner-first-transfer-parity",plan!=null&&plan.targetSlot()==6&&plan.replacement().getCount()==60&&supplied(preview(a),"ammo")==64);
        var fakeReceipt=new ServiceReceipt();fakeReceipt.quarantine();
        var fakeSource=new ServiceSource(station,new net.minecraftforge.items.ItemStackHandler(1),fakeReceipt,1);
        var canonical=ServicePlanner.plan(a,station,List.of(fakeSource,fakeSource));
        check("source-wrappers-use-canonical-handlers-and-receipts",canonical!=null&&canonical.source().inventory()==station.inventory()&&canonical.source().receipt()==station.serviceReceipt()&&canonical.stockBefore().getCount()==60);
        check("native-reservation-created",QuartermasterServiceRuntime.reserve(plan));
        report=preview(a,b);
        check("existing-reservation-subtracted",supplied(report,"ammo")==20&&report.stock().stream().mapToInt(ServiceAssessment.Stock::reserved).sum()==60);
        unchanged(a,List.of(new ServiceAssessment.Request(a.getUUID(),null),new ServiceAssessment.Request(b.getUUID(),null)));
        QuartermasterServiceRuntime.cancel(a,ServiceStatus.RETRY);
        check("real-service-conserves-loose-ammo",ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED&&GuardAmmoAccess.getAmmoHandler(a).getStackInSlot(0).getCount()==60&&station.inventory().getStackInSlot(0).isEmpty()&&station.inventory().getStackInSlot(1).getCount()==20);
        check("old-plan-cannot-replay",ServiceTransactions.transfer(plan)==ServiceStatus.RETRY);
        plan=ServicePlanner.plan(a,station,QuartermasterServiceRuntime.sources(station));
        check("native-stack-limit-splits-total-into-two-transfers",plan!=null&&plan.targetSlot()==7&&plan.replacement().getCount()==4&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED&&station.inventory().getStackInSlot(1).getCount()==16);
        station.inventory().setStackInSlot(1,ItemStack.EMPTY);
        unchanged(a,List.of(new ServiceAssessment.Request(a.getUUID(),null)));

        var tagged=ammo("762x39",60);tagged.getOrCreateTag().putString("Evidence","variant");
        GuardAmmoAccess.getAmmoHandler(a).setStackInSlot(0,ammo("762x39",58));
        GuardAmmoAccess.getAmmoHandler(a).setStackInSlot(1,ammo("9mm",64));GuardAmmoAccess.getAmmoHandler(a).setStackInSlot(2,ammo("9mm",64));
        station.inventory().setStackInSlot(0,tagged);
        check("different-tags-never-merge",supplied(preview(a),"ammo")==0&&reason(preview(a),ServiceNeeds.Reason.INCOMPATIBLE_STOCK));
        GuardAmmoAccess.getAmmoHandler(a).setStackInSlot(1,ItemStack.EMPTY);
        plan=ServicePlanner.plan(a,station,QuartermasterServiceRuntime.sources(station));
        check("tagged-stock-uses-empty-slot",plan!=null&&plan.targetSlot()==7&&plan.replacement().getCount()==6&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED&&ItemStack.isSameItemSameTags(tagged,GuardAmmoAccess.getAmmoHandler(a).getStackInSlot(1)));

        var profile=new ServiceSettings(GuardServiceState.Policy.PROFILE,id("villagerguardgunners:rifleman"),true,null);
        a.setItemSlot(EquipmentSlot.MAINHAND,gun("glock_17"));station.inventory().setStackInSlot(0,gun("ak47"));
        var proposed=proposed(a,profile);
        check("proposed-gun-projects-new-ammo",proposed.guards().get(0).rows().stream().anyMatch(r->r.category().equals("ammo")&&r.item().equals("tacz:762x39"))&&proposed.guards().get(0).rows().stream().noneMatch(r->r.category().equals("ammo")&&r.item().equals("tacz:9mm")));
        unchanged(a,List.of(new ServiceAssessment.Request(a.getUUID(),profile)));
        check("preview-does-not-apply-settings",GuardEquipmentServiceAccess.get(a).policy()==GuardServiceState.Policy.RESTOCK);
        check("replacement-off-is-protected",reason(proposed(a,new ServiceSettings(profile.policy(),profile.profile(),false,null)),ServiceNeeds.Reason.PROTECTED));
        for(int i=27;i<36;i++)station.inventory().setStackInSlot(i,new ItemStack(Items.IRON_SWORD));
        check("full-returns-and-conditional-ammo",reason(proposed(a,profile),ServiceNeeds.Reason.RETURN_FULL)&&reason(proposed(a,profile),ServiceNeeds.Reason.CONDITIONAL));
        for(int i=27;i<36;i++)station.inventory().setStackInSlot(i,ItemStack.EMPTY);
        check("missing-profile-explicit",reason(proposed(a,new ServiceSettings(GuardServiceState.Policy.PROFILE,id("missing:profile"),false,null)),ServiceNeeds.Reason.PROFILE_MISSING));
        var oldGun=a.getMainHandItem().copy();GuardEquipmentServiceAccess.get(a).update(profile);
        plan=ServicePlanner.plan(a,station,QuartermasterServiceRuntime.sources(station));
        check("real-replacement-preserves-returned-gun",plan!=null&&plan.targetSlot()==0&&plan.returnSlot()==27&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED&&ItemStack.matches(oldGun,station.inventory().getStackInSlot(27)));
        station.inventory().setStackInSlot(27,ItemStack.EMPTY);

        // Deliver a real configured sidearm through the existing transaction boundary.
        a.setItemSlot(EquipmentSlot.MAINHAND,gun("glock_17"));station.inventory().setStackInSlot(0,gun("glock_17"));
        GuardEquipmentServiceAccess.get(a).update(new ServiceSettings(profile.policy(),profile.profile(),false,null));
        plan=ServicePlanner.plan(a,station,QuartermasterServiceRuntime.sources(station));
        check("native-sidearm-transfer-still-works",plan!=null&&plan.targetSlot()==1&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED);
        GuardEquipmentServiceAccess.get(a).policy(GuardServiceState.Policy.RESTOCK);
        for(int i=0;i<3;i++)GuardAmmoAccess.getAmmoHandler(a).setStackInSlot(i,ItemStack.EMPTY);
        station.inventory().setStackInSlot(0,ammo("9mm",64));
        check("native-shared-caliber-one-reserve",supplied(preview(a),"ammo")==64);

        var dummy=gun("ak47");((IGun)dummy.getItem()).setDummyAmmoAmount(dummy,50);b.setItemSlot(EquipmentSlot.MAINHAND,dummy);
        check("dummy-ammo-not-physical-demand",reason(preview(b),ServiceNeeds.Reason.RESERVE_NOT_PHYSICAL)&&supplied(preview(b),"ammo")==0);
        var missing=gun("ak47");((IGun)missing.getItem()).setGunId(missing,id("missing:gun"));b.setItemSlot(EquipmentSlot.MAINHAND,missing);
        check("unknown-gun-not-guessed",reason(preview(b),ServiceNeeds.Reason.DEFINITION_UNAVAILABLE));
        b.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47"));

        var box=new ItemStack(ForgeRegistries.ITEMS.getValue(id("tacz:ammo_box")));
        ((IAmmoBox)box.getItem()).setAmmoId(box,id("tacz:762x39"));((IAmmoBox)box.getItem()).setAmmoCount(box,200);
        station.inventory().setStackInSlot(0,box);
        check("native-box-not-loose-stock",supplied(preview(b),"ammo")==0&&preview(b).stock().stream().anyMatch(s->s.kind()==ServiceNeeds.Kind.AMMO_BOX));
        var bandage=ForgeRegistries.ITEMS.getValue(id("villagerguardgunners:bandage"));
        GuardMedicalAccess.get(b).inventory().setStackInSlot(0,new ItemStack(bandage));
        station.inventory().setStackInSlot(1,new ItemStack(bandage,8));station.inventory().setStackInSlot(2,new ItemStack(bandage,8));
        check("seeded-medical-reserve-preview",supplied(preview(b),"medical")==15);
        plan=ServicePlanner.plan(b,station,QuartermasterServiceRuntime.sources(station));
        check("medical-first-native-transfer",plan!=null&&plan.targetSlot()==9&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED);
        plan=ServicePlanner.plan(b,station,QuartermasterServiceRuntime.sources(station));
        check("medical-total-matches-preview",plan!=null&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED&&GuardMedicalAccess.get(b).inventory().getStackInSlot(0).getCount()==16&&station.inventory().getStackInSlot(2).getCount()==1);
        GuardMedicalAccess.get(b).inventory().setStackInSlot(0,ItemStack.EMPTY);
        station.inventory().setStackInSlot(1,ItemStack.EMPTY);station.inventory().setStackInSlot(2,ItemStack.EMPTY);
        station.inventory().setStackInSlot(0,ItemStack.EMPTY);
        BlockPos armoryPos=new BlockPos(3,80,0);level.setBlockAndUpdate(armoryPos,ModBlocks.ARMORY.get().defaultBlockState());
        var armory=(ArmoryBlockEntity)level.getBlockEntity(armoryPos);QuartermasterServiceRuntime.loaded(armory);
        armory.getInventory().setStackInSlot(0,ammo("762x39",48));station.changeSetting(owner,4);
        report=preview(b);
        check("linked-stock-counted-once",supplied(report,"ammo")==48&&report.stock().stream().mapToInt(ServiceAssessment.Stock::count).sum()==48);
        var armoryNbt=((BlockEntity)armory).saveWithFullMetadata();unchanged(b,List.of(new ServiceAssessment.Request(b.getUUID(),null)));
        check("linked-inventory-and-receipt-unchanged",armoryNbt.equals(((BlockEntity)armory).saveWithFullMetadata()));
        armory.setSquadLock(ArmorySquadLock.assigned(UUID.randomUUID(),"Other squad"));report=preview(b);
        check("guard-squad-lock-restricts-supply",supplied(report,"ammo")==0&&report.restrictedSources());
        armory.setSquadLock(null);
        armory.serviceReceipt().revision(4);report=preview(b);
        check("mismatched-source-hidden-without-quarantine",report.stock().isEmpty()&&report.restrictedSources()&&!armory.serviceReceipt().locked());
        armory.serviceReceipt().revision(0);

        var stranger=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"AssessmentVisitor"));stranger.setPos(owner.position());
        report=ServiceAssessment.preview(stranger,station,ServiceManagementPermissions.Context.of(station),List.of(new ServiceAssessment.Request(a.getUUID(),null)));
        check("unauthorized-view-has-no-inventory",report.status().equals("access_denied")&&report.stock().isEmpty()&&report.guards().isEmpty());
        b.setOwnerId(stranger.getUUID());report=preview(a,b);
        check("station-owner-cannot-inspect-other-guard",report.unavailable()==1&&report.inspected()==1);
        b.setOwnerId(owner.getUUID());
        var requests=new ArrayList<ServiceAssessment.Request>();for(int i=0;i<65;i++)requests.add(new ServiceAssessment.Request(UUID.randomUUID(),null));
        int chunks=level.getChunkSource().getLoadedChunksCount();
        report=ServiceAssessment.preview(owner,station,ServiceManagementPermissions.Context.of(station),requests);
        check("coverage-truncation-and-unavailable",report.truncated()&&report.requested()==65&&report.unavailable()==64&&report.inspected()==0);
        check("preview-loads-no-chunks",chunks==level.getChunkSource().getLoadedChunksCount());

        var state=GuardEquipmentServiceAccess.get(b);var bad=new CompoundTag();var future=new CompoundTag();future.putInt("Version",99);bad.put(GuardServiceState.KEY,future);state.load(bad);
        var before=saved(b);report=preview(b);
        check("unreadable-guard-preserved-and-unavailable",report.unavailable()==1&&reason(report,ServiceNeeds.Reason.UNAVAILABLE)&&before.equals(saved(b)));
    }
}
