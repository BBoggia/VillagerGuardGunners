package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.quartermaster.management.ManagementSession;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import tallestegg.guardvillagers.entities.Guard;

final class UpgradeCases {
    private static CompoundTag expected;
    private static BlockPos relocated=LegacyWorld.POS.offset(3,0,0);
    private static QuartermasterBlockEntity original;
    private static int unloadedAt;
    static boolean tick(ServerLevel l,int tick) throws Exception {
        boolean restart=System.getProperty("vgg.acceptance.mode").equals("upgrade-restart");
        if(tick==20){LegacyWorld.load(l);expected=TagParser.parseTag(Files.readString(AcceptanceProbe.out().resolveSibling("legacy-state.snbt")));}
        if(tick==50) {
            var s=(QuartermasterBlockEntity)l.getBlockEntity(restart?relocated:LegacyWorld.POS);
            AcceptanceProbe.check("station-identity-preserved",s.stationId().equals(expected.getUUID("Station")));
            AcceptanceProbe.check("station-exact-stock-and-returns",LegacyWorld.inventory(s).equals(expected.getCompound("Inventory")));
            for(int i=0;i<3;i++) {
                var g=(Guard)l.getEntity(LegacyWorld.guardId(i));var state=GuardEquipmentServiceAccess.get(g);
                AcceptanceProbe.check("guard-items-conserved-"+i,LegacyWorld.equipment(g).equals(expected.getCompound("Guard"+i)));
                AcceptanceProbe.check("policy-and-profile-preserved-"+i,state.readable()&&state.policy()==GuardServiceState.Policy.values()[i]&&state.replaceExisting()==(i==2)&&state.profile().toString().equals("villagerguardgunners:rifleman"));
                AcceptanceProbe.check("legacy-default-auto-"+i,state.assignment()==null);
                AcceptanceProbe.check("receipt-valid-"+i,ServiceJournal.get(l.getServer()).verify(state.receipt));
            }
            var v=(Villager)l.getEntity(LegacyWorld.NPC);var offers=v.getOffers();
            AcceptanceProbe.check("legacy-offer-retained",offers.stream().anyMatch(o->o.createTag().equals(expected.getCompound("Offer"))));
            AcceptanceProbe.check("existing-journeyman-initializes-six-new-offers-once",offers.size()==7&&v.getOffers().size()==7);
            AcceptanceProbe.check("upgraded-station-receipt-valid",ServiceJournal.get(l.getServer()).verify(s.serviceReceipt()));
            if(restart)return true;
            var p=LegacyWorld.owner(l);var menu=new ManagementSession(p,s,null);original=s;
            AcceptanceProbe.check("menu-before-packing",menu.valid(p));
            l.destroyBlock(LegacyWorld.POS,true,p);
            var drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(LegacyWorld.POS).inflate(4));
            AcceptanceProbe.check("one-packed-station-drop",drops.size()==1);
            ItemStack packed=drops.get(0).getItem().copy();drops.get(0).discard();p.setItemInHand(InteractionHand.MAIN_HAND,packed);
            var context=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,packed,new BlockHitResult(relocated.below().getCenter(),Direction.UP,relocated.below(),false));
            AcceptanceProbe.check("packed-item-placed",((BlockItem)packed.getItem()).place(context).consumesAction());
            s=(QuartermasterBlockEntity)l.getBlockEntity(relocated);
            AcceptanceProbe.check("packing-invalidates-old-menu",!menu.valid(p));
            AcceptanceProbe.check("packed-movement-conserves-exact-stock",s!=null&&LegacyWorld.inventory(s).equals(expected.getCompound("Inventory"))&&s.stationId().equals(original.stationId())&&!s.dropId().equals(original.dropId()));
            AcceptanceProbe.check("placement-consumes-packed-item",packed.isEmpty());
            // Flush the real world, release every fixture ticket, then wait for actual chunk removal.
            l.getServer().saveEverything(false,true,true);
            for(int x=63;x<=65;x++)for(int z=63;z<=65;z++)l.setChunkForced(x,z,false);
        }
        if(tick>50&&unloadedAt==0&&!l.hasChunkAt(relocated)) {
            unloadedAt=tick;AcceptanceProbe.check("fixture-chunks-actually-unloaded",l.getEntity(LegacyWorld.guardId(0))==null);LegacyWorld.load(l);
        }
        if(unloadedAt>0&&tick>=unloadedAt+30) {
            var s=(QuartermasterBlockEntity)l.getBlockEntity(relocated);
            AcceptanceProbe.check("unload-reload-exact-station-stock",s!=original&&LegacyWorld.inventory(s).equals(expected.getCompound("Inventory")));
            for(int i=0;i<3;i++)AcceptanceProbe.check("unload-reload-exact-guard-items-"+i,LegacyWorld.equipment((Guard)l.getEntity(LegacyWorld.guardId(i))).equals(expected.getCompound("Guard"+i)));
            return true;
        }
        if(tick>900)throw new AssertionError("Chunk unload/reload timeout");return false;
    }
}
