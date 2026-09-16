package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus.WeaponActivity;
import org.junit.jupiter.api.Test;

final class GuardCombatStatusTest {
    @Test
    void explicitNetworkIdsRoundTripAndUnknownValuesFailClosed() {
        for (GuardCombatStatus status : GuardCombatStatus.values()) {
            assertEquals(status, GuardCombatStatus.fromNetworkId(status.networkId()));
        }
        assertAll(
                () -> assertEquals(
                        GuardCombatStatus.UNKNOWN,
                        GuardCombatStatus.fromNetworkId(-1)),
                () -> assertEquals(
                        GuardCombatStatus.UNKNOWN,
                        GuardCombatStatus.fromNetworkId(Integer.MAX_VALUE)));
    }

    @Test
    void playerRelevantBlockersWinInDeterministicOrder() {
        assertAll(
                () -> assertEquals(
                        GuardCombatStatus.HOLDING_FIRE,
                        resolve(false, true, true, true, true, true, false, true,
                                false, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.WEAPON_RESTRICTED,
                        GuardCombatStatus.resolveActiveCombat(
                                true,
                                false,
                                true,
                                true,
                                true,
                                true,
                                true,
                                false,
                                true,
                                false,
                                false,
                                WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.OUT_OF_AMMO,
                        resolve(true, true, true, true, true, true, false, true,
                                false, false, WeaponActivity.OUT_OF_AMMO)),
                () -> assertEquals(
                        GuardCombatStatus.RELOADING,
                        resolve(true, true, true, true, true, true, false, false,
                                false, false, WeaponActivity.RELOADING)),
                () -> assertEquals(
                        GuardCombatStatus.INVALID_FIRING_WINDOW,
                        resolve(true, true, true, true, true, false, false, false,
                                false, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.TOO_CLOSE,
                        resolve(true, true, true, true, true, true, true, true,
                                false, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.OUT_OF_RANGE,
                        resolve(true, true, true, true, true, true, false, false,
                                false, false, WeaponActivity.READY)));
    }

    @Test
    void aimingSafetyAndNativeCadenceStatesRemainDistinct() {
        assertAll(
                () -> assertEquals(
                        GuardCombatStatus.ACQUIRING_TARGET,
                        resolve(true, false, false, false, false, true, false, true,
                                false, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.NO_LINE_OF_SIGHT,
                        resolve(true, true, false, false, false, true, false, true,
                                false, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.AIMING,
                        resolve(true, true, true, true, false, true, false, true,
                                false, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.UNSAFE_SHOT,
                        resolve(true, true, true, true, true, true, false, true,
                                true, false, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.REPOSITIONING,
                        resolve(true, true, true, true, true, true, false, true,
                                true, true, WeaponActivity.READY)),
                () -> assertEquals(
                        GuardCombatStatus.CHARGING,
                        resolve(true, true, true, true, true, true, false, true,
                                false, false, WeaponActivity.CHARGING)),
                () -> assertEquals(
                        GuardCombatStatus.COOLING_DOWN,
                        resolve(true, true, true, true, true, true, false, true,
                                false, false, WeaponActivity.COOLING_DOWN)),
                () -> assertEquals(
                        GuardCombatStatus.READY,
                        resolve(true, true, true, true, true, true, false, true,
                                false, false, WeaponActivity.READY)));
    }

    @Test
    void everyRefusalHasAPlayerFacingExplanationBeforeNativeCooldown() {
        var expected = java.util.Map.ofEntries(
                java.util.Map.entry(GuardShotSafety.Status.UNSUPPORTED_TRAJECTORY, GuardCombatStatus.UNSUPPORTED_GUN),
                java.util.Map.entry(GuardShotSafety.Status.INVALID_AIM, GuardCombatStatus.WEAPON_ERROR),
                java.util.Map.entry(GuardShotSafety.Status.INVALID_BALLISTICS, GuardCombatStatus.WEAPON_ERROR),
                java.util.Map.entry(GuardShotSafety.Status.EXCESSIVE_ENVELOPE, GuardCombatStatus.SAFETY_CHECK_LIMIT),
                java.util.Map.entry(GuardShotSafety.Status.ENTITY_LIMIT, GuardCombatStatus.SAFETY_CHECK_LIMIT),
                java.util.Map.entry(GuardShotSafety.Status.BLOCK_LIMIT, GuardCombatStatus.SAFETY_CHECK_LIMIT),
                java.util.Map.entry(GuardShotSafety.Status.STALE_AIM, GuardCombatStatus.SAFETY_CHECK_UNAVAILABLE),
                java.util.Map.entry(GuardShotSafety.Status.INVALID_WORLD, GuardCombatStatus.SAFETY_CHECK_UNAVAILABLE),
                java.util.Map.entry(GuardShotSafety.Status.UNLOADED_IMPACT_AREA, GuardCombatStatus.SAFETY_CHECK_UNAVAILABLE),
                java.util.Map.entry(GuardShotSafety.Status.TARGET_IS_PROTECTED, GuardCombatStatus.HOLDING_FIRE),
                java.util.Map.entry(GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH, GuardCombatStatus.UNSAFE_SHOT),
                java.util.Map.entry(GuardShotSafety.Status.EXPLOSIVE_ENTITY_IMPACT, GuardCombatStatus.UNSAFE_SHOT),
                java.util.Map.entry(GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT, GuardCombatStatus.UNSAFE_SHOT),
                java.util.Map.entry(GuardShotSafety.Status.UNSAFE_FUSE, GuardCombatStatus.UNSAFE_SHOT));
        for (var status : GuardShotSafety.Status.values()) {
            assertEquals(expected.getOrDefault(status, GuardCombatStatus.COOLING_DOWN),
                    GuardCombatStatus.resolveActiveCombat(true, true, true, true, true, true,
                            true, false, true, status, false, WeaponActivity.COOLING_DOWN), status.name());
        }
    }

    @Test
    void movementDoesNotMislabelLimitsOrUnsupportedGunsAsRepositioning() {
        assertEquals(GuardCombatStatus.UNSUPPORTED_GUN,
                GuardCombatStatus.shotRefusalStatus(GuardShotSafety.Status.UNSUPPORTED_TRAJECTORY, true));
        assertEquals(GuardCombatStatus.SAFETY_CHECK_LIMIT,
                GuardCombatStatus.shotRefusalStatus(GuardShotSafety.Status.ENTITY_LIMIT, true));
        assertEquals(GuardCombatStatus.REPOSITIONING,
                GuardCombatStatus.shotRefusalStatus(GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH, true));
    }

    private static GuardCombatStatus resolve(
            boolean mayEngage,
            boolean trackingCurrentTarget,
            boolean cachedLineOfSight,
            boolean visibilityRequirementMet,
            boolean aimRequirementMet,
            boolean validFiringWindow,
            boolean belowMinimumRange,
            boolean withinMaximumRange,
            boolean unsafeShot,
            boolean repositioning,
            WeaponActivity weaponActivity) {
        return GuardCombatStatus.resolveActiveCombat(
                mayEngage,
                true,
                trackingCurrentTarget,
                cachedLineOfSight,
                visibilityRequirementMet,
                aimRequirementMet,
                validFiringWindow,
                belowMinimumRange,
                withinMaximumRange,
                unsafeShot,
                repositioning,
                weaponActivity);
    }
}
