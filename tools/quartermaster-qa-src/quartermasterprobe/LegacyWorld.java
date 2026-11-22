package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.tacz.guns.api.item.builder.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Only uses APIs present in the released 1.8 JAR. Saves real legacy state, not synthesized migration NBT. */
final class LegacyWorld {
    static final BlockPos POS=new BlockPos(1024,80,1024);
    static final UUID OWNER=UUID.fromString("51d6d8bb-07ea-43f3-918d-6019cce940ab");
    static final UUID NPC=UUID.fromString("81262375-56bb-41bc-bf12-0c3a442e1a67");
    static UUID guardId(int n){return UUID.nameUUIDFromBytes(("VggLegacyGuard"+n).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static FakePlayer owner(ServerLevel l){var p=new FakePlayer(l,new com.mojang.authlib.GameProfile(OWNER,"LegacyOwner"));p.setPos(1024.5,80,1025.5);return p;}
    static void load(ServerLevel l){for(int x=63;x<=65;x++)for(int z=63;z<=65;z++){l.getChunk(x,z);l.setChunkForced(x,z,true);}}
    static CompoundTag inventory(QuartermasterBlockEntity s){var root=new CompoundTag();for(int i=0;i<36;i++)root.put("s"+i,s.inventory().getStackInSlot(i).save(new CompoundTag()));return root;}
    static CompoundTag equipment(Guard g){var root=new CompoundTag();for(int i=0;i<12;i++)root.put("s"+i,ServiceEquipment.get(g,i).save(new CompoundTag()));return root;}
    static boolean seed(ServerLevel l,int tick) throws Exception {
        if(tick==20) {
            load(l);l.setDayTime(3000);
            for(int x=1020;x<=1030;x++)for(int z=1020;z<=1030;z++)l.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState());
            l.setBlockAndUpdate(POS,ModBlocks.QUARTERMASTER.get().defaultBlockState());var s=(QuartermasterBlockEntity)l.getBlockEntity(POS);s.placedBy(owner(l));
            var gun=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build();gun.setHoverName(net.minecraft.network.chat.Component.literal("Legacy custom rifle"));gun.getOrCreateTag().putString("AcceptanceMarker","preserve-exactly");
            s.inventory().setStackInSlot(0,gun);s.inventory().setStackInSlot(1,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(47).build());
            s.inventory().setStackInSlot(27,new ItemStack(Items.IRON_HELMET));
            for(int i=0;i<3;i++) {
                var g=GuardEntityType.GUARD.get().create(l);g.setUUID(guardId(i));g.setPos(1026.5+i,80,1026.5);g.setNoAi(true);g.setPersistenceRequired();g.setOwnerId(OWNER);l.addFreshEntity(g);
                var state=GuardEquipmentServiceAccess.get(g);state.policy(GuardServiceState.Policy.values()[i]);state.profile(ResourceLocation.parse("villagerguardgunners:rifleman"));state.replaceExisting(i==2);
                g.setItemSlot(EquipmentSlot.MAINHAND,gun.copy());GuardAmmoAccess.getAmmoHandler(g).setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(17+i).build());
            }
            var v=new Villager(EntityType.VILLAGER,l);v.setUUID(NPC);v.setPos(1024.5,80,1025.5);v.setNoAi(true);v.setPersistenceRequired();
            v.setVillagerData(v.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)).setLevel(3));
            v.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(l.dimension(),POS));
            v.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD,3),new ItemStack(Items.BREAD,2),8,4,.05f));v.getOffers().get(0).increaseUses();l.addFreshEntity(v);s.operator=NPC;
        }
        if(tick!=50)return false;
        l.getPoiManager().take(t->t.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(t,p)->p.equals(POS),POS,1);
        var s=(QuartermasterBlockEntity)l.getBlockEntity(POS);var expected=new CompoundTag();expected.putUUID("Station",s.stationId());expected.put("Inventory",inventory(s));
        for(int i=0;i<3;i++) {
            var g=(Guard)l.getEntity(guardId(i));expected.put("Guard"+i,equipment(g));
            var save=new CompoundTag();g.saveWithoutId(save);
            AcceptanceProbe.check("released-world-writes-version-1-"+i,save.getCompound(GuardServiceState.KEY).getInt("Version")==1);
        }
        expected.put("Offer",((Villager)l.getEntity(NPC)).getOffers().get(0).createTag());
        Files.writeString(AcceptanceProbe.out().resolveSibling("legacy-state.snbt"),expected.toString());
        AcceptanceProbe.check("legacy-world-stock-saved",s.inventory().getStackInSlot(1).getCount()==47);return true;
    }
}
