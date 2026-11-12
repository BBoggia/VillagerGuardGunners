package com.gamergaming.villagerguardgunners.ammunition;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

public enum InfiniteAmmoMode implements EnumTranslatable {
    OFF,
    WITH_RELOADING,
    WITHOUT_RELOADING,
    CREATIVE_AMMO_BOX;

    public boolean virtualReserve() {
        return this == WITH_RELOADING || this == WITHOUT_RELOADING;
    }

    @Override
    public String prefix() {
        return "config.villagerguardgunners.infinite_ammo_mode";
    }
}
