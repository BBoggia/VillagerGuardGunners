package com.gamergaming.villagerguardgunners.combat.tactics;

import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * One guard's cached tactical envelope for the gun it holds: where to stand,
 * how far it may fire, and how steady its aim must be.
 *
 * <p>A weapon driver feeds this what it read from its gun mod (effective
 * range, gun type, explosive and multi-projectile traits) at a low frequency.
 * The combat loop then reads only the immutable snapshot. Unreadable or
 * contradictory inputs close the firing window instead of widening it.</p>
 *
 * <p>Accessors take {@code weaponBound} and {@code gunId} from the driver:
 * whether the guard still holds the gun the driver last read without error,
 * and that gun's identity. A snapshot cached for a different gun is ignored.</p>
 */
public final class WeaponTacticsState {
    private static final float FALLBACK_ENGAGEMENT_RANGE = 16.0F;
    private static final float MIN_ENGAGEMENT_RANGE = 8.0F;
    private static final float DEFAULT_MAX_ENGAGEMENT_RANGE = 32.0F;
    private static final double DEFAULT_RETREAT_DISTANCE = 4.0D;
    private static final int DEFAULT_REQUIRED_VISIBLE_TICKS = 5;
    private static final double DEFAULT_AIM_TOLERANCE_DEGREES = 10.0D;

    /** Orders and configuration the envelope is resolved against. Read on demand, never cached here. */
    public interface Inputs {
        long gameTime();

        /** Server maximum engagement range after the guard's orders are applied. */
        double maximumRange();

        @Nullable
        TacticalProfile guardOverride();

        /** Malformed configuration may throw; the state then falls back to defaults. */
        @Nullable
        TacticalProfile gunOverride(@Nullable ResourceLocation gunId);

        /** Malformed configuration may throw; the state then falls back to defaults. */
        Map<String, ? extends TacticalProfile> typeProfiles();

        TacticalProfileDefinition definition(TacticalProfile profile);

        ExplosiveStandoffPolicy explosiveStandoffPolicy();

        double closeRangeDistance();

        int requiredVisibleTicks();

        double aimToleranceDegrees();
    }

    private final Inputs inputs;
    private float engagementRange;
    private CachedTacticalState cachedTacticalState;
    private long dynamicExplosionRefreshGameTick = Long.MIN_VALUE;

    public WeaponTacticsState(Inputs inputs) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        reset();
    }

    /** Reads the guard's effective orders and the mod configuration live on every call. */
    public static WeaponTacticsState forGuard(Guard guard, VillagerGuardGunnersConfig config) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(config, "config");
        return new WeaponTacticsState(new Inputs() {
            @Override
            public long gameTime() {
                return guard.level().getGameTime();
            }

            @Override
            public double maximumRange() {
                return GuardRoleResolver.effectiveOrders(guard)
                        .resolveMaximumRange(config.combat.maximumEngagementRange.get());
            }

            @Override
            public TacticalProfile guardOverride() {
                return GuardRoleResolver.effectiveOrders(guard).tacticalProfileOverride();
            }

            @Override
            public TacticalProfile gunOverride(ResourceLocation gunId) {
                return config.tactics.gunOverrides.get().get(gunId);
            }

            @Override
            public Map<String, ? extends TacticalProfile> typeProfiles() {
                return config.tactics.typeProfiles.get();
            }

            @Override
            public TacticalProfileDefinition definition(TacticalProfile profile) {
                return config.tactics.definition(profile);
            }

            @Override
            public ExplosiveStandoffPolicy explosiveStandoffPolicy() {
                return config.safety.explosiveStandoffPolicy();
            }

            @Override
            public double closeRangeDistance() {
                return config.combat.closeRangeDistance.get();
            }

            @Override
            public int requiredVisibleTicks() {
                return config.combat.requiredVisibleTicks.get();
            }

            @Override
            public double aimToleranceDegrees() {
                return config.combat.aimToleranceDegrees.get();
            }
        });
    }

    // Driver side: what was read from the gun mod

    /** The weapon changed or its data was lost. Nothing may fire until the next {@link #cache}. */
    public void reset() {
        useFallbackEngagementRange();
        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                null,
                null,
                WeaponTacticalTraits.NONE);
        cachedTacticalState = new CachedTacticalState(
                null,
                false,
                resolution,
                WeaponTacticalTraits.NONE,
                disableFiringWindow(resolveDefaultTacticalEnvelope(
                        TacticalProfile.GENERALIST,
                        WeaponTacticalTraits.NONE)));
        dynamicExplosionRefreshGameTick = Long.MIN_VALUE;
    }

    public void useFallbackEngagementRange() {
        engagementRange = normalizeEngagementRange(
                FALLBACK_ENGAGEMENT_RANGE,
                (float) inputs.maximumRange());
    }

    /** Stores the gun's effective range, bounded by the guard's maximum range. */
    public void setEngagementRange(float effectiveRange) {
        engagementRange = normalizeEngagementRange(effectiveRange, (float) inputs.maximumRange());
    }

    /**
     * Rebuilds the snapshot. Called beside a driver's low-frequency data
     * refresh, never from the firing loop.
     *
     * @param metadataReadable false when any input the driver read was missing or invalid
     */
    public void cache(
            @Nullable ResourceLocation gunId,
            @Nullable String gunType,
            boolean metadataReadable,
            WeaponTacticalTraits traits) {
        TacticalProfile guardOverride = inputs.guardOverride();
        WeaponTacticalResolver.Resolution resolution;
        try {
            TacticalProfile gunOverride = inputs.gunOverride(gunId);
            resolution = WeaponTacticalResolver.resolve(
                    guardOverride,
                    gunOverride,
                    gunType,
                    traits,
                    inputs.typeProfiles());
        } catch (RuntimeException exception) {
            metadataReadable = false;
            resolution = WeaponTacticalResolver.resolve(
                    guardOverride,
                    null,
                    gunType,
                    traits,
                    WeaponTacticalResolver.DEFAULT_TYPE_PROFILES);
        }

        TacticalEnvelope envelope;
        try {
            envelope = resolveTacticalEnvelope(resolution.profile(), traits);
        } catch (RuntimeException exception) {
            metadataReadable = false;
            envelope = resolveDefaultTacticalEnvelope(resolution.profile(), traits);
        }
        if (!metadataReadable) {
            envelope = disableFiringWindow(envelope);
        }
        cachedTacticalState = new CachedTacticalState(
                gunId,
                metadataReadable,
                resolution,
                traits,
                envelope);
        dynamicExplosionRefreshGameTick = inputs.gameTime();
    }

    /**
     * Limits re-reading of explosive state to once per game tick.
     *
     * @return false when it was already read this tick and {@code force} is not set
     */
    public boolean beginExplosionRefresh(boolean force) {
        long gameTime = inputs.gameTime();
        if (!force && dynamicExplosionRefreshGameTick == gameTime) {
            return false;
        }
        dynamicExplosionRefreshGameTick = gameTime;
        return true;
    }

    /** Rebuilds the snapshot only when the gun's explosive state actually changed. */
    public void updateExplosion(
            @Nullable ResourceLocation gunId,
            @Nullable String gunType,
            boolean explosive,
            double explosionRadius) {
        WeaponTacticalTraits previous = cachedTacticalState.traits();
        if (previous.explosive() != explosive
                || Double.compare(previous.explosionRadius(), explosionRadius) != 0) {
            WeaponTacticalTraits updated = new WeaponTacticalTraits(
                    explosive,
                    previous.projectileCount(),
                    explosionRadius);
            cache(gunId, gunType, cachedTacticalState.metadataReadable(), updated);
        }
    }

    // Combat loop side: cheap reads of the snapshot

    public CachedTacticalState snapshot() {
        return cachedTacticalState;
    }

    /** True when the snapshot was built from readable data for this exact gun. */
    public boolean isReadableFor(@Nullable ResourceLocation gunId) {
        return cachedTacticalState.metadataReadable()
                && cachedTacticalState.gunId() != null
                && cachedTacticalState.gunId().equals(gunId);
    }

    public boolean isExplosiveFor(@Nullable ResourceLocation gunId) {
        return cachedTacticalState.gunId() != null
                && cachedTacticalState.gunId().equals(gunId)
                && cachedTacticalState.traits().explosive();
    }

    private boolean hasUsableCachedTacticalState(boolean weaponBound, @Nullable ResourceLocation gunId) {
        return weaponBound && isReadableFor(gunId);
    }

    /**
     * Distance at which movement stops approaching. Without a usable snapshot
     * this falls back to the gun's bounded effective range.
     */
    public double preferredApproachDistance(boolean weaponBound, @Nullable ResourceLocation gunId) {
        if (hasUsableCachedTacticalState(weaponBound, gunId)) {
            return cachedTacticalState.envelope().preferredApproachDistance();
        }
        return compatibilityEngagementRange(weaponBound);
    }

    /** Distance at which retreat begins. */
    public double retreatDistance(boolean weaponBound, @Nullable ResourceLocation gunId) {
        if (hasUsableCachedTacticalState(weaponBound, gunId)) {
            return cachedTacticalState.envelope().retreatDistance();
        }
        return configuredFallbackRetreatDistance();
    }

    /** Continuous-visibility requirement before firing. */
    public int requiredVisibleTicks(boolean weaponBound, @Nullable ResourceLocation gunId) {
        if (hasUsableCachedTacticalState(weaponBound, gunId)) {
            return cachedTacticalState.envelope().requiredVisibleTicks();
        }
        return configuredFallbackRequiredVisibleTicks();
    }

    /** Maximum yaw and pitch error. */
    public double aimToleranceDegrees(boolean weaponBound, @Nullable ResourceLocation gunId) {
        if (hasUsableCachedTacticalState(weaponBound, gunId)) {
            return cachedTacticalState.envelope().aimToleranceDegrees();
        }
        return configuredFallbackAimToleranceDegrees();
    }

    /** Lower firing bound, including explosive standoff. */
    public double minimumFiringRange(boolean weaponBound, @Nullable ResourceLocation gunId) {
        if (hasUsableCachedTacticalState(weaponBound, gunId)) {
            return cachedTacticalState.envelope().minimumFiringDistance();
        }
        return 0.0D;
    }

    /** The profile cap, or the configured cap before the gun's data is ready. */
    public float maximumFiringRange(boolean weaponBound, @Nullable ResourceLocation gunId) {
        if (hasUsableCachedTacticalState(weaponBound, gunId)) {
            return (float) cachedTacticalState.envelope().maximumFiringDistance();
        }
        return normalizeMaximumFiringRange((float) inputs.maximumRange());
    }

    /** True only when current, readable data produced a coherent window. */
    public boolean hasValidFiringWindow(boolean weaponBound, @Nullable ResourceLocation gunId) {
        return hasUsableCachedTacticalState(weaponBound, gunId)
                && cachedTacticalState.envelope().validFiringWindow();
    }

    /** Hot-loop gate. Missing, unreadable, stale, or contradictory data returns false. */
    public boolean canFireAtDistanceSquared(
            boolean weaponBound,
            @Nullable ResourceLocation gunId,
            double distanceSquared) {
        return hasValidFiringWindow(weaponBound, gunId)
                && cachedTacticalState.envelope().canFireAtDistanceSquared(distanceSquared);
    }

    public static float normalizeMaximumFiringRange(float range) {
        return Float.isFinite(range) && range >= MIN_ENGAGEMENT_RANGE
                ? range
                : DEFAULT_MAX_ENGAGEMENT_RANGE;
    }

    public static float normalizeEngagementRange(float range, float maximumRange) {
        float boundedMaximum = Float.isFinite(maximumRange) && maximumRange >= MIN_ENGAGEMENT_RANGE
                ? maximumRange
                : DEFAULT_MAX_ENGAGEMENT_RANGE;
        if (!Float.isFinite(range) || range <= 0.0F) {
            return Mth.clamp(FALLBACK_ENGAGEMENT_RANGE, MIN_ENGAGEMENT_RANGE, boundedMaximum);
        }
        return Mth.clamp(range, MIN_ENGAGEMENT_RANGE, boundedMaximum);
    }

    private float compatibilityEngagementRange(boolean weaponBound) {
        if (!weaponBound) {
            return normalizeEngagementRange(
                    FALLBACK_ENGAGEMENT_RANGE,
                    (float) inputs.maximumRange());
        }
        float maximumRange = (float) inputs.maximumRange();
        return Float.isFinite(maximumRange)
                ? Math.min(engagementRange, maximumRange)
                : engagementRange;
    }

    private TacticalEnvelope resolveTacticalEnvelope(
            TacticalProfile profile,
            WeaponTacticalTraits traits) {
        return TacticalEnvelope.resolve(
                inputs.definition(profile),
                traits,
                inputs.explosiveStandoffPolicy(),
                engagementRange,
                inputs.maximumRange(),
                inputs.closeRangeDistance(),
                inputs.requiredVisibleTicks(),
                inputs.aimToleranceDegrees());
    }

    private TacticalEnvelope resolveDefaultTacticalEnvelope(
            TacticalProfile profile,
            WeaponTacticalTraits traits) {
        return TacticalEnvelope.resolve(
                TacticalProfileDefinition.defaultsFor(profile),
                traits,
                inputs.explosiveStandoffPolicy(),
                engagementRange,
                inputs.maximumRange(),
                inputs.closeRangeDistance(),
                inputs.requiredVisibleTicks(),
                inputs.aimToleranceDegrees());
    }

    private static TacticalEnvelope disableFiringWindow(TacticalEnvelope envelope) {
        return new TacticalEnvelope(
                envelope.profile(),
                envelope.traits(),
                envelope.minimumFiringDistance(),
                envelope.retreatDistance(),
                envelope.preferredApproachDistance(),
                envelope.maximumFiringDistance(),
                envelope.requiredVisibleTicks(),
                envelope.aimToleranceDegrees(),
                false);
    }

    private double configuredFallbackRetreatDistance() {
        double retreatDistance = inputs.closeRangeDistance();
        return Double.isFinite(retreatDistance)
                        && retreatDistance >= 1.0D
                        && retreatDistance <= 8.0D
                ? retreatDistance
                : DEFAULT_RETREAT_DISTANCE;
    }

    private int configuredFallbackRequiredVisibleTicks() {
        int requiredVisibleTicks = inputs.requiredVisibleTicks();
        return requiredVisibleTicks >= 0 && requiredVisibleTicks <= 40
                ? requiredVisibleTicks
                : DEFAULT_REQUIRED_VISIBLE_TICKS;
    }

    private double configuredFallbackAimToleranceDegrees() {
        double aimToleranceDegrees = inputs.aimToleranceDegrees();
        return Double.isFinite(aimToleranceDegrees)
                        && aimToleranceDegrees >= 1.0D
                        && aimToleranceDegrees <= 45.0D
                ? aimToleranceDegrees
                : DEFAULT_AIM_TOLERANCE_DEGREES;
    }
}
