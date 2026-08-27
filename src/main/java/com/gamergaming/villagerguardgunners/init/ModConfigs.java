package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.config.LegacyConfigNotice;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

public final class ModConfigs {

    public static final String BASE_TRANSLATION_KEY = "config." + VillagerGuardGunners.MODID + ".";

    public static final VillagerGuardGunnersConfig GUARD_GUNNERS =
            ConfigApiJava.registerAndLoadConfig(VillagerGuardGunnersConfig::new, RegisterType.BOTH);

    private ModConfigs() {
    }

    public static void init() {
        // Loading this class registers and loads the config before gameplay systems use it.
        LegacyConfigNotice.warnIfPresent();
    }
}
