package com.gamergaming.villagerguardgunners.combat.engagement;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/** Server-selected policy applied after Guard Villagers chooses a target. */
public enum EngagementMode implements EnumTranslatable {
    /** Preserve Guard Villagers' normal target selection. */
    GUARD_TARGETS,
    /** Permit proactive fire only against Minecraft hostile mobs. */
    HOSTILE_MOBS_ONLY,
    /** Fire only at a recent attacker of the guard or one of its protected allies. */
    DEFENSIVE,
    /** Keep the gun goal active, but never press the trigger. */
    HOLD_FIRE;

    @Override
    public String prefix() {
        return "config.villagerguardgunners.engagement_mode";
    }
}
