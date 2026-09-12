package com.gamergaming.villagerguardgunners.quartermaster;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModBlockEntities;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Actual loaded stations and Forge transport, with idle and active samples in the same JVM. */
@GameTestHolder(VillagerGuardGunners.MODID + "_village_buildings_performance")
@PrefixGameTestTemplate(false)
public final class QuartermasterPerformanceGameTests {
    @GameTest(batch = "quartermaster_100", templateNamespace = "villagerguardgunners_village_buildings_performance",
            template = "villagerguardgunners/gametest_arena", timeoutTicks = 550)
    public static void loadedStationsAndTransportFor100Stations(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var owner = helper.makeMockPlayer();
        List<QuartermasterBlockEntity> stations = new ArrayList<>();
        List<IItemHandler> pipes = new ArrayList<>();
        int forcedBefore = level.getForcedChunks().size();
        for (int i = 0; i < 100; i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(2 + i % 10 * 2, 2, 2 + i / 10 * 2));
            level.setBlock(pos, ModBlocks.QUARTERMASTER.get().defaultBlockState(), 3);
            var station = (QuartermasterBlockEntity) level.getBlockEntity(pos);
            owner.setPos(Vec3.atCenterOf(pos)); station.placedBy(owner);
            helper.assertTrue(station.changeSetting(owner, 1), "Could not enable transport fixture");
            for (int slot = 27; slot < 36; slot++) station.inventory().setStackInSlot(slot, new ItemStack(Items.GOLDEN_APPLE, 64));
            helper.assertTrue(((QuartermasterBlock) ModBlocks.QUARTERMASTER.get())
                    .getTicker(level, station.getBlockState(), ModBlockEntities.QUARTERMASTER.get()) == null,
                    "Loaded station introduced a per-block ticker");
            stations.add(station);
            pipes.add(station.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new));
        }
        var recording = new Recording(Configuration.getConfiguration("default"));
        recording.setName("quartermaster-100"); recording.setToDisk(true); recording.setMaxSize(64L * 1024 * 1024);
        recording.start();
        long[] idle = new long[200], active = new long[200], transport = new long[200];
        int[] samples = {0}; boolean[] completed = {false};
        ItemStack input = new ItemStack(Items.GOLDEN_APPLE);
        helper.onEachTick(() -> {
            if (completed[0] || helper.getTick() < 40) return;
            try {
                int index = samples[0]++;
                long[] tickTimes = level.getServer().tickTimes;
                long previous = tickTimes[Math.floorMod(level.getServer().getTickCount() - 1, tickTimes.length)];
                if (index >= 201 && index <= 400) active[index - 201] = previous;
                if (index < 200) idle[index] = previous;
                else if (index < 400) {
                    int sample = index - 200;
                    long started = System.nanoTime();
                    for (IItemHandler pipe : pipes) {
                        helper.assertTrue(pipe.insertItem(sample % 27, input, false).isEmpty(), "Stock insertion lost an item");
                        helper.assertTrue(pipe.extractItem(27 + sample % 9, 1, false).getCount() == 1, "Return extraction failed");
                        pipe.getStackInSlot(sample % 27);
                    }
                    transport[sample] = System.nanoTime() - started;
                } else {
                    completed[0] = true;
                    for (var station : stations) {
                        int stock = 0, returns = 0;
                        for (int i = 0; i < 36; i++) {
                            if (i < 27) stock += station.inventory().getStackInSlot(i).getCount();
                            else returns += station.inventory().getStackInSlot(i).getCount();
                        }
                        helper.assertTrue(stock == 200 && returns == 376, "Transport workload did not conserve stock");
                    }
                    helper.assertTrue(forcedBefore == level.getForcedChunks().size(), "Station workload forced chunks");
                    Path directory = Path.of(System.getProperty("villagerguardgunners.villageBuildingPerformanceReportDir"))
                            .resolveSibling("quartermaster-performance");
                    Files.createDirectories(directory); recording.stop(); recording.dump(directory.resolve("quartermaster-100.jfr"));
                    JsonObject report = new JsonObject();
                    report.addProperty("scenario", "quartermaster-100");
                    report.addProperty("stations", 100); report.addProperty("sampleTicksPerStage", 200);
                    report.addProperty("nativeInsertions", 20000); report.addProperty("nativeExtractions", 20000);
                    report.addProperty("perBlockTickers", 0); report.addProperty("itemsConserved", true);
                    report.addProperty("forcedChunksBefore", forcedBefore);
                    report.addProperty("forcedChunksAfter", level.getForcedChunks().size());
                    metrics(report, "idle", idle); metrics(report, "active", active); metrics(report, "transportBatch", transport);
                    report.addProperty("productionArtifactSha256", System.getProperty("villagerguardgunners.phase8ProductionArtifactSha256"));
                    report.addProperty("performanceHarnessSha256", System.getProperty("villagerguardgunners.performanceHarnessSha256"));
                    report.addProperty("timingNote", "Whole-server idle/active MSPT and 100-station transport batch durations in milliseconds; hardware-specific observations, no absolute timing gate.");
                    Files.writeString(directory.resolve("quartermaster-100.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                    recording.close(); helper.succeed();
                }
            } catch (Exception | AssertionError exception) {
                completed[0] = true; recording.close();
                helper.fail("Quartermaster performance workload failed: " + exception);
            }
        });
    }
    private static void metrics(JsonObject report, String prefix, long[] samples) {
        long[] sorted = samples.clone(); Arrays.sort(sorted);
        report.addProperty(prefix + "P50Millis", sorted[99] / 1_000_000.0);
        report.addProperty(prefix + "P95Millis", sorted[189] / 1_000_000.0);
        report.addProperty(prefix + "P99Millis", sorted[197] / 1_000_000.0);
        report.addProperty(prefix + "MaxMillis", sorted[199] / 1_000_000.0);
    }
}
