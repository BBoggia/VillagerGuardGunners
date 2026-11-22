package com.gamergaming.villagerguardgunners.worldgen.structure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class WatchtowerGenerationPolicyTest {

    @Test
    void guardLimitIsBoundedAndFailClosedWhenSpawningIsDisabled() {
        WatchtowerGenerationPolicy enabled =
                new WatchtowerGenerationPolicy(true, true, 2, true, true);
        WatchtowerGenerationPolicy disabled =
                new WatchtowerGenerationPolicy(true, false, 4, true, true);

        assertAll(
                () -> assertEquals(0, enabled.limitGuardCount(-1)),
                () -> assertEquals(0, enabled.limitGuardCount(0)),
                () -> assertEquals(1, enabled.limitGuardCount(1)),
                () -> assertEquals(2, enabled.limitGuardCount(4)),
                () -> assertEquals(0, disabled.limitGuardCount(4)));
    }

    @Test
    void constructionRejectsValuesOutsideTheHardCeiling() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WatchtowerGenerationPolicy(
                                true, true, -1, true, true)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WatchtowerGenerationPolicy(
                                true, true, 5, true, true)));
    }
}
