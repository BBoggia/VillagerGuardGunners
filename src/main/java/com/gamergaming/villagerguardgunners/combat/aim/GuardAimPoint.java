package com.gamergaming.villagerguardgunners.combat.aim;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

public enum GuardAimPoint implements EnumTranslatable {
    EYE_LEVEL,
    CENTER_MASS;

    @Override
    public String prefix() {
        return "config.villagerguardgunners.aim_point";
    }
}
