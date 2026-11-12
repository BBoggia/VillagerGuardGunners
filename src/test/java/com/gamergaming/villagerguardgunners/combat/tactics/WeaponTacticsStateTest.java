package com.gamergaming.villagerguardgunners.combat.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class WeaponTacticsStateTest {
    private static final ResourceLocation RIFLE = ResourceLocation.fromNamespaceAndPath("test", "rifle");
    private static final ResourceLocation OTHER = ResourceLocation.fromNamespaceAndPath("test", "other");

    private final FakeInputs inputs = new FakeInputs();
    private final WeaponTacticsState tactics = new WeaponTacticsState(inputs);

    @Test
    void freshStateFiresAtNothingAndReportsConfiguredFallbacks() {
        assertFalse(tactics.hasValidFiringWindow(true, RIFLE));
        assertFalse(tactics.canFireAtDistanceSquared(true, RIFLE, 16.0D));
        assertEquals(16.0D, tactics.preferredApproachDistance(true, RIFLE));
        assertEquals(16.0D, tactics.preferredApproachDistance(false, RIFLE));
        assertEquals(4.0D, tactics.retreatDistance(true, RIFLE));
        assertEquals(5, tactics.requiredVisibleTicks(true, RIFLE));
        assertEquals(10.0D, tactics.aimToleranceDegrees(true, RIFLE));
        assertEquals(0.0D, tactics.minimumFiringRange(true, RIFLE));
        assertEquals(32.0F, tactics.maximumFiringRange(true, RIFLE));
    }

    @Test
    void readableGunOpensAWindowAndServesTheCachedEnvelope() {
        tactics.setEngagementRange(24.0F);
        tactics.cache(RIFLE, "rifle", true, WeaponTacticalTraits.NONE);
        TacticalEnvelope envelope = tactics.snapshot().envelope();

        assertTrue(tactics.hasValidFiringWindow(true, RIFLE));
        assertEquals(envelope.preferredApproachDistance(), tactics.preferredApproachDistance(true, RIFLE));
        assertEquals(envelope.retreatDistance(), tactics.retreatDistance(true, RIFLE));
        assertEquals(envelope.requiredVisibleTicks(), tactics.requiredVisibleTicks(true, RIFLE));
        assertEquals(envelope.aimToleranceDegrees(), tactics.aimToleranceDegrees(true, RIFLE));
        assertEquals(envelope.minimumFiringDistance(), tactics.minimumFiringRange(true, RIFLE));
        assertEquals((float) envelope.maximumFiringDistance(), tactics.maximumFiringRange(true, RIFLE));
        double inside = envelope.preferredApproachDistance();
        double outside = envelope.maximumFiringDistance() + 1.0D;
        assertTrue(tactics.canFireAtDistanceSquared(true, RIFLE, inside * inside));
        assertFalse(tactics.canFireAtDistanceSquared(true, RIFLE, outside * outside));
    }

    @Test
    void snapshotForAnotherGunOrAnUnboundWeaponIsIgnored() {
        tactics.setEngagementRange(24.0F);
        tactics.cache(RIFLE, "rifle", true, WeaponTacticalTraits.NONE);
        double inside = tactics.preferredApproachDistance(true, RIFLE);

        assertFalse(tactics.hasValidFiringWindow(true, OTHER));
        assertFalse(tactics.hasValidFiringWindow(true, null));
        assertFalse(tactics.hasValidFiringWindow(false, RIFLE));
        assertFalse(tactics.canFireAtDistanceSquared(false, RIFLE, inside * inside));
        assertEquals(4.0D, tactics.retreatDistance(false, RIFLE));
        assertEquals(16.0D, tactics.preferredApproachDistance(false, RIFLE),
                "An unbound weapon uses the configured fallback, not the last gun's range");
    }

    @Test
    void unreadableGunDataClosesTheWindowButKeepsBoundedRange() {
        tactics.setEngagementRange(24.0F);
        tactics.cache(RIFLE, "rifle", false, WeaponTacticalTraits.NONE);

        assertFalse(tactics.isReadableFor(RIFLE));
        assertFalse(tactics.hasValidFiringWindow(true, RIFLE));
        assertFalse(tactics.snapshot().envelope().validFiringWindow());
        assertEquals(24.0D, tactics.preferredApproachDistance(true, RIFLE));
    }

    @Test
    void engagementRangeIsBoundedByTheGuardsMaximumRange() {
        inputs.maximumRange = 12.0D;
        tactics.setEngagementRange(24.0F);
        tactics.cache(RIFLE, "rifle", false, WeaponTacticalTraits.NONE);

        assertEquals(12.0D, tactics.preferredApproachDistance(true, RIFLE));
        assertEquals(12.0F, tactics.maximumFiringRange(true, RIFLE));
    }

    @Test
    void invalidEffectiveRangeFallsBack() {
        tactics.setEngagementRange(Float.NaN);
        tactics.cache(RIFLE, "rifle", false, WeaponTacticalTraits.NONE);

        assertEquals(16.0D, tactics.preferredApproachDistance(true, RIFLE));
    }

    @Test
    void brokenProfileMappingFallsBackToDefaultsAndClosesTheWindow() {
        inputs.failTypeProfiles = true;
        tactics.setEngagementRange(24.0F);

        tactics.cache(RIFLE, "rifle", true, WeaponTacticalTraits.NONE);

        assertFalse(tactics.snapshot().metadataReadable());
        assertFalse(tactics.hasValidFiringWindow(true, RIFLE));
        assertEquals(
                WeaponTacticalResolver.resolve(null, "rifle", WeaponTacticalTraits.NONE).profile(),
                tactics.snapshot().resolution().profile());
    }

    @Test
    void brokenProfileDefinitionFallsBackToDefaultsAndClosesTheWindow() {
        inputs.failDefinition = true;
        tactics.setEngagementRange(24.0F);

        tactics.cache(RIFLE, "rifle", true, WeaponTacticalTraits.NONE);

        assertFalse(tactics.snapshot().metadataReadable());
        assertFalse(tactics.hasValidFiringWindow(true, RIFLE));
    }

    @Test
    void perGuardOverrideWinsOverTheGunsOwnProfile() {
        inputs.guardOverride = TacticalProfile.MARKSMAN;
        inputs.gunOverride = TacticalProfile.BREACHER;

        tactics.cache(RIFLE, "rifle", true, WeaponTacticalTraits.NONE);

        assertEquals(TacticalProfile.MARKSMAN, tactics.snapshot().resolution().profile());
    }

    @Test
    void explosiveChangeRebuildsTheSnapshotAndKeepsTheProjectileCount() {
        tactics.setEngagementRange(24.0F);
        tactics.cache(RIFLE, "rifle", true, new WeaponTacticalTraits(false, 8, 0.0D));
        CachedTacticalState before = tactics.snapshot();
        assertFalse(tactics.isExplosiveFor(RIFLE));

        tactics.updateExplosion(RIFLE, "rifle", true, 3.0D);

        assertNotSame(before, tactics.snapshot());
        assertTrue(tactics.isExplosiveFor(RIFLE));
        assertFalse(tactics.isExplosiveFor(OTHER));
        assertEquals(3.0D, tactics.snapshot().traits().explosionRadius());
        assertEquals(8, tactics.snapshot().traits().projectileCount());
        assertTrue(tactics.snapshot().metadataReadable());
    }

    @Test
    void unchangedExplosiveStateKeepsTheSameSnapshot() {
        tactics.cache(RIFLE, "rifle", true, new WeaponTacticalTraits(true, 1, 3.0D));
        CachedTacticalState before = tactics.snapshot();

        tactics.updateExplosion(RIFLE, "rifle", true, 3.0D);

        assertSame(before, tactics.snapshot());
    }

    @Test
    void explosiveStateIsReadAtMostOncePerTickUnlessForced() {
        inputs.gameTime = 10L;
        tactics.cache(RIFLE, "rifle", true, WeaponTacticalTraits.NONE);

        assertFalse(tactics.beginExplosionRefresh(false), "Caching already read it this tick");
        assertTrue(tactics.beginExplosionRefresh(true));
        inputs.gameTime = 11L;
        assertTrue(tactics.beginExplosionRefresh(false));
        assertFalse(tactics.beginExplosionRefresh(false));
    }

    @Test
    void resetForgetsTheGun() {
        tactics.setEngagementRange(24.0F);
        tactics.cache(RIFLE, "rifle", true, new WeaponTacticalTraits(true, 1, 3.0D));

        tactics.reset();

        assertFalse(tactics.isReadableFor(RIFLE));
        assertFalse(tactics.isExplosiveFor(RIFLE));
        assertFalse(tactics.hasValidFiringWindow(true, RIFLE));
        assertEquals(16.0D, tactics.preferredApproachDistance(true, RIFLE));
        assertTrue(tactics.beginExplosionRefresh(false));
    }

    private static final class FakeInputs implements WeaponTacticsState.Inputs {
        long gameTime;
        double maximumRange = 32.0D;
        TacticalProfile guardOverride;
        TacticalProfile gunOverride;
        boolean failTypeProfiles;
        boolean failDefinition;

        @Override
        public long gameTime() {
            return gameTime;
        }

        @Override
        public double maximumRange() {
            return maximumRange;
        }

        @Override
        public TacticalProfile guardOverride() {
            return guardOverride;
        }

        @Override
        public TacticalProfile gunOverride(ResourceLocation gunId) {
            return gunOverride;
        }

        @Override
        public Map<String, ? extends TacticalProfile> typeProfiles() {
            if (failTypeProfiles) {
                throw new IllegalStateException("broken type profiles");
            }
            return WeaponTacticalResolver.DEFAULT_TYPE_PROFILES;
        }

        @Override
        public TacticalProfileDefinition definition(TacticalProfile profile) {
            if (failDefinition) {
                throw new IllegalStateException("broken profile definition");
            }
            return TacticalProfileDefinition.defaultsFor(profile);
        }

        @Override
        public ExplosiveStandoffPolicy explosiveStandoffPolicy() {
            return ExplosiveStandoffPolicy.DEFAULT;
        }

        @Override
        public double closeRangeDistance() {
            return 4.0D;
        }

        @Override
        public int requiredVisibleTicks() {
            return 5;
        }

        @Override
        public double aimToleranceDegrees() {
            return 10.0D;
        }
    }
}
