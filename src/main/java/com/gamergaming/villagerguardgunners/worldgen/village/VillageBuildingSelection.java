package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;

/** Admission before jigsaw fit checks, shared by all weighted copies of an element. */
public final class VillageBuildingSelection {
    private VillageBuildingSelection() { }

    public static <T> List<T> filter(List<T> candidates, ToDoubleFunction<T> chance, DoubleSupplier random) {
        // Preserve both the list and random stream when no entry opts into thinning.
        if (candidates.stream().noneMatch(e -> chance.applyAsDouble(e) < 1.0)) return candidates;
        var decisions = new IdentityHashMap<T, Boolean>();
        var admitted = new ArrayList<T>(candidates.size());
        for (T candidate : candidates) {
            double probability = chance.applyAsDouble(candidate);
            if (probability >= 1.0 || decisions.computeIfAbsent(candidate,
                    e -> probability > 0.0 && random.getAsDouble() < probability)) {
                admitted.add(candidate);
            }
        }
        return admitted;
    }
}
