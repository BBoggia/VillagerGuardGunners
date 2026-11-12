package com.gamergaming.villagerguardgunners.guns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Chooses which provider's driver serves a guard.
 *
 * <p>Each provider gets at most one driver per guard, created the first time
 * one of its guns is held and kept afterwards. A stack that no provider claims
 * leaves the current driver in place, so an empty hand keeps reporting through
 * the driver that last owned a gun.</p>
 */
final class DriverSwitch {
    private final Function<GunProvider, WeaponDriver> factory;
    private final Map<String, WeaponDriver> drivers = new LinkedHashMap<>(2);
    private String currentProviderId;
    private WeaponDriver current;

    DriverSwitch(GunProvider fallback, Function<GunProvider, WeaponDriver> factory) {
        Objects.requireNonNull(fallback, "fallback");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.current = driverFor(fallback);
        this.currentProviderId = fallback.id();
    }

    WeaponDriver current() {
        return current;
    }

    /**
     * Makes the claiming provider's driver current. The outgoing driver stops
     * aiming first, so it cannot keep trigger work queued against a stack it no
     * longer owns.
     */
    WeaponDriver select(Optional<GunProvider> claimed) {
        GunProvider next = claimed.orElse(null);
        if (next == null || next.id().equals(currentProviderId)) {
            return current;
        }
        // Create before releasing the old driver: a failed creation changes nothing.
        WeaponDriver selected = driverFor(next);
        current.stopAiming();
        current = selected;
        currentProviderId = next.id();
        return current;
    }

    <T extends WeaponDriver> Optional<T> driver(Class<T> type) {
        for (WeaponDriver driver : drivers.values()) {
            if (type.isInstance(driver)) {
                return Optional.of(type.cast(driver));
            }
        }
        return Optional.empty();
    }

    /** The driver already created for a provider, if any. Never creates one. */
    Optional<WeaponDriver> driver(String providerId) {
        return Optional.ofNullable(drivers.get(providerId));
    }

    private WeaponDriver driverFor(GunProvider provider) {
        WeaponDriver existing = drivers.get(provider.id());
        if (existing != null) {
            return existing;
        }
        WeaponDriver created = Objects.requireNonNull(
                factory.apply(provider),
                () -> "Gun provider created no driver: " + provider.id());
        drivers.put(provider.id(), created);
        return created;
    }
}
