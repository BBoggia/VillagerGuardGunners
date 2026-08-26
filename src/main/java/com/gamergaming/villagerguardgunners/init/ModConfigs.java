package com.gamergaming.villagerguardgunners.init;

import java.util.function.Supplier;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.compat.fzzy_config.GeneralConfig;
import com.gamergaming.villagerguardgunners.compat.fzzy_config.GuardGunnerConfig;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;

public class ModConfigs {

    public static final String BASE_KEY = "config." + VillagerGuardGunners.MODID + ".";

    public static final GeneralConfig GENERAL = register(GeneralConfig::new);
    public static final GuardGunnerConfig GUARD_GUNNER = register(GuardGunnerConfig::new);

    public static void init() {
    }

    private static <T extends Config> T register(Supplier<T> supplier) {
        return ConfigApiJava.registerAndLoadConfig(supplier, RegisterType.BOTH);
    }
}
