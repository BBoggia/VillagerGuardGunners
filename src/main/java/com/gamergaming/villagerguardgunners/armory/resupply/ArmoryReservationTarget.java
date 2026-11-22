package com.gamergaming.villagerguardgunners.armory.resupply;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Stable, reference-free identity for one Armory inventory slot. */
public record ArmoryReservationTarget(
        ResourceLocation dimension,
        BlockPos position,
        int sourceSlot) {
    public ArmoryReservationTarget {
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").immutable();
        if (sourceSlot < 0
                || sourceSlot >= ArmoryResupplyLimits.HARD_MAX_RESERVATION_SOURCE_SLOTS) {
            throw new IllegalArgumentException("sourceSlot is outside the hard reservation bound");
        }
    }
}
