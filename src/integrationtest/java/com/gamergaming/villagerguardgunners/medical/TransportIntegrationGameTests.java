package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.GuardTransportAccess;
import com.gamergaming.villagerguardgunners.integration.ProbeScope;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class TransportIntegrationGameTests {
    @GameTest(templateNamespace="villagerguardgunners_integration", template="villagerguardgunners/gametest_arena")
    public static void passengerClearanceRejectsUnloadedTerrainWithoutLoadingIt(GameTestHelper helper) {
        var level = helper.getLevel();
        var point = new Vec3(8_000_000.5, 100, 8_000_000.5);
        helper.assertTrue(!level.hasChunkAt(BlockPos.containing(point)), "Probe terrain must be unloaded");
        try (var probe = new ProbeScope()) {
            helper.assertTrue(!TransportRuntime.clear(level, TransportRuntime.carryBox(point)), "Unloaded clearance accepted");
            helper.assertTrue(probe.missingChunkRequests == 0 && probe.poiReads == 0, "Clearance requested terrain or POIs");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace="villagerguardgunners_integration", template="villagerguardgunners/gametest_arena")
    public static void orphanPassengerRecoveryDoesNotLoadSavedTerrain(GameTestHelper helper) {
        var level = helper.getLevel();
        var point = new Vec3(8_000_032.5, 100, 8_000_032.5);
        var patient = GuardEntityType.GUARD.get().create(level);
        patient.setPos(point);
        GuardTransportAccess.get(patient).mark(UUID.randomUUID(), level.dimension().location(), point);
        try (var probe = new ProbeScope()) {
            TransportRuntime.tickGuard(patient);
            helper.assertTrue(!GuardTransportAccess.get(patient).present(), "Orphan ownership hint remained");
            helper.assertTrue(patient.position().equals(point), "Recovery teleported into unloaded terrain");
            helper.assertTrue(probe.missingChunkRequests == 0 && probe.poiReads == 0, "Recovery requested terrain or POIs");
        } finally { patient.discard(); }
        helper.succeed();
    }
}
