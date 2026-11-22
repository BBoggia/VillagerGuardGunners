package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.function.BiFunction;

/** Cheap pre-assembly screening of land buildings against unmodified terrain. */
public final class VillageBuildingTerrainSafety {
    public static final int MAXIMUM_CUT = 4;
    private VillageBuildingTerrainSafety() { }

    /** Heights are the first free block above terrain, with and without fluids. */
    public record Column(int surface, int oceanFloor) { }

    public static boolean allows(int minX, int minZ, int maxX, int maxZ, int standingY,
            BiFunction<Integer, Integer, Column> terrain) {
        int[] xs = {minX - 1, minX + (maxX - minX) / 2, maxX + 1};
        int[] zs = {minZ - 1, minZ + (maxZ - minZ) / 2, maxZ + 1};
        for (int x : xs) {
            for (int z : zs) {
                Column column = terrain.apply(x, z);
                if (column.surface() > standingY + MAXIMUM_CUT
                        || column.surface() > column.oceanFloor() && column.surface() > standingY) {
                    return false;
                }
            }
        }
        return true;
    }
}
