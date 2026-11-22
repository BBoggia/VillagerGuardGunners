package com.gamergaming.villagerguardgunners.compat.cgm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Keeps accepted pellets accountable even when a later factory or impact callback fails. */
final class CgmDischarge {
    private CgmDischarge() {}

    static <T> List<T> fire(int count, IntFunction<T> create, Predicate<T> add,
            Consumer<T> tick, Consumer<RuntimeException> reportFailure) {
        List<T> accepted = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                T projectile = create.apply(index);
                if (add.test(projectile)) {
                    accepted.add(projectile);
                    tick.accept(projectile);
                }
            }
        } catch (RuntimeException failure) {
            if (accepted.isEmpty()) throw failure;
            reportFailure.accept(failure);
        }
        return accepted;
    }
}
