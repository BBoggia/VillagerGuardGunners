package com.gamergaming.villagerguardgunners.spawn;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/**
 * Spawn origins relevant to guard loadout eligibility.
 *
 * <p>The one-time spawn integration maps Minecraft reasons onto these stable
 * values. Keeping this model independent makes policy selection deterministic
 * and directly testable.</p>
 */
public enum NaturalGuardSpawnSource implements EnumTranslatable {
    STRUCTURE,
    CONVERSION,
    SPAWN_EGG,
    COMMAND,
    OTHER;

    @Override
    public String prefix() {
        return "config.villagerguardgunners.natural_spawn_source";
    }
}
