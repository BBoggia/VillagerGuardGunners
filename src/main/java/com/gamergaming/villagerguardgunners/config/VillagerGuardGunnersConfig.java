package com.gamergaming.villagerguardgunners.config;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
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
    public static final int CONFIG_VERSION = 7;

    @Comment("Enables Guard Villagers to use TaCZ guns. The server controls this setting.")
    public ValidatedBoolean enabled = new ValidatedBoolean(true);

    @Comment("Controls one-time TaCZ loadouts for newly spawned Guard Villagers. Existing guards and manually equipped weapons are not changed.")
    public NaturalLoadoutSettings naturalLoadouts = new NaturalLoadoutSettings();

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
