package com.gamergaming.villagerguardgunners.spawn;

import java.util.Objects;
import java.util.Set;

/** Global eligibility and safety boundaries for naturally issued loadouts. */
public record NaturalGuardSpawnRules(
        Set<NaturalGuardSpawnSource> enabledSources,
        boolean replaceCrossbowsOnly,
        double nearbyCapRadius,
        boolean allowExplosiveWeapons) {

    public NaturalGuardSpawnRules {
        enabledSources = Set.copyOf(Objects.requireNonNull(
                enabledSources,
                "enabledSources"));
        if (!Double.isFinite(nearbyCapRadius) || nearbyCapRadius <= 0.0D) {
            throw new IllegalArgumentException("Nearby cap radius must be finite and positive");
        }
    }

    public boolean permits(NaturalGuardSpawnSource source) {
        return source != null && enabledSources.contains(source);
    }
}
