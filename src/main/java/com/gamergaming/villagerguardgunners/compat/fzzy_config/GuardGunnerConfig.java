package com.gamergaming.villagerguardgunners.compat.fzzy_config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;

@Translation(prefix = ModConfigs.BASE_KEY + "guard_gunner")
public class GuardGunnerConfig extends Config {

    public GuardGunnerConfig() {
        super(VillagerGuardGunners.loc("guard_gunner"));
    }

    // @Comment("Settings related to the guard's attack behavior")
    public AttackSettings attackSettings = new AttackSettings();

    @Translation(prefix = ModConfigs.BASE_KEY + "guard_gunner.attack")
    public static class AttackSettings extends ConfigSection {

        // @Comment("Minimum attack range (in blocks squared)")
        public ValidatedInt switchToMeleeRange = new ValidatedInt(4, 16, 0);

        // @Comment("Maximum attack range (in blocks squared)")
        public ValidatedInt maxAttackRange = new ValidatedInt(36, Integer.MAX_VALUE, 8);

        // @Comment("Minimum distance to maintain from target (in blocks). This is the distance at which the guard will try to start moving away from the target if they get too close.")
        public ValidatedInt minDistanceToMaintainFromTarget = new ValidatedInt(8, 36, 1);

        // @Comment("Minimum attack interval (ticks)")
        public ValidatedInt minAttackInterval = new ValidatedInt(3, 100, 1);

        // @Comment("Maximum attack interval (ticks)")
        public ValidatedInt maxAttackInterval = new ValidatedInt(40, 200, 4);

        // @Comment("Guard accuracy")
        public ValidatedDouble accuracy = new ValidatedDouble(0.65, 1.0, 0.1, ValidatedNumber.WidgetType.SLIDER);

        // @Comment("Time required to see target before attacking (ticks)")
        public ValidatedInt seeTimeRequired = new ValidatedInt(15, 140, 0);
    }

    // @Comment("Settings related to the guard's movement")
    public MovementSettings movementSettings = new MovementSettings();

    @Translation(prefix = ModConfigs.BASE_KEY + "guard_gunner.movement")
    public static class MovementSettings extends ConfigSection {

        // @Comment("Guard movement speed")
        public ValidatedDouble movementSpeed = new ValidatedDouble(1.0, 2.5, 0.1, ValidatedNumber.WidgetType.SLIDER);
    }

    // @Comment("Settings related to the guard's ammo consumption and reloading")
    public ReloadSettings reloadSettings = new ReloadSettings();

    @Translation(prefix = ModConfigs.BASE_KEY + "guard_gunner.reload")
    public static class ReloadSettings extends ConfigSection {

        private static final double MIN_PERCENTAGE = 0.0;
        private static final double MAX_PERCENTAGE = 0.9;

        // @Comment("Reload when ammo is below this percentage")
        public ValidatedDouble reloadThreshold = new ValidatedDouble(0.1, MAX_PERCENTAGE, MIN_PERCENTAGE, ValidatedNumber.WidgetType.SLIDER);

        // @Comment("Reload time multiplier")
        public ValidatedDouble reloadTimeMultiplier = new ValidatedDouble(1.0, 5.0, 0.1, ValidatedNumber.WidgetType.SLIDER);

        // @Comment("Whether guards have infinite ammo")
        public ValidatedBoolean infiniteGuardAmmo = new ValidatedBoolean(false);
    }

    // Retreat Settings
    // @Comment("Health percentage at which the guard retreats. Set to 0 to disable retreating.")
    public ValidatedDouble retreatHealthThreshold = new ValidatedDouble(0.2, 0.9, 0.0, ValidatedNumber.WidgetType.SLIDER);

    @Override
    public void update(int deserializedVersion) {
        // Ensure switchToMeleeRange <= minDistanceToMaintainFromTarget <= maxAttackRange
        int switchToMeleeRangeValue = attackSettings.switchToMeleeRange.get();
        int minDistanceValue = attackSettings.minDistanceToMaintainFromTarget.get();
        int maxAttackRangeValue = attackSettings.maxAttackRange.get();

        if (switchToMeleeRangeValue > minDistanceValue) {
            attackSettings.switchToMeleeRange.accept(minDistanceValue);
        }
        if (minDistanceValue > maxAttackRangeValue) {
            attackSettings.minDistanceToMaintainFromTarget.accept(maxAttackRangeValue);
        }
        if (attackSettings.switchToMeleeRange.get() > attackSettings.maxAttackRange.get()) {
            attackSettings.switchToMeleeRange.accept(attackSettings.maxAttackRange.get());
        }

        // Ensure minAttackInterval <= maxAttackInterval
        int minAttackIntervalValue = attackSettings.minAttackInterval.get();
        int maxAttackIntervalValue = attackSettings.maxAttackInterval.get();

        if (minAttackIntervalValue > maxAttackIntervalValue) {
            attackSettings.minAttackInterval.accept(maxAttackIntervalValue);
        }
    }
}