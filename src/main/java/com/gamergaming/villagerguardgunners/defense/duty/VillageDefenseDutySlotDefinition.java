package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/** Strict pack-authored duty slot resolved relative to one building anchor. */
public record VillageDefenseDutySlotDefinition(
        String slotKey,
        BlockPos offset,
        Direction facing,
        VillageDefenseDutySlotKind kind,
        VillageDefenseDutyWeaponRequirement weaponRequirement) {

    public VillageDefenseDutySlotDefinition {
        Objects.requireNonNull(slotKey, "slotKey");
        // Reuse the persistent slot's canonical key contract before generation.
        if (slotKey.isEmpty()
                || slotKey.length() > VillageDefenseDutySlot.MAXIMUM_SLOT_KEY_LENGTH
                || !slotKey.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Defense-duty slot key is not canonical");
        }
        offset = Objects.requireNonNull(offset, "offset").immutable();
        if (Math.abs(offset.getX()) > VillageDefenseDutyLayout.MAXIMUM_SLOT_OFFSET
                || Math.abs(offset.getY()) > VillageDefenseDutyLayout.MAXIMUM_SLOT_OFFSET
                || Math.abs(offset.getZ()) > VillageDefenseDutyLayout.MAXIMUM_SLOT_OFFSET) {
            throw new IllegalArgumentException("Defense-duty slot offset exceeds its hard bound");
        }
        facing = Objects.requireNonNull(facing, "facing");
        if (facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("Defense-duty facing must be horizontal");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(weaponRequirement, "weaponRequirement");
    }

    public VillageDefenseDutySlot resolve(
            VillageBuildingIdentity building,
            Rotation rotation) {
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(rotation, "rotation");
        BlockPos rotatedOffset = rotate(offset, rotation);
        return new VillageDefenseDutySlot(
                VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                VillageDefenseDutyIds.slotId(building.buildingId(), slotKey),
                building.buildingId(),
                building.dimensionId(),
                slotKey,
                building.anchorPosition().offset(rotatedOffset),
                rotation.rotate(facing),
                kind,
                weaponRequirement);
    }

    private static BlockPos rotate(BlockPos value, Rotation rotation) {
        return switch (rotation) {
            case NONE -> value;
            case CLOCKWISE_90 -> new BlockPos(-value.getZ(), value.getY(), value.getX());
            case CLOCKWISE_180 -> new BlockPos(-value.getX(), value.getY(), -value.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(value.getZ(), value.getY(), -value.getX());
        };
    }
}
