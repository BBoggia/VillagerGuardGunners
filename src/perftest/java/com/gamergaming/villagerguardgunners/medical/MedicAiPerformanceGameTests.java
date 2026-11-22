package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID + "_village_buildings_performance")
@PrefixGameTestTemplate(false)
public final class MedicAiPerformanceGameTests {
    @GameTest(batch = "medic_ai_32", templateNamespace = "villagerguardgunners_village_buildings_performance",
            template = "villagerguardgunners/gametest_arena", timeoutTicks = 550)
    public static void medicChannelsFor32Guards(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var guards = new ArrayList<Guard>();
        // These isolated actors still use native line-of-sight checks. Earlier batches can leave structures here.
        for(int x=1;x<=21;x++)for(int z=1;z<=9;z++)for(int y=1;y<=5;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        int forcedBefore = level.getForcedChunks().size();
        for (int i = 0; i < 32; i++) {
            var guard = GuardEntityType.GUARD.get().create(level); var pos = h.absolutePos(new BlockPos(2 + i % 10 * 2, 2, 2 + i / 10 * 2));
            guard.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); guard.setNoAi(false); guard.setNoGravity(true);
            guard.goalSelector.removeAllGoals(goal -> true); guard.targetSelector.removeAllGoals(goal -> true);
            var holder = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).getDataHolder();
            holder.drawTimestamp=-1; holder.shootTimestamp=-1; holder.meleePrepTickCount=-1;
            guards.add(guard);
            GuardMedicalAccess.get(guard).inventory().setStackInSlot(0, new ItemStack(ModItems.BANDAGE.get(), 64));
        }
        var recording = new Recording(Configuration.getConfiguration("default")); recording.setName("medic-ai-32");
        recording.setToDisk(true); recording.setMaxSize(64L * 1024 * 1024); recording.start();
        long[] idle = new long[200], active = new long[200], treatment = new long[200]; int[] samples = {0}; boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < 40) return;
            try {
                int index = samples[0]++; long[] tickTimes = level.getServer().tickTimes;
                long previous = tickTimes[Math.floorMod(level.getServer().getTickCount() - 1, tickTimes.length)];
                if (index < 200) idle[index] = previous;
                if (index >= 201 && index <= 400) active[index - 201] = previous;
                if (index >= 200 && index < 400) {
                    // Exercise accepted channels at the lease cap, outside normal channel/cooldown cadence.
                    for (var guard : guards) { guard.setHealth(8); var pouch = GuardMedicalAccess.get(guard); pouch.readyAt(0); pouch.inventory().setStackInSlot(0, new ItemStack(ModItems.BANDAGE.get(), 64)); }
                    long start = System.nanoTime();
                    for (int i = 0; i < 32; i += 2) {
                        Guard medic=guards.get(i), patient=guards.get(i+1);
                        com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(medic).medic(true);
                        h.assertTrue(MedicRuntime.reserve(medic,patient), "Channel reservation failed");
                    }
                    for (int i = 0; i < 32; i += 2) {
                        Guard medic=guards.get(i), patient=guards.get(i+1);
                        var lease=MedicRuntime.lease(medic); MedicRuntime.treat(lease);
                        h.assertTrue(lease.preparedAt>=40,"Stress fixture could not prepare a channel: lineOfSight="+medic.hasLineOfSight(patient)+", time="+level.getGameTime());
                        lease.preparedAt-=40; MedicRuntime.treat(lease);
                        h.assertTrue(patient.getHealth()==12 && GuardMedicalAccess.get(medic).inventory().getStackInSlot(0).getCount()==63, "Dose conservation failed: pair="+i+", health="+patient.getHealth()+", count="+GuardMedicalAccess.get(medic).inventory().getStackInSlot(0).getCount()+", status="+com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(medic).status+", prepared="+lease.preparedAt+", now="+level.getGameTime());
                    }
                    treatment[index - 200] = System.nanoTime() - start;

                } else if (index >= 400) {
                    done[0] = true; h.assertTrue(forcedBefore == level.getForcedChunks().size(), "Medical workload forced chunks");
                    Path directory = Path.of(System.getProperty("villagerguardgunners.villageBuildingPerformanceReportDir")).resolveSibling("medic-ai-performance");
                    Files.createDirectories(directory); recording.stop(); recording.dump(directory.resolve("medic-ai-32.jfr")); recording.close();
                    var report = new JsonObject(); report.addProperty("scenario", "medic-ai-32"); report.addProperty("guards", 32);
                    report.addProperty("sampleTicksPerStage", 200); report.addProperty("acceptedTreatments", 3200); report.addProperty("itemsConserved", true);
                    report.addProperty("forcedChunksBefore", forcedBefore); report.addProperty("forcedChunksAfter", level.getForcedChunks().size());
                    metrics(report, "idle", idle); metrics(report, "active", active); metrics(report, "treatmentBatch", treatment);
                    report.addProperty("productionArtifactSha256", System.getProperty("villagerguardgunners.phase8ProductionArtifactSha256"));
                    report.addProperty("performanceHarnessSha256", System.getProperty("villagerguardgunners.performanceHarnessSha256"));
                    report.addProperty("timingNote", "Hardware-specific milliseconds: whole-server idle/active MSPT and 16 accepted medic channels per batch; fixture resets cooldown/health/stock outside the timed batch and advances channel timestamps. Actors are isolated from world AI; navigation and triage are covered by native behavior tests. No absolute timing gate.");
                    Files.writeString(directory.resolve("medic-ai-32.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                    guards.forEach(Guard::discard); h.succeed();
                }
            } catch (Exception | AssertionError exception) { done[0] = true; recording.close(); guards.forEach(Guard::discard); h.fail("Medical performance workload failed: " + exception); }
        });
    }
    private static void metrics(JsonObject report, String prefix, long[] values) {
        var sorted = values.clone(); Arrays.sort(sorted);
        report.addProperty(prefix + "P50Millis", sorted[99] / 1_000_000.0); report.addProperty(prefix + "P95Millis", sorted[189] / 1_000_000.0);
        report.addProperty(prefix + "P99Millis", sorted[197] / 1_000_000.0); report.addProperty(prefix + "MaxMillis", sorted[199] / 1_000_000.0);
    }
}
