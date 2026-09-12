package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayout;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayouts;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySavedData;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;

/** Server-thread handoff for one persistent anchor and its bounded one-shot actions. */
public final class VillageBuildingAnchorLifecycle {
    static final int MAXIMUM_FUNCTIONAL_LOAD_RETRIES = 10;
    static final int MAXIMUM_RETRY_DELAY_TICKS = 40;
    private static final Logger LOGGER = LogUtils.getLogger();

    public static VillageBuildingIdentity record(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition) {
        return recordInternal(
                worldGenLevel,
                entryId,
                buildingType,
                anchorPosition,
                null,
                null,
                false);
    }

    /** Records the anchor and activates its bounded building behavior in one server task. */
    public static VillageBuildingIdentity recordAndPopulate(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition,
            Rotation rotation) {
        Objects.requireNonNull(worldGenLevel, "worldGenLevel");
        return recordInternal(
                worldGenLevel,
                entryId,
                buildingType,
                anchorPosition,
                Objects.requireNonNull(rotation, "rotation"),
                GuardPostSpawner.mapDifficulty(
                        worldGenLevel.getLevel().getDifficulty()),
                true);
    }

    private static VillageBuildingIdentity recordInternal(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty,
            boolean populate) {
        Objects.requireNonNull(worldGenLevel, "worldGenLevel");
        ServerLevel sourceLevel = worldGenLevel.getLevel();
        ResourceLocation dimensionId = sourceLevel.dimension().location();
        VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                worldGenLevel.getSeed(),
                dimensionId,
                entryId,
                buildingType,
                anchorPosition);
        MinecraftServer server = sourceLevel.getServer();
        ResourceKey<Level> dimension = sourceLevel.dimension();
        VillageDefenseDutyLayout dutyLayout = rotation == null
                ? null
                : resolveDutyLayout(identity, rotation);
        var populationBinding = com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlacement.binding();
        boolean populationRequired = com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlacement.bindingRequired();
        Runnable claim = () -> {
            ServerLevel liveLevel = server.getLevel(dimension);
            if (liveLevel != null) {
                if (populationRequired && (populationBinding == null
                        || !com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime.bind(liveLevel, identity, populationBinding))) return;
                VillageBuildingPendingActivation pending = populate
                        && rotation != null
                        && generationDifficulty != null
                        ? VillageBuildingPendingActivation.forBuilding(
                                identity, rotation, generationDifficulty, dutyLayout)
                        : null;
                VillageBuildingSavedData.ClaimResult anchorClaim =
                        VillageBuildingSavedData.get(liveLevel).claimAnchor(identity, pending);
                if (anchorClaim == VillageBuildingSavedData.ClaimResult.CLAIMED
                        || anchorClaim
                                == VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED) {
                    VillageDefenseNetworkSavedData.get(liveLevel)
                            .claimBuilding(identity);
                    if (rotation != null) {
                        VillageDefenseDutySavedData.get(liveLevel)
                                .claimLayout(Objects.requireNonNull(dutyLayout));
                    }
                }
                if (pending != null
                        && (anchorClaim == VillageBuildingSavedData.ClaimResult.CLAIMED
                                || anchorClaim
                                        == VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED)) {
                    VillageBuildingActivationRecovery.schedule(
                            liveLevel, identity.buildingId());
                }
            }
        };
        if (server.isSameThread()) {
            claim.run();
        } else {
            server.execute(claim);
        }
        return identity;
    }

    private static VillageDefenseDutyLayout resolveDutyLayout(
            VillageBuildingIdentity identity,
            Rotation rotation) {
        VillageBuildingInstallationSnapshot installation =
                VillageBuildingPoolIntegration.installationSnapshot();
        VillageBuildingPoolEntry definition = installation.attempted()
                ? VillageBuildingPoolIntegration.startupDefinition(identity.entryId())
                : VillageBuildingDefinitions.get(identity.entryId());
        return definition != null && definition.dutySlots().isPresent()
                ? VillageDefenseDutyLayouts.forBuilding(
                        identity, rotation, definition.dutySlots().orElseThrow())
                : VillageDefenseDutyLayouts.forBuilding(identity, rotation);
    }

    static void activatePending(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID buildingId,
            int attempt) {
        ServerLevel liveLevel = server.getLevel(dimension);
        if (liveLevel == null) {
            VillageBuildingActivationRecovery.release(server, buildingId);
            return;
        }
        VillageBuildingSavedData data = VillageBuildingSavedData.get(liveLevel);
        VillageBuildingRecord record = data.get(buildingId);
        if (record == null || record.pendingActivation().isEmpty()) {
            VillageBuildingActivationRecovery.release(server, buildingId);
            return;
        }
        VillageBuildingIdentity identity = record.identity();
        VillageBuildingPendingActivation pending = record.pendingActivation().orElseThrow();
        Rotation rotation = pending.rotation();
        NaturalGuardDifficulty generationDifficulty = pending.generationDifficulty();
        // Recovery may be entered from a persisted anchor before the original
        // network claim completed. Reassert membership before any guard joins.
        VillageDefenseNetworkSavedData.get(liveLevel).claimBuilding(identity);
        // A pending activation retains the exact placement rotation, so it can
        // restore a missing duty layout without scanning blocks or chunks.
        VillageDefenseDutySavedData.get(liveLevel)
                .claimLayout(pending.dutyLayout());
        ActivationAttempt result;
        if (identity.buildingType() == VillageBuildingType.ARMORY) {
            VillageBuildingArmoryLifecycle.Status status =
                    VillageBuildingArmoryLifecycle.activate(liveLevel, identity, rotation)
                            .status();
            result = new ActivationAttempt(
                    switch (status) {
                        case UNLOADED, PLACEMENT_FAILED -> Disposition.RETRYABLE_FAILURE;
                        case BLOCKED, CLAIM_REJECTED, WRONG_THREAD, WRONG_DIMENSION ->
                                Disposition.TERMINAL_FAILURE;
                        default -> Disposition.COMPLETE;
                    },
                    status.name());
        } else {
            VillageBuildingGarrisonLifecycle.Status status =
                    VillageBuildingGarrisonLifecycle.populate(
                                    liveLevel, identity, rotation, generationDifficulty)
                            .status();
            result = new ActivationAttempt(
                    switch (status) {
                        case UNLOADED, UNSAFE, SPAWN_FAILED ->
                                Disposition.RETRYABLE_FAILURE;
                        case MISSING_GROUP -> Disposition.DEFERRED_FAILURE;
                        case CLAIM_REJECTED,
                                WRONG_THREAD, WRONG_DIMENSION -> Disposition.TERMINAL_FAILURE;
                        default -> Disposition.COMPLETE;
                    },
                    status.name());
        }
        if (result.disposition() == Disposition.RETRYABLE_FAILURE
                && attempt < MAXIMUM_FUNCTIONAL_LOAD_RETRIES) {
            int delay = retryDelayTicks(attempt);
            VillageBuildingActivationRecovery.retry(
                    server, dimension, buildingId, attempt + 1, delay);
        } else if (result.disposition() == Disposition.RETRYABLE_FAILURE) {
            if (VillageBuildingActivationRecovery.shouldReport(
                    server, identity, "deferred-after-retries", result.status())) {
                LOGGER.warn(
                        "Village Defense Building activation remains pending after {} bounded attempts; it will resume when a relevant chunk loads: firstBuilding={}, type={}, dimension={}, anchor={}, status={}",
                        attempt + 1,
                        identity.buildingId(),
                        identity.buildingType().serializedName(),
                        identity.dimensionId(),
                        identity.anchorPosition(),
                        result.status());
            }
            VillageBuildingActivationRecovery.release(server, buildingId);
        } else if (result.disposition() == Disposition.DEFERRED_FAILURE) {
            if (VillageBuildingActivationRecovery.shouldReport(
                    server, identity, "deferred-configuration", result.status())) {
                LOGGER.warn(
                        "Village Defense Building activation is waiting for valid datapack content and will retry on a later relevant chunk load: firstBuilding={}, type={}, dimension={}, status={}",
                        identity.buildingId(),
                        identity.buildingType().serializedName(),
                        identity.dimensionId(),
                        result.status());
            }
            VillageBuildingActivationRecovery.release(server, buildingId);
        } else if (result.disposition() == Disposition.TERMINAL_FAILURE) {
            data.completePendingActivation(buildingId);
            if (VillageBuildingActivationRecovery.shouldReport(
                    server, identity, "terminal", result.status())) {
                LOGGER.warn(
                        "Village Defense Building activation failed closed: firstBuilding={}, type={}, dimension={}, anchor={}, status={}",
                        identity.buildingId(),
                        identity.buildingType().serializedName(),
                        identity.dimensionId(),
                        identity.anchorPosition(),
                        result.status());
            }
            VillageBuildingActivationRecovery.release(server, buildingId);
        } else {
            data.completePendingActivation(buildingId);
            VillageBuildingActivationRecovery.release(server, buildingId);
        }
    }

    static int retryDelayTicks(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("Retry attempt cannot be negative");
        }
        int exponent = Math.min(attempt, 6);
        return Math.min(MAXIMUM_RETRY_DELAY_TICKS, 1 << exponent);
    }

    private record ActivationAttempt(Disposition disposition, String status) {
        private ActivationAttempt {
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(status, "status");
        }
    }

    private enum Disposition {
        COMPLETE,
        RETRYABLE_FAILURE,
        DEFERRED_FAILURE,
        TERMINAL_FAILURE
    }

    private VillageBuildingAnchorLifecycle() {
    }
}
