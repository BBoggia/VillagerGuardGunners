package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID + "_village_buildings_performance")
@PrefixGameTestTemplate(false)
public final class MedicalPerformanceGameTests {
    @GameTest(batch = "medical_100", templateNamespace = "villagerguardgunners_village_buildings_performance",
            template = "villagerguardgunners/gametest_arena", timeoutTicks = 550)
    public static void medicalStorageAndTreatmentFor100Guards(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var guards = new ArrayList<Guard>(); var players = new ArrayList<FakePlayer>();
        int forcedBefore = level.getForcedChunks().size();
        for (int i = 0; i < 100; i++) {
            var guard = GuardEntityType.GUARD.get().create(level); var pos = h.absolutePos(new BlockPos(2 + i % 10 * 2, 2, 2 + i / 10 * 2));
            guard.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); guard.setNoAi(true); guard.setNoGravity(true);
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MedicalPerf"));
            player.setPos(guard.position().add(.5, 0, 0)); player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 10000));
            guard.setOwnerId(player.getUUID()); level.addFreshEntity(guard); guards.add(guard); players.add(player);
            GuardMedicalAccess.get(guard).inventory().setStackInSlot(0, new ItemStack(ModItems.BANDAGE.get(), 64));
        }
        var recording = new Recording(Configuration.getConfiguration("default")); recording.setName("medical-100");
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
                    // Force a worst-case accepted treatment on every guard each sample, outside normal cooldown cadence.
                    for (var guard : guards) { guard.setHealth(8); var pouch = GuardMedicalAccess.get(guard); pouch.readyAt(0); pouch.inventory().setStackInSlot(0, new ItemStack(ModItems.BANDAGE.get(), 64)); }
                    long start = System.nanoTime();
                    for (int i = 0; i < 100; i++) h.assertTrue(MedicalTreatment.fromPouch(players.get(i), guards.get(i)) == MedicalTreatment.Result.HEALED, "Stress treatment failed");
                    treatment[index - 200] = System.nanoTime() - start;
                    for (var guard : guards) h.assertTrue(guard.getHealth() == 12 && GuardMedicalAccess.get(guard).inventory().getStackInSlot(0).getCount() == 63, "Dose conservation failed");
                } else if (index >= 400) {
                    done[0] = true; h.assertTrue(forcedBefore == level.getForcedChunks().size(), "Medical workload forced chunks");
                    Path directory = Path.of(System.getProperty("villagerguardgunners.villageBuildingPerformanceReportDir")).resolveSibling("medical-performance");
                    Files.createDirectories(directory); recording.stop(); recording.dump(directory.resolve("medical-100.jfr")); recording.close();
                    var report = new JsonObject(); report.addProperty("scenario", "medical-100"); report.addProperty("guards", 100);
                    report.addProperty("sampleTicksPerStage", 200); report.addProperty("acceptedTreatments", 20000); report.addProperty("itemsConserved", true);
                    report.addProperty("forcedChunksBefore", forcedBefore); report.addProperty("forcedChunksAfter", level.getForcedChunks().size());
                    metrics(report, "idle", idle); metrics(report, "active", active); metrics(report, "treatmentBatch", treatment);
                    report.addProperty("productionArtifactSha256", System.getProperty("villagerguardgunners.phase8ProductionArtifactSha256"));
                    report.addProperty("performanceHarnessSha256", System.getProperty("villagerguardgunners.performanceHarnessSha256"));
                    report.addProperty("timingNote", "Hardware-specific milliseconds: whole-server idle/active MSPT and 100 accepted treatments per batch; fixture resets cooldown/health/stock outside the timed batch. No absolute timing gate.");
                    Files.writeString(directory.resolve("medical-100.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
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
