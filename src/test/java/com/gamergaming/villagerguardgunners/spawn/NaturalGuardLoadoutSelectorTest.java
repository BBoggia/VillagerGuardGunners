package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPool.Entry;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutSelector.Context;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutSelector.Decision;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutSelector.DecisionStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutSelectorTest {

    @Test
    void defaultsKeepPopulationConservativeAndDifficultyScaled() {
        NaturalGuardSpawnRules rules = NaturalGuardLoadoutDefaults.SPAWN_RULES;
        NaturalGuardDifficultySettings peaceful = settings(NaturalGuardDifficulty.PEACEFUL);
        NaturalGuardDifficultySettings easy = settings(NaturalGuardDifficulty.EASY);
        NaturalGuardDifficultySettings normal = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardDifficultySettings hard = settings(NaturalGuardDifficulty.HARD);

        assertAll(
                () -> assertEquals(Set.of(NaturalGuardSpawnSource.STRUCTURE), rules.enabledSources()),
                () -> assertTrue(rules.replaceCrossbowsOnly()),
                () -> assertEquals(64.0D, rules.nearbyCapRadius()),
                () -> assertFalse(rules.allowExplosiveWeapons()),
                () -> assertFalse(peaceful.enabled()),
                () -> assertEquals(0.0D, peaceful.upgradeChance()),
                () -> assertEquals(0, peaceful.maximumNearby()),
                () -> assertNull(peaceful.poolId()),
                () -> assertEquals(0.35D, easy.upgradeChance()),
                () -> assertEquals(1, easy.maximumNearby()),
                () -> assertEquals(NaturalGuardLoadoutDefaults.EASY_POOL, easy.poolId()),
                () -> assertEquals(0.50D, normal.upgradeChance()),
                () -> assertEquals(2, normal.maximumNearby()),
                () -> assertEquals(NaturalGuardLoadoutDefaults.NORMAL_POOL, normal.poolId()),
                () -> assertEquals(0.65D, hard.upgradeChance()),
                () -> assertEquals(2, hard.maximumNearby()),
                () -> assertEquals(NaturalGuardLoadoutDefaults.HARD_POOL, hard.poolId()));
    }

    @Test
    void everyPreflightGateRejectsWithoutRandomnessOrNearbyQueries() {
        AtomicInteger chanceCalls = new AtomicInteger();
        AtomicInteger nearbyCalls = new AtomicInteger();
        AtomicInteger weightCalls = new AtomicInteger();
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardLoadoutPool pool = pool(difficulty.poolId());

        assertAll(
                () -> assertRejected(
                        context(false, true, true, NaturalGuardSpawnSource.STRUCTURE, true, false),
                        difficulty, pool, DecisionStatus.CLIENT_SIDE,
                        chanceCalls, nearbyCalls, weightCalls),
                () -> assertRejected(
                        context(true, false, true, NaturalGuardSpawnSource.STRUCTURE, true, false),
                        difficulty, pool, DecisionStatus.ADDON_DISABLED,
                        chanceCalls, nearbyCalls, weightCalls),
                () -> assertRejected(
                        context(true, true, false, NaturalGuardSpawnSource.STRUCTURE, true, false),
                        difficulty, pool, DecisionStatus.NOT_INITIAL_SPAWN,
                        chanceCalls, nearbyCalls, weightCalls),
                () -> assertRejected(
                        context(true, true, true, NaturalGuardSpawnSource.CONVERSION, true, false),
                        difficulty, pool, DecisionStatus.SPAWN_SOURCE_DISABLED,
                        chanceCalls, nearbyCalls, weightCalls),
                () -> assertRejected(
                        context(true, true, true, NaturalGuardSpawnSource.STRUCTURE, true, true),
                        difficulty, pool, DecisionStatus.ALREADY_ISSUED,
                        chanceCalls, nearbyCalls, weightCalls),
                () -> assertRejected(
                        context(true, true, true, NaturalGuardSpawnSource.STRUCTURE, false, false),
                        difficulty, pool, DecisionStatus.WEAPON_NOT_ELIGIBLE,
                        chanceCalls, nearbyCalls, weightCalls),
                () -> assertRejected(
                        validContext(), settings(NaturalGuardDifficulty.PEACEFUL), null,
                        DecisionStatus.DIFFICULTY_DISABLED,
                        chanceCalls, nearbyCalls, weightCalls));

        assertAll(
                () -> assertEquals(0, chanceCalls.get()),
                () -> assertEquals(0, nearbyCalls.get()),
                () -> assertEquals(0, weightCalls.get()));
    }

    @Test
    void chancePoolAndCapFailuresStopBeforeLaterWork() {
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.EASY);
        NaturalGuardLoadoutPool pool = pool(difficulty.poolId());
        AtomicInteger nearbyCalls = new AtomicInteger();
        AtomicInteger weightCalls = new AtomicInteger();

        Decision chanceMiss = NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                difficulty,
                () -> 0.35D,
                () -> incrementAndReturn(nearbyCalls, 0),
                pool,
                bound -> incrementAndReturn(weightCalls, 0));
        Decision missingPool = NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                difficulty,
                () -> 0.0D,
                () -> incrementAndReturn(nearbyCalls, 0),
                null,
                bound -> incrementAndReturn(weightCalls, 0));
        Decision capped = NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                difficulty,
                () -> 0.0D,
                () -> incrementAndReturn(nearbyCalls, difficulty.maximumNearby()),
                pool,
                bound -> incrementAndReturn(weightCalls, 0));

        assertAll(
                () -> assertEquals(DecisionStatus.CHANCE_MISSED, chanceMiss.status()),
                () -> assertEquals(DecisionStatus.POOL_UNAVAILABLE, missingPool.status()),
                () -> assertEquals(DecisionStatus.NEARBY_CAP_REACHED, capped.status()),
                () -> assertEquals(1, nearbyCalls.get()),
                () -> assertEquals(0, weightCalls.get()));
    }

    @Test
    void formatTwoRejectsNoMatchingEntriesBeforePopulationAndWeightedWork() {
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardLoadoutConditionSpec hardOnly = new NaturalGuardLoadoutConditionSpec(
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                Set.of(NaturalGuardDifficulty.HARD),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());
        NaturalGuardLoadoutPool conditionalPool = new NaturalGuardLoadoutPool(
                difficulty.poolId(),
                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                List.of(new Entry(id("pistol"), 1, hardOnly)));
        AtomicInteger contextCalls = new AtomicInteger();
        AtomicInteger nearbyCalls = new AtomicInteger();
        AtomicInteger weightCalls = new AtomicInteger();

        Decision decision = NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                NaturalGuardDifficulty.NORMAL,
                difficulty,
                () -> {
                    contextCalls.incrementAndGet();
                    return matchContext(NaturalGuardDifficulty.NORMAL);
                },
                () -> 0.0D,
                () -> incrementAndReturn(nearbyCalls, 0),
                conditionalPool,
                bound -> incrementAndReturn(weightCalls, 0));

        assertAll(
                () -> assertEquals(DecisionStatus.NO_MATCHING_ENTRIES, decision.status()),
                () -> assertEquals(1, contextCalls.get()),
                () -> assertEquals(0, nearbyCalls.get()),
                () -> assertEquals(0, weightCalls.get()));
    }

    @Test
    void conditionalSelectionUsesEligibleWeightAndMatchingSecondPass() {
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardLoadoutConditionSpec normalOnly = new NaturalGuardLoadoutConditionSpec(
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                Set.of(NaturalGuardDifficulty.NORMAL),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());
        NaturalGuardLoadoutConditionSpec hardOnly = new NaturalGuardLoadoutConditionSpec(
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                Set.of(NaturalGuardDifficulty.HARD),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());
        Entry unconditional = new Entry(id("pistol"), 2);
        Entry matching = new Entry(id("rifle"), 3, normalOnly);
        NaturalGuardLoadoutPool conditionalPool = new NaturalGuardLoadoutPool(
                difficulty.poolId(),
                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                List.of(unconditional, new Entry(id("hard"), 100, hardOnly), matching));
        AtomicInteger contextCalls = new AtomicInteger();
        AtomicInteger nearbyCalls = new AtomicInteger();
        AtomicInteger weightCalls = new AtomicInteger();

        Decision decision = NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                NaturalGuardDifficulty.NORMAL,
                difficulty,
                () -> {
                    contextCalls.incrementAndGet();
                    return matchContext(NaturalGuardDifficulty.NORMAL);
                },
                () -> 0.0D,
                () -> incrementAndReturn(nearbyCalls, 0),
                conditionalPool,
                bound -> {
                    weightCalls.incrementAndGet();
                    assertEquals(5, bound);
                    return 4;
                });

        assertAll(
                () -> assertTrue(decision.selected()),
                () -> assertEquals(matching, decision.entry()),
                () -> assertEquals(1, contextCalls.get()),
                () -> assertEquals(1, nearbyCalls.get()),
                () -> assertEquals(1, weightCalls.get()));
    }

    @Test
    void contextCaptureIsLazyAndFailuresOrMismatchesFailClosed() {
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardLoadoutConditionSpec conditional = new NaturalGuardLoadoutConditionSpec(
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                Set.of(NaturalGuardDifficulty.NORMAL),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                difficulty.poolId(),
                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                List.of(new Entry(id("pistol"), 1, conditional)));
        AtomicInteger contextCalls = new AtomicInteger();

        Decision missed = NaturalGuardLoadoutSelector.select(
                validContext(), NaturalGuardLoadoutDefaults.SPAWN_RULES,
                NaturalGuardDifficulty.NORMAL, difficulty,
                () -> {
                    contextCalls.incrementAndGet();
                    return matchContext(NaturalGuardDifficulty.NORMAL);
                },
                () -> difficulty.upgradeChance(), () -> 0, pool, bound -> 0);
        Decision failed = NaturalGuardLoadoutSelector.select(
                validContext(), NaturalGuardLoadoutDefaults.SPAWN_RULES,
                NaturalGuardDifficulty.NORMAL, difficulty,
                () -> {
                    contextCalls.incrementAndGet();
                    throw new IllegalStateException("simulated capture failure");
                },
                () -> 0.0D, () -> 0, pool, bound -> 0);
        Decision mismatched = NaturalGuardLoadoutSelector.select(
                validContext(), NaturalGuardLoadoutDefaults.SPAWN_RULES,
                NaturalGuardDifficulty.NORMAL, difficulty,
                () -> {
                    contextCalls.incrementAndGet();
                    return matchContext(NaturalGuardDifficulty.HARD);
                },
                () -> 0.0D, () -> 0, pool, bound -> 0);

        assertAll(
                () -> assertEquals(DecisionStatus.CHANCE_MISSED, missed.status()),
                () -> assertEquals(DecisionStatus.CONTEXT_UNAVAILABLE, failed.status()),
                () -> assertEquals(DecisionStatus.CONTEXT_UNAVAILABLE, mismatched.status()),
                () -> assertEquals(2, contextCalls.get()));
    }

    @Test
    void successfulSelectionUsesOneBoundedRollAndReturnsTheChosenEntry() {
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.HARD);
        Entry pistol = new Entry(id("pistol"), 2);
        Entry rifle = new Entry(id("rifle"), 1);
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                difficulty.poolId(),
                List.of(pistol, rifle));
        AtomicInteger chanceCalls = new AtomicInteger();
        AtomicInteger nearbyCalls = new AtomicInteger();
        AtomicInteger weightCalls = new AtomicInteger();

        Decision decision = NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                difficulty,
                () -> incrementAndReturn(chanceCalls, 0) + 0.25D,
                () -> incrementAndReturn(nearbyCalls, 0),
                pool,
                bound -> {
                    weightCalls.incrementAndGet();
                    assertEquals(3, bound);
                    return 2;
                });

        assertAll(
                () -> assertTrue(decision.selected()),
                () -> assertEquals(DecisionStatus.SELECTED, decision.status()),
                () -> assertEquals(rifle, decision.entry()),
                () -> assertEquals(1, chanceCalls.get()),
                () -> assertEquals(1, nearbyCalls.get()),
                () -> assertEquals(1, weightCalls.get()));
    }

    @Test
    void customRulesCanPermitNonCrossbowWeaponsWithoutOpeningOtherSources() {
        NaturalGuardSpawnRules customRules = new NaturalGuardSpawnRules(
                Set.of(NaturalGuardSpawnSource.STRUCTURE),
                false,
                32.0D,
                false);
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardLoadoutPool pool = pool(difficulty.poolId());

        Decision decision = NaturalGuardLoadoutSelector.select(
                context(true, true, true, NaturalGuardSpawnSource.STRUCTURE, false, false),
                customRules,
                difficulty,
                () -> 0.0D,
                () -> 0,
                pool,
                bound -> 0);

        assertTrue(decision.selected());
    }

    @Test
    void numericInputsAndDecisionInvariantsFailClosed() {
        NaturalGuardDifficultySettings difficulty = settings(NaturalGuardDifficulty.NORMAL);
        NaturalGuardLoadoutPool pool = pool(difficulty.poolId());

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardDifficultySettings(Double.NaN, 1, id("pool"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardDifficultySettings(-0.1D, 1, id("pool"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardDifficultySettings(1.1D, 1, id("pool"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardDifficultySettings(0.5D, -1, id("pool"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardSpawnRules(Set.of(), true, 0.0D, false)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> selectWithChance(difficulty, pool, 1.0D, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> selectWithChance(difficulty, pool, Double.NaN, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> selectWithChance(difficulty, pool, 0.0D, -1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Decision(DecisionStatus.SELECTED, null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Decision(DecisionStatus.CHANCE_MISSED, pool.entries().get(0))));
    }

    @Test
    void provenanceCarriesStableIdentityWithoutAcceptingMalformedVersions() {
        UUID issuanceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        NaturalGuardLoadoutProvenance provenance = NaturalGuardLoadoutProvenance.issued(
                NaturalGuardLoadoutDefaults.HARD_POOL,
                id("rifle"),
                issuanceId,
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);

        assertAll(
                () -> assertEquals(
                        NaturalGuardLoadoutProvenance.CURRENT_SCHEMA_VERSION,
                        provenance.schemaVersion()),
                () -> assertEquals(NaturalGuardLoadoutDefaults.HARD_POOL, provenance.poolId()),
                () -> assertEquals(id("rifle"), provenance.gunId()),
                () -> assertEquals(issuanceId, provenance.issuanceId()),
                () -> assertEquals(NaturalGuardDifficulty.HARD, provenance.difficulty()),
                () -> assertEquals(NaturalGuardSpawnSource.STRUCTURE, provenance.spawnSource()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutProvenance(
                                0,
                                NaturalGuardLoadoutDefaults.HARD_POOL,
                                id("rifle"),
                                issuanceId,
                                NaturalGuardDifficulty.HARD,
                                NaturalGuardSpawnSource.STRUCTURE)));
    }

    private static void assertRejected(
            Context context,
            NaturalGuardDifficultySettings difficulty,
            NaturalGuardLoadoutPool pool,
            DecisionStatus expected,
            AtomicInteger chanceCalls,
            AtomicInteger nearbyCalls,
            AtomicInteger weightCalls) {
        Decision decision = NaturalGuardLoadoutSelector.select(
                context,
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                difficulty,
                () -> incrementAndReturn(chanceCalls, 0),
                () -> incrementAndReturn(nearbyCalls, 0),
                pool,
                bound -> incrementAndReturn(weightCalls, 0));
        assertFalse(decision.selected());
        assertEquals(expected, decision.status());
        assertNull(decision.entry());
    }

    private static Decision selectWithChance(
            NaturalGuardDifficultySettings difficulty,
            NaturalGuardLoadoutPool pool,
            double chance,
            int nearby) {
        return NaturalGuardLoadoutSelector.select(
                validContext(),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                difficulty,
                () -> chance,
                () -> nearby,
                pool,
                bound -> 0);
    }

    private static int incrementAndReturn(AtomicInteger counter, int value) {
        counter.incrementAndGet();
        return value;
    }

    private static NaturalGuardDifficultySettings settings(NaturalGuardDifficulty difficulty) {
        return NaturalGuardLoadoutDefaults.settingsFor(difficulty);
    }

    private static NaturalGuardLoadoutPool pool(ResourceLocation poolId) {
        return new NaturalGuardLoadoutPool(
                poolId,
                List.of(new Entry(id("pistol"), 1)));
    }

    private static Context validContext() {
        return context(
                true,
                true,
                true,
                NaturalGuardSpawnSource.STRUCTURE,
                true,
                false);
    }

    private static NaturalGuardLoadoutMatchContext matchContext(
            NaturalGuardDifficulty difficulty) {
        return new NaturalGuardLoadoutMatchContext(
                id("overworld"),
                Set.of(),
                Set.of(),
                difficulty,
                NaturalGuardSpawnSource.STRUCTURE,
                false,
                Set.of());
    }

    private static Context context(
            boolean serverSide,
            boolean addonEnabled,
            boolean initialSpawn,
            NaturalGuardSpawnSource source,
            boolean holdingCrossbow,
            boolean alreadyIssued) {
        return new Context(
                serverSide,
                addonEnabled,
                initialSpawn,
                source,
                holdingCrossbow,
                alreadyIssued);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
