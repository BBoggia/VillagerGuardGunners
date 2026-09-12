package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Test-only loaded area, separated from other fixtures' actors and saved defense networks. */
public final class LoadedDutyTestArea implements AutoCloseable {
    private final ServerLevel level;
    private final List<ChunkPos> forced = new ArrayList<>();
    public LoadedDutyTestArea(ServerLevel level, BlockPos center) {
        this.level = level; var chunk = new ChunkPos(center);
        try {
            for (int x = chunk.x - 2; x <= chunk.x + 2; x++) for (int z = chunk.z - 2; z <= chunk.z + 2; z++) {
                var pos = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(pos.toLong())) { level.setChunkForced(x, z, true); forced.add(pos); }
                level.getChunk(x, z);
            }
        } catch (RuntimeException exception) { close(); throw exception; }
    }
    @Override public void close() {
        for (var pos : forced) level.setChunkForced(pos.x, pos.z, false);
        forced.clear();
    }
}
