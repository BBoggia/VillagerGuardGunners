package com.gamergaming.villagerguardgunners.combat.tactics;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/** Broad guard behavior selected from TaCZ gun metadata and server overrides. */
public enum TacticalProfile implements EnumTranslatable {
    GENERALIST,
    SKIRMISHER,
    BREACHER,
    MARKSMAN,
    SUPPORT,
    DEMOLITIONS;

    @Override
    public String prefix() {
        return "config.villagerguardgunners.tactical_profile";
    }
}
