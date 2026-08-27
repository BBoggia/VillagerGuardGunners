package com.gamergaming.villagerguardgunners.config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.resources.ResourceLocation;

@Version(version = VillagerGuardGunnersConfig.CONFIG_VERSION)
@Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners")
public final class VillagerGuardGunnersConfig extends Config {
    public static final int CONFIG_VERSION = 1;

    @Comment("Enables Guard Villagers to use TaCZ guns. The server controls this setting.")
    public ValidatedBoolean enabled = new ValidatedBoolean(true);

    @Comment("Target acquisition, range, and aiming settings. TaCZ remains authoritative for weapon statistics.")
    public CombatSettings combat = new CombatSettings();

    @Comment("Movement behavior while a guard is using a TaCZ gun.")
    public MovementSettings movement = new MovementSettings();

    @Comment("Friendly-fire protection settings. Core fail-closed protections cannot be disabled.")
    public SafetySettings safety = new SafetySettings();

    @Comment("Advanced scheduling settings. Lower intervals react faster but require more server work.")
    public PerformanceSettings performance = new PerformanceSettings();

    public VillagerGuardGunnersConfig() {
        super(ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, "guard_gunners"));
    }

    @Override
    public int defaultPermLevel() {
        return 4;
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.combat")
    public static final class CombatSettings extends ConfigSection {

        @Comment("Ticks a guard must continuously see its target before it may fire. Range: 0 to 40.")
        public ValidatedInt requiredVisibleTicks = new ValidatedInt(5, 40, 0);

        @Comment("Maximum distance at which a guard may fire, in blocks. TaCZ effective range still controls the guard's preferred combat distance. Range: 8 to 64.")
        public ValidatedDouble maximumEngagementRange = new ValidatedDouble(32.0D, 64.0D, 8.0D);

        @Comment("Distance at which a guard considers its target too close, in blocks. Range: 1 to 8.")
        public ValidatedDouble closeRangeDistance = new ValidatedDouble(4.0D, 8.0D, 1.0D);

        @Comment("Maximum aiming error accepted before firing, in degrees. Range: 1 to 45.")
        public ValidatedDouble aimToleranceDegrees = new ValidatedDouble(10.0D, 45.0D, 1.0D);
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.movement")
    public static final class MovementSettings extends ConfigSection {

        @Comment("Navigation speed modifier while approaching a target. Range: 0.1 to 2.")
        public ValidatedDouble approachSpeed = new ValidatedDouble(1.0D, 2.0D, 0.1D);

        @Comment("Navigation speed modifier while retreating from a close target. Range: 0.1 to 1.")
        public ValidatedDouble retreatSpeed = new ValidatedDouble(0.75D, 1.0D, 0.1D);

        @Comment("Navigation speed modifier while finding a safe firing lane. Range: 0.1 to 1.")
        public ValidatedDouble repositionSpeed = new ValidatedDouble(0.75D, 1.0D, 0.1D);

        @Comment("Ticks before retrying a shot lane blocked by a protected entity. Range: 5 to 100.")
        public ValidatedInt friendlyRepositionTicks = new ValidatedInt(20, 100, 5);
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.safety")
    public static final class SafetySettings extends ConfigSection {

        @Comment("Extra clearance around protected entities when validating a shot, in blocks. Range: 0.25 to 4.")
        public ValidatedDouble baseClearance = new ValidatedDouble(0.75D, 4.0D, 0.25D);
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.performance")
    public static final class PerformanceSettings extends ConfigSection {

        @Comment("Ticks between target visibility samples. Lower values react faster. Range: 1 to 10.")
        public ValidatedInt visibilitySampleIntervalTicks = new ValidatedInt(2, 10, 1);

        @Comment("Ticks between combat movement decisions. Lower values react faster. Range: 1 to 20.")
        public ValidatedInt movementDecisionIntervalTicks = new ValidatedInt(5, 20, 1);

        @Comment("Ticks between navigation path recalculations. Lower values use more CPU. Range: 5 to 100.")
        public ValidatedInt pathRecalculationIntervalTicks = new ValidatedInt(20, 100, 5);

        @Comment("Ticks between cached TaCZ gun-data refreshes. Range: 5 to 200.")
        public ValidatedInt gunDataRefreshIntervalTicks = new ValidatedInt(20, 200, 5);

        @Comment("Ticks an unsafe-shot result may be reused across ticks. Zero keeps only same-tick reuse. Range: 0 to 5.")
        public ValidatedInt unsafeShotCacheTicks = new ValidatedInt(2, 5, 0);

        @Comment("Ticks between checks that repositioning is making progress. Range: 5 to 40.")
        public ValidatedInt repositionProgressCheckTicks = new ValidatedInt(10, 40, 5);
    }
}
