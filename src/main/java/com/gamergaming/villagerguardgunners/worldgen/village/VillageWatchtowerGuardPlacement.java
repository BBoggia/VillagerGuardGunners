package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIdentity;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import tallestegg.guardvillagers.entities.Guard;

/** One-shot placement of armed integrated-Watchtower guards on the upper deck. */
public final class VillageWatchtowerGuardPlacement {
    public static final int UPPER_DECK_OFFSET = 5;

    private VillageWatchtowerGuardPlacement() {
    }

    public static Result finalizePosition(
            ServerLevel level,
            Guard guard,
            GuardPostIdentity postIdentity) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(postIdentity, "postIdentity");
        if (!VillageBuildingGarrisons.WATCHTOWER_GROUP.equals(postIdentity.groupId())) {
            return Result.NOT_APPLICABLE;
        }
        if (!hasSupportedRangedWeapon(guard)) {
            return Result.READY_ROOM;
        }

        VillageBuildingRecord record = VillageBuildingSavedData.get(level)
                .get(postIdentity.postId());
        if (record == null
                || record.identity().buildingType() != VillageBuildingType.WATCHTOWER
                || !record.identity().buildingId().equals(postIdentity.postId())
                || !record.identity().dimensionId().equals(level.dimension().location())) {
            return Result.INVALID_BUILDING;
        }

        BlockPos lowerPosition = guard.blockPosition();
        BlockPos anchor = record.identity().anchorPosition();
        int horizontalOffset = Math.abs(lowerPosition.getX() - anchor.getX())
                + Math.abs(lowerPosition.getZ() - anchor.getZ());
        int expectedHorizontalOffset = postIdentity.memberIndex() == 0 ? 0 : 1;
        if (postIdentity.memberIndex() > 1
                || lowerPosition.getY() != anchor.getY()
                || horizontalOffset != expectedHorizontalOffset) {
            return Result.INVALID_READY_ROOM_POSITION;
        }

        BlockPos upperPosition = lowerPosition.above(UPPER_DECK_OFFSET);
        if (!level.hasChunkAt(upperPosition)) {
            return Result.UNLOADED;
        }
        BlockPos upperAnchor = anchor.above(UPPER_DECK_OFFSET);
        BoundingBox upperBounds = new BoundingBox(
                upperAnchor.getX() - 1,
                upperAnchor.getY() - 1,
                upperAnchor.getZ() - 1,
                upperAnchor.getX() + 1,
                upperAnchor.getY() + 1,
                upperAnchor.getZ() + 1);
        if (!GuardPostSpawner.isSafeSpawnCell(level, upperBounds, upperPosition)) {
            return Result.UNSAFE_UPPER_CELL;
        }

        double targetX = upperPosition.getX() + 0.5D;
        double targetY = upperPosition.getY();
        double targetZ = upperPosition.getZ() + 0.5D;
        AABB targetBounds = guard.getBoundingBox().move(
                targetX - guard.getX(),
                targetY - guard.getY(),
                targetZ - guard.getZ());
        if (!level.noCollision(guard, targetBounds)
                || !level.getEntities(guard, targetBounds).isEmpty()) {
            return Result.BLOCKED_UPPER_CELL;
        }

        guard.getNavigation().stop();
        guard.setDeltaMovement(0.0D, 0.0D, 0.0D);
        guard.teleportTo(targetX, targetY, targetZ);
        guard.setOnGround(true);
        return Result.UPPER_DECK;
    }

    static boolean hasSupportedRangedWeapon(Guard guard) {
        return NativeGunController.isGun(guard.getMainHandItem())
                || guard.getMainHandItem().is(Items.CROSSBOW)
                || guard.getOffhandItem().is(Items.CROSSBOW);
    }

    public enum Result {
        NOT_APPLICABLE,
        READY_ROOM,
        UPPER_DECK,
        INVALID_BUILDING,
        INVALID_READY_ROOM_POSITION,
        UNLOADED,
        UNSAFE_UPPER_CELL,
        BLOCKED_UPPER_CELL
    }
}
