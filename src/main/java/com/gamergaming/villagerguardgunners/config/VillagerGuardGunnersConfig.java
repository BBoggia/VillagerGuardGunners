package com.gamergaming.villagerguardgunners.config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyLimits;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplySettings;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimSettings;
import com.gamergaming.villagerguardgunners.combat.tactics.ExplosiveStandoffPolicy;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfileDefinition;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseTimings;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.squad.PatrolRouteExecutionSettings;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficultySettings;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDefaults;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnRules;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import com.gamergaming.villagerguardgunners.worldgen.structure.WatchtowerGenerationPolicy;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingArmoryPolicy;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingGarrisonPolicy;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.RequiresAction;
import me.fzzyhmstrs.fzzy_config.annotations.Action;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.config.ConfigAction;
import net.minecraft.network.chat.Component;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedIdentifierMap;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedSet;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedStringMap;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.minecraft.ValidatedIdentifier;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.resources.ResourceLocation;

@Version(version = VillagerGuardGunnersConfig.CONFIG_VERSION)
@Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners")
public final class VillagerGuardGunnersConfig extends Config {
    public static final int CONFIG_VERSION = 22;

    @Comment("Enable gun combat, natural gun loadouts, command movement, squad patrols, village discovery, initial guard staffing, automatic defense duties, and Village Armory resupply. Medical treatment, Quartermaster service, and building generation have separate switches.")
    public ValidatedBoolean enabled = new ValidatedBoolean(true);

    @Comment("One-time gun and ammo loadouts for new guards. Requires Combat and Village AI. This equips guards; Initial Village Guards controls their numbers. Existing guards are not re-equipped when settings change.")
    public NaturalLoadoutSettings naturalLoadouts = new NaturalLoadoutSettings();

    @Comment("Server-authoritative limits for temporary commands and saved patrol-route execution.")
    public SquadSettings squads = new SquadSettings();

    @Comment("Automatic patrols cover ordinary villages as well as villages with Village Defense Buildings. Fixed defense posts and saved squad routes use separate controls. Readiness durations apply at the next state transition.")
    public VillageDefenseSettings villageDefense = new VillageDefenseSettings();
    @Comment("Size the initial guard allowance in new villages. Guard counts and automatic gun loadouts are separate: a village can have more guards than automatically issued guns. Later deaths do not trigger replacement guards.")
    public VillagePopulationSettings villagePopulation = new VillagePopulationSettings();
    @Comment("Treat injured guards and optionally rescue downed guards. Server permissions below work together with each guard’s Medic Duty and Medical Retreat controls. Timings affect new treatment actions; ongoing actions keep their starting duration.")
    public MedicalSettings medical = new MedicalSettings();

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.medical")
    public static final class MedicalSettings extends ConfigSection {
        @Comment("Allow manual treatment and medic care. Supplies remain accessible when this is off. Disabling treatment moves existing downed or stabilized guards into recovery.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Allow guards with Medic Duty on to treat nearby allied guards. Requires Enable Medical Treatment and supplies in the medic’s pouch. Each guard starts with Medic Duty off.")
        public ValidatedBoolean medicAi = new ValidatedBoolean(true);

        @Comment("Allow guards with Medical Retreat on to seek a safe medic at 35% health or less and resume normal behavior at 75%. Requires Medical Treatment and Medic Duty to be allowed. Individual guards start with retreat off; player movement orders take priority.")
        public ValidatedBoolean retreat = new ValidatedBoolean(true);

        @Comment("Allow two-dose rescue after lethal injuries: the first dose stabilizes and the second revives. Requires Medical Treatment and valid treatment supplies. Some damage bypasses downing. Off by default; disabling it moves existing casualties into recovery.")
        public ValidatedBoolean downed = new ValidatedBoolean(false);

        @Comment("Enable cot and field revival timings below. Requires Medical Treatment and Downed Guards. Carrying additionally requires Medic Duty to be allowed, a supplied medic, a free nearby Recovery Cot, and a usable route. Manual revival remains available when medic AI is off.")
        public ValidatedBoolean transport = new ValidatedBoolean(false);

        @Comment("Time for the second dose to revive a stabilized guard on a Recovery Cot. Default: 40 ticks = 2 seconds. Requires Medical Treatment, Downed Guards, and Cot Care and Transport. Does not change ordinary healing or the first stabilization dose. Travel, assessment, and dose cooldowns add time. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt cotRevivalTicks = new ValidatedInt(40, 600, 20);

        @Comment("Time for the second dose to revive a stabilized guard away from a cot. Default: 160 ticks = 8 seconds. Uses the larger of this value and Cot Revival Treatment Time. Requires Medical Treatment, Downed Guards, and Cot Care and Transport. Does not change ordinary healing or the first stabilization dose. Travel, assessment, and dose cooldowns add time. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt fieldRevivalTicks = new ValidatedInt(160, 600, 20);

        @Comment("Rest after revival on a Recovery Cot before returning to duty; this is not time to full health. Default: 100 ticks = 5 seconds. A blocked exit can extend the stay. Requires Medical Treatment, Downed Guards, and Cot Care and Transport. Does not change ordinary healing or the first stabilization dose. Travel, assessment, and dose cooldowns add time. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt cotRecoveryTicks = new ValidatedInt(100, 2400, 20);

        @Comment("Rest after revival away from a cot before returning to duty; this is not time to full health. Default: 400 ticks = 20 seconds. Uses the larger of this value and Rest After Cot Revival. Requires Medical Treatment, Downed Guards, and Cot Care and Transport. Does not change ordinary healing or the first stabilization dose. Travel, assessment, and dose cooldowns add time. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt fieldRecoveryTicks = new ValidatedInt(400, 2400, 20);

        // EntryTransient controls: visible in Fzzy, never saved or synchronized as settings.
        public ConfigAction careAvailability = new ConfigAction.Builder()
                .title(() -> Component.translatable("config.villagerguardgunners.medical.status."
                        + (!enabled.get() ? "treatment_off" : !downed.get() ? "downing_off"
                        : !transport.get() ? "cot_off" : !medicAi.get() ? "manual" : "allowed")))
                .active(() -> false).build(() -> {});
        public ConfigAction revivalTimes = timingPreview(cotRevivalTicks, fieldRevivalTicks);
        public ConfigAction restTimes = timingPreview(cotRecoveryTicks, fieldRecoveryTicks);

        private static ConfigAction timingPreview(ValidatedInt cot, ValidatedInt field) {
            return new ConfigAction.Builder().title(() -> Component.translatable(
                    "config.villagerguardgunners.medical.effective_times",
                    seconds(cot.get()), seconds(Math.max(cot.get(), field.get()))))
                    .active(() -> false).build(() -> {});
        }

        private static String seconds(int ticks) {
            return java.math.BigDecimal.valueOf(ticks).divide(java.math.BigDecimal.valueOf(20))
                    .stripTrailingZeros().toPlainString();
        }

    }

    @Comment("Target acquisition, range, and aiming settings. TaCZ still controls each weapon's statistics.")
    public CombatSettings combat = new CombatSettings();

    @Comment("Navigation behavior while guards are using TaCZ guns")
    public MovementSettings movement = new MovementSettings();

    @Comment("Profile selection and guard behavior controls. These settings do not change TaCZ weapon statistics.")
    public TacticalSettings tactics = new TacticalSettings();

    @Comment("Server-authoritative firing policy applied after Guard Villagers chooses a target.")
    public EngagementSettings engagement = new EngagementSettings();

    @Comment("Friendly-fire clearance settings. Core friendly-fire protection stays active.")
    public SafetySettings safety = new SafetySettings();

    @Comment("Nearby pickup and emergency sharing controls. TaCZ remains authoritative for ammo compatibility and reloading.")
    public AmmunitionSettings ammunition = new AmmunitionSettings();

    @Comment("Automatic ammo resupply from loaded Village Armories. Searches never scan the world or force chunks to load.")
    public ArmorySettings armoryResupply = new ArmorySettings();

    @Comment("Controls separate Watchtower structures in newly generated terrain. Village-integrated Watchtowers are controlled under Village Defense Buildings. Datapacks set placement frequency, guard groups, and starting stock.")
    public WatchtowerSettings watchtowers = new WatchtowerSettings();

    @Comment("Add defense buildings to newly generated villages. Layout switches require reopening the world or restarting the server. Standalone Watchtowers use separate settings; existing villages are preserved.")
    public VillageBuildingSettings villageBuildings = new VillageBuildingSettings();

    @Comment("Scheduling controls. Lower intervals react faster but require more server work.")
    public PerformanceSettings performance = new PerformanceSettings();

    public VillagerGuardGunnersConfig() {
        super(ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, "guard_gunners"));
    }

    @Override
    public int defaultPermLevel() {
        return 4;
    }

    @Override
    public void update(int previousVersion) {
        if (previousVersion < 2) {
            // Version 1 predates terrain preservation. Make the new safe
            // default explicit when Fzzy Config normalizes the old file.
            safety.preserveBlocksFromGuardExplosions.accept(true);
        }
        if (previousVersion < 3) {
            // Version 2 predates tactical profiles. New settings cannot carry
            // an intentional old value, so initialize the complete profile
            // schema while retaining every version-2 combat setting.
            tactics.restoreVersionThreeDefaults();
            safety.restoreVersionThreeDefaults();
        }
        if (previousVersion < 4) {
            // Version 4 introduces modpack ally controls. Older files have no
            // intentional values for this section, so initialize safe defaults.
            safety.allies.restoreVersionFourDefaults();
        }
        if (previousVersion < 5) {
            // Version 5 introduces rules of engagement. Preserve the previous
            // behavior for upgraded worlds by following Guard Villagers'
            // existing target selection with empty override sets.
            engagement.restoreVersionFiveDefaults();
        }
        if (previousVersion < 6) {
            // Version 6 introduces ammunition quality-of-life controls. Older
            // files have no intentional values for this section, so initialize
            // the complete independently switchable, ownership-safe defaults.
            ammunition.restoreVersionSixDefaults();
        }
        if (previousVersion < 7) {
            // Version 7 introduces natural guard loadouts. Older files have no
            // intentional values for this section, so initialize conservative,
            // structure-only defaults without changing existing guard behavior.
            naturalLoadouts.restoreVersionSevenDefaults();
        }
        if (previousVersion < 8) {
            // Version 8 introduces temporary squad-command dispatch. These
            // defaults do not alter a guard until a player deliberately issues
            // a command with a bound Squad Command Ledger.
            squads.restoreVersionEightDefaults();
        }
        if (previousVersion < 9) {
            // Version 9 introduces Armory resupply policy. Every field is new,
            // so initialize its bounded defaults while preserving all schema-8
            // combat, safety, loadout, ammunition, and squad values.
            armoryResupply.restoreVersionNineDefaults();
        }
        if (previousVersion < 10) {
            // Version 10 adds only watchtower controls. Initialize every new
            // bounded field without changing any existing schema-9 value.
            watchtowers.restoreVersionTenDefaults();
        }
        if (previousVersion < 11) {
            // Version 11 adds only the tower-specific, one-time loadout chance
            // multiplier. Existing watchtower and natural-loadout choices are
            // preserved while the new field receives its reviewed default.
            watchtowers.restoreVersionElevenDefaults();
        }
        if (previousVersion < 12) {
            // Version 12 reserves the village-building integration boundary.
            // Keep the integration default-off so migrated worlds explicitly
            // opt into Step 5's still-manual visual and compatibility review.
            villageBuildings.restoreVersionTwelveDefaults();
        }
        if (previousVersion < 13) {
            // Version 13 adds one-way per-building suppression. Preserve the
            // schema-12 master choice and initialize only the five new flags.
            villageBuildings.restoreVersionThirteenDefaults();
        }
        if (previousVersion < 14) {
            // Version 14 activates only one-shot village garrisons. Preserve
            // every existing building choice and initialize the new bounds.
            villageBuildings.restoreVersionFourteenDefaults();
        }
        if (previousVersion < 15) {
            // Version 15 activates only generated village Armories. Preserve
            // every prior building and garrison choice while adding the two
            // independent, one-way Armory controls.
            villageBuildings.restoreVersionFifteenDefaults();
        }
        if (previousVersion < 16) {
            // Version 16 adds only defense-duty and patrol execution policy.
            // Persisted routes, squads, readiness state, and every existing
            // administrator choice remain unchanged.
            villageDefense.restoreVersionSixteenDefaults();
            squads.restoreVersionSixteenDefaults();
        }
        if (previousVersion < 22) {
            // Initialize only the new aiming controls, preserving existing combat settings.
            combat.aiming.restoreDefaults();
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.squads")
    public static final class SquadSettings extends ConfigSection {

        @Comment("Maximum distance from the issuing player to loaded squad members that can receive a command. Farther or unloaded members keep their current orders.")
        public ValidatedDouble commandDispatchRadius = new ValidatedDouble(
                48.0D,
                128.0D,
                8.0D);

        @Comment("Hard limit on authorized squad members changed by one command.")
        public ValidatedInt maximumAffectedGuards = new ValidatedInt(32, 64, 1);

        @Comment("Lifetime in ticks for Rally, Defend, Hold, and Fall Back commands. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt positionalCommandDurationTicks = new ValidatedInt(
                1200,
                72000,
                20);

        @Comment("Lifetime in ticks for Focus Target commands. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt focusCommandDurationTicks = new ValidatedInt(
                400,
                12000,
                20);

        @Comment("Distance guards can move from a Defend command point.")
        public ValidatedDouble defendRadius = new ValidatedDouble(
                12.0D,
                64.0D,
                2.0D);

        @Comment("Distance ahead of you used to choose a command point when you issue Rally, Defend, Hold, or Fall Back while looking into the air.")
        public ValidatedDouble airCommandRange = new ValidatedDouble(
                32.0D,
                64.0D,
                4.0D);

        @Comment("Allow guards to follow routes saved with a Squad Command Ledger. Requires Combat and Village AI. Routes stay saved and editable when this is off. Automatic village patrols and building defense posts use separate switches.")
        public ValidatedBoolean patrolRouteExecution = new ValidatedBoolean(true);

        @Comment("Navigation speed modifier while guards follow squad patrol routes. A speed factor: 1 uses the guard’s normal movement speed, 0.5 uses half. This is not blocks per second.")
        public ValidatedDouble patrolMovementSpeed = new ValidatedDouble(
                PatrolRouteExecutionSettings.DEFAULT.movementSpeed(),
                PatrolRouteExecutionSettings.MAXIMUM_MOVEMENT_SPEED,
                PatrolRouteExecutionSettings.MINIMUM_MOVEMENT_SPEED);

        @Comment("Ticks a guard pauses after reaching a patrol waypoint before continuing. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt patrolWaypointDwellTicks = new ValidatedInt(
                PatrolRouteExecutionSettings.DEFAULT.waypointDwellTicks(),
                PatrolRouteExecutionSettings.MAXIMUM_DWELL_TICKS,
                PatrolRouteExecutionSettings.MINIMUM_DWELL_TICKS);

        @Comment("Failed or stalled path attempts allowed before a guard skips one unreachable waypoint.")
        public ValidatedInt patrolMaximumFailuresPerWaypoint = new ValidatedInt(
                PatrolRouteExecutionSettings.DEFAULT.maximumFailuresPerWaypoint(),
                PatrolRouteExecutionSettings.MAXIMUM_FAILURES_PER_WAYPOINT,
                PatrolRouteExecutionSettings.MINIMUM_FAILURES_PER_WAYPOINT);

        public PatrolRouteExecutionSettings patrolRouteSettings(boolean globalEnabled) {
            return new PatrolRouteExecutionSettings(
                    globalEnabled && patrolRouteExecution.get(),
                    patrolMovementSpeed.get(),
                    patrolWaypointDwellTicks.get(),
                    patrolMaximumFailuresPerWaypoint.get());
        }

        private void restoreVersionEightDefaults() {
            restoreDefault(commandDispatchRadius);
            restoreDefault(maximumAffectedGuards);
            restoreDefault(positionalCommandDurationTicks);
            restoreDefault(focusCommandDurationTicks);
            restoreDefault(defendRadius);
            restoreDefault(airCommandRange);
        }

        private void restoreVersionSixteenDefaults() {
            restoreDefault(patrolRouteExecution);
            restoreDefault(patrolMovementSpeed);
            restoreDefault(patrolWaypointDwellTicks);
            restoreDefault(patrolMaximumFailuresPerWaypoint);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.villagePopulation")
    public static final class VillagePopulationSettings extends ConfigSection {
        @Comment("Size initial guard numbers in new villages, including ordinary villages. Requires Combat and Village AI. Existing villages keep their guards; deaths do not trigger replacements. Spawn sites and Guard Villagers settings can limit the actual count. Off by default.")
        public ValidatedBoolean managedGeneration = new ValidatedBoolean(false);
        @Comment("Lower limit for the size-based initial target. Must not exceed Maximum Initial Guard Target. Valid spawn sites can limit the actual count. Requires Scale Initial Guards to Village Size.")
        public RelatedIntSetting minimumGuards = new RelatedIntSetting(2, 32, 0,
                value -> value <= this.maximumGuards.get(), "config.villagerguardgunners.validation.population");
        @Comment("Upper limit for initial ordinary guards and building garrisons together. Must be at least Minimum Initial Guard Target. For a zero managed target, set both limits to 0. Does not limit player-created guards. Requires Scale Initial Guards to Village Size.")
        public RelatedIntSetting maximumGuards = new RelatedIntSetting(16, 32, 0,
                value -> value >= this.minimumGuards.get(), "config.villagerguardgunners.validation.population");
        @Comment("Add one to the initial target per this many classified residences, rounded up. Higher values give fewer guards. Later beds do not increase the allowance. Requires Scale Initial Guards to Village Size.")
        public ValidatedInt homesPerGuard = new ValidatedInt(3, 64, 1);
        @Comment("Add one to the initial target per this many occupied horizontal 32 × 32 block cells, rounded up. Cells contain classified house or defense-building centers; roads alone do not count. Higher values give fewer guards. Requires Scale Initial Guards to Village Size.")
        public ValidatedInt areasPerGuard = new ValidatedInt(2, 64, 1);
        @Comment("Maximum added to the initial target by buildings that can generate garrisons, at one guard per building. The maximum village target still applies. Requires Scale Initial Guards to Village Size.")
        public ValidatedInt defenseBonusCap = new ValidatedInt(2, 32, 0);
        @Comment("Add this many to the initial target on Hard difficulty, within Maximum Initial Guard Target. Other difficulties get no extra bonus. Requires Scale Initial Guards to Village Size.")
        public ValidatedInt hardDifficultyBonus = new ValidatedInt(0, 32, 0);
        @Comment("Count these exact structure template IDs as residences, up to 128 entries. Guard Villagers must also permit guard spawns in those templates. Example: minecraft:village/plains/houses/plains_small_house_1. Requires Scale Initial Guards to Village Size.")
        public ValidatedSet<ResourceLocation> residentialTemplates = new ValidatedSet<>(java.util.Set.of(),
                new ValidatedIdentifier(ResourceLocation.parse("minecraft:village/plains/houses/plains_small_house_1")));
        public com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan.Settings settings() {
            return new com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan.Settings(
                    minimumGuards.get(), maximumGuards.get(), homesPerGuard.get(), areasPerGuard.get(), defenseBonusCap.get());
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.villageDefense")
    public static final class VillageDefenseSettings extends ConfigSection {

        @Comment("Send available village residents around outdoor patrol areas, including ordinary villages. Requires Combat and Village AI. Guards need a weapon and at least half health. Manual squads, follow/hold/patrol orders, and other work take priority. Does not spawn or equip guards. Off by default.")
        public ValidatedBoolean automaticCoverage = new ValidatedBoolean(false);

        @Comment("Assign available guards to fixed posts at generated Village Defense Buildings. Requires Combat and Village AI. This is separate from automatic village patrols and saved squad routes. Turning it off preserves saved buildings and orders.")
        public ValidatedBoolean automaticDuties = new ValidatedBoolean(true);

        @Comment("Quiet ticks after a bell before the village returns from Watch to Peace. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt watchDurationTicks = new ValidatedInt(600, 72_000, 20);

        @Comment("Quiet ticks after a confirmed threat before the village enters Recovery. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt alertDurationTicks = new ValidatedInt(1_200, 72_000, 20);

        @Comment("Quiet time after Alert before the village returns to Peace. This is village readiness, not medical recovery. Existing deadlines keep their starting duration. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt recoveryDurationTicks = new ValidatedInt(600, 72_000, 20);

        @Comment("Navigation speed modifier while guards travel to automatic defense posts. A speed factor: 1 uses the guard’s normal movement speed, 0.5 uses half. This is not blocks per second.")
        public ValidatedDouble dutyMovementSpeed = new ValidatedDouble(0.8D, 2.0D, 0.1D);

        public VillageDefenseTimings timings() {
            return new VillageDefenseTimings(
                    watchDurationTicks.get(),
                    alertDurationTicks.get(),
                    recoveryDurationTicks.get());
        }

        private void restoreVersionSixteenDefaults() {
            restoreDefault(automaticDuties);
            restoreDefault(watchDurationTicks);
            restoreDefault(alertDurationTicks);
            restoreDefault(recoveryDurationTicks);
            restoreDefault(dutyMovementSpeed);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.naturalLoadouts")
    public static final class NaturalLoadoutSettings extends ConfigSection {

        @Comment("Allow eligible newly spawned guards to replace their initial weapon with a datapack-defined TaCZ loadout.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Choose which initial spawn origins can receive automatic loadouts. Only structure spawning is enabled by default.")
        public ValidatedSet<NaturalGuardSpawnSource> enabledSpawnSources = new ValidatedSet<>(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.enabledSources(),
                new ValidatedEnum<>(NaturalGuardSpawnSource.STRUCTURE));

        @Comment("Protect equipment from other mods by replacing only a guard's initial crossbow.")
        public ValidatedBoolean replaceCrossbowsOnly = new ValidatedBoolean(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.replaceCrossbowsOnly());

        @Comment("Radius around each new guard used to count guards with this add-on’s automatic gun loadouts, including recorded guards in unloaded chunks. Manually equipped guards do not count.")
        public ValidatedDouble nearbyCapRadius = new ValidatedDouble(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.nearbyCapRadius(),
                256.0D,
                8.0D);

        @Comment("Allow datapack pools to issue weapons TaCZ identifies as explosive. All native blast safety remains active.")
        public ValidatedBoolean allowExplosiveWeapons = new ValidatedBoolean(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.allowExplosiveWeapons());

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.naturalLoadouts.easy")
        public NaturalDifficultySettings easy = new NaturalDifficultySettings(
                NaturalGuardLoadoutDefaults.settingsFor(NaturalGuardDifficulty.EASY));

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.naturalLoadouts.normal")
        public NaturalDifficultySettings normal = new NaturalDifficultySettings(
                NaturalGuardLoadoutDefaults.settingsFor(NaturalGuardDifficulty.NORMAL));

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.naturalLoadouts.hard")
        public NaturalDifficultySettings hard = new NaturalDifficultySettings(
                NaturalGuardLoadoutDefaults.settingsFor(NaturalGuardDifficulty.HARD));

        public NaturalGuardSpawnRules spawnRules() {
            return new NaturalGuardSpawnRules(
                    Set.<NaturalGuardSpawnSource>copyOf(enabledSpawnSources.get()),
                    replaceCrossbowsOnly.get(),
                    nearbyCapRadius.get(),
                    allowExplosiveWeapons.get());
        }

        public NaturalGuardDifficultySettings settingsFor(NaturalGuardDifficulty difficulty) {
            return switch (difficulty) {
                case PEACEFUL -> NaturalGuardLoadoutDefaults.settingsFor(
                        NaturalGuardDifficulty.PEACEFUL);
                case EASY -> easy.settings();
                case NORMAL -> normal.settings();
                case HARD -> hard.settings();
            };
        }

        private void restoreVersionSevenDefaults() {
            restoreDefault(enabled);
            restoreDefault(enabledSpawnSources);
            restoreDefault(replaceCrossbowsOnly);
            restoreDefault(nearbyCapRadius);
            restoreDefault(allowExplosiveWeapons);
            easy.restore();
            normal.restore();
            hard.restore();
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.naturalLoadouts.difficulty")
    public static final class NaturalDifficultySettings extends ConfigSection {

        @Comment("Chance for a new guard to receive a gun loadout: 0 = 0%, 0.5 = 50%, 1 = 100%. The nearby automatic-gunner cap, spawn-source rules, and available loadout pool still apply.")
        public ValidatedDouble upgradeChance;

        @Comment("Maximum guards with automatic gun loadouts within Automatic Gunner Count Radius. Set to 0 to disable automatic loadouts for this difficulty. This is not a cap on all guards or manually supplied guns.")
        public ValidatedInt maximumNearby;

        @Comment("Datapack loadout pool for this difficulty, such as villagerguardgunners:normal. Use a namespaced pool ID; missing or invalid pools leave the guard’s starting equipment unchanged.")
        public ValidatedIdentifier poolId;

        private NaturalDifficultySettings(NaturalGuardDifficultySettings defaults) {
            if (defaults.poolId() == null) {
                throw new IllegalArgumentException(
                        "Configurable natural-loadout difficulty requires a pool ID");
            }
            upgradeChance = new ValidatedDouble(defaults.upgradeChance(), 1.0D, 0.0D);
            maximumNearby = new ValidatedInt(defaults.maximumNearby(), 32, 0);
            poolId = new ValidatedIdentifier(defaults.poolId());
        }

        private NaturalGuardDifficultySettings settings() {
            return new NaturalGuardDifficultySettings(
                    upgradeChance.get(),
                    maximumNearby.get(),
                    poolId.get());
        }

        private void restore() {
            restoreDefault(upgradeChance);
            restoreDefault(maximumNearby);
            restoreDefault(poolId);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.combat")
    public static final class CombatSettings extends ConfigSection {

        @Comment("Base time a guard must see its target before firing. The active tactical profile can add or subtract time; the result stays between 0 and 40 ticks. Setting this to 0 does not remove a profile’s added delay. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt requiredVisibleTicks = new ValidatedInt(5, 40, 0);

        @Comment("Maximum distance at which a guard can fire, in blocks. TaCZ effective range still controls the guard's preferred combat distance and damage falloff.")
        public ValidatedDouble maximumEngagementRange = new ValidatedDouble(32.0D, 64.0D, 8.0D);

        @Comment("Base distance at which the guard tries to retreat from a close target. The active tactical profile multiplies this distance, and explosive guns also require safe blast distance.")
        public ValidatedDouble closeRangeDistance = new ValidatedDouble(4.0D, 8.0D, 1.0D);

        @Comment("Maximum difference between the guard’s facing direction and its intended target direction before firing. Smaller values require closer alignment. Tactical profiles adjust this threshold within 1–45 degrees. This does not add aim error or change gun spread.")
        public ValidatedDouble aimToleranceDegrees = new ValidatedDouble(10.0D, 45.0D, 1.0D);

        @Comment("Adjust guard aim error and aim point. Player overrides apply only when enabled.")
        public AimingSettings aiming = new AimingSettings();
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.combat.aiming")
    public static final class AimingSettings extends ConfigSection {
        @Comment("Maximum guard aiming error in degrees before TaCZ applies gun spread. Zero adds no guard error; it does not remove gun spread. This is not a hit percentage.")
        public ValidatedDouble errorDegrees = new ValidatedDouble(0.0D, GuardAimSettings.MAX_ERROR_DEGREES, 0.0D);

        @Comment("Eye Level aims at the target's eyes. Center Mass aims at the center of its body bounds and can hit low cover even when the head is visible.")
        public ValidatedEnum<GuardAimPoint> aimPoint = new ValidatedEnum<>(GuardAimPoint.EYE_LEVEL);

        @Comment("Use separate aim error and aim point for player targets. When off, players inherit the general settings. Does not change which players guards can attack.")
        public ValidatedBoolean overridePlayers = new ValidatedBoolean(false);

        @Comment("Aim error for player targets, used only when Override Against Players is on. Start with 2 degrees and Center Mass, then adjust for your server. Groups of guards can still defeat players quickly.")
        public ValidatedDouble playerErrorDegrees = new ValidatedDouble(2.0D, GuardAimSettings.MAX_ERROR_DEGREES, 0.0D);

        @Comment("Aim point for player targets, used only when Override Against Players is on. Center Mass is the recommended starting point for player tuning; low cover can block it.")
        public ValidatedEnum<GuardAimPoint> playerAimPoint = new ValidatedEnum<>(GuardAimPoint.CENTER_MASS);

        /** Resolves numerical policy only; callers must separately enforce rules of engagement. */
        public GuardAimSettings resolve(boolean playerTarget) {
            boolean override = playerTarget && overridePlayers.get();
            return new GuardAimSettings(override ? playerErrorDegrees.get() : errorDegrees.get(),
                    override ? playerAimPoint.get() : aimPoint.get());
        }

        private void restoreDefaults() {
            restoreDefault(errorDegrees);
            restoreDefault(aimPoint);
            restoreDefault(overridePlayers);
            restoreDefault(playerErrorDegrees);
            restoreDefault(playerAimPoint);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.movement")
    public static final class MovementSettings extends ConfigSection {

        @Comment("Navigation speed modifier while approaching a target. A speed factor: 1 uses the guard’s normal movement speed, 0.5 uses half. This is not blocks per second.")
        public ValidatedDouble approachSpeed = new ValidatedDouble(1.0D, 2.0D, 0.1D);

        @Comment("Navigation speed modifier while retreating from a target that is too close. A speed factor: 1 uses the guard’s normal movement speed, 0.5 uses half. This is not blocks per second.")
        public ValidatedDouble retreatSpeed = new ValidatedDouble(0.75D, 1.0D, 0.1D);

        @Comment("Navigation speed modifier while finding a safe firing lane. A speed factor: 1 uses the guard’s normal movement speed, 0.5 uses half. This is not blocks per second.")
        public ValidatedDouble repositionSpeed = new ValidatedDouble(0.75D, 1.0D, 0.1D);

        @Comment("Ticks before retrying a firing lane blocked by a protected entity. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt friendlyRepositionTicks = new ValidatedInt(20, 100, 5);
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics")
    public static final class TacticalSettings extends ConfigSection {

        @Comment("Map TaCZ gun type labels to tactical profiles. Keys ignore surrounding whitespace and capitalization; conflicting aliases fall back safely.")
        public ValidatedStringMap<TacticalProfile> typeProfiles =
                new ValidatedStringMap.Builder<TacticalProfile>()
                        .keyHandler(new ValidatedString())
                        .valueHandler(new ValidatedEnum<>(TacticalProfile.GENERALIST))
                        .defaults(defaultTypeProfiles())
                        .build();

        @Comment("Choose a profile for an exact TaCZ gun ID. Explosive safety cannot be disabled by an override.")
        public ValidatedIdentifierMap<TacticalProfile> gunOverrides =
                new ValidatedIdentifierMap.Builder<TacticalProfile>()
                        .keyHandler(new ValidatedIdentifier())
                        .valueHandler(new ValidatedEnum<>(TacticalProfile.GENERALIST))
                        .defaults(new LinkedHashMap<>())
                        .build();

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.generalist")
        public ProfileSettings generalist = new ProfileSettings(TacticalProfile.GENERALIST);

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.skirmisher")
        public ProfileSettings skirmisher = new ProfileSettings(TacticalProfile.SKIRMISHER);

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.breacher")
        public ProfileSettings breacher = new ProfileSettings(TacticalProfile.BREACHER);

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.marksman")
        public ProfileSettings marksman = new ProfileSettings(TacticalProfile.MARKSMAN);

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.support")
        public ProfileSettings support = new ProfileSettings(TacticalProfile.SUPPORT);

        @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.demolitions")
        public ProfileSettings demolitions = new ProfileSettings(TacticalProfile.DEMOLITIONS);

        public TacticalProfileDefinition definition(TacticalProfile profile) {
            return settings(profile).definition(profile);
        }

        private ProfileSettings settings(TacticalProfile profile) {
            return switch (profile) {
                case GENERALIST -> generalist;
                case SKIRMISHER -> skirmisher;
                case BREACHER -> breacher;
                case MARKSMAN -> marksman;
                case SUPPORT -> support;
                case DEMOLITIONS -> demolitions;
            };
        }

        private void restoreVersionThreeDefaults() {
            restoreDefault(typeProfiles);
            restoreDefault(gunOverrides);
            generalist.restore();
            skirmisher.restore();
            breacher.restore();
            marksman.restore();
            support.restore();
            demolitions.restore();
        }

        private static Map<String, TacticalProfile> defaultTypeProfiles() {
            Map<String, TacticalProfile> defaults = new LinkedHashMap<>();
            defaults.put("pistol", TacticalProfile.SKIRMISHER);
            defaults.put("smg", TacticalProfile.SKIRMISHER);
            defaults.put("rifle", TacticalProfile.GENERALIST);
            defaults.put("sniper", TacticalProfile.MARKSMAN);
            defaults.put("shotgun", TacticalProfile.BREACHER);
            defaults.put("mg", TacticalProfile.SUPPORT);
            defaults.put("rpg", TacticalProfile.DEMOLITIONS);
            return defaults;
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics.profile")
    public static final class ProfileSettings extends ConfigSection {
        @Comment("Multiply the gun’s TaCZ effective range to choose an approach distance. 1 = unchanged, 0.5 = half, 2 = double. The result stays within the guard’s retreat and maximum firing distances.")
        public ValidatedDouble preferredRangeMultiplier;

        @Comment("Multiply Base Combat Retreat Distance. 1 = unchanged, 2 = double. Explosive blast clearance can require more distance; non-explosive movement stays within the firing limit.")
        public ValidatedDouble retreatDistanceMultiplier;

        @Comment("Add to or subtract from Base Visibility Delay. Positive values delay firing; negative values shorten the delay. The final delay stays between 0 and 40 ticks. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt visibleTicksAdjustment;

        @Comment("Multiply Base Aim Tolerance. Smaller values require more precise aim; 0.5 halves the allowed error. Final tolerance stays between 1 and 45 degrees. Does not change gun spread.")
        public ValidatedDouble aimToleranceMultiplier;

        @Comment("Also limit firing distance using TaCZ effective range instead of only the global hard cap.")
        public ValidatedBoolean limitFiringToEffectiveRange;

        @Comment("Multiply TaCZ effective range when Use Effective-Range Firing Limit is on. Has no effect while that option is off. 1 = unchanged, 2 = double; the server and individual guard firing caps still apply.")
        public ValidatedDouble maximumFiringRangeMultiplier;

        private ProfileSettings(TacticalProfile profile) {
            TacticalProfileDefinition defaults = TacticalProfileDefinition.defaultsFor(profile);
            preferredRangeMultiplier = boundedMultiplier(defaults.preferredRangeMultiplier());
            retreatDistanceMultiplier = boundedMultiplier(defaults.retreatDistanceMultiplier());
            visibleTicksAdjustment = new ValidatedInt(defaults.visibleTicksAdjustment(), 40, -40);
            aimToleranceMultiplier = boundedMultiplier(defaults.aimToleranceMultiplier());
            limitFiringToEffectiveRange = new ValidatedBoolean(
                    defaults.limitFiringToEffectiveRange());
            maximumFiringRangeMultiplier = boundedMultiplier(
                    defaults.maximumFiringRangeMultiplier());
        }

        private TacticalProfileDefinition definition(TacticalProfile profile) {
            return new TacticalProfileDefinition(
                    profile,
                    preferredRangeMultiplier.get(),
                    retreatDistanceMultiplier.get(),
                    visibleTicksAdjustment.get(),
                    aimToleranceMultiplier.get(),
                    limitFiringToEffectiveRange.get(),
                    maximumFiringRangeMultiplier.get());
        }

        private void restore() {
            restoreDefault(preferredRangeMultiplier);
            restoreDefault(retreatDistanceMultiplier);
            restoreDefault(visibleTicksAdjustment);
            restoreDefault(aimToleranceMultiplier);
            restoreDefault(limitFiringToEffectiveRange);
            restoreDefault(maximumFiringRangeMultiplier);
        }

        private static ValidatedDouble boundedMultiplier(double defaultValue) {
            return new ValidatedDouble(defaultValue, 4.0D, 0.25D);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.engagement")
    public static final class EngagementSettings extends ConfigSection {

        @Comment("Choose whether guards follow normal Guard Villagers targets, restrict proactive fire to hostile mobs, retaliate defensively, or hold fire.")
        public ValidatedEnum<EngagementMode> mode =
                new ValidatedEnum<>(EngagementMode.GUARD_TARGETS);

        @Comment("Ticks after an attack during which DEFENSIVE guards can engage the attacker. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt defensiveMemoryTicks = new ValidatedInt(200, 1200, 20);

        @Comment("Allow these entity types in restricted firing modes if Guard Villagers already selects them as targets. Does not make guards search for them. Hold Fire, target exclusions, and ally protection still take priority. Example: minecraft:zombie.")
        public ValidatedSet<ResourceLocation> additionalTargets =
                new ValidatedSet<>(Set.of(), new ValidatedIdentifier());

        @Comment("Prevent guards from targeting these entity types with guns. Takes priority over Additional Allowed Target Types. Example: minecraft:creeper. This is a targeting rule, not general protection from stray shots.")
        public ValidatedSet<ResourceLocation> excludedEntities =
                new ValidatedSet<>(Set.of(), new ValidatedIdentifier());

        private void restoreVersionFiveDefaults() {
            restoreDefault(mode);
            restoreDefault(defensiveMemoryTicks);
            restoreDefault(additionalTargets);
            restoreDefault(excludedEntities);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.safety")
    public static final class SafetySettings extends ConfigSection {

        @Comment("Extra clearance around protected entities when validating a shot, in blocks.")
        public ValidatedDouble baseClearance = new ValidatedDouble(0.75D, 4.0D, 0.25D);

        @Comment("Prevent guard-owned TaCZ projectile explosions from destroying blocks while retaining their native effects on hostile entities.")
        public ValidatedBoolean preserveBlocksFromGuardExplosions = new ValidatedBoolean(true);

        @Comment("Multiply the resolved TaCZ explosion radius when calculating the guard's required minimum firing distance.")
        public ValidatedDouble explosiveStandoffRadiusMultiplier = new ValidatedDouble(
                ExplosiveStandoffPolicy.DEFAULT.radiusMultiplier(),
                8.0D,
                1.0D);

        @Comment("Additional blocks added after the explosion-radius multiplier when calculating minimum firing distance.")
        public ValidatedDouble explosiveStandoffAdditionalBlocks = new ValidatedDouble(
                ExplosiveStandoffPolicy.DEFAULT.additionalBlocks(),
                32.0D,
                0.0D);

        @Comment("Entity tags, explicit type lists, ownership, and team controls shared by every guard-gun safety layer.")
        public AllySettings allies = new AllySettings();

        public ExplosiveStandoffPolicy explosiveStandoffPolicy() {
            return new ExplosiveStandoffPolicy(
                    explosiveStandoffRadiusMultiplier.get(),
                    explosiveStandoffAdditionalBlocks.get());
        }

        private void restoreVersionThreeDefaults() {
            restoreDefault(explosiveStandoffRadiusMultiplier);
            restoreDefault(explosiveStandoffAdditionalBlocks);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.safety.allies")
    public static final class AllySettings extends ConfigSection {

        @Comment("Protect living mobs that have an owner, including pets belonging to other players and compatible modded mobs. Types listed under Remove Ally Protection for These Types are excluded.")
        public ValidatedBoolean protectOwnedEntities = new ValidatedBoolean(true);

        @Comment("Protect living entities Minecraft considers allied to the guard, including entities on the same scoreboard team.")
        public ValidatedBoolean protectAlliedEntities = new ValidatedBoolean(true);

        @Comment("Namespaced living entity type IDs to protect in addition to the guard_allies datapack tag.")
        public ValidatedSet<ResourceLocation> additionalProtectedEntities =
                new ValidatedSet<>(Set.of(), new ValidatedIdentifier());

        @Comment("Remove protection from the listed entity types, including protection from tags, villager/golem status, ownership, teams, and compatible mods. Guards and the shooter’s owner stay protected. This does not automatically make an entity a target.")
        public ValidatedSet<ResourceLocation> excludedEntities =
                new ValidatedSet<>(Set.of(), new ValidatedIdentifier());

        private void restoreVersionFourDefaults() {
            restoreDefault(protectOwnedEntities);
            restoreDefault(protectAlliedEntities);
            restoreDefault(additionalProtectedEntities);
            restoreDefault(excludedEntities);
        }
    }

    private static <T> void restoreDefault(ValidatedField<T> field) {
        field.accept(field.copyValue(field.getDefault()));
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.ammunition")
    public static final class AmmunitionSettings extends ConfigSection {

        @Comment("Allow guards to collect compatible TaCZ ammo they naturally walk over without pathfinding to it. Requires the mobGriefing game rule to be on and this guard’s Ammo Pickup order to allow it.")
        public ValidatedBoolean pickupDroppedAmmo = new ValidatedBoolean(true);

        @Comment("Maximum passive pickup distance in blocks. Smaller values reduce the area checked around dropped ammo.")
        public ValidatedDouble droppedAmmoPickupRadius = new ValidatedDouble(1.5D, 3.0D, 0.5D);

        @Comment("Allow a dry guard to request compatible loose ammo from a nearby eligible guard.")
        public ValidatedBoolean shareAmmoBetweenGuards = new ValidatedBoolean(true);

        @Comment("Maximum emergency sharing distance in blocks. Searches occur only after a guard runs out of compatible reserve ammo.")
        public ValidatedDouble ammoSharingRadius = new ValidatedDouble(8.0D, 16.0D, 1.0D);

        @Comment("Spare ammo a donor keeps after sharing, measured in full magazines for the donor’s gun and attachments. Loaded rounds do not count toward this reserve.")
        public ValidatedInt donorReserveMagazines = new ValidatedInt(1, 4, 0);

        @Comment("Restrict sharing to guards with the same owner while still allowing two unowned guards to share.")
        public ValidatedBoolean requireMatchingOwnerForSharing = new ValidatedBoolean(true);

        private void restoreVersionSixDefaults() {
            restoreDefault(pickupDroppedAmmo);
            restoreDefault(droppedAmmoPickupRadius);
            restoreDefault(shareAmmoBetweenGuards);
            restoreDefault(ammoSharingRadius);
            restoreDefault(donorReserveMagazines);
            restoreDefault(requireMatchingOwnerForSharing);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.armoryResupply")
    public static final class ArmorySettings extends ConfigSection {

        @Comment("Let otherwise-idle guards with low spare ammo visit accessible Village Armories in loaded chunks. Requires Combat and Village AI. Quartermaster service is configured separately.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Ticks between staggered ammo-need assessments. Higher values reduce server work but react more slowly. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt checkIntervalTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_CHECK_INTERVAL_TICKS,
                ArmoryResupplyLimits.MAX_CHECK_INTERVAL_TICKS,
                ArmoryResupplyLimits.MIN_CHECK_INTERVAL_TICKS);

        @Comment("Maximum distance for finding Village Armories in loaded chunks. The search does not load distant chunks.")
        public ValidatedDouble searchRadius = new ValidatedDouble(
                48.0D,
                ArmoryResupplyLimits.HARD_MAX_SEARCH_RADIUS,
                ArmoryResupplyLimits.MIN_SEARCH_RADIUS);

        @Comment("Maximum nearby loaded Village Armories checked for one resupply request. A compatible source can be selected from these candidates; Armories beyond this limit are not checked in that search.")
        public ValidatedInt maximumCandidateArmories = new ValidatedInt(
                8,
                ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES,
                1);

        @Comment("Request resupply when spare ammo falls below this many magazines for the current gun and attachments. Loaded rounds do not count. 0 disables automatic requests. Must not exceed Target Spare Ammo.")
        public RelatedIntSetting lowReserveMagazines = new RelatedIntSetting(
                ArmoryResupplyLimits.DEFAULT_LOW_RESERVE_MAGAZINES,
                ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES,
                0,
                value -> value <= this.targetReserveMagazines.get(), "config.villagerguardgunners.validation.armory");

        @Comment("Desired spare ammo after resupply, measured in magazines using the guard’s current gun and attachments. Must be at least Low-Ammo Trigger. A lower value stops automatic Armory resupply. Loaded rounds are not spare ammo.")
        public RelatedIntSetting targetReserveMagazines = new RelatedIntSetting(
                ArmoryResupplyLimits.DEFAULT_TARGET_RESERVE_MAGAZINES,
                ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES,
                0,
                value -> value >= this.lowReserveMagazines.get(), "config.villagerguardgunners.validation.armory");

        @Comment("Navigation speed used only while travelling toward an already-reserved Village Armory. A speed factor: 1 uses the guard’s normal movement speed, 0.5 uses half. This is not blocks per second.")
        public ValidatedDouble movementSpeed = new ValidatedDouble(
                ArmoryResupplyLimits.DEFAULT_RESUPPLY_MOVEMENT_SPEED,
                ArmoryResupplyLimits.HARD_MAX_RESUPPLY_MOVEMENT_SPEED,
                ArmoryResupplyLimits.MIN_RESUPPLY_MOVEMENT_SPEED);

        @Comment("Maximum lifetime in ticks of one active ammo request. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt requestLifetimeTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_REQUEST_LIFETIME_TICKS,
                ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Maximum time in ticks that one guard can reserve a specific Armory supply slot. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt reservationLifetimeTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Delay in ticks after a successful withdrawal before the guard can reassess Armory need. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt successCooldownTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_SUCCESS_COOLDOWN_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Delay in ticks after no compatible loaded Village Armory can be selected. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt noSupplyRetryTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_NO_SUPPLY_RETRY_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Base delay in ticks after a guard cannot reach a reserved Village Armory. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt unreachableRetryTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_UNREACHABLE_RETRY_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        public ArmoryResupplySettings snapshot(boolean parentEnabled) {
            return new ArmoryResupplySettings(
                    parentEnabled && enabled.get(),
                    checkIntervalTicks.get(),
                    searchRadius.get(),
                    maximumCandidateArmories.get(),
                    lowReserveMagazines.get(),
                    targetReserveMagazines.get(),
                    movementSpeed.get(),
                    requestLifetimeTicks.get(),
                    reservationLifetimeTicks.get(),
                    successCooldownTicks.get(),
                    noSupplyRetryTicks.get(),
                    unreachableRetryTicks.get());
        }

        private void restoreVersionNineDefaults() {
            restoreDefault(enabled);
            restoreDefault(checkIntervalTicks);
            restoreDefault(searchRadius);
            restoreDefault(maximumCandidateArmories);
            restoreDefault(lowReserveMagazines);
            restoreDefault(targetReserveMagazines);
            restoreDefault(movementSpeed);
            restoreDefault(requestLifetimeTicks);
            restoreDefault(reservationLifetimeTicks);
            restoreDefault(successCooldownTicks);
            restoreDefault(noSupplyRetryTicks);
            restoreDefault(unreachableRetryTicks);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.watchtowers")
    public static final class WatchtowerSettings extends ConfigSection {

        @Comment("Allow new standalone Village Watchtowers in newly generated terrain. Existing towers and already selected structure starts are preserved. Village-integrated Watchtowers use a separate switch.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Allow guard markers in newly placed Watchtower pieces to create Guard Villagers.")
        public ValidatedBoolean spawnGuards = new ValidatedBoolean(true);

        @Comment("Maximum guards created from one Watchtower guard marker, from 0 through 4.")
        public ValidatedInt maximumGuardsPerPost = new ValidatedInt(
                WatchtowerGenerationPolicy.MAXIMUM_GUARDS_PER_POST,
                WatchtowerGenerationPolicy.MAXIMUM_GUARDS_PER_POST,
                WatchtowerGenerationPolicy.MINIMUM_GUARDS_PER_POST);

        @Comment("Allow Armory markers in newly placed Watchtower pieces to create Village Armories.")
        public ValidatedBoolean placeArmory = new ValidatedBoolean(true);

        @Comment("Allow newly generated Watchtower Armories to receive their datapack-defined initial ammo stock.")
        public ValidatedBoolean stockArmory = new ValidatedBoolean(true);

        @Comment("Multiply each new Watchtower guard's normal difficulty-based gun chance once, then clamp the result to 100%. This does not bypass loadout eligibility, nearby caps, datapack pools, inventory limits, or weapon safety. 1 = the base chance; 1.5 = one and a half times that chance. 0 prevents these gun upgrades.")
        public ValidatedDouble guardGunChanceMultiplier = new ValidatedDouble(
                1.5D,
                3.0D,
                0.0D);

        /**
         * Applies the tower-only chance modifier without widening any other
         * natural-loadout eligibility or safety boundary.
         */
        public double guardGunUpgradeChance(double baseChance) {
            if (!Double.isFinite(baseChance) || baseChance < 0.0D || baseChance > 1.0D) {
                throw new IllegalArgumentException("Base gun chance must be finite and in [0, 1]");
            }
            return Math.min(1.0D, baseChance * guardGunChanceMultiplier.get());
        }

        public WatchtowerGenerationPolicy snapshot() {
            return new WatchtowerGenerationPolicy(
                    enabled.get(),
                    spawnGuards.get(),
                    maximumGuardsPerPost.get(),
                    placeArmory.get(),
                    stockArmory.get());
        }

        private void restoreVersionTenDefaults() {
            restoreDefault(enabled);
            restoreDefault(spawnGuards);
            restoreDefault(maximumGuardsPerPost);
            restoreDefault(placeArmory);
            restoreDefault(stockArmory);
        }

        private void restoreVersionElevenDefaults() {
            restoreDefault(guardGunChanceMultiplier);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.villageBuildings")
    public static final class VillageBuildingSettings extends ConfigSection {

        @Comment("Add enabled defense building types to supported villages. Off by default so you can choose whether to extend village generation. Reopen the world or restart the server for layout changes. Affects newly generated villages; existing villages are preserved.")
        @RequiresAction(action = Action.RESTART)
        public ValidatedBoolean enabled = new ValidatedBoolean(false);

        @Comment("Allow Barracks when Generate Village Defense Buildings is on. Reopen the world or restart the server for layout changes. Affects newly generated villages; existing villages are preserved.")
        @RequiresAction(action = Action.RESTART)
        public ValidatedBoolean barracks = new ValidatedBoolean(true);

        @Comment("Allow Village Armory Buildings when Generate Village Defense Buildings is on. Reopen the world or restart the server for layout changes. Affects newly generated villages; existing villages are preserved.")
        @RequiresAction(action = Action.RESTART)
        public ValidatedBoolean armories = new ValidatedBoolean(true);

        @Comment("Allow Sentry Posts when Generate Village Defense Buildings is on. Reopen the world or restart the server for layout changes. Affects newly generated villages; existing villages are preserved.")
        @RequiresAction(action = Action.RESTART)
        public ValidatedBoolean sentryPosts = new ValidatedBoolean(true);

        @Comment("Allow Village-Integrated Watchtowers when Generate Village Defense Buildings is on. Standalone Watchtowers use a separate switch. Reopen the world or restart the server for layout changes. Affects newly generated villages; existing villages are preserved.")
        @RequiresAction(action = Action.RESTART)
        public ValidatedBoolean integratedWatchtowers = new ValidatedBoolean(true);

        @Comment("Allow Training Yards when Generate Village Defense Buildings is on. Reopen the world or restart the server for layout changes. Affects newly generated villages; existing villages are preserved.")
        @RequiresAction(action = Action.RESTART)
        public ValidatedBoolean trainingYards = new ValidatedBoolean(true);

        @Comment("Allow newly generated Barracks, Sentry Posts, integrated Watchtowers, and Training Yards to create one limited guard group. Existing buildings are never staffed retroactively.")
        public ValidatedBoolean spawnGarrisons = new ValidatedBoolean(true);

        @Comment("Global ceiling from 0 through 4. Sentry Posts remain limited to 1 guard; integrated Watchtowers and Training Yards remain limited to 2.")
        public ValidatedInt maximumGuardsPerBuilding = new ValidatedInt(
                VillageBuildingGarrisonPolicy.MAXIMUM_GUARDS_PER_BUILDING,
                VillageBuildingGarrisonPolicy.MAXIMUM_GUARDS_PER_BUILDING,
                VillageBuildingGarrisonPolicy.MINIMUM_GUARDS_PER_BUILDING);

        @Comment("Multiply each new village-garrison guard's difficulty-based gun chance once, then clamp it to 100%. Natural-loadout population, datapack, inventory, and safety gates still apply. 1 = the base chance; 1.5 = one and a half times that chance. 0 prevents these gun upgrades.")
        public ValidatedDouble guardGunChanceMultiplier = new ValidatedDouble(
                3.0D,
                VillageBuildingGarrisonPolicy.MAXIMUM_GUN_CHANCE_MULTIPLIER,
                VillageBuildingGarrisonPolicy.MINIMUM_GUN_CHANCE_MULTIPLIER);

        @Comment("Place a public Village Armory storage block inside each new Armory building. Requires Generate Village Defense Buildings. Existing buildings are preserved.")
        public ValidatedBoolean placeArmoryBlocks = new ValidatedBoolean(true);

        @Comment("Fill newly generated village Armories once from their configured datapack loot table. Disabling this still places an empty Armory.")
        public ValidatedBoolean stockArmories = new ValidatedBoolean(true);

        public boolean allows(VillageBuildingType buildingType) {
            return switch (buildingType) {
                case BARRACKS -> barracks.get();
                case ARMORY -> armories.get();
                case SENTRY_POST -> sentryPosts.get();
                case WATCHTOWER -> integratedWatchtowers.get();
                case TRAINING_YARD -> trainingYards.get();
            };
        }

        public VillageBuildingGarrisonPolicy garrisonPolicy() {
            return new VillageBuildingGarrisonPolicy(
                    enabled.get() && spawnGarrisons.get(),
                    maximumGuardsPerBuilding.get(),
                    guardGunChanceMultiplier.get());
        }

        public double guardGunUpgradeChance(double baseChance) {
            return garrisonPolicy().guardGunUpgradeChance(baseChance);
        }

        public VillageBuildingArmoryPolicy armoryPolicy() {
            boolean placementEnabled = enabled.get() && placeArmoryBlocks.get();
            return new VillageBuildingArmoryPolicy(
                    placementEnabled,
                    placementEnabled && stockArmories.get());
        }

        private void restoreVersionTwelveDefaults() {
            restoreDefault(enabled);
        }

        private void restoreVersionThirteenDefaults() {
            restoreDefault(barracks);
            restoreDefault(armories);
            restoreDefault(sentryPosts);
            restoreDefault(integratedWatchtowers);
            restoreDefault(trainingYards);
        }

        private void restoreVersionFourteenDefaults() {
            restoreDefault(spawnGarrisons);
            restoreDefault(maximumGuardsPerBuilding);
            restoreDefault(guardGunChanceMultiplier);
        }

        private void restoreVersionFifteenDefaults() {
            restoreDefault(placeArmoryBlocks);
            restoreDefault(stockArmories);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.performance")
    public static final class PerformanceSettings extends ConfigSection {

        @Comment("Ticks between target visibility samples. Lower values react faster but use more CPU. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt visibilitySampleIntervalTicks = new ValidatedInt(2, 10, 1);

        @Comment("Ticks between combat movement decisions. Lower values react faster but use more CPU. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt movementDecisionIntervalTicks = new ValidatedInt(5, 20, 1);

        @Comment("Ticks between navigation path recalculations. Lower values respond faster to changes but use more CPU. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt pathRecalculationIntervalTicks = new ValidatedInt(20, 100, 5);

        @Comment("Ticks between cached TaCZ gun-data refreshes. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt gunDataRefreshIntervalTicks = new ValidatedInt(20, 200, 5);

        @Comment("Retained for existing configurations. Native rounds now check shot safety individually; this setting no longer delays those checks.")
        public ValidatedInt unsafeShotCacheTicks = new ValidatedInt(2, 5, 0);

        @Comment("A guard stops and replans after twice this duration without route progress, with a minimum of 20 ticks. 20 ticks = 1 second at normal game speed.")
        public ValidatedInt repositionProgressCheckTicks = new ValidatedInt(10, 40, 5);
    }
}
