package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** The position is a hint; only identity and dimension select the assigned station. */
public record ServiceStationAssignment(UUID stationId, ResourceLocation dimension, BlockPos hint) {
    public ServiceStationAssignment {
        java.util.Objects.requireNonNull(stationId);
        java.util.Objects.requireNonNull(dimension);
        hint = java.util.Objects.requireNonNull(hint).immutable();
        if (dimension.toString().length() > 96) throw new IllegalArgumentException("Dimension ID too long");
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putUUID("Station", stationId);
        tag.putString("Dimension", dimension.toString()); tag.putLong("Hint", hint.asLong()); return tag;
    }
    public static ServiceStationAssignment load(CompoundTag tag) {
        if (!tag.getAllKeys().equals(Set.of("Station", "Dimension", "Hint")) || !tag.hasUUID("Station")
                || !tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("Hint", Tag.TAG_LONG))
            throw new IllegalArgumentException("Invalid station assignment");
        String value = tag.getString("Dimension"); var dimension = ResourceLocation.tryParse(value);
        if (dimension == null || !dimension.toString().equals(value)) throw new IllegalArgumentException("Invalid dimension");
        return new ServiceStationAssignment(tag.getUUID("Station"), dimension, BlockPos.of(tag.getLong("Hint")));
    }
}
