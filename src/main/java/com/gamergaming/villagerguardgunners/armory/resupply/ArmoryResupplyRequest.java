package com.gamergaming.villagerguardgunners.armory.resupply;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable identity and demand snapshot for one bounded resupply attempt.
 *
 * <p>This record never authorizes a transfer. Candidate selection and the
 * final transaction must revalidate the live guard, weapon role, TaCZ gun,
 * Armory access, compatible stack, and accepted quantity.</p>
 */
public record ArmoryResupplyRequest(
        int contractVersion,
        long generation,
        UUID guardId,
        ResourceLocation dimension,
        BlockPos origin,
        @Nullable UUID squadId,
        ArmoryResupplyWeaponRole weaponRole,
        ResourceLocation gunId,
        ArmoryResupplyUrgency urgency,
        int magazineCapacity,
        int observedLoadedAmmo,
        long observedReserveUnits,
        long targetReserveUnits,
        long requestedUnits,
        long createdAtGameTick,
        long expiresAtGameTick) {
    public static final int CURRENT_CONTRACT_VERSION = 1;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public ArmoryResupplyRequest {
        if (contractVersion != CURRENT_CONTRACT_VERSION) {
            throw new IllegalArgumentException("Unsupported Armory request contract version");
        }
        if (generation < 0L) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        guardId = requireNonNil(guardId, "guardId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        origin = Objects.requireNonNull(origin, "origin").immutable();
        if (squadId != null) {
            squadId = requireNonNil(squadId, "squadId");
        }
        weaponRole = Objects.requireNonNull(weaponRole, "weaponRole");
        gunId = Objects.requireNonNull(gunId, "gunId");
        urgency = Objects.requireNonNull(urgency, "urgency");
        if (magazineCapacity < 0
                || observedLoadedAmmo < 0
                || observedReserveUnits < 0L
                || targetReserveUnits < 0L
                || requestedUnits <= 0L
                || requestedUnits > ArmoryResupplyLimits.HARD_MAX_REQUESTED_UNITS) {
            throw new IllegalArgumentException("Invalid Armory request ammunition quantities");
        }
        long deficit = targetReserveUnits <= observedReserveUnits
                ? 0L
                : targetReserveUnits - observedReserveUnits;
        long boundedDeficit = Math.min(
                deficit,
                ArmoryResupplyLimits.HARD_MAX_REQUESTED_UNITS);
        ArmoryResupplyUrgency expectedUrgency = observedLoadedAmmo == 0
                        && observedReserveUnits == 0L
                ? ArmoryResupplyUrgency.OUT_OF_AMMO
                : ArmoryResupplyUrgency.LOW_RESERVE;
        if (requestedUnits != boundedDeficit || urgency != expectedUrgency) {
            throw new IllegalArgumentException("Contradictory Armory request ammunition demand");
        }
        if (createdAtGameTick < 0L || expiresAtGameTick <= createdAtGameTick) {
            throw new IllegalArgumentException("Invalid Armory request lifetime");
        }
        long lifetime = expiresAtGameTick - createdAtGameTick;
        if (lifetime <= 0L
                || lifetime > ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS) {
            throw new IllegalArgumentException("Armory request lifetime exceeds the hard limit");
        }
    }

    public static ArmoryResupplyRequest create(
            long generation,
            UUID guardId,
            ResourceLocation dimension,
            BlockPos origin,
            @Nullable UUID squadId,
            ArmoryResupplyNeed.Assessment need,
            long createdAtGameTick,
            int lifetimeTicks) {
        Objects.requireNonNull(need, "need");
        if (!need.requestRequired()) {
            throw new IllegalArgumentException("A request requires an actionable ammunition need");
        }
        if (lifetimeTicks <= 0
                || lifetimeTicks > ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS
                || createdAtGameTick < 0L
                || createdAtGameTick > Long.MAX_VALUE - lifetimeTicks) {
            throw new IllegalArgumentException("Invalid Armory request lifetime");
        }
        return new ArmoryResupplyRequest(
                CURRENT_CONTRACT_VERSION,
                generation,
                guardId,
                dimension,
                origin,
                squadId,
                need.weaponRole(),
                need.gunId(),
                need.urgency(),
                need.magazineCapacity(),
                need.observedLoadedAmmo(),
                need.observedReserveUnits(),
                need.targetReserveUnits(),
                need.requestedUnits(),
                createdAtGameTick,
                createdAtGameTick + lifetimeTicks);
    }

    public boolean isExpired(long gameTick) {
        return gameTick < createdAtGameTick || gameTick >= expiresAtGameTick;
    }

    public boolean matchesLiveWeapon(
            ResourceLocation liveDimension,
            ArmoryResupplyWeaponRole liveRole,
            ResourceLocation liveGunId) {
        return dimension.equals(liveDimension)
                && weaponRole == liveRole
                && gunId.equals(liveGunId);
    }

    public RequestKey key() {
        return new RequestKey(guardId, generation, createdAtGameTick);
    }

    private static UUID requireNonNil(UUID value, String name) {
        Objects.requireNonNull(value, name);
        if (NIL_UUID.equals(value)) {
            throw new IllegalArgumentException(name + " may not be the nil UUID");
        }
        return value;
    }

    public record RequestKey(UUID guardId, long generation, long createdAtGameTick) {
        public RequestKey {
            guardId = requireNonNil(guardId, "guardId");
            if (generation < 0L || createdAtGameTick < 0L) {
                throw new IllegalArgumentException(
                        "generation and createdAtGameTick must not be negative");
            }
        }
    }
}
