package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroupPool;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroupSelection;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroups;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** One-shot server-thread bridge from a claimed building anchor to its guards. */
public final class VillageBuildingGarrisonLifecycle {
    private VillageBuildingGarrisonLifecycle() {
    }

    public static Result populate(
            ServerLevel level,
            VillageBuildingIdentity identity,
            Rotation rotation) {
        var settings = ModConfigs.GUARD_GUNNERS.villageBuildings;
        return populate(
                level,
                identity,
                rotation,
                GuardPostSpawner.mapDifficulty(level.getDifficulty()),
                settings.garrisonPolicy());
    }

    static Result populate(
            ServerLevel level,
            VillageBuildingIdentity identity,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty) {
        return populate(
                level,
                identity,
                rotation,
                generationDifficulty,
                ModConfigs.GUARD_GUNNERS.villageBuildings.garrisonPolicy());
    }

    static Result populate(
            ServerLevel level,
            VillageBuildingIdentity identity,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty,
            VillageBuildingGarrisonPolicy policy) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(generationDifficulty, "generationDifficulty");
        Objects.requireNonNull(policy, "policy");
        if (!level.getServer().isSameThread()) {
            return new Result(Status.WRONG_THREAD, 0);
        }
        if (!level.dimension().location().equals(identity.dimensionId())) {
            return new Result(Status.WRONG_DIMENSION, 0);
        }
        Optional<VillageBuildingGarrisons.Plan> planned =
                VillageBuildingGarrisons.planFor(identity.buildingType());
        if (planned.isEmpty()) {
            return new Result(Status.NOT_APPLICABLE, 0);
        }
        VillageBuildingSavedData data = VillageBuildingSavedData.get(level);
        VillageBuildingRecord record = data.get(identity.buildingId());
        if (record == null || !record.identity().equals(identity)) {
            return new Result(Status.CLAIM_REJECTED, 0);
        }
        if (record.hasClaimed(VillageBuildingAction.GARRISON)) {
            return new Result(Status.ALREADY_CLAIMED, 0);
        }
        if (!policy.enabled()) {
            return new Result(Status.DISABLED, 0);
        }

        VillageBuildingGarrisons.Plan plan = planned.get();
        GuardPostGroupPool group = GuardPostGroups.get(plan.groupId());
        if (group == null) {
            return new Result(Status.MISSING_GROUP, 0);
        }

        Optional<GuardPostGroupSelection> selected = group.select(
                generationDifficulty,
                selectionSeed(identity));
        int guardCount = selected
                .map(selection -> policy.limitGuardCount(
                        identity.buildingType(), selection.members().size()))
                .orElse(0);
        BlockPos spawnAnchor = identity.anchorPosition().above(plan.verticalSpawnOffset());
        BoundingBox spawnBounds = new BoundingBox(
                spawnAnchor.getX() - 1,
                spawnAnchor.getY() - 1,
                spawnAnchor.getZ() - 1,
                spawnAnchor.getX() + 1,
                spawnAnchor.getY() + 1,
                spawnAnchor.getZ() + 1);
        if (guardCount > 0) {
            for (int memberIndex = 0; memberIndex < guardCount; memberIndex++) {
                BlockPos position = GuardPostSpawner.spawnPosition(
                        spawnAnchor, rotation, memberIndex);
                if (!level.hasChunkAt(position)) {
                    return new Result(Status.UNLOADED, 0);
                }
            }
            for (int memberIndex = 0; memberIndex < guardCount; memberIndex++) {
                BlockPos position = GuardPostSpawner.spawnPosition(
                        spawnAnchor, rotation, memberIndex);
                if (!GuardPostSpawner.isSafeSpawnCell(level, spawnBounds, position)) {
                    return new Result(Status.UNSAFE, 0);
                }
            }
        }

        VillageBuildingSavedData.ClaimResult claim =
                data.claimAction(
                        identity.buildingId(), VillageBuildingAction.GARRISON);
        if (claim == VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED) {
            return new Result(Status.ALREADY_CLAIMED, 0);
        }
        if (claim != VillageBuildingSavedData.ClaimResult.CLAIMED) {
            return new Result(Status.CLAIM_REJECTED, 0);
        }
        if (selected.isEmpty()) {
            return new Result(
                    generationDifficulty == NaturalGuardDifficulty.PEACEFUL
                            ? Status.PEACEFUL
                            : Status.NO_ELIGIBLE_GROUP,
                    0);
        }
        if (guardCount == 0) {
            return new Result(Status.CONFIGURED_ZERO, 0);
        }

        GuardPostGroupSelection selection = selected.get();
        int spawned = GuardPostSpawner.spawnMembersAtomically(
                level,
                spawnBounds,
                spawnAnchor,
                rotation,
                identity.buildingId(),
                selection.groupId(),
                selection.variantName(),
                selection.members(),
                guardCount,
                generationDifficulty);
        return new Result(
                spawned == guardCount ? Status.SPAWNED : Status.SPAWN_FAILED,
                spawned);
    }

    static long selectionSeed(VillageBuildingIdentity identity) {
        long seed = identity.buildingId().getMostSignificantBits()
                ^ Long.rotateLeft(identity.buildingId().getLeastSignificantBits(), 17);
        seed ^= 0xD1B54A32D192ED03L;
        seed ^= seed >>> 29;
        seed *= 0x94D049BB133111EBL;
        return seed ^ seed >>> 31;
    }

    public record Result(Status status, int spawnedGuards) {
        public Result {
            Objects.requireNonNull(status, "status");
            if (spawnedGuards < 0
                    || spawnedGuards
                            > VillageBuildingGarrisonPolicy.MAXIMUM_GUARDS_PER_BUILDING) {
                throw new IllegalArgumentException("Spawned guard count is outside its bound");
            }
        }
    }

    public enum Status {
        SPAWNED,
        PEACEFUL,
        CONFIGURED_ZERO,
        NOT_APPLICABLE,
        DISABLED,
        MISSING_GROUP,
        NO_ELIGIBLE_GROUP,
        UNLOADED,
        UNSAFE,
        ALREADY_CLAIMED,
        CLAIM_REJECTED,
        SPAWN_FAILED,
        WRONG_THREAD,
        WRONG_DIMENSION
    }
}
