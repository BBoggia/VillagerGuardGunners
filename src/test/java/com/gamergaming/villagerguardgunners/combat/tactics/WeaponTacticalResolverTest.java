package com.gamergaming.villagerguardgunners.combat.tactics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class WeaponTacticalResolverTest {

    @Test
    void defaultTaCZTypesMapToTheSixSupportedProfiles() {
        assertAll(
                () -> assertProfile("rifle", TacticalProfile.GENERALIST),
                () -> assertProfile("pistol", TacticalProfile.SKIRMISHER),
                () -> assertProfile("smg", TacticalProfile.SKIRMISHER),
                () -> assertProfile("shotgun", TacticalProfile.BREACHER),
                () -> assertProfile("sniper", TacticalProfile.MARKSMAN),
                () -> assertProfile("mg", TacticalProfile.SUPPORT),
                () -> assertProfile("rpg", TacticalProfile.DEMOLITIONS));
    }

    @Test
    void perGunOverrideWinsWithoutRemovingStructuralSafetyTraits() {
        WeaponTacticalTraits traits = new WeaponTacticalTraits(true, 8, 6.0D);

        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                TacticalProfile.SUPPORT,
                "shotgun",
                traits);

        assertAll(
                () -> assertEquals(TacticalProfile.SUPPORT, resolution.profile()),
                () -> assertEquals(
                        WeaponTacticalResolver.Source.GUN_OVERRIDE,
                        resolution.source()),
                () -> assertTrue(traits.explosive()),
                () -> assertTrue(traits.multiProjectile()));
    }

    @Test
    void perGuardOverridePrecedesGunOverridesAndTypeMappings() {
        WeaponTacticalTraits traits = new WeaponTacticalTraits(true, 8, 6.0D);

        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                TacticalProfile.MARKSMAN,
                TacticalProfile.BREACHER,
                "rpg",
                traits,
                Map.of("rpg", TacticalProfile.DEMOLITIONS));

        assertAll(
                () -> assertEquals(TacticalProfile.MARKSMAN, resolution.profile()),
                () -> assertEquals(
                        WeaponTacticalResolver.Source.PER_GUARD_ORDER,
                        resolution.source()),
                () -> assertTrue(traits.explosive()),
                () -> assertTrue(traits.multiProjectile()));
    }

    @Test
    void configurableTypeMappingPrecedesTraitBasedFallback() {
        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                null,
                "  PLASMA_CASTER  ",
                new WeaponTacticalTraits(true, 1, 2.0D),
                Map.of("plasma_caster", TacticalProfile.MARKSMAN));

        assertAll(
                () -> assertEquals(TacticalProfile.MARKSMAN, resolution.profile()),
                () -> assertEquals(
                        WeaponTacticalResolver.Source.TYPE_MAPPING,
                        resolution.source()),
                () -> assertEquals("plasma_caster", resolution.normalizedGunType()));
    }

    @Test
    void equivalentNormalizedAliasesRemainDeterministic() {
        Map<String, TacticalProfile> mappings = new LinkedHashMap<>();
        mappings.put("smg", TacticalProfile.SKIRMISHER);
        mappings.put(" SMG ", TacticalProfile.SKIRMISHER);

        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                null,
                "SmG",
                WeaponTacticalTraits.NONE,
                mappings);

        assertAll(
                () -> assertEquals(TacticalProfile.SKIRMISHER, resolution.profile()),
                () -> assertEquals(WeaponTacticalResolver.Source.TYPE_MAPPING, resolution.source()));
    }

    @Test
    void contradictoryNormalizedAliasesAreIgnoredInsteadOfUsingIterationOrder() {
        Map<String, TacticalProfile> mappings = new LinkedHashMap<>();
        mappings.put("smg", TacticalProfile.SKIRMISHER);
        mappings.put(" SMG ", TacticalProfile.MARKSMAN);

        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                null,
                "smg",
                WeaponTacticalTraits.NONE,
                mappings);

        assertAll(
                () -> assertEquals(TacticalProfile.GENERALIST, resolution.profile()),
                () -> assertEquals(WeaponTacticalResolver.Source.FALLBACK, resolution.source()));
    }

    @Test
    void unambiguousTraitsClassifyUnknownTypesBeforeGeneralFallback() {
        WeaponTacticalResolver.Resolution explosive = WeaponTacticalResolver.resolve(
                null,
                "custom_launcher",
                new WeaponTacticalTraits(true, 1, 3.0D),
                Map.of());
        WeaponTacticalResolver.Resolution pellet = WeaponTacticalResolver.resolve(
                null,
                "scatter_weapon",
                new WeaponTacticalTraits(false, 9, 0.0D),
                Map.of());

        assertAll(
                () -> assertEquals(TacticalProfile.DEMOLITIONS, explosive.profile()),
                () -> assertEquals(
                        WeaponTacticalResolver.Source.EXPLOSIVE_TRAIT,
                        explosive.source()),
                () -> assertEquals(TacticalProfile.BREACHER, pellet.profile()),
                () -> assertEquals(
                        WeaponTacticalResolver.Source.MULTI_PROJECTILE_TRAIT,
                        pellet.source()));
    }

    @Test
    void unknownOrMissingMetadataFallsBackToGeneralist() {
        WeaponTacticalResolver.Resolution unknown = WeaponTacticalResolver.resolve(
                null,
                "third_party_unknown",
                WeaponTacticalTraits.NONE);
        WeaponTacticalResolver.Resolution missing = WeaponTacticalResolver.resolve(
                null,
                null,
                null,
                null);

        assertAll(
                () -> assertEquals(TacticalProfile.GENERALIST, unknown.profile()),
                () -> assertEquals(WeaponTacticalResolver.Source.FALLBACK, unknown.source()),
                () -> assertEquals(TacticalProfile.GENERALIST, missing.profile()),
                () -> assertEquals(WeaponTacticalResolver.Source.FALLBACK, missing.source()),
                () -> assertEquals("", missing.normalizedGunType()));
    }

    @Test
    void traitsNormalizeMalformedPackValues() {
        WeaponTacticalTraits malformed = new WeaponTacticalTraits(false, -20, Double.NaN);
        WeaponTacticalTraits radiusImpliesExplosion = new WeaponTacticalTraits(false, 50_000, 2.5D);

        assertAll(
                () -> assertEquals(1, malformed.projectileCount()),
                () -> assertFalse(malformed.explosive()),
                () -> assertEquals(0.0D, malformed.explosionRadius()),
                () -> assertEquals(1_024, radiusImpliesExplosion.projectileCount()),
                () -> assertTrue(radiusImpliesExplosion.multiProjectile()),
                () -> assertTrue(radiusImpliesExplosion.explosive()));
    }

    private static void assertProfile(String gunType, TacticalProfile expectedProfile) {
        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                null,
                gunType,
                WeaponTacticalTraits.NONE);
        assertEquals(expectedProfile, resolution.profile());
        assertEquals(WeaponTacticalResolver.Source.TYPE_MAPPING, resolution.source());
    }
}
