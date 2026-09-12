package com.gamergaming.villagerguardgunners.defense;

import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Server-thread entry point for persistent village defense-state changes. */
public final class VillageDefenseStateService {
    private VillageDefenseStateService() {
    }

    public static VillageDefenseNetworkSavedData.StateUpdateResult signal(
            ServerLevel level,
            UUID networkId,
            VillageDefenseSignal signal) {
        ServerLevel normalizedLevel = Objects.requireNonNull(level, "level");
        if (!normalizedLevel.getServer().isSameThread()) {
            return VillageDefenseNetworkSavedData.StateUpdateResult.failed(
                    VillageDefenseNetworkSavedData.StateUpdateStatus.WRONG_THREAD);
        }
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(
                normalizedLevel);
        VillageDefenseTimings timings = VillageDefenseRuntimePolicy.timings();
        VillageDefenseNetworkSavedData.StateUpdateResult result = data.applySignal(
                Objects.requireNonNull(networkId, "networkId"),
                Objects.requireNonNull(signal, "signal"),
                normalizedLevel.getGameTime(),
                timings);
        if (result.status().successful()) {
            VillageDefenseStateTicker.track(normalizedLevel, data);
            VillageDefenseStaffingRuntime.requestReview(
                    normalizedLevel, result.networkId());
        }
        return result;
    }

    public static VillageDefenseNetworkSavedData.StateUpdateResult raidEnded(
            ServerLevel level,
            UUID networkId,
            boolean confirmedThreatActive) {
        ServerLevel normalizedLevel = Objects.requireNonNull(level, "level");
        if (!normalizedLevel.getServer().isSameThread()) {
            return VillageDefenseNetworkSavedData.StateUpdateResult.failed(
                    VillageDefenseNetworkSavedData.StateUpdateStatus.WRONG_THREAD);
        }
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(
                normalizedLevel);
        VillageDefenseTimings timings = VillageDefenseRuntimePolicy.timings();
        VillageDefenseNetworkSavedData.StateUpdateResult result = data.raidEnded(
                Objects.requireNonNull(networkId, "networkId"),
                normalizedLevel.getGameTime(),
                confirmedThreatActive,
                timings);
        if (result.status().successful()) {
            VillageDefenseStateTicker.track(normalizedLevel, data);
            VillageDefenseStaffingRuntime.requestReview(
                    normalizedLevel, result.networkId());
        }
        return result;
    }
}
