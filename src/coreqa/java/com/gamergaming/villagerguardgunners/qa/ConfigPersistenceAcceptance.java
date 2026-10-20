package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimSettings;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.impl.ConfigApiImpl;

/** Fzzy 0.7.7's full config reflection initializes Minecraft item/hover registries. */
final class ConfigPersistenceAcceptance {
    static void run() {
        restartAnnotations();
        var config = new VillagerGuardGunnersConfig();
        var errors = new ArrayList<String>();
        ConfigApiImpl.INSTANCE.deserializeConfig$fzzy_config(config, """
                version = 21
                enabled = false
                [combat]
                requiredVisibleTicks = 9
                aimToleranceDegrees = 4.0
                maximumEngagementRange = 48.0
                [armoryResupply]
                enabled = false
                """, errors, (byte) 0, FileType.TOML);
        require(!config.enabled.get() && config.combat.requiredVisibleTicks.get() == 9
                && config.combat.aimToleranceDegrees.get() == 4
                && config.combat.maximumEngagementRange.get() == 48 && !config.armoryResupply.enabled.get(),
                "Old TOML settings changed: " + errors);
        require(GuardAimSettings.DEFAULT.equals(config.combat.aiming.resolve(true)), "Old TOML changed default accuracy");
        config.combat.aiming.overridePlayers.accept(true);
        config.combat.aiming.playerErrorDegrees.accept(3.5);
        errors.clear();
        var encoded = ConfigApiImpl.INSTANCE.serializeConfig$fzzy_config(config, errors, (byte) 0, FileType.TOML);
        require(!encoded.isError(), "Config serialization failed: " + errors);
        var reloaded = new VillagerGuardGunnersConfig();
        errors.clear();
        var decoded = ConfigApiImpl.INSTANCE.deserializeConfig$fzzy_config(reloaded, encoded.get(), errors, (byte) 0, FileType.TOML);
        if (decoded.isError()) decoded.log();
        require(!decoded.isError(), "Config deserialization failed: " + errors);
        require(new GuardAimSettings(3.5, GuardAimPoint.CENTER_MASS).equals(reloaded.combat.aiming.resolve(true))
                && !reloaded.enabled.get() && reloaded.combat.maximumEngagementRange.get() == 48,
                "Config round trip changed aim or existing controls");
        LogUtils.getLogger().info("VGG_BEHAVIOR_QA_CONFIG_PASS");
    }
    static void restartAnnotations() {
        try {
            var type = VillagerGuardGunnersConfig.VillageBuildingSettings.class;
            for (String name : new String[]{"enabled", "barracks", "armories", "sentryPosts", "integratedWatchtowers", "trainingYards"})
                require(type.getField(name).getAnnotation(me.fzzyhmstrs.fzzy_config.annotations.RequiresAction.class).action()
                        == me.fzzyhmstrs.fzzy_config.annotations.Action.RESTART, "Missing restart annotation: " + name);
            for (String name : new String[]{"spawnGarrisons", "maximumGuardsPerBuilding", "placeArmoryBlocks", "stockArmories"})
                require(type.getField(name).getAnnotation(me.fzzyhmstrs.fzzy_config.annotations.RequiresAction.class) == null,
                        "Live setting requires restart: " + name);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
