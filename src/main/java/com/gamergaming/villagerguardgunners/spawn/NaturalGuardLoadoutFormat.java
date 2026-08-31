package com.gamergaming.villagerguardgunners.spawn;

import java.util.Set;

/**
 * Stable field and version contract for natural-loadout datapack documents.
 *
 * <p>All versions are accepted by the reload parser. Format 1 preserves its
 * legacy selection behavior, format 2 adds immutable one-shot spawn
 * conditions, and format 3 may additionally issue one explicitly configured
 * sidearm.</p>
 */
public final class NaturalGuardLoadoutFormat {
    public static final int LEGACY_VERSION = 1;
    public static final int CONDITIONAL_VERSION = 2;
    public static final int SIDEARM_VERSION = 3;

    /** Prevents one spawn decision from becoming an unbounded server-thread pass. */
    public static final int MAX_ENTRIES_PER_POOL = 256;

    /** Applies independently to every include, exclude, enum, or guard-tag list. */
    public static final int MAX_VALUES_PER_CONDITION_COLLECTION = 64;

    /** Bounds the biome/tag and structure/tag world queries captured for one pool. */
    public static final int MAX_CONTEXT_SELECTORS_PER_POOL = 128;

    public static final Set<String> ROOT_FIELDS = Set.of("format", "entries");
    public static final Set<String> VERSION_1_ENTRY_FIELDS = Set.of("gun", "weight");
    public static final Set<String> VERSION_2_ENTRY_FIELDS =
            Set.of("gun", "weight", "conditions");
    public static final Set<String> VERSION_3_ENTRY_FIELDS =
            Set.of("gun", "sidearm", "weight", "conditions");
    public static final Set<String> CONDITION_FIELDS = Set.of(
            "dimensions",
            "biomes",
            "structures",
            "difficulties",
            "spawn_sources",
            "raid",
            "required_guard_tags",
            "excluded_guard_tags");
    public static final Set<String> SELECTOR_GROUP_FIELDS = Set.of("include", "exclude");

    private NaturalGuardLoadoutFormat() {
    }
}
