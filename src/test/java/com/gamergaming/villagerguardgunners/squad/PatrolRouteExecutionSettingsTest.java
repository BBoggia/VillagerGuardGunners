package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PatrolRouteExecutionSettingsTest {
    @Test
    void defaultsPreserveTheReviewedStepTenBehavior() {
        PatrolRouteExecutionSettings settings = PatrolRouteExecutionSettings.DEFAULT;

        assertAll(
                () -> assertTrue(settings.enabled()),
                () -> assertEquals(0.8D, settings.movementSpeed()),
                () -> assertEquals(20, settings.waypointDwellTicks()),
                () -> assertEquals(3, settings.maximumFailuresPerWaypoint()),
                () -> assertFalse(PatrolRouteExecutionSettings.DISABLED.enabled()));
    }

    @Test
    void constructorEnforcesEveryAbsoluteBound() {
        assertAll(
                () -> new PatrolRouteExecutionSettings(true, 0.1D, 0, 1),
                () -> new PatrolRouteExecutionSettings(true, 2.0D, 1_200, 8),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, 0.09D, 20, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, 2.01D, 20, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, Double.NaN, 20, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, 0.8D, -1, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, 0.8D, 1_201, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, 0.8D, 20, 0)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new PatrolRouteExecutionSettings(true, 0.8D, 20, 9)));
    }
}
