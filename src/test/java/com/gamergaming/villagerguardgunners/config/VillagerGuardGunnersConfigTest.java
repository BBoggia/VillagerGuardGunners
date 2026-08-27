package com.gamergaming.villagerguardgunners.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import org.junit.jupiter.api.Test;

final class VillagerGuardGunnersConfigTest {

    @Test
    void defaultsMatchThePreConfigRuntimeBehavior() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();

        assertAll(
                () -> assertEquals(1, VillagerGuardGunnersConfig.CONFIG_VERSION),
                () -> assertTrue(config.enabled.get()),
                () -> assertEquals(5, config.combat.requiredVisibleTicks.get()),
                () -> assertEquals(32.0D, config.combat.maximumEngagementRange.get()),
                () -> assertEquals(4.0D, config.combat.closeRangeDistance.get()),
                () -> assertEquals(10.0D, config.combat.aimToleranceDegrees.get()),
                () -> assertEquals(1.0D, config.movement.approachSpeed.get()),
                () -> assertEquals(0.75D, config.movement.retreatSpeed.get()),
                () -> assertEquals(0.75D, config.movement.repositionSpeed.get()),
                () -> assertEquals(20, config.movement.friendlyRepositionTicks.get()),
                () -> assertEquals(0.75D, config.safety.baseClearance.get()),
                () -> assertEquals(2, config.performance.visibilitySampleIntervalTicks.get()),
                () -> assertEquals(5, config.performance.movementDecisionIntervalTicks.get()),
                () -> assertEquals(20, config.performance.pathRecalculationIntervalTicks.get()),
                () -> assertEquals(20, config.performance.gunDataRefreshIntervalTicks.get()),
                () -> assertEquals(2, config.performance.unsafeShotCacheTicks.get()),
                () -> assertEquals(10, config.performance.repositionProgressCheckTicks.get()));
    }

    @Test
    void validatedFieldsCorrectValuesOutsideTheirSupportedRanges() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();

        assertAll(
                () -> assertEquals(0, config.combat.requiredVisibleTicks
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(40, config.combat.requiredVisibleTicks
                        .correctEntry(41, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.combat.maximumEngagementRange
                        .correctEntry(7.0D, ValidationType.STRONG).get()),
                () -> assertEquals(64.0D, config.combat.maximumEngagementRange
                        .correctEntry(65.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1.0D, config.combat.closeRangeDistance
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.combat.closeRangeDistance
                        .correctEntry(9.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0.1D, config.movement.approachSpeed
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(2.0D, config.movement.approachSpeed
                        .correctEntry(3.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0.25D, config.safety.baseClearance
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(4.0D, config.safety.baseClearance
                        .correctEntry(5.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1, config.performance.visibilitySampleIntervalTicks
                        .correctEntry(0, ValidationType.STRONG).get()),
                () -> assertEquals(200, config.performance.gunDataRefreshIntervalTicks
                        .correctEntry(201, ValidationType.STRONG).get()),
                () -> assertEquals(0, config.performance.unsafeShotCacheTicks
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(5, config.performance.unsafeShotCacheTicks
                        .correctEntry(6, ValidationType.STRONG).get()));
    }
}
