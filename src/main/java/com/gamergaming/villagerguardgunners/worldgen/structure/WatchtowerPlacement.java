package com.gamergaming.villagerguardgunners.worldgen.structure;

import java.util.OptionalInt;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationContext;

/** Pure, bounded terrain policy shared by watchtower generation and tests. */
public final class WatchtowerPlacement {
    public static final int FOOTPRINT_SIZE = 9;
    public static final int FOOTPRINT_RADIUS = FOOTPRINT_SIZE / 2;
    public static final int TEMPLATE_HEIGHT = 14;
    public static final int MAX_SURFACE_VARIANCE = 2;
    public static final int SAMPLE_COUNT = 9;

    private static final int[][] SAMPLE_OFFSETS = {
        {0, 0},
        {FOOTPRINT_RADIUS, 0},
        {FOOTPRINT_SIZE - 1, 0},
        {0, FOOTPRINT_RADIUS},
        {FOOTPRINT_RADIUS, FOOTPRINT_RADIUS},
        {FOOTPRINT_SIZE - 1, FOOTPRINT_RADIUS},
        {0, FOOTPRINT_SIZE - 1},
        {FOOTPRINT_RADIUS, FOOTPRINT_SIZE - 1},
        {FOOTPRINT_SIZE - 1, FOOTPRINT_SIZE - 1}
    };

    public static OptionalInt findGroundY(
            GenerationContext context,
            int originX,
            int originZ) {
        int[] worldSurface = new int[SAMPLE_COUNT];
        int[] oceanFloor = new int[SAMPLE_COUNT];
        ChunkGenerator generator = context.chunkGenerator();

        for (int index = 0; index < SAMPLE_COUNT; index++) {
            int x = originX + SAMPLE_OFFSETS[index][0];
            int z = originZ + SAMPLE_OFFSETS[index][1];
            worldSurface[index] = generator.getFirstOccupiedHeight(
                    x,
                    z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(),
                    context.randomState());
            oceanFloor[index] = generator.getFirstOccupiedHeight(
                    x,
                    z,
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(),
                    context.randomState());
        }

        OptionalInt preliminaryGround = acceptedGroundY(
                worldSurface,
                oceanFloor,
                context.heightAccessor().getMinBuildHeight(),
                context.heightAccessor().getMaxBuildHeight());
        if (preliminaryGround.isEmpty()) {
            return OptionalInt.empty();
        }

        boolean[] drySupport = new boolean[SAMPLE_COUNT];
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            int x = originX + SAMPLE_OFFSETS[index][0];
            int z = originZ + SAMPLE_OFFSETS[index][1];
            NoiseColumn column = generator.getBaseColumn(
                    x,
                    z,
                    context.heightAccessor(),
                    context.randomState());
            int surfaceY = worldSurface[index];
            drySupport[index] = column.getBlock(surfaceY).getFluidState().isEmpty()
                    && column.getBlock(surfaceY - 1).getFluidState().isEmpty();
        }
        return acceptedGroundY(
                worldSurface,
                oceanFloor,
                drySupport,
                context.heightAccessor().getMinBuildHeight(),
                context.heightAccessor().getMaxBuildHeight());
    }

    public static OptionalInt acceptedGroundY(
            int[] worldSurface,
            int[] oceanFloor,
            int minimumBuildHeight,
            int maximumBuildHeight) {
        boolean[] drySupport = new boolean[SAMPLE_COUNT];
        java.util.Arrays.fill(drySupport, true);
        return acceptedGroundY(
                worldSurface,
                oceanFloor,
                drySupport,
                minimumBuildHeight,
                maximumBuildHeight);
    }

    public static OptionalInt acceptedGroundY(
            int[] worldSurface,
            int[] oceanFloor,
            boolean[] drySupport,
            int minimumBuildHeight,
            int maximumBuildHeight) {
        if (worldSurface.length != SAMPLE_COUNT
                || oceanFloor.length != SAMPLE_COUNT
                || drySupport.length != SAMPLE_COUNT) {
            return OptionalInt.empty();
        }

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            if (worldSurface[index] != oceanFloor[index] || !drySupport[index]) {
                return OptionalInt.empty();
            }
            lowest = Math.min(lowest, worldSurface[index]);
            highest = Math.max(highest, worldSurface[index]);
        }

        if (highest - lowest > MAX_SURFACE_VARIANCE
                || lowest < minimumBuildHeight
                || lowest + TEMPLATE_HEIGHT > maximumBuildHeight) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(lowest);
    }

    private WatchtowerPlacement() {
    }
}
