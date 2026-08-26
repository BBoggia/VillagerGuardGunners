package com.gamergaming.villagerguardgunners.compat.fzzy_config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;

@Translation(prefix = ModConfigs.BASE_KEY + "general")
public class GeneralConfig extends Config {

    public GeneralConfig() {
        super(VillagerGuardGunners.loc("general"));
    }

    @Comment("Enable or disable debug mode")
    public ValidatedBoolean debugMode = new ValidatedBoolean(false);
}