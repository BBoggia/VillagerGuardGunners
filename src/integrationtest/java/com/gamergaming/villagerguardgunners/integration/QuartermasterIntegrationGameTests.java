package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.behavior.ResetProfession;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class QuartermasterIntegrationGameTests {
    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void customProfessionUsesExclusivePoiAndSurvivesSaveLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(station, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        var poi = ForgeRegistries.POI_TYPES.getValue(QuartermasterProbeRegistry.ID);
        var profession = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProbeRegistry.ID);
        helper.assertTrue(poi != null && profession != null, "Test registries were not registered");
        var pois = level.getPoiManager();
        var first = pois.take(type -> type.value() == poi, (type, position) -> position.equals(station), station, 1);
        var second = pois.take(type -> type.value() == poi, (type, position) -> position.equals(station), station, 1);
        helper.assertTrue(first.isPresent() && second.isEmpty(), "Station POI is not exclusive");
        Villager operator = EntityType.VILLAGER.create(level);
        operator.setPos(station.getX() + 0.5, station.getY(), station.getZ() + 1.5);
        operator.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), station));
        AssignProfessionFromJobSite.create().tryStart(level, operator, level.getGameTime());
        helper.assertTrue(operator.getVillagerData().getProfession() == profession
                        && operator.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "Vanilla job assignment did not select the registered Quartermaster profession");
        helper.assertTrue(operator.getBrain().getSchedule().getActivityAt(2_000) == Activity.WORK,
                "Custom profession did not use the adult work schedule");
        CompoundTag saved = new CompoundTag();
        operator.saveWithoutId(saved);
        Villager restored = EntityType.VILLAGER.create(level);
        restored.load(saved);
        helper.assertTrue(restored.getVillagerData().getProfession() == profession
                        && restored.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                                .filter(pos -> pos.pos().equals(station)).isPresent(),
                "Operator identity/job memory did not persist");
        level.setBlock(station, Blocks.AIR.defaultBlockState(), 3);
        ValidateNearbyPoi.create(type -> type.value() == poi, MemoryModuleType.JOB_SITE)
                .tryStart(level, restored, level.getGameTime());
        ResetProfession.create().tryStart(level, restored, level.getGameTime());
        helper.assertTrue(restored.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()
                        && restored.getVillagerData().getProfession() == VillagerProfession.NONE,
                "Removed station did not invalidate the novice operator");
        System.out.println("VGG_INTEGRATION Quartermaster POI exclusive; profession/work/save/load/removal passed");
        helper.succeed();
    }
    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void productionQuartermasterUsesExclusivePoiAndSurvivesSaveLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos station = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(station, com.gamergaming.villagerguardgunners.init.ModBlocks.QUARTERMASTER.get().defaultBlockState(), 3);
        var poi = ForgeRegistries.POI_TYPES.getValue(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession.ID);
        var profession = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession.ID);
        helper.assertTrue(poi != null && profession != null, "Production registries were not registered");
        var pois = level.getPoiManager();
        var first = pois.take(type -> type.value() == poi, (type, position) -> position.equals(station), station, 1);
        var second = pois.take(type -> type.value() == poi, (type, position) -> position.equals(station), station, 1);
        helper.assertTrue(first.isPresent() && second.isEmpty(), "Station POI is not exclusive");
        Villager operator = EntityType.VILLAGER.create(level);
        operator.setPos(station.getX() + 0.5, station.getY(), station.getZ() + 1.5);
        operator.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), station));
        AssignProfessionFromJobSite.create().tryStart(level, operator, level.getGameTime());
        helper.assertTrue(operator.getVillagerData().getProfession() == profession
                        && operator.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "Vanilla job assignment did not select the registered Quartermaster profession");
        helper.assertTrue(operator.getBrain().getSchedule().getActivityAt(2_000) == Activity.WORK,
                "Custom profession did not use the adult work schedule");
        CompoundTag saved = new CompoundTag();
        operator.saveWithoutId(saved);
        Villager restored = EntityType.VILLAGER.create(level);
        restored.load(saved);
        helper.assertTrue(restored.getVillagerData().getProfession() == profession
                        && restored.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                                .filter(pos -> pos.pos().equals(station)).isPresent(),
                "Operator identity/job memory did not persist");
        level.setBlock(station, Blocks.AIR.defaultBlockState(), 3);
        ValidateNearbyPoi.create(type -> type.value() == poi, MemoryModuleType.JOB_SITE)
                .tryStart(level, restored, level.getGameTime());
        ResetProfession.create().tryStart(level, restored, level.getGameTime());
        helper.assertTrue(restored.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()
                        && restored.getVillagerData().getProfession() == VillagerProfession.NONE,
                "Removed station did not invalidate the novice operator");
        System.out.println("VGG_INTEGRATION Production Quartermaster POI exclusive; profession/work/save/load/removal passed");
        helper.succeed();
    }
}
