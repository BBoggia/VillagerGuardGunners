package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

final class GuardPostSpawnerTest {
    @Test
    void boundedMemberOffsetsRotateAroundTheMarker() {
        BlockPos marker = new BlockPos(10, 65, -8);
        for (Rotation rotation : Rotation.values()) {
            Set<BlockPos> positions = new HashSet<>();
            for (int index = 0; index < 4; index++) {
                positions.add(GuardPostSpawner.spawnPosition(marker, rotation, index));
            }
            assertEquals(4, positions.size());
            assertEquals(marker, GuardPostSpawner.spawnPosition(marker, rotation, 0));
        }

        assertEquals(marker.west(), GuardPostSpawner.spawnPosition(marker, Rotation.NONE, 1));
        assertEquals(marker.north(),
                GuardPostSpawner.spawnPosition(marker, Rotation.CLOCKWISE_90, 1));
        assertEquals(marker.east(),
                GuardPostSpawner.spawnPosition(marker, Rotation.CLOCKWISE_180, 1));
        assertEquals(marker.south(),
                GuardPostSpawner.spawnPosition(marker, Rotation.COUNTERCLOCKWISE_90, 1));
        assertThrows(IllegalArgumentException.class,
                () -> GuardPostSpawner.spawnPosition(marker, Rotation.NONE, 4));
    }

    @Test
    void vanillaDifficultyMappingIsTotalAndExact() {
        assertEquals(NaturalGuardDifficulty.PEACEFUL,
                GuardPostSpawner.mapDifficulty(Difficulty.PEACEFUL));
        assertEquals(NaturalGuardDifficulty.EASY,
                GuardPostSpawner.mapDifficulty(Difficulty.EASY));
        assertEquals(NaturalGuardDifficulty.NORMAL,
                GuardPostSpawner.mapDifficulty(Difficulty.NORMAL));
        assertEquals(NaturalGuardDifficulty.HARD,
                GuardPostSpawner.mapDifficulty(Difficulty.HARD));
    }
}
