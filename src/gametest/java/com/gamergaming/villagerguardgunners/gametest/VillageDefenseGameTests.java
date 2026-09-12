package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventRuntime;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventSource;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetwork;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Live Forge and Mixin coverage for village defense-state event routing. */
@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class VillageDefenseGameTests {
    private static final String EMPTY_TEMPLATE =
            "villagerguardgunners/gametest_arena";

    private VillageDefenseGameTests() {
    }

    @GameTest(
            batch = "village_defense_events",
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE)
    public static void successfulBellRingRoutesWatchThroughTheMixin(
            GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 2, 8));
        VillageDefenseNetwork network = registerNetwork(
                helper, anchor, "bell_event");
        var level = helper.getLevel();
        level.setBlock(anchor.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(anchor, Blocks.BELL.defaultBlockState(), 3);
        VillageDefenseEventRuntime.DiagnosticsSnapshot before =
                VillageDefenseEventRuntime.diagnostics(level);

        boolean rang = ((BellBlock) Blocks.BELL).attemptToRing(
                level, anchor, Direction.NORTH);

        VillageDefenseNetwork updated = VillageDefenseNetworkSavedData.get(level)
                .getNetwork(network.networkId());
        VillageDefenseEventRuntime.DiagnosticsSnapshot after =
                VillageDefenseEventRuntime.diagnostics(level);
        helper.assertTrue(rang, "The placed bell did not ring successfully");
        helper.assertTrue(
                updated != null
                        && updated.defenseStatus().state().isAtLeast(
                                VillageDefenseState.WATCH),
                "The bell mixin did not raise its defense network to Watch");
        helper.assertTrue(
                after.routedEvents() == before.routedEvents() + 1L
                        && after.lastEvent() != null
                        && after.lastEvent().source()
                                == VillageDefenseEventSource.BELL,
                "The successful bell ring was not recorded as one routed event");
        helper.succeed();
    }

    @GameTest(
            batch = "village_defense_events",
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE)
    public static void raidStartAndStopDriveSiegeAndRecovery(
            GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 2, 8));
        VillageDefenseNetwork network = registerNetwork(
                helper, anchor, "raid_event");
        var level = helper.getLevel();
        int raidId = 1_500_000_000
                + Math.floorMod(network.networkId().hashCode(), 100_000_000);
        Raid raid = new Raid(raidId, level, anchor);

        VillageDefenseEventRuntime.observeRaid(raid);

        VillageDefenseNetwork duringRaid = VillageDefenseNetworkSavedData.get(level)
                .getNetwork(network.networkId());
        helper.assertTrue(
                duringRaid != null
                        && duringRaid.defenseStatus().state()
                                == VillageDefenseState.SIEGE,
                "An ongoing raid did not raise its defense network to Siege");

        raid.stop();

        VillageDefenseNetwork afterRaid = VillageDefenseNetworkSavedData.get(level)
                .getNetwork(network.networkId());
        helper.assertTrue(
                afterRaid != null
                        && afterRaid.defenseStatus().state()
                                == VillageDefenseState.RECOVERY,
                "The Raid.stop mixin did not move the network into Recovery");
        helper.succeed();
    }

    private static VillageDefenseNetwork registerNetwork(
            GameTestHelper helper,
            BlockPos anchor,
            String fixtureName) {
        var level = helper.getLevel();
        VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                level.getSeed(),
                level.dimension().location(),
                ResourceLocation.fromNamespaceAndPath(
                        VillagerGuardGunners.MODID,
                        "gametest/defense_" + fixtureName),
                VillageBuildingType.SENTRY_POST,
                anchor);
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(level);
        VillageDefenseNetworkSavedData.MembershipResult result =
                data.claimBuilding(identity);
        helper.assertTrue(
                result.status().successful(),
                "The defense-event fixture could not register its network");
        VillageDefenseNetwork network = data.networkForBuilding(identity.buildingId());
        helper.assertTrue(network != null,
                "The registered defense-event fixture has no network");
        return network;
    }
}
