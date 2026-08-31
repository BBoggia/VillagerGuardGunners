package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class LoadedArmoryIndexMetricsTest {

    @Test
    void metricsAcceptOnlyMonotonicConsistentEvidence() {
        LoadedArmoryIndex.Metrics metrics = new LoadedArmoryIndex.Metrics(4L, 8L, 3L);

        assertAll(
                () -> assertEquals(4L, metrics.queries()),
                () -> assertEquals(8L, metrics.visitedRecords()),
                () -> assertEquals(3L, metrics.returnedArmories()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new LoadedArmoryIndex.Metrics(-1L, 0L, 0L)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new LoadedArmoryIndex.Metrics(1L, 1L, 2L)));
    }
}
