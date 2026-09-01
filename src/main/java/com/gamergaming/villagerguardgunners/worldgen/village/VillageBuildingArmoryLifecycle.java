package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostArmoryPlacer;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostMarker;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/** One-shot server-thread bridge from a claimed village Armory anchor to storage. */
public final class VillageBuildingArmoryLifecycle {
    public static final ResourceLocation INITIAL_STOCK_LOOT_TABLE =
            ResourceLocation.fromNamespaceAndPath(
                    VillagerGuardGunners.MODID, "chests/village_armory");
    private static final GuardPostMarker PUBLIC_ARMORY_MARKER = new GuardPostMarker(
            GuardPostMarker.Action.ARMORY,
            INITIAL_STOCK_LOOT_TABLE,
            GuardPostMarker.ArmoryAccess.PUBLIC);

    private VillageBuildingArmoryLifecycle() {
    }

    public static Result activate(
            ServerLevel level,
            VillageBuildingIdentity identity,
            Rotation rotation) {
        return activate(
                level,
                identity,
                rotation,
                ModConfigs.GUARD_GUNNERS.villageBuildings.armoryPolicy());
    }

    public static Result activate(
            ServerLevel level,
            VillageBuildingIdentity identity,
            Rotation rotation,
            VillageBuildingArmoryPolicy policy) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(policy, "policy");
        if (!level.getServer().isSameThread()) {
            return new Result(Status.WRONG_THREAD);
        }
        if (!level.dimension().location().equals(identity.dimensionId())) {
            return new Result(Status.WRONG_DIMENSION);
        }
        if (identity.buildingType() != VillageBuildingType.ARMORY) {
            return new Result(Status.NOT_APPLICABLE);
        }

        VillageBuildingSavedData data = VillageBuildingSavedData.get(level);
        VillageBuildingRecord record = data.get(identity.buildingId());
        if (record == null || !record.identity().equals(identity)) {
            return new Result(Status.CLAIM_REJECTED);
        }
        if (record.hasClaimed(VillageBuildingAction.INITIAL_STOCK)) {
            return new Result(Status.ALREADY_CLAIMED);
        }
        if (!policy.placementEnabled()) {
            return new Result(Status.DISABLED);
        }

        BlockPos position = identity.anchorPosition();
        if (!level.hasChunkAt(position)) {
            return new Result(Status.UNLOADED);
        }
        if (level.getBlockEntity(position) instanceof ArmoryBlockEntity existing) {
            if (!existing.isGeneratedFor(identity.buildingId())) {
                return new Result(Status.BLOCKED);
            }
            return claimSetup(data, identity) == Status.CLAIMED
                    ? new Result(Status.ALREADY_PRESENT)
                    : new Result(Status.CLAIM_REJECTED);
        }
        if (!level.getBlockState(position).isAir()) {
            return new Result(Status.BLOCKED);
        }

        Status claimStatus = claimSetup(data, identity);
        if (claimStatus != Status.CLAIMED) {
            return new Result(claimStatus);
        }
        GuardPostArmoryPlacer.PlacementResult placement = GuardPostArmoryPlacer.place(
                level,
                position,
                rotation,
                identity.buildingId(),
                PUBLIC_ARMORY_MARKER,
                policy.initialStockEnabled());
        return new Result(switch (placement) {
            case PLACED_PENDING_LOOT -> Status.PLACED_PENDING_STOCK;
            case PLACED_EMPTY -> Status.PLACED_EMPTY;
            case ALREADY_PRESENT -> Status.ALREADY_PRESENT;
            case BLOCKED_BY_EXISTING_ARMORY -> Status.BLOCKED;
            case PLACEMENT_FAILED -> Status.PLACEMENT_FAILED;
        });
    }

    private static Status claimSetup(
            VillageBuildingSavedData data,
            VillageBuildingIdentity identity) {
        VillageBuildingSavedData.ClaimResult claim =
                data.claimArmorySetup(identity.buildingId());
        return switch (claim) {
            case CLAIMED -> Status.CLAIMED;
            case ALREADY_CLAIMED -> Status.ALREADY_CLAIMED;
            default -> Status.CLAIM_REJECTED;
        };
    }

    public record Result(Status status) {
        public Result {
            Objects.requireNonNull(status, "status");
        }
    }

    public enum Status {
        CLAIMED,
        PLACED_PENDING_STOCK,
        PLACED_EMPTY,
        ALREADY_PRESENT,
        NOT_APPLICABLE,
        DISABLED,
        UNLOADED,
        BLOCKED,
        ALREADY_CLAIMED,
        CLAIM_REJECTED,
        PLACEMENT_FAILED,
        WRONG_THREAD,
        WRONG_DIMENSION
    }
}
