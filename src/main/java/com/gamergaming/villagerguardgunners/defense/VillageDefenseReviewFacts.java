package com.gamergaming.villagerguardgunners.defense;

/** Bounded live facts consulted when a persisted quiet deadline becomes due. */
public record VillageDefenseReviewFacts(
        boolean raidActive,
        boolean confirmedThreatActive) {
    public static final VillageDefenseReviewFacts QUIET =
            new VillageDefenseReviewFacts(false, false);
}
