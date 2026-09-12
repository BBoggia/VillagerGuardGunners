package vggcompatprobe;

import java.util.*;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/** Controlled native POI lifecycle after natural generation, not autonomous job acquisition. */
final class QuartermasterClaimChecks {
    static Map<String,Object> run() {
        var level=Probe.level;
        var pos=Probe.start.getChunkPos().getWorldPosition().offset(8,level.getMaxBuildHeight()-12,8);
        require(level.hasChunkAt(pos) && level.getBlockState(pos).isAir(),"Station fixture unavailable");
        long time=level.getDayTime(); Villager operator=new Villager(EntityType.VILLAGER,level);
        try {
            level.setDayTime(6000);
            level.setBlock(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
            var station=(QuartermasterBlockEntity)level.getBlockEntity(pos);
            var type=ForgeRegistries.POI_TYPES.getHolder(QuartermasterProfession.ID).orElseThrow();
            level.getPoiManager().add(pos,type);
            operator.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
            operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
            operator.refreshBrain(level);
            operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));
            level.addFreshEntity(operator);station.operator=operator.getUUID();
            expect(station,ServiceStatus.OPERATOR_UNAVAILABLE,"unclaimed memory");
            require(level.getPoiManager().take(t->t.value()==type.value(),(t,p)->p.equals(pos),pos,1).isPresent(),"Native claim failed");
            expect(station,ServiceStatus.READY,"claimed station");
            operator.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            expect(station,ServiceStatus.OPERATOR_UNAVAILABLE,"lost memory");
            operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));
            operator.setVillagerData(operator.getVillagerData().setProfession(VillagerProfession.NONE));
            expect(station,ServiceStatus.OPERATOR_UNAVAILABLE,"lost profession");
            operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
            expect(station,ServiceStatus.READY,"restored profession");
            level.setDayTime(10000);expect(station,ServiceStatus.OUTSIDE_WORK_HOURS,"service window");level.setDayTime(6000);
            level.getPoiManager().release(pos);
            expect(station,ServiceStatus.OPERATOR_UNAVAILABLE,"released ticket with stale identity and memory");
            level.getPoiManager().take(t->t.value()==type.value(),(t,p)->p.equals(pos),pos,1);
            expect(station,ServiceStatus.READY,"reclaimed ticket");
            CompoundTag saved=new CompoundTag();((Entity)operator).saveWithoutId(saved);
            operator.discard();operator=new Villager(EntityType.VILLAGER,level);((Entity)operator).load(saved);level.addFreshEntity(operator);
            expect(station,ServiceStatus.READY,"operator native save/load");
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            require(ServiceEligibility.station(station)!=ServiceStatus.READY,"Removed station accepted");
            return Map.of("kind","controlled_native_poi_lifecycle","unclaimedRejected",true,"claimedReady",true,"releasedRejected",true,"professionAndMemory",true,"serviceWindow",true,"nativeSaveLoad",true,"removedRejected",true);
        } finally {operator.discard();level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.getPoiManager().remove(pos);level.setDayTime(time);}
    }
    private static void expect(QuartermasterBlockEntity station,ServiceStatus wanted,String context) {
        var actual=ServiceEligibility.station(station);require(actual==wanted,context+": expected "+wanted+", got "+actual);
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
