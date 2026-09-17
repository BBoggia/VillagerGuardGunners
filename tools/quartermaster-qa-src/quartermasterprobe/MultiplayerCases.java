package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Two real connected ServerPlayers; no FakePlayers or simulated client packets. */
final class MultiplayerCases {
    private static int stage,entered;
    private static QuartermasterBlockEntity station;
    private static Guard guard;
    private static Villager operator;
    private static long revision;
    private static net.minecraft.nbt.CompoundTag stock;
    private static UUID owner;
    static Path shared(){return AcceptanceProbe.out().getParent();}
    private static boolean ack(String role,int step){return Files.exists(shared().resolve(role+"-"+step+".done"));}
    private static void stage(int next,int tick) throws Exception {
        stage=next;entered=tick;
        var payload=new Gson().toJson(Map.of("stage",stage,"guard",guard.getUUID().toString()));
        var temporary=shared().resolve("control.tmp");Files.writeString(temporary,payload);Files.move(temporary,shared().resolve("control.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        System.out.println("VGG multiplayer stage="+stage);
    }
    static boolean tick(ServerLevel l,int tick) throws Exception {
        for(String role:List.of("A","B"))if(Files.exists(shared().resolve(role+"-error.txt")))throw new AssertionError(Files.readString(shared().resolve(role+"-error.txt")));
        var players=l.getServer().getPlayerList();var a=players.getPlayerByName("VggOwner");var b=players.getPlayerByName("VggPeer");
        if(stage==0) {
            if(tick>3600)throw new AssertionError("Two real clients did not connect");
            if(a==null||b==null)return false;
            AcceptanceProbe.check("two-distinct-connected-clients",!a.getUUID().equals(b.getUUID())&&!a.hasDisconnected()&&!b.hasDisconnected());
            var example=com.gamergaming.villagerguardgunners.quartermaster.trade.TradeDefinitions.snapshot().get(net.minecraft.resources.ResourceLocation.parse("vgg_example:rifle_ammo"));
            AcceptanceProbe.check("example-datapack-loaded",example!=null&&example.level()==3&&example.inputs().size()==2&&example.output().count()==30);
            l.setDayTime(3000);for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)l.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState());
            for(var p:List.of(a,b)){p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.teleportTo(p==a?1.5:-2.5,80,2.5);if(p==b)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,5000));}
            owner=a.getUUID();players.op(b.getGameProfile());
            var pos=new BlockPos(0,80,0);l.setBlockAndUpdate(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState());station=(QuartermasterBlockEntity)l.getBlockEntity(pos);station.placedBy(a);station.inventory().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,12));stock=LegacyWorld.inventory(station);
            guard=GuardEntityType.GUARD.get().create(l);guard.setNoAi(true);guard.setPos(.5,80,3.5);guard.setOwnerId(owner);l.addFreshEntity(guard);
            operator=new Villager(EntityType.VILLAGER,l);operator.setPos(.5,80,1.5);operator.setNoAi(true);operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(l.dimension(),pos));l.addFreshEntity(operator);station.operator=operator.getUUID();stage=-1;entered=tick;return false;
        }
        if(tick-entered>1200)throw new AssertionError("Multiplayer stage timeout "+stage);
        if(stage==-1&&tick-entered>20) {
            var pos=new BlockPos(0,80,0);l.getPoiManager().take(t->t.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(t,p)->p.equals(pos),pos,1);
            AcceptanceProbe.check("both-management-sessions-open",QuartermasterManagement.open(a,station,null)&&QuartermasterManagement.open(b,station,null));stage(1,tick);
        }else if(stage==1&&ack("A",1)&&ack("B",1)){stage(2,tick);}
        else if(stage==2&&ack("A",2)) {
            AcceptanceProbe.check("first-client-applied-settings",GuardEquipmentServiceAccess.get(guard).policy()==GuardServiceState.Policy.RESTOCK&&GuardEquipmentServiceAccess.get(guard).assignment()!=null);
            revision=GuardEquipmentServiceAccess.get(guard).settingsRevision();stage(3,tick);
        }else if(stage==3&&ack("B",3)) {
            AcceptanceProbe.check("second-client-stale-preview-no-overwrite",GuardEquipmentServiceAccess.get(guard).settingsRevision()==revision);
            a.getInventory().setItem(10,new ItemStack(Items.EMERALD,20));a.containerMenu.broadcastChanges();stage(4,tick);
        }else if(stage==4&&ack("A",4)) {
            AcceptanceProbe.check("first-client-trading",operator.getTradingPlayer()==a&&a.containerMenu instanceof MerchantMenu);stage(5,tick);
        }else if(stage==5&&ack("B",5)){AcceptanceProbe.check("second-client-cannot-steal-trade",operator.getTradingPlayer()==a&&!(b.containerMenu instanceof MerchantMenu));stage(6,tick);}
        else if(stage==6&&a==null) {
            AcceptanceProbe.check("disconnect-releases-merchant",operator.getTradingPlayer()==null);stage(7,tick);
        }else if(stage==7&&a!=null&&ack("A",7)) {
            int emeralds=a.getInventory().countItem(Items.EMERALD)+b.getInventory().countItem(Items.EMERALD);
            for(var drop:l.getEntitiesOfClass(ItemEntity.class,new AABB(new BlockPos(0,80,0)).inflate(12)))if(drop.getItem().is(Items.EMERALD))emeralds+=drop.getItem().getCount();
            AcceptanceProbe.check("reconnect-payment-conserved",emeralds==18);
            AcceptanceProbe.check("reconnect-purchase-conserved",a.getInventory().countItem(com.gamergaming.villagerguardgunners.init.ModItems.BANDAGE.get())==4);
            AcceptanceProbe.check("reconnect-stock-untouched",LegacyWorld.inventory(station).equals(stock));
            a.teleportTo(1.5,80,2.5);a.getInventory().selected=0;AcceptanceProbe.check("reconnect-new-session",QuartermasterManagement.open(a,station,null));stage(8,tick);
        }else if(stage==8&&ack("A",8)) {
            AcceptanceProbe.check("old-session-replay-after-reconnect-refused",GuardEquipmentServiceAccess.get(guard).settingsRevision()==revision&&LegacyWorld.inventory(station).equals(stock));stage(9,tick);return true;
        }
        return false;
    }
}
