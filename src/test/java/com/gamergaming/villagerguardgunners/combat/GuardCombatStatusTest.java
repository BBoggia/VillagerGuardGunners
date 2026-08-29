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
