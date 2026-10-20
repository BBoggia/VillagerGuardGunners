package com.gamergaming.villagerguardgunners.qa;

import static com.gamergaming.villagerguardgunners.qa.SupplyMedicalAcceptance.*;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.armory.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.inventory.ItemCustomData;
import com.gamergaming.villagerguardgunners.quartermaster.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.quartermaster.trade.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;

final class WorkflowSupplyChecks {
    static void resources(SupplyMedicalAcceptance q) {
        Item I=Items.IRON_INGOT,C=Items.CHEST,P=Items.OAK_PLANKS,W=Items.WHITE_WOOL,S=Items.STICK;
        craft(q,"armory",3,3,List.of(I,I,I,C,Items.BARREL,C,I,I,I),1);
        craft(q,"quartermaster",3,3,List.of(I,Items.BOOK,I,P,C,P,P,P,P),1);
        craft(q,"bandage",3,1,List.of(Items.PAPER,Items.STRING,W),4);
        craft(q,"recovery_cot",3,3,List.of(W,W,W,P,P,P,S,Items.AIR,S),1);
        for(var block:List.of(ModBlocks.ARMORY.get(),ModBlocks.QUARTERMASTER.get()))
            require(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),"Axe tag missing: "+block);
        var pos=ORIGIN.offset(-12,0,0);
        var drops=Block.getDrops(ModBlocks.ARMORY.get().defaultBlockState(),q.level,pos,null);
        require(drops.size()==1&&drops.getFirst().is(ModItems.ARMORY.get()),"Armory block loot missing");
        q.placeCot(pos);
        var head=q.level.getBlockState(pos); var foot=q.level.getBlockState(pos.west());
        require(Block.getDrops(head,q.level,pos,q.level.getBlockEntity(pos)).size()==1
                &&Block.getDrops(foot,q.level,pos.west(),null).isEmpty(),"Cot loot missing or duplicated");
        q.level.removeBlock(pos,false); q.level.removeBlock(pos.west(),false);
        passed("supply_resources");
    }
    static void craft(SupplyMedicalAcceptance q,String name,int width,int height,List<Item> items,int count) {
        var input=CraftingInput.of(width,height,items.stream().map(i->i==Items.AIR?ItemStack.EMPTY:new ItemStack(i)).toList());
        var recipe=q.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,q.level).orElseThrow();
        ItemStack result=recipe.value().assemble(input,q.level.registryAccess());
        require(recipe.id().equals(id(name))&&result.getCount()==count,"Craft failed: "+name);
    }
    static void storage(SupplyMedicalAcceptance q) {
        var pos=ORIGIN.offset(-10,0,0); q.level.setBlock(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
        var station=(QuartermasterBlockEntity)q.level.getBlockEntity(pos); q.owner.setPos(-9.5,160,1.5); station.placedBy(q.owner);
        var transport=q.level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.UP);
        require(transport!=null&&transport.insertItem(0,ammo(17),false).getCount()==17,"Private automation default changed");
        require(station.changeSetting(q.owner,1),"Could not enable automation");
        transport=q.level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.UP);
        var input=ammo(17); require(transport.insertItem(0,input,true).isEmpty()&&station.inventory().getStackInSlot(0).isEmpty(),"Simulation mutated stock");
        require(transport.insertItem(0,input,false).isEmpty(),"Automation rejected ammo"); input.setCount(1);
        transport.getStackInSlot(0).setCount(1);
        require(station.inventory().getStackInSlot(0).getCount()==17&&transport.extractItem(0,17,false).isEmpty(),"Stock alias or extraction leak");
        require(transport.insertItem(27,ammo(2),false).getCount()==2,"Automation inserted into returns");
        station.inventory().setStackInSlot(27,new ItemStack(Items.IRON_SWORD));
        require(transport.extractItem(27,1,false).is(Items.IRON_SWORD),"Return extraction failed");
        var gun=q.gun("ak47"); ItemCustomData.update(gun,t->t.putString("portable","exact")); station.inventory().setStackInSlot(1,gun);
        var identity=station.stationId(); var receipt=station.serviceReceipt().id();
        require(Block.getDrops(station.getBlockState(),q.level,pos,station).isEmpty(),"Station also has ordinary block loot");
        q.level.removeBlock(pos,false);
        require(transport.getStackInSlot(0).isEmpty()&&transport.insertItem(0,ammo(2),false).getCount()==2,"Removed station transport stayed usable");
        var drops=q.level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3),e->e.getItem().is(ModItems.QUARTERMASTER.get()));
        require(drops.size()==1,"Packed station did not drop exactly once");
        ItemStack packed=drops.getFirst().getItem().copy(); drops.getFirst().discard();
        require(packed.has(DataComponents.BLOCK_ENTITY_DATA),"Packed station lacks block entity component");
        q.owner.setItemInHand(InteractionHand.MAIN_HAND,packed);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false);
        require(packed.useOn(new UseOnContext(q.owner,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Packed station placement failed");
        var restored=(QuartermasterBlockEntity)q.level.getBlockEntity(pos);
        require(restored!=null&&restored.stationId().equals(identity)&&restored.serviceReceipt().id().equals(receipt)
                &&restored.canManage(q.owner)&&restored.automationEnabled()
                &&restored.inventory().getStackInSlot(0).getCount()==17&&ItemStack.matches(gun,restored.inventory().getStackInSlot(1)),"Portable station lost stock, identity, or permissions");
        // Keep no spare stock in the live service area.
        for(int i=0;i<restored.inventory().getSlots();i++)restored.inventory().setStackInSlot(i,ItemStack.EMPTY);
        q.level.removeBlock(pos,false);
        q.level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
        q.owner.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); q.owner.setPos(.5,160,2.5);
        passed("station_storage_and_portability");
    }
    static void service(SupplyMedicalAcceptance q) {
        var station=q.station;
        var g=q.guard(ORIGIN.offset(7,0,0),false);
        var state=GuardEquipmentServiceAccess.get(g); var context=ServiceManagementPermissions.Context.of(station);
        require(ServiceEligibility.station(station)==ServiceStatus.READY,"Operator not ready");
        ItemStack supplied=q.gun("ak47"); ItemCustomData.update(supplied,t->t.putString("ownership","unique"));
        station.inventory().setStackInSlot(0,supplied.copy());
        require(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))==null,"Manual guard opted into service");
        var requested=new ServiceSettings(GuardServiceState.Policy.PROFILE,id("rifleman"),false,null);
        require(ServiceControls.applyAtStation(q.owner,station,context,g,requested)==ServiceManagementPermissions.Result.ALLOWED,"Manager settings rejected");
        var before=g.saveWithoutId(new CompoundTag()); var stock=station.saveWithoutMetadata(q.level.registryAccess());
        var report=ServiceAssessment.preview(q.owner,station,context,List.of(new ServiceAssessment.Request(g.getUUID(),null)));
        require(report.inspected()==1&&before.equals(g.saveWithoutId(new CompoundTag()))&&stock.equals(station.saveWithoutMetadata(q.level.registryAccess())),"Needs preview mutated inventory or guard");
        var p=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station)); require(p!=null,"No physical gun plan");
        require(QuartermasterServiceRuntime.reserve(p),"Reservation failed");
        var other=q.guard(ORIGIN.offset(7,0,1),false); GuardEquipmentServiceAccess.get(other).update(requested);
        require(ServicePlanner.plan(other,station,QuartermasterServiceRuntime.sources(station))==null,"Stock reserved twice");
        require(ServiceTransactions.transfer(p)==ServiceStatus.SUPPLIED&&ItemStack.matches(g.getMainHandItem(),supplied)
                &&station.inventory().getStackInSlot(0).isEmpty(),"Gun ownership transfer failed");
        require(ServiceTransactions.transfer(p)==ServiceStatus.RETRY,"Transfer replay duplicated gear");
        QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);
        var old=new ItemStack(Items.DIAMOND_SWORD); old.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Keep me"));
        g.setItemSlot(EquipmentSlot.MAINHAND,old.copy()); station.inventory().setStackInSlot(0,supplied.copy());
        require(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))==null,"Protected gear replaced");
        state.replaceExisting(true);
        for(int i=27;i<36;i++)station.inventory().setStackInSlot(i,new ItemStack(Items.STONE,64));
        require(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))==null,"Full returns allowed replacement");
        station.inventory().setStackInSlot(27,ItemStack.EMPTY);
        p=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
        require(p!=null&&ServiceTransactions.transfer(p)==ServiceStatus.SUPPLIED&&ItemStack.matches(old,station.inventory().getStackInSlot(27)),"Replacement lost exact old gear");
        var primary=g.getMainHandItem().copy(); var sidearm=q.gun("glock_17");
        station.inventory().setStackInSlot(0,sidearm.copy());
        p=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
        require(p!=null&&p.targetSlot()==1&&ServiceTransactions.transfer(p)==ServiceStatus.SUPPLIED
                &&ItemStack.matches(primary,g.getMainHandItem())
                &&ItemStack.matches(sidearm,GuardSidearmAccess.getConfiguredSidearmInventory(g).getStackInSlot(0)),"Sidearm service changed the primary or lost components");
        station.inventory().setStackInSlot(0,new ItemStack(Items.IRON_HELMET));
        p=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
        require(p!=null&&ServiceTransactions.transfer(p)==ServiceStatus.SUPPLIED&&g.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),"Armor service failed");
        // Permission rejection and stale station identity must leave explicit settings unchanged.
        var stranger=net.neoforged.neoforge.common.util.FakePlayerFactory.get(q.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"VGG stranger"));
        stranger.setPos(q.owner.position());
        require(ServiceControls.applyAtStation(stranger,station,context,g,requested)!=ServiceManagementPermissions.Result.ALLOWED,"Stranger managed station");
        var stale=new ServiceManagementPermissions.Context(context.station(),UUID.randomUUID(),context.dimension(),context.position());
        require(ServiceControls.applyAtStation(q.owner,station,stale,g,requested)==ServiceManagementPermissions.Result.STALE_STATION,"Stale station context accepted");
        // Revalidation must catch a changed physical stack, and opening hours still apply.
        g.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); station.inventory().setStackInSlot(0,supplied.copy());
        p=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station)); require(p!=null,"No stale-stock plan");
        ItemCustomData.update(station.inventory().getStackInSlot(0),t->t.putInt("changed",1));
        require(!ServiceTransactions.valid(p),"Changed stock still deliverable");
        q.level.setDayTime(12000); require(ServiceEligibility.station(station)==ServiceStatus.OUTSIDE_WORK_HOURS,"Service ignored work hours"); q.level.setDayTime(3000);
        for(int i=0;i<station.inventory().getSlots();i++)station.inventory().setStackInSlot(i,ItemStack.EMPTY);
        // The nearby Armory was placed before natural job acquisition, so normal onLoad indexed it.
        var linked=(ArmoryBlockEntity)q.level.getBlockEntity(ORIGIN.offset(8,0,3));
        require(station.changeSetting(q.owner,4)&&station.linkedArmories().contains(linked.getBlockPos()),"Nearby Armory was not linked");
        g.setItemSlot(EquipmentSlot.MAINHAND,q.gun("ak47")); state.policy(GuardServiceState.Policy.RESTOCK);
        linked.getInventory().setStackInSlot(0,ammo(12));
        linked.setSquadLock(ArmorySquadLock.assigned(UUID.randomUUID(),"Restricted"));
        require(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))==null,"Linked Armory ignored squad lock");
        linked.setSquadLock(null); g.setPos(8.5,160,2.5);
        p=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
        require(p!=null&&ServiceTransactions.transfer(p)==ServiceStatus.SUPPLIED&&reserve(g)==12&&linked.getInventory().getStackInSlot(0).isEmpty(),"Linked ammo was lost or duplicated");
        station.changeSetting(q.owner,5); q.level.removeBlock(linked.getBlockPos(),false);
        // A write-ahead intent without matching owner revisions must stop cached inventory access.
        var cached=g.getCapability(Capabilities.ItemHandler.ENTITY);
        require(ServiceJournal.get(q.level.getServer()).begin(linked.serviceReceipt(),state.receipt),"Could not record interrupted ownership intent");
        require(ServiceEligibility.guard(g)==ServiceStatus.RECOVERY_REQUIRED&&cached.getStackInSlot(6).isEmpty()
                &&cached.extractItem(6,12,false).isEmpty()&&reserve(g)==12,"Interrupted receipt exposed or removed ammo");
        passed("service_ownership_and_management");
        trades(q);
        q.clearActors();
    }
    static void trades(SupplyMedicalAcceptance q) {
        ModConfigs.GUARD_GUNNERS.quartermasterTrades.suppliesEnabled.accept(true);
        var offers=q.operator.getOffers();
        require(!offers.isEmpty(),"Quartermaster has no offers"); int size=offers.size(); QuartermasterTrades.populate(q.operator,offers);
        require(offers.size()==size,"Offers duplicated");
        var offer=offers.stream().filter(o->o.getResult().is(ModItems.BANDAGE.get())).findFirst().orElseThrow();
        require(QuartermasterTrades.id(offer)!=null&&QuartermasterTrades.allowed(offer),"Owned offer identity invalid");
        q.operator.setTradingPlayer(q.owner);
        var menu=new MerchantMenu(42,q.owner.getInventory(),q.operator);
        menu.setSelectionHint(offers.indexOf(offer)); menu.getSlot(0).set(offer.getCostA().copy());
        require(menu.getSlot(2).hasItem(),"Paid trade has no result");
        ModConfigs.GUARD_GUNNERS.quartermasterTrades.suppliesEnabled.accept(false);
        require(!menu.getSlot(2).mayPickup(q.owner),"Disabled trade result can be taken");
        ModConfigs.GUARD_GUNNERS.quartermasterTrades.suppliesEnabled.accept(true);
        int beforeUses=offer.getUses();
        require(!menu.quickMoveStack(q.owner,2).isEmpty()&&offer.getUses()==beforeUses+1&&menu.getSlot(0).getItem().isEmpty(),"Trade failed to consume payment once");
        q.operator.setTradingPlayer(null);
        var saved=q.operator.saveWithoutId(new CompoundTag());
        Villager restored=net.minecraft.world.entity.EntityType.VILLAGER.create(q.level); restored.load(saved);
        var restoredOffer=restored.getOffers().stream().filter(o->offer.getResult().is(o.getResult().getItem())).findFirst().orElseThrow();
        require(QuartermasterTrades.id(offer).equals(QuartermasterTrades.id(restoredOffer))&&restoredOffer.getUses()==offer.getUses(),"Offer identity/uses lost on save");
        passed("quartermaster_trading");
    }
}
