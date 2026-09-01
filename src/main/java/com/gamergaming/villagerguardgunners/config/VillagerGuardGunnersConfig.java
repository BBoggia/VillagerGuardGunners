package com.gamergaming.villagerguardgunners.config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyLimits;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplySettings;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.tactics.ExplosiveStandoffPolicy;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfileDefinition;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficultySettings;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDefaults;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnRules;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import com.gamergaming.villagerguardgunners.worldgen.structure.WatchtowerGenerationPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
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
    public static final int CONFIG_VERSION = 12;

    @Comment("Enables Guard Villagers to use TaCZ guns. The server controls this setting.")
    public ValidatedBoolean enabled = new ValidatedBoolean(true);

    @Comment("Controls one-time TaCZ loadouts for newly spawned Guard Villagers. Existing guards and manually equipped weapons are not changed.")
    public NaturalLoadoutSettings naturalLoadouts = new NaturalLoadoutSettings();

    @Comment("Bounded, event-driven dispatch settings for temporary squad commands.")
    public SquadSettings squads = new SquadSettings();

    @Comment("Target acquisition, range, and aiming settings. TaCZ remains authoritative for weapon statistics.")
    public CombatSettings combat = new CombatSettings();

    @Comment("Movement behavior while a guard is using a TaCZ gun.")
    public MovementSettings movement = new MovementSettings();

    @Comment("Weapon-specific guard behavior. These settings never alter TaCZ weapon statistics.")
    public TacticalSettings tactics = new TacticalSettings();

    @Comment("Rules applied after Guard Villagers selects a target and before any native TaCZ round may fire.")
    public EngagementSettings engagement = new EngagementSettings();

    @Comment("Friendly-fire protection settings. Core fail-closed protections cannot be disabled.")
    public SafetySettings safety = new SafetySettings();

    @Comment("Ammunition convenience settings. TaCZ remains authoritative for ammunition compatibility and reloading.")
    public AmmunitionSettings ammunition = new AmmunitionSettings();

    @Comment("Bounded Village Armory resupply policy. Searches may inspect only loaded indexed Armories and never force a chunk to load.")
    public ArmorySettings armoryResupply = new ArmorySettings();

    @Comment("One-way controls for new Village Watchtower generation. Datapacks remain authoritative for placement, groups, and loot.")
    public WatchtowerSettings watchtowers = new WatchtowerSettings();

    @Comment("Controls additive Village Defense Building integration for newly generated villages. Existing villages are never modified.")
    public VillageBuildingSettings villageBuildings = new VillageBuildingSettings();

    @Comment("Advanced scheduling settings. Lower intervals react faster but require more server work.")
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
            // The Step 2 compatibility probe is deliberately opt-in until the
            // reviewed 1.5 building templates replace it.
            villageBuildings.restoreVersionTwelveDefaults();
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.squads")
    public static final class SquadSettings extends ConfigSection {

        @Comment("Maximum distance from the issuing player used for the one-time loaded-guard query. Range: 8 to 128 blocks.")
        public ValidatedDouble commandDispatchRadius = new ValidatedDouble(
                48.0D,
                128.0D,
                8.0D);

        @Comment("Maximum authorized guards changed by one command dispatch. Range: 1 to 64 guards.")
        public ValidatedInt maximumAffectedGuards = new ValidatedInt(32, 64, 1);

        @Comment("Lifetime of Rally, Defend, Hold, and Fall Back commands. Range: 20 to 72000 ticks.")
        public ValidatedInt positionalCommandDurationTicks = new ValidatedInt(
                1200,
                72000,
                20);

        @Comment("Lifetime of Focus Target commands. Range: 20 to 12000 ticks.")
        public ValidatedInt focusCommandDurationTicks = new ValidatedInt(
                400,
                12000,
                20);

        @Comment("Maximum distance a Defend command allows from its designated point. Range: 2 to 64 blocks.")
        public ValidatedDouble defendRadius = new ValidatedDouble(
                12.0D,
                64.0D,
                2.0D);

        @Comment("Maximum projection distance for positional commands issued while looking into the air. Range: 4 to 64 blocks.")
        public ValidatedDouble airCommandRange = new ValidatedDouble(
                32.0D,
                64.0D,
                4.0D);

        private void restoreVersionEightDefaults() {
            restoreDefault(commandDispatchRadius);
            restoreDefault(maximumAffectedGuards);
            restoreDefault(positionalCommandDurationTicks);
            restoreDefault(focusCommandDurationTicks);
            restoreDefault(defendRadius);
            restoreDefault(airCommandRange);
        }
    }

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.naturalLoadouts")
    public static final class NaturalLoadoutSettings extends ConfigSection {

        @Comment("Allows eligible newly spawned guards to replace their initial weapon with a datapack-defined TaCZ loadout.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Spawn origins eligible for an automatic loadout. Structure spawning is the only enabled source by default.")
        public ValidatedSet<NaturalGuardSpawnSource> enabledSpawnSources = new ValidatedSet<>(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.enabledSources(),
                new ValidatedEnum<>(NaturalGuardSpawnSource.STRUCTURE));

        @Comment("Only replace the guard's initial crossbow. Disable this only when another mod's starting equipment may be overwritten intentionally.")
        public ValidatedBoolean replaceCrossbowsOnly = new ValidatedBoolean(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.replaceCrossbowsOnly());

        @Comment("Radius used by the one-time nearby natural-gunner population cap. Range: 8 to 256 blocks.")
        public ValidatedDouble nearbyCapRadius = new ValidatedDouble(
                NaturalGuardLoadoutDefaults.SPAWN_RULES.nearbyCapRadius(),
                256.0D,
                8.0D);

        @Comment("Allows datapack pools to issue weapons TaCZ identifies as explosive. Native blast standoff and ally protection still apply.")
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

    public static final class NaturalDifficultySettings extends ConfigSection {

        @Comment("Chance that an otherwise eligible new guard receives this difficulty's natural TaCZ loadout. Range: 0 to 1.")
        public ValidatedDouble upgradeChance;

        @Comment("Maximum naturally equipped guards allowed inside the configured nearby-cap radius. Zero disables this difficulty. Range: 0 to 32.")
        public ValidatedInt maximumNearby;

        @Comment("Datapack natural-loadout pool selected for this difficulty.")
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

    @Translation(prefix = ModConfigs.BASE_TRANSLATION_KEY + "guard_gunners.tactics")
    public static final class TacticalSettings extends ConfigSection {

        @Comment("Maps TaCZ gun type labels to tactical profiles. Keys are matched case-insensitively after trimming.")
        public ValidatedStringMap<TacticalProfile> typeProfiles =
                new ValidatedStringMap.Builder<TacticalProfile>()
                        .keyHandler(new ValidatedString())
                        .valueHandler(new ValidatedEnum<>(TacticalProfile.GENERALIST))
                        .defaults(defaultTypeProfiles())
                        .build();

        @Comment("Overrides the profile for an exact TaCZ gun ID. Explosive safety remains active regardless of this selection.")
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

    public static final class ProfileSettings extends ConfigSection {
        @Comment("Multiplier applied to TaCZ's effective range when choosing an approach distance. Range: 0.25 to 4.")
        public ValidatedDouble preferredRangeMultiplier;

        @Comment("Multiplier applied to the configured close-range retreat distance. Range: 0.25 to 4.")
        public ValidatedDouble retreatDistanceMultiplier;

        @Comment("Ticks added to the configured continuous-visibility requirement. Range: -40 to 40.")
        public ValidatedInt visibleTicksAdjustment;

        @Comment("Multiplier applied to the configured aim tolerance. Smaller values require more precise aim. Range: 0.25 to 4.")
        public ValidatedDouble aimToleranceMultiplier;

        @Comment("Limits this profile's firing distance using TaCZ's effective range in addition to the global hard cap.")
        public ValidatedBoolean limitFiringToEffectiveRange;

        @Comment("Multiplier applied to TaCZ's effective range when the profile-specific firing limit is enabled. Range: 0.25 to 4.")
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

        @Comment("Determines which targets selected by Guard Villagers may be engaged with a TaCZ gun.")
        public ValidatedEnum<EngagementMode> mode =
                new ValidatedEnum<>(EngagementMode.GUARD_TARGETS);

        @Comment("Ticks that an attack remains valid in DEFENSIVE mode. Range: 20 to 1200.")
        public ValidatedInt defensiveMemoryTicks = new ValidatedInt(200, 1200, 20);

        @Comment("Additional living entity type IDs permitted in any mode except HOLD_FIRE. Ally protection still wins.")
        public ValidatedSet<ResourceLocation> additionalTargets =
                new ValidatedSet<>(Set.of(), new ValidatedIdentifier());

        @Comment("Living entity type IDs never engaged with TaCZ guns. Ally protection and this exclusion take precedence over additions.")
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

        @Comment("Extra clearance around protected entities when validating a shot, in blocks. Range: 0.25 to 4.")
        public ValidatedDouble baseClearance = new ValidatedDouble(0.75D, 4.0D, 0.25D);

        @Comment("Prevents guard-owned TaCZ projectile explosions from destroying blocks while preserving their native effects on hostile entities.")
        public ValidatedBoolean preserveBlocksFromGuardExplosions = new ValidatedBoolean(true);

        @Comment("Explosion-radius multiplier used for the non-bypassable explosive-weapon standoff distance. Range: 1 to 8.")
        public ValidatedDouble explosiveStandoffRadiusMultiplier = new ValidatedDouble(
                ExplosiveStandoffPolicy.DEFAULT.radiusMultiplier(),
                8.0D,
                1.0D);

        @Comment("Additional blocks added to the non-bypassable explosive-weapon standoff distance. Range: 0 to 32.")
        public ValidatedDouble explosiveStandoffAdditionalBlocks = new ValidatedDouble(
                ExplosiveStandoffPolicy.DEFAULT.additionalBlocks(),
                32.0D,
                0.0D);

        @Comment("Modpack integration controls for entities protected from direct shots, firing corridors, bursts, and explosions.")
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

        @Comment("Protect living entities with an owner UUID, including vanilla and compatible modded tameables.")
        public ValidatedBoolean protectOwnedEntities = new ValidatedBoolean(true);

        @Comment("Protect living entities Minecraft considers allied to the guard, including entities on the same scoreboard team.")
        public ValidatedBoolean protectAlliedEntities = new ValidatedBoolean(true);

        @Comment("Additional living entity type IDs to protect. Datapacks may instead add types to #villagerguardgunners:guard_allies.")
        public ValidatedSet<ResourceLocation> additionalProtectedEntities =
                new ValidatedSet<>(Set.of(), new ValidatedIdentifier());

        @Comment("Living entity type IDs excluded from configurable, tagged, villager, golem, owned, team, and compatibility protection. Guards and their owners remain non-bypassable.")
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

        @Comment("Allows guards to pick up compatible TaCZ ammunition they naturally walk over.")
        public ValidatedBoolean pickupDroppedAmmo = new ValidatedBoolean(true);

        @Comment("Maximum passive dropped-ammunition pickup distance, in blocks. Guards never pathfind to ammunition. Range: 0.5 to 3.")
        public ValidatedDouble droppedAmmoPickupRadius = new ValidatedDouble(1.5D, 3.0D, 0.5D);

        @Comment("Allows a guard with no compatible reserve ammunition to request loose ammunition from a nearby guard.")
        public ValidatedBoolean shareAmmoBetweenGuards = new ValidatedBoolean(true);

        @Comment("Maximum distance for an emergency ammunition request, in blocks. Searches occur only when a guard is dry. Range: 1 to 16.")
        public ValidatedDouble ammoSharingRadius = new ValidatedDouble(8.0D, 16.0D, 1.0D);

        @Comment("Whole attachment-adjusted magazines a donor should retain before sharing. Range: 0 to 4.")
        public ValidatedInt donorReserveMagazines = new ValidatedInt(1, 4, 0);

        @Comment("Restricts ammunition sharing to guards with the same owner, while still allowing two unowned guards to share.")
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

        @Comment("Allows otherwise-idle guards below the reserve trigger to use loaded Village Armories.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Ticks between staggered ammunition-need assessments. Range: 20 to 200 ticks.")
        public ValidatedInt checkIntervalTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_CHECK_INTERVAL_TICKS,
                ArmoryResupplyLimits.MAX_CHECK_INTERVAL_TICKS,
                ArmoryResupplyLimits.MIN_CHECK_INTERVAL_TICKS);

        @Comment("Maximum loaded-Armory search radius. Range: 8 to 64 blocks.")
        public ValidatedDouble searchRadius = new ValidatedDouble(
                48.0D,
                ArmoryResupplyLimits.HARD_MAX_SEARCH_RADIUS,
                ArmoryResupplyLimits.MIN_SEARCH_RADIUS);

        @Comment("Maximum loaded Armories inspected for one request. Range: 1 to 32 Armories.")
        public ValidatedInt maximumCandidateArmories = new ValidatedInt(
                8,
                ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES,
                1);

        @Comment("Request resupply when physical reserve is below this many magazines. Zero disables automatic requests. Range: 0 to 64.")
        public ValidatedInt lowReserveMagazines = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_LOW_RESERVE_MAGAZINES,
                ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES,
                0);

        @Comment("Desired physical reserve after resupply. Values below the trigger fail closed. Range: 0 to 64 magazines.")
        public ValidatedInt targetReserveMagazines = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_TARGET_RESERVE_MAGAZINES,
                ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES,
                0);

        @Comment("Navigation speed while travelling to a reserved Armory. Range: 0.1 to 2.")
        public ValidatedDouble movementSpeed = new ValidatedDouble(
                ArmoryResupplyLimits.DEFAULT_RESUPPLY_MOVEMENT_SPEED,
                ArmoryResupplyLimits.HARD_MAX_RESUPPLY_MOVEMENT_SPEED,
                ArmoryResupplyLimits.MIN_RESUPPLY_MOVEMENT_SPEED);

        @Comment("Maximum lifetime of one immutable resupply request. Range: 20 to 2400 ticks.")
        public ValidatedInt requestLifetimeTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_REQUEST_LIFETIME_TICKS,
                ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Maximum lifetime of a claimed Armory supply slot. Range: 20 to 1200 ticks.")
        public ValidatedInt reservationLifetimeTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Delay after a successful withdrawal before reassessment. Range: 20 to 2400 ticks.")
        public ValidatedInt successCooldownTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_SUCCESS_COOLDOWN_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Delay after no compatible loaded Armory is found. Range: 20 to 2400 ticks.")
        public ValidatedInt noSupplyRetryTicks = new ValidatedInt(
                ArmoryResupplyLimits.DEFAULT_NO_SUPPLY_RETRY_TICKS,
                ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                ArmoryResupplyLimits.MIN_CONFIGURED_DELAY_TICKS);

        @Comment("Base delay after bounded navigation cannot reach an Armory. Range: 20 to 2400 ticks.")
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

        @Comment("Allows new Village Watchtower structure starts. Disabling this does not remove or rewrite existing starts.")
        public ValidatedBoolean enabled = new ValidatedBoolean(true);

        @Comment("Allows guard markers in newly placed Watchtower pieces to create guards.")
        public ValidatedBoolean spawnGuards = new ValidatedBoolean(true);

        @Comment("Maximum guards created by one Watchtower guard marker. Range: 0 to 4 guards.")
        public ValidatedInt maximumGuardsPerPost = new ValidatedInt(
                WatchtowerGenerationPolicy.MAXIMUM_GUARDS_PER_POST,
                WatchtowerGenerationPolicy.MAXIMUM_GUARDS_PER_POST,
                WatchtowerGenerationPolicy.MINIMUM_GUARDS_PER_POST);

        @Comment("Allows Armory markers in newly placed Watchtower pieces to place an Armory.")
        public ValidatedBoolean placeArmory = new ValidatedBoolean(true);

        @Comment("Allows newly generated Watchtower Armories to receive their datapack-defined initial stock.")
        public ValidatedBoolean stockArmory = new ValidatedBoolean(true);

        @Comment("Multiplier applied once to a Watchtower guard's difficulty-based natural gun chance. The final chance is clamped to 1. Range: 0 to 3.")
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

        @Comment("Allows Villager Guard Gunners to append its entries to supported village jigsaw pools during server startup. This affects only villages generated afterward. Disabled during the 1.5 integration-probe stage.")
        public ValidatedBoolean enabled = new ValidatedBoolean(false);

        private void restoreVersionTwelveDefaults() {
            restoreDefault(enabled);
        }
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
