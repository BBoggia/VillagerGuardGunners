package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.api.GuardPostAccess;
import com.gamergaming.villagerguardgunners.api.GuardPostAssignmentAccess;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** One-time, bounded Guard Villagers creation used only by a claimed structure marker. */
public final class GuardPostSpawner {
    private static final List<BlockPos> LOCAL_OFFSETS = List.of(
            BlockPos.ZERO,
            new BlockPos(-1, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, -1));

    private GuardPostSpawner() {
    }

    public static NaturalGuardDifficulty mapDifficulty(Difficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case PEACEFUL -> NaturalGuardDifficulty.PEACEFUL;
            case EASY -> NaturalGuardDifficulty.EASY;
            case NORMAL -> NaturalGuardDifficulty.NORMAL;
            case HARD -> NaturalGuardDifficulty.HARD;
        };
    }

    public static BlockPos spawnPosition(
            BlockPos markerPosition,
            Rotation rotation,
            int memberIndex) {
        Objects.requireNonNull(markerPosition, "markerPosition");
        Objects.requireNonNull(rotation, "rotation");
        if (memberIndex < 0 || memberIndex >= LOCAL_OFFSETS.size()) {
            throw new IllegalArgumentException("Guard member index is outside its hard bound");
        }
        BlockPos offset = LOCAL_OFFSETS.get(memberIndex);
        int rotatedX;
        int rotatedZ;
        switch (rotation) {
            case NONE -> {
                rotatedX = offset.getX();
                rotatedZ = offset.getZ();
            }
            case CLOCKWISE_90 -> {
                rotatedX = -offset.getZ();
                rotatedZ = offset.getX();
            }
            case CLOCKWISE_180 -> {
                rotatedX = -offset.getX();
                rotatedZ = -offset.getZ();
            }
            case COUNTERCLOCKWISE_90 -> {
                rotatedX = offset.getZ();
                rotatedZ = -offset.getX();
            }
            default -> throw new IllegalStateException("Unhandled watchtower rotation " + rotation);
        }
        return markerPosition.offset(rotatedX, 0, rotatedZ);
    }

    public static boolean spawnMember(
            ServerLevelAccessor level,
            BoundingBox placementBounds,
            BlockPos markerPosition,
            Rotation rotation,
            UUID postId,
            ResourceLocation groupId,
            String variantName,
            int memberIndex,
            GuardPostGroupMember member,
            NaturalGuardDifficulty generationDifficulty) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementBounds, "placementBounds");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(variantName, "variantName");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(generationDifficulty, "generationDifficulty");

        Guard guard = prepareMember(
                level,
                placementBounds,
                markerPosition,
                rotation,
                postId,
                groupId,
                variantName,
                memberIndex,
                member,
                generationDifficulty);
        if (guard == null) {
            return false;
        }
        if (addPrepared(level, guard)) {
            return true;
        }
        guard.discard();
        return false;
    }

    /**
     * Adds every requested member or removes the complete batch. This keeps a
     * building garrison from being left half-created when one entity is rejected.
     */
    public static int spawnMembersAtomically(
            ServerLevelAccessor level,
            BoundingBox placementBounds,
            BlockPos markerPosition,
            Rotation rotation,
            UUID postId,
            ResourceLocation groupId,
            String variantName,
            List<GuardPostGroupMember> members,
            int memberCount,
            NaturalGuardDifficulty generationDifficulty) {
        Objects.requireNonNull(members, "members");
        if (memberCount < 0
                || memberCount > members.size()
                || memberCount > LOCAL_OFFSETS.size()) {
            throw new IllegalArgumentException("Atomic guard batch is outside its hard bound");
        }
        List<Guard> prepared = new ArrayList<>(memberCount);
        for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
            Guard guard = prepareMember(
                    level,
                    placementBounds,
                    markerPosition,
                    rotation,
                    postId,
                    groupId,
                    variantName,
                    memberIndex,
                    members.get(memberIndex),
                    generationDifficulty);
            if (guard == null) {
                prepared.forEach(Guard::discard);
                return 0;
            }
            prepared.add(guard);
        }

        for (Guard guard : prepared) {
            if (!addPrepared(level, guard)) {
                prepared.forEach(Guard::discard);
                return 0;
            }
        }
        return prepared.size();
    }

    private static Guard prepareMember(
            ServerLevelAccessor level,
            BoundingBox placementBounds,
            BlockPos markerPosition,
            Rotation rotation,
            UUID postId,
            ResourceLocation groupId,
            String variantName,
            int memberIndex,
            GuardPostGroupMember member,
            NaturalGuardDifficulty generationDifficulty) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementBounds, "placementBounds");
        Objects.requireNonNull(markerPosition, "markerPosition");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(variantName, "variantName");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(generationDifficulty, "generationDifficulty");

        BlockPos position = spawnPosition(markerPosition, rotation, memberIndex);
        if (!isSafeSpawnCell(level, placementBounds, position)) {
            return null;
        }
        UUID guardId = GuardPostIds.guardId(postId, memberIndex);
        if (level.getLevel().getEntity(guardId) != null) {
            return null;
        }
        Guard guard = GuardEntityType.GUARD.get().create(level.getLevel());
        if (guard == null) {
            return null;
        }

        guard.setUUID(guardId);
        float yaw = rotation.rotate(Direction.NORTH).toYRot();
        guard.moveTo(
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D,
                yaw,
                0.0F);
        guard.setPersistenceRequired();
        ((GuardPostAccess) guard).villagerGuardGunners$setGuardPostIdentity(
                GuardPostIdentity.assigned(postId, groupId, variantName, memberIndex));
        if (!GuardPostAssignmentAccess.set(
                guard,
                GuardPostAssignment.pending(member, generationDifficulty))) {
            guard.discard();
            return null;
        }
        guard.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(position),
                MobSpawnType.STRUCTURE,
                null,
                null);
        return guard;
    }

    private static boolean addPrepared(ServerLevelAccessor level, Guard guard) {
        return level.addFreshEntity(guard);
    }

    public static boolean isSafeSpawnCell(
            ServerLevelAccessor level,
            BoundingBox placementBounds,
            BlockPos position) {
        BlockPos floorPosition = position.below();
        BlockPos headPosition = position.above();
        if (!placementBounds.isInside(floorPosition)
                || !placementBounds.isInside(position)
                || !placementBounds.isInside(headPosition)) {
            return false;
        }
        var floor = level.getBlockState(floorPosition);
        return floor.getFluidState().isEmpty()
                && floor.isFaceSturdy(level, floorPosition, Direction.UP)
                && level.getFluidState(position).isEmpty()
                && level.getFluidState(headPosition).isEmpty()
                && level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                && level.getBlockState(headPosition).getCollisionShape(level, headPosition).isEmpty();
    }
}
