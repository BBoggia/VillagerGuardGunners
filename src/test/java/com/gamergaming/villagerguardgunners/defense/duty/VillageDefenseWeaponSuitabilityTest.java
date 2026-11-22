package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseWeaponSuitability.WeaponClass;
import org.junit.jupiter.api.Test;

final class VillageDefenseWeaponSuitabilityTest {
    @Test
    void rangedRequirementsNeverAcceptMeleeOrUnarmedGuards() {
        assertAll(
                () -> assertTrue(VillageDefenseWeaponSuitability.meets(
                        WeaponClass.RANGED,
                        VillageDefenseDutyWeaponRequirement.RANGED)),
                () -> assertFalse(VillageDefenseWeaponSuitability.meets(
                        WeaponClass.MELEE,
                        VillageDefenseDutyWeaponRequirement.RANGED)),
                () -> assertTrue(VillageDefenseWeaponSuitability.meets(
                        WeaponClass.MELEE,
                        VillageDefenseDutyWeaponRequirement.ANY)),
                () -> assertFalse(VillageDefenseWeaponSuitability.meets(
                        WeaponClass.UNARMED,
                        VillageDefenseDutyWeaponRequirement.ANY)));
    }
}
