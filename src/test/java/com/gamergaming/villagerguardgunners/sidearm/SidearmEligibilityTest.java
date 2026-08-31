package com.gamergaming.villagerguardgunners.sidearm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.sidearm.SidearmEligibility.EligibilityStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SidearmEligibilityTest {
    @Test
    void defaultPistolTypeIsEligibleAfterNormalization() {
        EligibilityStatus status = evaluate(
                "tacz:glock_17",
                "  PiStOl  ",
                false,
                Set.of(),
                Set.of());

        assertEquals(EligibilityStatus.ELIGIBLE_BY_GUN_TYPE, status);
        assertTrue(status.eligible());
    }

    @Test
    void exactGunInclusionSupportsNonstandardThirdPartyTypes() {
        EligibilityStatus status = evaluate(
                "example:compact_backup",
                "personal_defense_weapon",
                false,
                Set.of("example:compact_backup"),
                Set.of());

        assertEquals(EligibilityStatus.ELIGIBLE_BY_GUN_ID, status);
    }

    @Test
    void exactExclusionWinsOverBothInclusionRoutes() {
        EligibilityStatus status = evaluate(
                "example:unsafe_pistol",
                "pistol",
                false,
                Set.of("example:unsafe_pistol"),
                Set.of("example:unsafe_pistol"));

        assertEquals(EligibilityStatus.EXCLUDED_GUN, status);
        assertFalse(status.eligible());
    }

    @Test
    void explosiveSafetyCannotBeBypassedByAnExactInclusion() {
        EligibilityStatus status = evaluate(
                "example:explosive_pistol",
                "pistol",
                true,
                Set.of("example:explosive_pistol"),
                Set.of());

        assertEquals(EligibilityStatus.EXPLOSIVE_WEAPON, status);
    }

    @Test
    void unreadableExplosionStateFailsClosedBeforeInclusion() {
        EligibilityStatus status = SidearmEligibility.evaluate(
                "example:scripted_pistol",
                "pistol",
                true,
                1,
                true,
                false,
                false,
                SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                Set.of("example:scripted_pistol"),
                Set.of());

        assertEquals(EligibilityStatus.EXPLOSIVE_STATE_UNREADABLE, status);
    }

    @Test
    void nonGunInvalidCountAndUnavailableDefinitionFailClosed() {
        assertEquals(
                EligibilityStatus.NOT_GUN,
                SidearmEligibility.evaluate(
                        "minecraft:stick",
                        "pistol",
                        false,
                        1,
                        true,
                        true,
                        false,
                        SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                        Set.of(),
                        Set.of()));
        assertEquals(
                EligibilityStatus.INVALID_STACK_COUNT,
                SidearmEligibility.evaluate(
                        "tacz:glock_17",
                        "pistol",
                        true,
                        2,
                        true,
                        true,
                        false,
                        SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                        Set.of(),
                        Set.of()));
        assertEquals(
                EligibilityStatus.DEFINITION_UNAVAILABLE,
                SidearmEligibility.evaluate(
                        null,
                        "pistol",
                        true,
                        1,
                        false,
                        true,
                        false,
                        SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                        Set.of(),
                        Set.of()));
    }

    @Test
    void unsupportedAndBlankTypesRemainIneligible() {
        assertEquals(
                EligibilityStatus.UNSUPPORTED_GUN_TYPE,
                evaluate("tacz:m4a1", "rifle", false, Set.of(), Set.of()));
        assertEquals(
                EligibilityStatus.UNSUPPORTED_GUN_TYPE,
                evaluate("example:unknown", "   ", false, Set.of(), Set.of()));
    }

    private static EligibilityStatus evaluate(
            String gunId,
            String gunType,
            boolean explosive,
            Set<String> additionalGunIds,
            Set<String> excludedGunIds) {
        return SidearmEligibility.evaluate(
                gunId,
                gunType,
                true,
                1,
                true,
                true,
                explosive,
                SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                additionalGunIds,
                excludedGunIds);
    }
}
