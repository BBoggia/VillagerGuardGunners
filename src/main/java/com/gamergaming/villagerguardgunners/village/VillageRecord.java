package com.gamergaming.villagerguardgunners.village;

import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

public record VillageRecord(VillageIdentity identity, VillageFootprint footprint, long revision, Set<BlockPos> homes) {
    public static final int MAX_HOMES = 1024;
    public VillageRecord {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(footprint, "footprint");
        if (revision < 1) throw new IllegalArgumentException("Invalid observation revision");
        homes = homes.stream().map(BlockPos::immutable).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (homes.size() > MAX_HOMES || homes.stream().anyMatch(p -> !footprint.bounds().contains(p)
                || Math.abs((long) p.getY()) > 20_000)) throw new IllegalArgumentException("Invalid home observations");
        // Prevent a corrupt piece list from indexing a distant settlement under this start's identity.
        var start = new net.minecraft.world.level.ChunkPos(identity.startChunk());
        var b = footprint.bounds();
        if (start.getMinBlockX() < (long) b.minX() - VillageFootprint.MAX_SPAN
                || start.getMinBlockX() > (long) b.maxX() + VillageFootprint.MAX_SPAN
                || start.getMinBlockZ() < (long) b.minZ() - VillageFootprint.MAX_SPAN
                || start.getMinBlockZ() > (long) b.maxZ() + VillageFootprint.MAX_SPAN) {
            throw new IllegalArgumentException("Footprint is too far from its start");
        }
    }
}
