package com.gamergaming.villagerguardgunners.spawn;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;

/** Pure selection pipeline; all world queries and randomness are supplied lazily. */
public final class NaturalGuardLoadoutSelector {
    private NaturalGuardLoadoutSelector() {
    }

    public static Decision select(
            Context context,
            NaturalGuardSpawnRules rules,
            NaturalGuardDifficultySettings difficulty,
            DoubleSupplier chanceRoll,
            IntSupplier nearbyNaturalGunners,
            @Nullable NaturalGuardLoadoutPool pool,
            IntUnaryOperator weightedRoll) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(chanceRoll, "chanceRoll");
        Objects.requireNonNull(nearbyNaturalGunners, "nearbyNaturalGunners");
        Objects.requireNonNull(weightedRoll, "weightedRoll");

        DecisionStatus preflightFailure = preflight(context, rules, difficulty);
        if (preflightFailure != null) {
            return Decision.rejected(preflightFailure);
        }

        double rolledChance = chanceRoll.getAsDouble();
        if (!Double.isFinite(rolledChance) || rolledChance < 0.0D || rolledChance >= 1.0D) {
            throw new IllegalArgumentException("Chance roll must be within [0, 1)");
        }
        if (rolledChance >= difficulty.upgradeChance()) {
            return Decision.rejected(DecisionStatus.CHANCE_MISSED);
        }

        if (pool == null || !pool.id().equals(difficulty.poolId())) {
            return Decision.rejected(DecisionStatus.POOL_UNAVAILABLE);
        }

        int nearbyCount = nearbyNaturalGunners.getAsInt();
        if (nearbyCount < 0) {
            throw new IllegalArgumentException("Nearby natural gunner count cannot be negative");
        }
        if (nearbyCount >= difficulty.maximumNearby()) {
            return Decision.rejected(DecisionStatus.NEARBY_CAP_REACHED);
        }

        int weightedIndex = weightedRoll.applyAsInt(pool.totalWeight());
        return Decision.selected(pool.select(weightedIndex));
    }

    private static @Nullable DecisionStatus preflight(
            Context context,
            NaturalGuardSpawnRules rules,
            NaturalGuardDifficultySettings difficulty) {
        if (!context.serverSide()) {
            return DecisionStatus.CLIENT_SIDE;
        }
        if (!context.addonEnabled()) {
            return DecisionStatus.ADDON_DISABLED;
        }
        if (!context.initialSpawn()) {
            return DecisionStatus.NOT_INITIAL_SPAWN;
        }
        if (!rules.permits(context.spawnSource())) {
            return DecisionStatus.SPAWN_SOURCE_DISABLED;
        }
        if (context.alreadyIssued()) {
            return DecisionStatus.ALREADY_ISSUED;
        }
        if (rules.replaceCrossbowsOnly() && !context.holdingCrossbow()) {
            return DecisionStatus.WEAPON_NOT_ELIGIBLE;
        }
        if (!difficulty.enabled()) {
            return DecisionStatus.DIFFICULTY_DISABLED;
        }
        return null;
    }

    public record Context(
            boolean serverSide,
            boolean addonEnabled,
            boolean initialSpawn,
            NaturalGuardSpawnSource spawnSource,
            boolean holdingCrossbow,
            boolean alreadyIssued) {
        public Context {
            spawnSource = Objects.requireNonNull(spawnSource, "spawnSource");
        }
    }

    public enum DecisionStatus {
        SELECTED,
        CLIENT_SIDE,
        ADDON_DISABLED,
        NOT_INITIAL_SPAWN,
        SPAWN_SOURCE_DISABLED,
        ALREADY_ISSUED,
        WEAPON_NOT_ELIGIBLE,
        DIFFICULTY_DISABLED,
        CHANCE_MISSED,
        POOL_UNAVAILABLE,
        NEARBY_CAP_REACHED
    }

    public record Decision(
            DecisionStatus status,
            @Nullable NaturalGuardLoadoutPool.Entry entry) {
        public Decision {
            status = Objects.requireNonNull(status, "status");
            if ((status == DecisionStatus.SELECTED) != (entry != null)) {
                throw new IllegalArgumentException(
                        "Only a selected natural loadout decision may contain an entry");
            }
        }

        public boolean selected() {
            return status == DecisionStatus.SELECTED;
        }

        private static Decision selected(NaturalGuardLoadoutPool.Entry entry) {
            return new Decision(DecisionStatus.SELECTED, Objects.requireNonNull(entry, "entry"));
        }

        private static Decision rejected(DecisionStatus status) {
            return new Decision(status, null);
        }
    }
}
