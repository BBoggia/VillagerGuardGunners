package com.gamergaming.villagerguardgunners.village;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Generated identity is independent of loading order, mutable geometry, and defense networks. */
public record VillageIdentity(ResourceLocation dimension, ResourceLocation structure, long startChunk) {
    public VillageIdentity {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(structure, "structure");
        ChunkPos start = new ChunkPos(startChunk);
        if (Math.abs((long) start.x) > 1_875_000 || Math.abs((long) start.z) > 1_875_000) {
            throw new IllegalArgumentException("Village start is outside supported world coordinates");
        }
    }

    public UUID id() {
        String key = "villagerguardgunners:village:1\0" + dimension + "\0" + structure + "\0" + startChunk;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
