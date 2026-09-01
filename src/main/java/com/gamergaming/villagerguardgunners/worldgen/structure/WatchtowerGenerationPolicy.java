package com.gamergaming.villagerguardgunners.worldgen.structure;

/**
 * Immutable, bounded snapshot of the server's one-way watchtower restrictions.
 * Datapacks select structure placement, guard groups, and loot; this policy may
 * only suppress those actions.
 */
public record WatchtowerGenerationPolicy(
        boolean generationEnabled,
        boolean guardSpawningEnabled,
        int maximumGuardsPerPost,
        boolean armoryPlacementEnabled,
        boolean armoryStockEnabled) {
    public static final int MINIMUM_GUARDS_PER_POST = 0;
    public static final int MAXIMUM_GUARDS_PER_POST = 4;

    public WatchtowerGenerationPolicy {
        if (maximumGuardsPerPost < MINIMUM_GUARDS_PER_POST
                || maximumGuardsPerPost > MAXIMUM_GUARDS_PER_POST) {
            throw new IllegalArgumentException(
                    "Maximum guards per post must be from "
                            + MINIMUM_GUARDS_PER_POST + " through "
                            + MAXIMUM_GUARDS_PER_POST);
        }
    }

    public int limitGuardCount(int selectedCount) {
        if (!guardSpawningEnabled || selectedCount <= 0) {
            return 0;
        }
        return Math.min(selectedCount, maximumGuardsPerPost);
    }
}
