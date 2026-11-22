package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import java.util.Objects;

/** Immutable, transient ownership of part of one Armory slot. */
public record ArmoryAmmoReservation(
        ArmoryResupplyRequest request,
        ArmoryReservationTarget target,
        ArmorySupplyFingerprint fingerprint,
        AmmoCompatibility.AmmoKind kind,
        int reservedItems,
        long reservedUnits,
        long createdAtGameTick,
        long expiresAtGameTick) {
    public ArmoryAmmoReservation {
        request = Objects.requireNonNull(request, "request");
        target = Objects.requireNonNull(target, "target");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        kind = Objects.requireNonNull(kind, "kind");
        if (!request.dimension().equals(target.dimension())
                || kind == AmmoCompatibility.AmmoKind.NONE
                || reservedItems <= 0
                || reservedUnits <= 0L
                || (kind == AmmoCompatibility.AmmoKind.LOOSE
                        && reservedUnits != reservedItems)
                || (kind == AmmoCompatibility.AmmoKind.BOX && reservedItems != 1)
                || createdAtGameTick < request.createdAtGameTick()
                || createdAtGameTick >= request.expiresAtGameTick()
                || expiresAtGameTick <= createdAtGameTick
                || expiresAtGameTick > request.expiresAtGameTick()
                || expiresAtGameTick - createdAtGameTick
                        > ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS) {
            throw new IllegalArgumentException("Invalid Armory ammunition reservation");
        }
    }

    public ArmoryResupplyRequest.RequestKey requestKey() {
        return request.key();
    }

    public boolean isExpired(long gameTick) {
        return gameTick < createdAtGameTick || gameTick >= expiresAtGameTick;
    }
}
