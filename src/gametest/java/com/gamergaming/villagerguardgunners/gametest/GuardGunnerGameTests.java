package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ai.SquadCommandMovementController;
import com.gamergaming.villagerguardgunners.ai.SquadCommandMovementGoal;
import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoDeposits;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoMaintenance;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSharing;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoTransactions;
import com.gamergaming.villagerguardgunners.ammunition.GuardDroppedAmmoPickup;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import com.gamergaming.villagerguardgunners.api.GuardAllyProtection.ProtectionReason;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.ArmoryLockInteractions;
import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.armory.ArmorySquadLockCodec;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryAmmunitionWithdrawal;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryReservationBook;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyLimits;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyNeed;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyRequest;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmorySupplyCandidate;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement.EngagementReason;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalResolver;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.menu.ArmoryMenu;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplateInteractions;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplateService;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplateService.OperationStatus;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.OrderField;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDefaults;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDiagnostics;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCandidate;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutContextCapture;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutFormat;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutIssuance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPool;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPools;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutReloadSnapshot;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenanceCodec;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutSelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnIntegration;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmStorage;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmGameTestAccess;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmRecovery;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmTransactions;
import com.gamergaming.villagerguardgunners.sidearm.SidearmActivationReason;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.sidearm.SidearmTransitionStatus;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.squad.SquadMembershipCodec;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBinding;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandDispatcher;
import com.gamergaming.villagerguardgunners.squad.SquadCommandRuntime;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerModeCodec;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.block.ProjectileExplosion;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;
import tallestegg.guardvillagers.entities.ai.goals.RaiseShieldGoal;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
/** Development-only Forge integration tests; excluded from the production JAR. */
public final class GuardGunnerGameTests {
    private static final String EMPTY_TEMPLATE = "villagerguardgunners/gametest_arena";
    private static final ResourceLocation GLOCK_17 = ResourceLocation.fromNamespaceAndPath("tacz", "glock_17");
    private static final ResourceLocation AK_47 = ResourceLocation.fromNamespaceAndPath("tacz", "ak47");
    private static final ResourceLocation AA_12 = ResourceLocation.fromNamespaceAndPath("tacz", "aa12");
    private static final ResourceLocation M16_A4 = ResourceLocation.fromNamespaceAndPath("tacz", "m16a4");
    private static final ResourceLocation M95 = ResourceLocation.fromNamespaceAndPath("tacz", "m95");
    private static final ResourceLocation M870 = ResourceLocation.fromNamespaceAndPath("tacz", "m870");
    private static final ResourceLocation RPG_7 = ResourceLocation.fromNamespaceAndPath("tacz", "rpg7");
    private static final ResourceLocation NINE_MILLIMETER = ResourceLocation.fromNamespaceAndPath("tacz", "9mm");
    private static final ResourceLocation DEFAULT_AMMO = ResourceLocation.fromNamespaceAndPath("tacz", "762x39");
    private static final String AMMO_INVENTORY_TAG = "villagerguardgunners:ammo_inventory";
    private static final String MIGRATION_OVERFLOW_TAG = "villagerguardgunners:ammo_overflow";

    private GuardGunnerGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutDatapacksPublishBundledPools(GameTestHelper helper) {
        NaturalGuardLoadoutPool easy = NaturalGuardLoadoutPools.get(
                NaturalGuardLoadoutDefaults.EASY_POOL);
        NaturalGuardLoadoutPool normal = NaturalGuardLoadoutPools.get(
                NaturalGuardLoadoutDefaults.NORMAL_POOL);
        NaturalGuardLoadoutPool hard = NaturalGuardLoadoutPools.get(
                NaturalGuardLoadoutDefaults.HARD_POOL);

        helper.assertTrue(easy != null && easy.totalWeight() == 10,
                "The bundled Easy natural-loadout pool was not published");
        helper.assertTrue(normal != null && normal.totalWeight() == 8,
                "The bundled Normal natural-loadout pool was not published");
        helper.assertTrue(hard != null && hard.totalWeight() == 10,
                "The bundled Hard natural-loadout pool was not published");
        NaturalGuardLoadoutReloadSnapshot reloadSnapshot =
                NaturalGuardLoadoutPools.reloadSnapshot();
        ResourceLocation malformedTestPool = ResourceLocation.fromNamespaceAndPath(
                "villagerguardgunners_gametest",
                "malformed");
        ResourceLocation conditionalTestPool = ResourceLocation.fromNamespaceAndPath(
                "villagerguardgunners_gametest",
                "conditional");
        ResourceLocation sidearmTestPool = ResourceLocation.fromNamespaceAndPath(
                "villagerguardgunners_gametest",
                "sidearm");
        NaturalGuardLoadoutPool conditional = NaturalGuardLoadoutPools.get(
                conditionalTestPool);
        NaturalGuardLoadoutPool sidearm = NaturalGuardLoadoutPools.get(sidearmTestPool);
        helper.assertTrue(NaturalGuardLoadoutPools.snapshot().size() >= 3
                        && reloadSnapshot.generation() > 0L
                        && reloadSnapshot.publishedPools().size() >= 3
                        && conditional != null
                        && conditional.format() == 2
                        && conditional.hasConditionalEntries()
                        && sidearm != null
                        && sidearm.format() == NaturalGuardLoadoutFormat.SIDEARM_VERSION
                        && GLOCK_17.equals(sidearm.entries().get(0).sidearmId())
                        && conditional.entries().get(0).conditions().difficulties()
                                .contains(NaturalGuardDifficulty.NORMAL)
                        && reloadSnapshot.publishedPools().stream()
                                .anyMatch(publication -> publication.id().equals(conditionalTestPool)
                                        && publication.format() == 2)
                        && reloadSnapshot.rejectedPools().stream()
                                .anyMatch(rejected -> rejected.id().equals(malformedTestPool)),
                "The live natural-loadout publication snapshot was incomplete");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutAdministratorCommandsAreRegisteredAndReadOnly(
            GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var administrator = helper.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0))))
                .withPermission(4);
        try {
            int validate = dispatcher.execute(
                    "vgg loadouts validate villagerguardgunners:normal",
                    administrator);
            int inspect = dispatcher.execute(
                    "vgg loadouts inspect villagerguardgunners:normal 1",
                    administrator);
            int preview = dispatcher.execute(
                    "vgg loadouts preview normal structure "
                            + "villagerguardgunners_gametest:conditional",
                    administrator);
            int invalidPage = dispatcher.execute(
                    "vgg loadouts inspect villagerguardgunners:normal 999",
                    administrator);
            helper.assertTrue(validate == 1 && inspect == 1 && preview == 1,
                    "One or more registered natural-loadout diagnostic commands failed");
            helper.assertTrue(invalidPage == 0,
                    "Out-of-range inspection emitted partial success");

            boolean permissionDenied = false;
            try {
                dispatcher.execute(
                        "vgg loadouts validate",
                        administrator.withPermission(1));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                permissionDenied = true;
            }
            helper.assertTrue(permissionDenied,
                    "Natural-loadout diagnostics were available below permission level 2");
            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            helper.fail("Natural-loadout diagnostics could not be parsed: "
                    + exception.getMessage());
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void conditionalNaturalLoadoutMatchesLiveSpawnContext(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.addTag("vgg_context_fixture");
        ResourceLocation dimension = helper.getLevel().dimension().location();
        ResourceLocation biome = helper.getLevel()
                .getBiome(guard.blockPosition())
                .unwrapKey()
                .orElseThrow()
                .location();
        NaturalGuardLoadoutConditionSpec matchingConditions =
                new NaturalGuardLoadoutConditionSpec(
                        new NaturalGuardLoadoutConditionSpec.SelectorGroup<>(
                                Set.of(dimension), Set.of()),
                        new NaturalGuardLoadoutConditionSpec.SelectorGroup<>(
                                Set.of(NaturalGuardLoadoutConditionSpec.RegistrySelector.exact(
                                        biome)),
                                Set.of()),
                        NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                        Set.of(NaturalGuardDifficulty.NORMAL),
                        Set.of(NaturalGuardSpawnSource.STRUCTURE),
                        NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                        Set.of("vgg_context_fixture"),
                        Set.of());
        NaturalGuardLoadoutConditionSpec hardOnly =
                new NaturalGuardLoadoutConditionSpec(
                        NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                        NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                        NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                        Set.of(NaturalGuardDifficulty.HARD),
                        Set.of(),
                        NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                        Set.of(),
                        Set.of());
        NaturalGuardLoadoutPool.Entry matching =
                new NaturalGuardLoadoutPool.Entry(GLOCK_17, 2, matchingConditions);
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                List.of(
                        new NaturalGuardLoadoutPool.Entry(AK_47, 100, hardOnly),
                        matching));

        NaturalGuardLoadoutSelector.Decision decision = NaturalGuardLoadoutSelector.select(
                new NaturalGuardLoadoutSelector.Context(
                        true,
                        true,
                        true,
                        NaturalGuardSpawnSource.STRUCTURE,
                        true,
                        false),
                NaturalGuardLoadoutDefaults.SPAWN_RULES,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardLoadoutDefaults.settingsFor(NaturalGuardDifficulty.NORMAL),
                () -> NaturalGuardLoadoutContextCapture.capture(
                        helper.getLevel(),
                        guard,
                        pool,
                        NaturalGuardDifficulty.NORMAL,
                        NaturalGuardSpawnSource.STRUCTURE),
                () -> 0.0D,
                () -> 0,
                pool,
                bound -> bound - 1);

        helper.assertTrue(decision.selected() && decision.entry().equals(matching),
                "Format-2 selection did not match the live dimension, biome, and guard tag");
        helper.succeed();
    }

    @GameTest(
            batch = "conditional_natural_loadout_integration",
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE,
            timeoutTicks = 100)
    public static void publishedConditionalPoolIssuesThroughDeferredEntityJoin(
            GameTestHelper helper) {
        ResourceLocation conditionalPoolId = ResourceLocation.fromNamespaceAndPath(
                "villagerguardgunners_gametest",
                "conditional");
        NaturalGuardLoadoutPool pool = NaturalGuardLoadoutPools.get(conditionalPoolId);
        helper.assertTrue(pool != null
                        && pool.format() == NaturalGuardLoadoutFormat.CONDITIONAL_VERSION
                        && pool.hasConditionalEntries(),
                "The published format-2 integration pool is unavailable");

        NaturalLoadoutConfigSnapshot original = NaturalLoadoutConfigSnapshot.capture(helper);
        try {
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            ModConfigs.GUARD_GUNNERS.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabledSpawnSources.accept(
                    Set.of(NaturalGuardSpawnSource.STRUCTURE));
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.replaceCrossbowsOnly.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.upgradeChance.accept(1.0D);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.maximumNearby.accept(32);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.poolId.accept(conditionalPoolId);

            Guard guard = newNaturalSpawnGuard(helper, new BlockPos(0, 1, 0));
            helper.assertTrue(helper.getLevel().addFreshEntity(guard),
                    "Could not add the format-2 entity-join fixture");
            helper.runAfterDelay(2, () -> {
                try {
                    GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
                    NaturalGuardLoadoutProvenance provenance =
                            access.villagerGuardGunners$getNaturalLoadoutProvenance();
                    helper.assertTrue(
                            access.villagerGuardGunners$getNaturalLoadoutDecisionStatus()
                                            == NaturalGuardLoadoutSelector.DecisionStatus.SELECTED
                                    && access.villagerGuardGunners$getNaturalLoadoutIssuanceStatus()
                                            == NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED,
                            "The deferred entity-join pipeline did not select and issue format 2");
                    helper.assertTrue(provenance != null
                                    && provenance.poolId().equals(conditionalPoolId)
                                    && provenance.gunId().equals(GLOCK_17)
                                    && provenance.difficulty() == NaturalGuardDifficulty.NORMAL
                                    && provenance.spawnSource()
                                            == NaturalGuardSpawnSource.STRUCTURE
                                    && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                            guard.getMainHandItem(), provenance),
                            "Format-2 issuance did not publish authentic pool/gun provenance");
                    helper.succeed();
                } finally {
                    original.restore(helper);
                }
            });
        } catch (RuntimeException | Error exception) {
            original.restore(helper);
            throw exception;
        }
    }

    @GameTest(
            batch = "format_three_natural_loadout_integration",
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE,
            timeoutTicks = 100)
    public static void publishedFormatThreePoolIssuesThroughDeferredEntityJoin(
            GameTestHelper helper) {
        ResourceLocation poolId = ResourceLocation.fromNamespaceAndPath(
                "villagerguardgunners_gametest",
                "sidearm");
        NaturalGuardLoadoutPool pool = NaturalGuardLoadoutPools.get(poolId);
        helper.assertTrue(pool != null
                        && pool.format() == NaturalGuardLoadoutFormat.SIDEARM_VERSION
                        && GLOCK_17.equals(pool.entries().get(0).sidearmId()),
                "The published format-3 integration pool is unavailable");

        NaturalLoadoutConfigSnapshot original = NaturalLoadoutConfigSnapshot.capture(helper);
        try {
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            ModConfigs.GUARD_GUNNERS.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabledSpawnSources.accept(
                    Set.of(NaturalGuardSpawnSource.STRUCTURE));
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.replaceCrossbowsOnly.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.upgradeChance.accept(1.0D);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.maximumNearby.accept(32);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.poolId.accept(poolId);

            Guard guard = newNaturalSpawnGuard(helper, new BlockPos(0, 1, 0));
            helper.assertTrue(helper.getLevel().addFreshEntity(guard),
                    "Could not add the format-3 entity-join fixture");
            helper.runAfterDelay(2, () -> {
                try {
                    GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
                    NaturalGuardLoadoutCandidate candidate =
                            access.villagerGuardGunners$getNaturalLoadoutCandidate();
                    helper.assertTrue(
                            access.villagerGuardGunners$getNaturalLoadoutDecisionStatus()
                                            == NaturalGuardLoadoutSelector.DecisionStatus.SELECTED
                                    && access.villagerGuardGunners$getNaturalLoadoutIssuanceStatus()
                                            == NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED
                                    && candidate != null
                                    && AK_47.equals(candidate.gunId())
                                    && GLOCK_17.equals(candidate.sidearmId()),
                            "Deferred format-3 selection did not preserve both weapon IDs");
                    helper.assertTrue(
                            AK_47.equals(gunId(guard.getMainHandItem()))
                                    && GLOCK_17.equals(gunId(
                                            GuardSidearmAccess
                                                    .getConfiguredSidearmInventory(guard)
                                                    .getStackInSlot(0)))
                                    && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                            guard,
                                            access.villagerGuardGunners$getNaturalLoadoutProvenance()),
                            "Deferred format-3 issuance did not commit both roles authentically");
                    helper.succeed();
                } finally {
                    original.restore(helper);
                }
            });
        } catch (RuntimeException | Error exception) {
            original.restore(helper);
            throw exception;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutStructureMarkerSurvivesDeferredWorldgenJoin(
            GameTestHelper helper) {
        NaturalLoadoutConfigSnapshot original = NaturalLoadoutConfigSnapshot.capture(helper);
        try {
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            ModConfigs.GUARD_GUNNERS.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabledSpawnSources.accept(
                    Set.of(NaturalGuardSpawnSource.STRUCTURE));
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.replaceCrossbowsOnly.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.upgradeChance.accept(1.0D);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.maximumNearby.accept(32);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.poolId.accept(
                    NaturalGuardLoadoutDefaults.NORMAL_POOL);

            BlockPos position = helper.absolutePos(new BlockPos(0, 1, 0));
            Guard worldgenGuard = GuardEntityType.GUARD.get().create(helper.getLevel());
            helper.assertTrue(worldgenGuard != null,
                    "Could not create the worldgen guard fixture");
            worldgenGuard.moveTo(position, 0.0F, 0.0F);
            worldgenGuard.finalizeSpawn(
                    helper.getLevel(),
                    helper.getLevel().getCurrentDifficultyAt(position),
                    MobSpawnType.STRUCTURE,
                    null,
                    null);

            GuardNaturalLoadoutAccess worldgenAccess =
                    (GuardNaturalLoadoutAccess) worldgenGuard;
            helper.assertFalse(
                    worldgenAccess.villagerGuardGunners$hasNaturalLoadoutSelectionAttempted(),
                    "Structure selection ran before the guard joined a live server level");

            CompoundTag serialized = new CompoundTag();
            helper.assertTrue(worldgenGuard.save(serialized),
                    "Could not serialize the structure guard's protochunk handoff");
            helper.assertTrue(serialized.contains(
                            "villagerguardgunners:pending_natural_loadout_spawn",
                            Tag.TAG_STRING),
                    "Structure source marker was not preserved through protochunk NBT");

            Guard restored = GuardEntityType.GUARD.get().create(helper.getLevel());
            helper.assertTrue(restored != null,
                    "Could not restore the structure guard fixture");
            restored.load(serialized);
            helper.assertTrue(restored.getMainHandItem().isEmpty(),
                    "The lifecycle fixture unexpectedly began with a weapon");
            helper.assertTrue(helper.getLevel().addFreshEntity(restored),
                    "Could not add the restored structure guard to the live level");
            helper.assertFalse(
                    ((GuardNaturalLoadoutAccess) restored)
                            .villagerGuardGunners$hasNaturalLoadoutSelectionAttempted(),
                    "Entity join consumed the marker before native equipment was ready");

            helper.runAfterDelay(2, () -> {
                try {
                    GuardNaturalLoadoutAccess restoredAccess =
                            (GuardNaturalLoadoutAccess) restored;
                    helper.assertTrue(restored.isAddedToWorld() && !restored.isRemoved(),
                            "Deferred selection did not retain a valid live guard");
                    helper.assertTrue(
                            restoredAccess.villagerGuardGunners$getNaturalLoadoutDecisionStatus()
                                            == NaturalGuardLoadoutSelector.DecisionStatus.SELECTED
                                    && restoredAccess
                                            .villagerGuardGunners$getNaturalLoadoutIssuanceStatus()
                                            == NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED
                                    && gunId(restored.getMainHandItem()) != null
                                    && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                            restored,
                                            restoredAccess
                                                    .villagerGuardGunners$getNaturalLoadoutProvenance()),
                            "The real native equipment lifecycle did not issue a natural loadout");
                    helper.succeed();
                } finally {
                    original.restore(helper);
                }
            });
        } catch (RuntimeException | Error exception) {
            original.restore(helper);
            throw exception;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutIssuanceProvidesNativeGunAndFullAmmoStack(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                GLOCK_17,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE);

        NaturalGuardLoadoutCompatibilityAnalyzer.Analysis analysis =
                NaturalGuardLoadoutCompatibilityAnalyzer.analyze(
                        guard,
                        GLOCK_17,
                        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REJECT);
        helper.assertTrue(analysis.compatible()
                        && analysis.explosionState()
                                == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState.NON_EXPLOSIVE,
                "Read-only Glock compatibility analysis failed: " + analysis.status());
        helper.assertTrue(stacksMatch(new ItemStack(Items.CROSSBOW), guard.getMainHandItem())
                        && isAmmoHandlerEmpty(GuardAmmoAccess.getAmmoHandler(guard)),
                "Compatibility analysis changed the guard before issuance");
        ItemStack exposedPreparedGun = analysis.preparedGun();
        exposedPreparedGun.setCount(0);
        helper.assertTrue(analysis.preparedGun().getCount() == 1,
                "Compatibility analysis exposed its mutable prepared gun");

        NaturalGuardLoadoutIssuance.Result result =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(result.issued(),
                "A validated non-explosive TaCZ loadout was not issued: " + result.status());
        helper.assertTrue(guard.getMainHandItem().getItem() instanceof IGun,
                "Natural issuance did not replace the starter crossbow with a TaCZ gun");

        IGun issuedGun = (IGun) guard.getMainHandItem().getItem();
        helper.assertTrue(GLOCK_17.equals(issuedGun.getGunId(guard.getMainHandItem())),
                "Natural issuance created the wrong TaCZ gun ID");
        var gunData = TimelessAPI.getCommonGunIndex(GLOCK_17)
                .orElseThrow()
                .getGunData();
        ResourceLocation ammoId = gunData.getAmmoId();
        int fullStack = TimelessAPI.getCommonAmmoIndex(ammoId)
                .orElseThrow()
                .getStackSize();
        helper.assertTrue(result.ammunitionItems() == fullStack,
                "Natural issuance did not report TaCZ's complete ammunition stack size");
        helper.assertTrue(result.provenance() != null
                        && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                guard.getMainHandItem(),
                                result.provenance()),
                "Natural issuance did not authenticate the committed TaCZ gun");
        helper.assertTrue(countAmmo(GuardAmmoAccess.getAmmoHandler(guard), ammoId) == fullStack,
                "Natural issuance did not store one complete compatible ammunition stack");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void formatThreeNaturalLoadoutIssuesSidearmAndBothAmmoStacks(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack originalWeapon = new ItemStack(Items.CROSSBOW);
        guard.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.copy());
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                ResourceLocation.fromNamespaceAndPath(
                        VillagerGuardGunners.MODID, "gametest/format_three"),
                AK_47,
                GLOCK_17,
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);
        GuardNaturalLoadoutAccess naturalAccess = (GuardNaturalLoadoutAccess) guard;
        naturalAccess.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);

        NaturalGuardLoadoutIssuance.Result result =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(result.issued() && result.issuedSidearm(),
                "Format-3 primary and sidearm were not issued atomically: " + result.status());
        helper.assertTrue(AK_47.equals(gunId(guard.getMainHandItem())),
                "Format-3 issuance equipped the wrong natural primary");
        IItemHandler sidearms = GuardSidearmAccess.getConfiguredSidearmInventory(guard);
        helper.assertTrue(sidearms.getSlots() == 1
                        && GLOCK_17.equals(gunId(sidearms.getStackInSlot(0))),
                "Format-3 issuance did not place the validated pistol in the configured slot");

        ResourceLocation primaryAmmoId = TimelessAPI.getCommonGunIndex(AK_47)
                .orElseThrow().getGunData().getAmmoId();
        ResourceLocation sidearmAmmoId = TimelessAPI.getCommonGunIndex(GLOCK_17)
                .orElseThrow().getGunData().getAmmoId();
        int primaryStack = TimelessAPI.getCommonAmmoIndex(primaryAmmoId)
                .orElseThrow().getStackSize();
        int sidearmStack = TimelessAPI.getCommonAmmoIndex(sidearmAmmoId)
                .orElseThrow().getStackSize();
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        helper.assertTrue(result.primaryAmmunitionItems() == primaryStack
                        && result.sidearmAmmunitionItems() == sidearmStack
                        && result.ammunitionItems() == primaryStack + sidearmStack
                        && countAmmo(reserves, primaryAmmoId) == primaryStack
                        && countAmmo(reserves, sidearmAmmoId) == sidearmStack,
                "Format-3 issuance did not supply one complete compatible stack per weapon");
        helper.assertTrue(result.provenance() != null
                        && result.provenance().equals(
                                naturalAccess.villagerGuardGunners$getNaturalLoadoutProvenance())
                        && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                guard, result.provenance()),
                "Format-3 commit did not authenticate its natural primary");

        UUID targetId = UUID.fromString("70000000-0000-0000-0000-000000000106");
        var activated = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.PRIMARY_DRY,
                300L,
                targetId,
                true);
        helper.assertTrue(activated.status() == SidearmTransitionStatus.ACTIVATED
                        && GLOCK_17.equals(gunId(guard.getMainHandItem()))
                        && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                guard, result.provenance()),
                "Activating the naturally issued sidearm lost primary provenance");
        helper.assertTrue(
                GuardSidearmTransactions.returnPrimary(guard, true).status()
                        == SidearmTransitionStatus.RETURNED,
                "Naturally issued sidearm could not return its primary");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void formatThreeNaturalLoadoutCapacityFailureIsAllOrNothing(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack originalWeapon = new ItemStack(Items.CROSSBOW);
        guard.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.copy());
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        int nineMillimeterStack = TimelessAPI.getCommonAmmoIndex(NINE_MILLIMETER)
                .orElseThrow().getStackSize();
        ItemStack existing = AmmoItemBuilder.create()
                .setId(NINE_MILLIMETER)
                .setCount(nineMillimeterStack)
                .build();
        reserves.setStackInSlot(0, existing.copy());
        reserves.setStackInSlot(1, existing.copy());
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                ResourceLocation.fromNamespaceAndPath(
                        VillagerGuardGunners.MODID, "gametest/format_three_capacity"),
                AK_47,
                GLOCK_17,
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);
        GuardNaturalLoadoutAccess naturalAccess = (GuardNaturalLoadoutAccess) guard;
        naturalAccess.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);

        NaturalGuardLoadoutIssuance.Result result =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(
                result.status()
                        == NaturalGuardLoadoutIssuance.IssuanceStatus
                                .AMMO_CAPACITY_UNAVAILABLE,
                "Two-stack capacity failure returned the wrong result: " + result.status());
        helper.assertTrue(stacksMatch(originalWeapon, guard.getMainHandItem())
                        && countAmmo(reserves, NINE_MILLIMETER)
                                == nineMillimeterStack * 2
                        && reserves.getStackInSlot(2).isEmpty()
                        && GuardSidearmAccess.getConfiguredSidearmInventory(guard)
                                .getStackInSlot(0).isEmpty()
                        && naturalAccess.villagerGuardGunners$getNaturalLoadoutProvenance()
                                == null
                        && candidate.equals(
                                naturalAccess.villagerGuardGunners$getNaturalLoadoutCandidate()),
                "A failed format-3 preflight partially changed weapon, ammo, or provenance state");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void formatThreeNaturalLoadoutNeverDisplacesExistingSidearm(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack originalWeapon = new ItemStack(Items.CROSSBOW);
        ItemStack existingSidearm = gun(M16_A4, FireMode.SEMI, 7, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.copy());
        GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                existingSidearm.copy());
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                ResourceLocation.fromNamespaceAndPath(
                        VillagerGuardGunners.MODID, "gametest/format_three_occupied"),
                AK_47,
                GLOCK_17,
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);

        NaturalGuardLoadoutIssuance.Result result =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(
                result.status()
                        == NaturalGuardLoadoutIssuance.IssuanceStatus
                                .SIDEARM_SLOT_UNAVAILABLE,
                "Occupied sidearm slot returned the wrong result: " + result.status());
        helper.assertTrue(stacksMatch(originalWeapon, guard.getMainHandItem())
                        && stacksMatch(
                                existingSidearm,
                                GuardSidearmAccess.getConfiguredSidearmInventory(guard)
                                        .getStackInSlot(0))
                        && isAmmoHandlerEmpty(GuardAmmoAccess.getAmmoHandler(guard))
                        && ((GuardNaturalLoadoutAccess) guard)
                                        .villagerGuardGunners$getNaturalLoadoutProvenance()
                                == null,
                "Natural issuance displaced an existing sidearm or retained a partial commit");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutIssuanceRejectsExplosivesWithoutMutation(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack originalWeapon = new ItemStack(Items.CROSSBOW);
        guard.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.copy());
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        ItemStack existingAmmo = AmmoItemBuilder.create()
                .setId(NINE_MILLIMETER)
                .setCount(7)
                .build();
        reserves.setStackInSlot(0, existingAmmo.copy());
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.HARD_POOL,
                RPG_7,
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);

        NaturalGuardLoadoutCompatibilityAnalyzer.Analysis reported =
                NaturalGuardLoadoutCompatibilityAnalyzer.analyze(
                        guard,
                        RPG_7,
                        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REPORT);
        NaturalGuardLoadoutCompatibilityAnalyzer.Analysis rejected =
                NaturalGuardLoadoutCompatibilityAnalyzer.analyze(
                        guard,
                        RPG_7,
                        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REJECT);
        helper.assertTrue(reported.compatible()
                        && reported.explosionState()
                                == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState.EXPLOSIVE
                        && rejected.status()
                                == NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus
                                        .EXPLOSIVE_DISALLOWED,
                "RPG report/reject compatibility policies disagreed");
        helper.assertTrue(stacksMatch(originalWeapon, guard.getMainHandItem())
                        && stacksMatch(existingAmmo, reserves.getStackInSlot(0)),
                "RPG compatibility analysis changed the guard before issuance");

        NaturalGuardLoadoutIssuance.Result result =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(
                result.status() == NaturalGuardLoadoutIssuance.IssuanceStatus.EXPLOSIVE_DISALLOWED,
                "Disallowed RPG issuance returned the wrong status: " + result.status());
        helper.assertTrue(stacksMatch(originalWeapon, guard.getMainHandItem()),
                "Rejected explosive issuance changed the guard's original weapon");
        helper.assertTrue(stacksMatch(existingAmmo, reserves.getStackInSlot(0)),
                "Rejected explosive issuance changed pre-existing ammunition");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutIssuanceRequiresCapacityForTheWholeAmmoStack(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack originalWeapon = new ItemStack(Items.CROSSBOW);
        guard.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.copy());
        int fullStack = TimelessAPI.getCommonAmmoIndex(NINE_MILLIMETER)
                .orElseThrow()
                .getStackSize();
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < reserves.getSlots(); slot++) {
            reserves.setStackInSlot(slot, AmmoItemBuilder.create()
                    .setId(NINE_MILLIMETER)
                    .setCount(fullStack)
                    .build());
        }
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                GLOCK_17,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE);

        NaturalGuardLoadoutIssuance.Result result =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(
                result.status()
                        == NaturalGuardLoadoutIssuance.IssuanceStatus.AMMO_CAPACITY_UNAVAILABLE,
                "Full reserve inventory returned the wrong issuance status: " + result.status());
        helper.assertTrue(stacksMatch(originalWeapon, guard.getMainHandItem()),
                "Capacity failure replaced the guard's original weapon");
        helper.assertTrue(countAmmo(reserves, NINE_MILLIMETER)
                        == fullStack * GuardAmmoHandler.SLOT_COUNT,
                "Capacity failure changed the guard's existing reserve inventory");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutProvenanceSurvivesReloadAndRejectsReplacement(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                GLOCK_17,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE);
        NaturalGuardLoadoutIssuance.Result issuance =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(issuance.issued() && issuance.provenance() != null,
                "Could not create an authenticated natural-loadout fixture");
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        access.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
        access.villagerGuardGunners$setNaturalLoadoutProvenance(issuance.provenance());

        CompoundTag serialized = new CompoundTag();
        helper.assertTrue(guard.save(serialized),
                "Could not serialize the naturally issued guard");
        helper.assertTrue(serialized.contains(
                        NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG,
                        Tag.TAG_COMPOUND),
                "The guard save omitted its natural-loadout provenance");

        Guard restored = GuardEntityType.GUARD.get().create(helper.getLevel());
        helper.assertTrue(restored != null,
                "Could not create the natural-loadout reload fixture");
        restored.load(serialized);
        GuardNaturalLoadoutAccess restoredAccess = (GuardNaturalLoadoutAccess) restored;
        helper.assertTrue(issuance.provenance().equals(
                        restoredAccess.villagerGuardGunners$getNaturalLoadoutProvenance()),
                "The versioned natural-loadout identity changed across save/load");
        helper.assertTrue(restoredAccess.villagerGuardGunners$getNaturalLoadoutCandidate() != null,
                "Reload did not reconstruct the nearby-cap candidate state");
        helper.assertTrue(NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                        restored.getMainHandItem(),
                        restoredAccess.villagerGuardGunners$getNaturalLoadoutProvenance()),
                "Reloaded guard and gun provenance did not authenticate");

        restored.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        CompoundTag afterReplacement = new CompoundTag();
        helper.assertTrue(restored.save(afterReplacement),
                "Could not serialize the replacement-weapon fixture");
        helper.assertFalse(afterReplacement.contains(
                        NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG),
                "A manually replaced weapon retained a stale natural-loadout identity");
        helper.assertTrue(
                restoredAccess.villagerGuardGunners$getNaturalLoadoutProvenance() == null
                        && restoredAccess.villagerGuardGunners$getNaturalLoadoutCandidate() == null,
                "A replacement weapon continued reserving a nearby population slot");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void naturalLoadoutProvenanceFailsClosedWhenTampered(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                GLOCK_17,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE);
        NaturalGuardLoadoutIssuance.Result issuance =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(issuance.issued() && issuance.provenance() != null,
                "Could not create the provenance-tamper fixture");
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        access.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
        access.villagerGuardGunners$setNaturalLoadoutProvenance(issuance.provenance());

        CompoundTag unsupportedVersion = new CompoundTag();
        helper.assertTrue(guard.save(unsupportedVersion),
                "Could not serialize the unsupported-version fixture");
        unsupportedVersion
                .getCompound(NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG)
                .putInt("Version", NaturalGuardLoadoutProvenance.CURRENT_SCHEMA_VERSION + 1);
        Guard futureRecordGuard = GuardEntityType.GUARD.get().create(helper.getLevel());
        helper.assertTrue(futureRecordGuard != null,
                "Could not create the unsupported-version reload fixture");
        futureRecordGuard.load(unsupportedVersion);
        helper.assertTrue(
                ((GuardNaturalLoadoutAccess) futureRecordGuard)
                                .villagerGuardGunners$getNaturalLoadoutProvenance() == null,
                "An unsupported provenance schema was treated as authentic");

        ItemStack mismatchedGun = guard.getMainHandItem().copy();
        CompoundTag gunRecord = Objects.requireNonNull(mismatchedGun.getTag())
                .getCompound(NaturalGuardLoadoutProvenanceCodec.GUN_PROVENANCE_TAG);
        gunRecord.putUUID("IssuanceId", UUID.randomUUID());
        guard.setItemSlot(EquipmentSlot.MAINHAND, mismatchedGun);
        NaturalGuardLoadoutDiagnostics.Snapshot mismatchDiagnostics =
                NaturalGuardLoadoutDiagnostics.capture(guard);
        helper.assertTrue(
                mismatchDiagnostics.authentication()
                                == NaturalGuardLoadoutDiagnostics.AuthenticationState.GUN_MISMATCH
                        && !mismatchDiagnostics.active(),
                "On-demand diagnostics did not expose a mismatched gun identity");
        CompoundTag mismatchSave = new CompoundTag();
        helper.assertTrue(guard.save(mismatchSave),
                "Could not serialize the mismatched-identity fixture");
        helper.assertFalse(mismatchSave.contains(
                        NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG),
                "Mismatched guard and gun identities survived reconciliation");
        helper.assertTrue(access.villagerGuardGunners$getNaturalLoadoutProvenance() == null,
                "A mismatched item marker remained active in memory");
        helper.succeed();
    }

    @GameTest(
            batch = "natural_loadout_population",
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE,
            timeoutTicks = 200)
    public static void reloadedNaturalGunnerEnforcesLiveNearbyPopulationCap(
            GameTestHelper helper) {
        NaturalLoadoutConfigSnapshot original = NaturalLoadoutConfigSnapshot.capture(helper);
        try {
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            ModConfigs.GUARD_GUNNERS.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.enabledSpawnSources.accept(
                    Set.of(NaturalGuardSpawnSource.STRUCTURE));
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.replaceCrossbowsOnly.accept(true);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.nearbyCapRadius.accept(16.0D);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.upgradeChance.accept(1.0D);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.maximumNearby.accept(1);
            ModConfigs.GUARD_GUNNERS.naturalLoadouts.normal.poolId.accept(
                    NaturalGuardLoadoutDefaults.NORMAL_POOL);

            Guard first = newNaturalSpawnGuard(helper, new BlockPos(0, 1, 0));
            helper.assertTrue(helper.getLevel().addFreshEntity(first),
                    "Could not add the first natural-loadout population fixture");

            helper.runAfterDelay(2, () -> {
                try {
                    GuardNaturalLoadoutAccess firstAccess = (GuardNaturalLoadoutAccess) first;
                    helper.assertTrue(
                            firstAccess.villagerGuardGunners$getNaturalLoadoutIssuanceStatus()
                                    == NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED,
                            "Forced first natural guard was not issued a valid loadout");
                    helper.assertTrue(NaturalGuardLoadoutDiagnostics.capture(first).active(),
                            "The first natural guard did not expose active provenance");

                    CompoundTag serialized = new CompoundTag();
                    helper.assertTrue(first.save(serialized),
                            "Could not serialize the first population fixture");
                    first.discard();
                    Guard restored = GuardEntityType.GUARD.get().create(helper.getLevel());
                    helper.assertTrue(restored != null,
                            "Could not create the reloaded population fixture");
                    restored.load(serialized);
                    helper.assertTrue(helper.getLevel().addFreshEntity(restored),
                            "Could not add the reloaded population fixture");
                    helper.assertTrue(NaturalGuardLoadoutDiagnostics.capture(restored).active(),
                            "Reloaded natural guard did not retain active provenance");

                    Guard second = newNaturalSpawnGuard(helper, new BlockPos(3, 1, 0));
                    helper.assertTrue(helper.getLevel().addFreshEntity(second),
                            "Could not add the second natural-loadout population fixture");
                    helper.runAfterDelay(2, () -> {
                        try {
                            GuardNaturalLoadoutAccess secondAccess =
                                    (GuardNaturalLoadoutAccess) second;
                            helper.assertTrue(
                                    secondAccess
                                                    .villagerGuardGunners$getNaturalLoadoutDecisionStatus()
                                            == NaturalGuardLoadoutSelector.DecisionStatus
                                                    .NEARBY_CAP_REACHED,
                                    "Reloaded natural guard did not consume a nearby-cap slot");
                            helper.assertTrue(
                                    secondAccess
                                                    .villagerGuardGunners$getNaturalLoadoutIssuanceStatus()
                                            == null
                                            && second.getMainHandItem().is(Items.CROSSBOW),
                                    "Population-cap rejection mutated the second guard's equipment");
                            helper.succeed();
                        } finally {
                            original.restore(helper);
                        }
                    });
                } catch (RuntimeException | Error exception) {
                    original.restore(helper);
                    throw exception;
                }
            });
        } catch (RuntimeException | Error exception) {
            original.restore(helper);
            throw exception;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guardConstructionExposesCombinedAmmoCapability(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        GuardAmmoHandler ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
        IItemHandler combinedHandler = guard.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);

        helper.assertTrue(ammoHandler.getSlots() == GuardAmmoHandler.SLOT_COUNT,
                "Guard did not receive the three-slot add-on ammo handler");
        helper.assertTrue(combinedHandler != null,
                "Guard did not expose its combined Forge item capability");
        helper.assertTrue(combinedHandler.getSlots()
                        == guard.guardInventory.getContainerSize() + GuardAmmoHandler.SLOT_COUNT,
                "Combined capability did not include both guard and add-on slots");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void ammunitionModelUsesNativeCompatibilityAndCountsActualReserveUnits(
            GameTestHelper helper) {
        ItemStack gunStack = gun(GLOCK_17, FireMode.SEMI, 7, true);
        var gunIndex = TimelessAPI.getCommonGunIndex(GLOCK_17).orElse(null);
        helper.assertTrue(gunIndex != null, "Reviewed Glock gun data was unavailable");

        GuardAmmoHandler reserves = new GuardAmmoHandler();
        reserves.setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(11).build());
        reserves.setStackInSlot(1, ammoBox(NINE_MILLIMETER, 31, false));
        reserves.setStackInSlot(
                2,
                AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(5).build());

        GuardAmmoSummary.Snapshot summary =
                GuardAmmoSummary.capture(gunStack, gunIndex.getGunData(), reserves);
        AmmoCompatibility.Assessment wrongAmmo =
                AmmoCompatibility.assess(gunStack, reserves.getStackInSlot(2));
        AmmoCompatibility.Assessment emptyBox =
                AmmoCompatibility.assess(gunStack, ammoBox(null, 0, false));

        int expectedCapacity = AttachmentDataUtils.getAmmoCountWithAttachment(
                gunStack,
                gunIndex.getGunData());
        helper.assertTrue(summary.isReady(), "Resolved Glock ammunition summary was unreadable");
        helper.assertTrue(summary.loadedAmmo() == 7,
                "Summary did not preserve TaCZ's loaded magazine count");
        helper.assertTrue(summary.chambered(),
                "Closed-bolt chamber round was not represented");
        helper.assertTrue(summary.supportsChamber(),
                "Closed-bolt gun did not advertise chamber capacity");
        helper.assertTrue(summary.displayedLoadedAmmo() == 8,
                "Closed-bolt displayed load did not include the chamber");
        helper.assertTrue(summary.magazineCapacity() == expectedCapacity,
                "Summary did not use TaCZ's attachment-aware capacity");
        helper.assertTrue(summary.displayedCapacity() == expectedCapacity + 1,
                "Closed-bolt displayed capacity did not include the chamber");

        ItemStack emptyChamberGun = gun(GLOCK_17, FireMode.SEMI, 7, false);
        GuardAmmoSummary.Snapshot emptyChamberSummary = GuardAmmoSummary.capture(
                emptyChamberGun,
                gunIndex.getGunData(),
                reserves);
        helper.assertFalse(emptyChamberSummary.chambered(),
                "Empty closed-bolt chamber was reported as loaded");
        helper.assertTrue(emptyChamberSummary.supportsChamber()
                        && emptyChamberSummary.displayedCapacity() == expectedCapacity + 1,
                "Empty closed-bolt chamber incorrectly reduced the gun's total capacity");

        ItemStack dummyGun = gun(GLOCK_17, FireMode.SEMI, 0, false);
        ((IGun) dummyGun.getItem()).setDummyAmmoAmount(dummyGun, 24);
        GuardAmmoSummary.Snapshot dummySummary = GuardAmmoSummary.capture(
                dummyGun,
                gunIndex.getGunData(),
                reserves);
        helper.assertTrue(dummySummary.reserveSource() == GuardAmmoSummary.ReserveSource.DUMMY
                        && dummySummary.reserveUnits() == 24L,
                "Dummy reserve was not represented as TaCZ's internal ammunition source");
        helper.assertTrue(dummySummary.compatibleStacks() == 0,
                "Dummy reserve incorrectly claimed to be a physical inventory stack");
        helper.assertTrue(summary.reserveUnits() == 42L,
                "Finite ammo-box contents were counted as an item instead of contained units");
        helper.assertTrue(summary.compatibleStacks() == 2,
                "Compatible loose and boxed ammunition were not both recognized");
        helper.assertTrue(
                wrongAmmo.compatibility() == AmmoCompatibility.Compatibility.INCOMPATIBLE,
                "Wrong-caliber loose ammunition was accepted");
        helper.assertTrue(
                emptyBox.compatibility() == AmmoCompatibility.Compatibility.INCOMPATIBLE,
                "An empty finite ammo box was accepted");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void ammunitionModelSeparatesOpenBoltChamberAndCreativeInfinity(
            GameTestHelper helper) {
        ItemStack gunStack = gun(AA_12, FireMode.AUTO, 5, true);
        var gunIndex = TimelessAPI.getCommonGunIndex(AA_12).orElse(null);
        helper.assertTrue(gunIndex != null, "Reviewed AA-12 gun data was unavailable");
        helper.assertTrue(gunIndex.getGunData().getBolt() == Bolt.OPEN_BOLT,
                "Reviewed AA-12 no longer supplies the expected open-bolt fixture");

        GuardAmmoHandler reserves = new GuardAmmoHandler();
        reserves.setStackInSlot(0, ammoBox(null, 0, true));
        GuardAmmoSummary.Snapshot summary =
                GuardAmmoSummary.capture(gunStack, gunIndex.getGunData(), reserves);

        helper.assertFalse(summary.chambered(),
                "Open-bolt gun incorrectly counted the stack's chamber flag");
        helper.assertFalse(summary.supportsChamber(),
                "Open-bolt gun incorrectly advertised chamber capacity");
        helper.assertTrue(summary.displayedLoadedAmmo() == 5,
                "Open-bolt displayed load incorrectly added a chamber round");
        helper.assertTrue(summary.displayedCapacity() == summary.magazineCapacity(),
                "Open-bolt displayed capacity incorrectly added a chamber round");
        helper.assertTrue(summary.infiniteReserve(),
                "Creative ammo box was not represented as an infinite reserve");
        helper.assertTrue(summary.reserveUnits() == 0L,
                "Creative ammo box leaked Integer.MAX_VALUE into the finite reserve total");
        helper.assertTrue(summary.hasCompatibleReserve(),
                "Creative ammo box did not satisfy compatible-reserve state");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void ammunitionTransactionsCommitExactAmountsAndRollbackMismatch(
            GameTestHelper helper) {
        ItemStack offered = AmmoItemBuilder.create()
                .setId(NINE_MILLIMETER)
                .setCount(10)
                .build();
        int stackLimit = offered.getMaxStackSize();
        helper.assertTrue(stackLimit >= 10, "Reviewed 9 mm stack limit is unexpectedly small");

        GuardAmmoHandler source = new GuardAmmoHandler();
        source.setStackInSlot(0, offered.copy());
        GuardAmmoHandler destination = new GuardAmmoHandler();
        destination.setStackInSlot(
                0,
                AmmoItemBuilder.create()
                        .setId(NINE_MILLIMETER)
                        .setCount(stackLimit - 4)
                        .build());

        GuardAmmoTransactions.InsertionPlan plan =
                GuardAmmoTransactions.planInsertion(destination, offered);
        GuardAmmoTransactions.TransferResult committed =
                GuardAmmoTransactions.transfer(source, 0, destination, offered.getCount());

        helper.assertTrue(plan.valid() && plan.accepted() == offered.getCount(),
                "Three-slot insertion plan did not account for the empty destination slots");
        helper.assertTrue(committed.committed() && committed.transferred() == offered.getCount(),
                "Transaction did not commit its exact simulated amount");
        helper.assertTrue(source.getStackInSlot(0).isEmpty(),
                "Committed transfer did not extract its source stack");
        helper.assertTrue(countAmmo(destination, NINE_MILLIMETER) == stackLimit + 6,
                "Committed transfer lost or duplicated ammunition");

        GuardAmmoHandler rollbackSource = new GuardAmmoHandler();
        rollbackSource.setStackInSlot(0, offered.copy());
        ItemStackHandler inconsistentDestination = new SimulationOnlyDestination();
        GuardAmmoTransactions.TransferResult rolledBack = GuardAmmoTransactions.transfer(
                rollbackSource,
                0,
                inconsistentDestination,
                offered.getCount());

        helper.assertTrue(
                rolledBack.status() == GuardAmmoTransactions.TransactionStatus.ROLLED_BACK,
                "Inconsistent insertion was not reported as a rollback");
        helper.assertTrue(rollbackSource.getStackInSlot(0).getCount() == offered.getCount(),
                "Rollback did not restore the extracted source ammunition");
        helper.assertTrue(inconsistentDestination.getStackInSlot(0).isEmpty(),
                "Rollback left ammunition in an inconsistent destination");

        MutatingSimulationDestination mutatingSimulation =
                new MutatingSimulationDestination();
        GuardAmmoTransactions.InsertionPlan rejectedPlan =
                GuardAmmoTransactions.planInsertion(mutatingSimulation, offered);
        helper.assertTrue(
                rejectedPlan.status() == GuardAmmoTransactions.PlanStatus.ROLLED_BACK,
                "A simulation-mutating handler was not rejected and rolled back");
        helper.assertTrue(mutatingSimulation.getStackInSlot(0).isEmpty(),
                "A rejected insertion simulation changed the destination inventory");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void crouchHandoffDepositsOnlyAuthorizedCompatibleAmmunition(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        int originalReputationRequirement = GuardConfig.reputationRequirement;
        Guard guard = spawnGuard(helper);
        Player player = helper.makeMockPlayer();
        boolean originalInstantBuild = player.getAbilities().instabuild;
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
        player.setShiftKeyDown(true);

        try {
            GuardConfig.giveGuardStuffHOTV = false;
            helper.assertTrue(
                    guard.getPlayerReputation(player) < GuardConfig.reputationRequirement,
                    "Quick-deposit fixture unexpectedly already had inventory-access reputation");
            ItemStack compatible = AmmoItemBuilder.create()
                    .setId(NINE_MILLIMETER)
                    .setCount(10)
                    .build();
            player.setItemInHand(InteractionHand.MAIN_HAND, compatible);

            InteractionResult interaction = guard.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(interaction.consumesAction(),
                    "Crouch-right-click ammunition handoff did not consume the interaction");
            helper.assertTrue(player.getMainHandItem().isEmpty(),
                    "Committed handoff did not remove deposited ammunition from the player");
            helper.assertTrue(countAmmo(reserves, NINE_MILLIMETER) == 10,
                    "Committed handoff did not reach the guard's dedicated ammunition slots");

            ItemStack incompatible = AmmoItemBuilder.create()
                    .setId(DEFAULT_AMMO)
                    .setCount(5)
                    .build();
            player.setItemInHand(InteractionHand.MAIN_HAND, incompatible);
            GuardAmmoDeposits.DepositResult incompatibleResult = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    incompatibleResult.status()
                            == GuardAmmoDeposits.DepositStatus.INCOMPATIBLE_AMMUNITION,
                    "Wrong-caliber handoff did not fail closed");
            helper.assertTrue(player.getMainHandItem().getCount() == 5
                            && countAmmo(reserves, NINE_MILLIMETER) == 10,
                    "Rejected wrong-caliber handoff mutated a player or guard inventory");

            reserves.clear();
            int stackLimit = compatible.getMaxStackSize();
            reserves.setStackInSlot(0, AmmoItemBuilder.create()
                    .setId(NINE_MILLIMETER)
                    .setCount(stackLimit)
                    .build());
            reserves.setStackInSlot(1, AmmoItemBuilder.create()
                    .setId(NINE_MILLIMETER)
                    .setCount(stackLimit)
                    .build());
            reserves.setStackInSlot(2, AmmoItemBuilder.create()
                    .setId(NINE_MILLIMETER)
                    .setCount(stackLimit - 3)
                    .build());
            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(8).build());

            GuardAmmoDeposits.DepositResult partial = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(partial.deposited() && partial.inserted() == 3,
                    "Capacity-limited handoff did not commit exactly its simulated amount");
            helper.assertTrue(player.getMainHandItem().getCount() == 5
                            && countAmmo(reserves, NINE_MILLIMETER) == stackLimit * 3,
                    "Partial handoff lost or duplicated ammunition");
            GuardAmmoDeposits.DepositResult full = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    full.status() == GuardAmmoDeposits.DepositStatus.INVENTORY_FULL
                            && player.getMainHandItem().getCount() == 5
                            && countAmmo(reserves, NINE_MILLIMETER) == stackLimit * 3,
                    "Full-inventory handoff did not remain a mutation-free no-op");

            reserves.clear();
            player.getAbilities().instabuild = true;
            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(4).build());
            GuardAmmoDeposits.DepositResult creative = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(creative.deposited()
                            && player.getMainHandItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 4,
                    "Creative handoff did not stock the guard while preserving the held stack");

            reserves.clear();
            player.getAbilities().instabuild = false;
            ItemStack dummyGun = gun(GLOCK_17, FireMode.SEMI, 0, false);
            ((IGun) dummyGun.getItem()).setDummyAmmoAmount(dummyGun, 20);
            guard.setItemSlot(EquipmentSlot.MAINHAND, dummyGun);
            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(4).build());
            GuardAmmoDeposits.DepositResult dummyReserve = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    dummyReserve.status()
                            == GuardAmmoDeposits.DepositStatus.GUN_DOES_NOT_USE_PHYSICAL_AMMO,
                    "Quick deposit accepted physical ammunition for a dummy-reserve gun");
            helper.assertTrue(player.getMainHandItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Rejected dummy-reserve handoff mutated a player or guard inventory");

            guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
            GunReloadData reloadData = TimelessAPI.getCommonGunIndex(GLOCK_17)
                    .orElseThrow()
                    .getGunData()
                    .getReloadData();
            try {
                Field infiniteField = GunReloadData.class.getDeclaredField("infinite");
                infiniteField.setAccessible(true);
                boolean originalInfinite = infiniteField.getBoolean(reloadData);
                try {
                    infiniteField.setBoolean(reloadData, true);
                    GuardAmmoDeposits.DepositResult nativeInfinite = GuardAmmoDeposits.deposit(
                            player,
                            guard,
                            InteractionHand.MAIN_HAND);
                    helper.assertTrue(
                            nativeInfinite.status()
                                    == GuardAmmoDeposits.DepositStatus.GUN_DOES_NOT_USE_PHYSICAL_AMMO,
                            "Quick deposit accepted physical ammunition for a native-infinite gun");
                    helper.assertTrue(player.getMainHandItem().getCount() == 4
                                    && countAmmo(reserves, NINE_MILLIMETER) == 0,
                            "Rejected native-infinite handoff mutated a player or guard inventory");
                } finally {
                    infiniteField.setBoolean(reloadData, originalInfinite);
                }
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not configure the native-infinite TaCZ fixture", exception);
            }

            guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
            guard.setNoAi(true);
            GuardAmmoDeposits.DepositResult inactiveGuard = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    inactiveGuard.status() == GuardAmmoDeposits.DepositStatus.INACTIVE_GUARD,
                    "Quick deposit bypassed Guard Villagers' inactive-AI interaction gate");
            helper.assertTrue(player.getMainHandItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Rejected inactive-guard handoff mutated a player or guard inventory");
            guard.setNoAi(false);

            GuardConfig.giveGuardStuffHOTV = true;
            GuardConfig.reputationRequirement = Integer.MAX_VALUE;
            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(4).build());
            GuardAmmoDeposits.DepositResult unauthorized = GuardAmmoDeposits.deposit(
                    player,
                    guard,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    unauthorized.status() == GuardAmmoDeposits.DepositStatus.UNAUTHORIZED,
                    "Guard Villagers' inventory authorization was bypassed by quick deposit");
            helper.assertTrue(player.getMainHandItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Unauthorized handoff mutated a player or guard inventory");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            GuardConfig.reputationRequirement = originalReputationRequirement;
            player.getAbilities().instabuild = originalInstantBuild;
            player.setShiftKeyDown(false);
            guard.setNoAi(false);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void sidearmAmmoQualityOfLifeRecognizesBothWeaponRoles(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        boolean originalPickup =
                ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.get();
        double originalPickupRadius =
                ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius.get();
        boolean originalSharing =
                ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.get();
        double originalSharingRadius =
                ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius.get();
        int originalRetainedMagazines =
                ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines.get();
        boolean originalOwnerMatching =
                ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing.get();
        boolean originalMobGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);

        Guard recipient = spawnGuard(helper, new BlockPos(0, 1, 0));
        Guard donor = spawnGuard(helper, new BlockPos(3, 1, 0));
        Player player = helper.makeMockPlayer();
        player.setShiftKeyDown(true);
        UUID owner = UUID.randomUUID();
        recipient.setOwnerId(owner);
        donor.setOwnerId(owner);
        recipient.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.SEMI, 0, false));
        donor.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.SEMI, 0, false));
        GuardSidearmGameTestAccess.getStorage(recipient).setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 0, false));
        GuardSidearmGameTestAccess.getStorage(donor).setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 0, false));
        GuardAmmoHandler recipientReserves = GuardAmmoAccess.getAmmoHandler(recipient);
        GuardAmmoHandler donorReserves = GuardAmmoAccess.getAmmoHandler(donor);

        try {
            GuardConfig.giveGuardStuffHOTV = false;
            ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.accept(true);
            ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius.accept(1.5D);
            ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.accept(true);
            ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius.accept(8.0D);
            ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines.accept(1);
            ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing.accept(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(7)
                            .build());
            GuardAmmoDeposits.DepositResult sidearmDeposit =
                    GuardAmmoDeposits.deposit(
                            player,
                            recipient,
                            InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    sidearmDeposit.deposited()
                            && countAmmo(recipientReserves, NINE_MILLIMETER) == 7,
                    "Manual handoff did not recognize the configured sidearm caliber");

            recipientReserves.clear();
            ItemEntity droppedSidearmAmmo = droppedAmmo(
                    helper,
                    recipient.position(),
                    NINE_MILLIMETER,
                    5);
            droppedSidearmAmmo.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult sidearmPickup =
                    GuardDroppedAmmoPickup.tryPickup(droppedSidearmAmmo);
            helper.assertTrue(
                    sidearmPickup.pickedUp()
                            && countAmmo(recipientReserves, NINE_MILLIMETER) == 5,
                    "Dropped-ammo pickup ignored the configured sidearm caliber");

            GuardWeaponAmmunition.LoadoutSummary inactiveSummary =
                    GuardWeaponAmmunition.captureSummary(
                            recipient,
                            GuardAmmoAccess.getTaCZInventoryHandler(recipient));
            helper.assertTrue(
                    inactiveSummary.activeRole()
                                    == GuardWeaponAmmunition.ActiveRole.PRIMARY
                            && AK_47.equals(inactiveSummary.primary().gunId())
                            && GLOCK_17.equals(inactiveSummary.sidearm().gunId())
                            && inactiveSummary.sidearm().reserveUnits() == 5L,
                    "Primary and sidearm ammunition summaries were not separated");

            recipientReserves.clear();
            int sidearmMagazine = inactiveSummary.sidearm().magazineCapacity();
            helper.assertTrue(sidearmMagazine > 0,
                    "Reviewed sidearm magazine capacity was not positive");
            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(sidearmMagazine * 2)
                            .build());

            SidearmTransitionStatus activation = GuardSidearmTransactions.activate(
                    recipient,
                    SidearmActivationReason.PRIMARY_DRY,
                    helper.getLevel().getGameTime(),
                    UUID.randomUUID(),
                    0.0D,
                    true).status();
            helper.assertTrue(
                    activation == SidearmTransitionStatus.ACTIVATED,
                    "Sidearm sharing fixture could not activate its configured sidearm");

            GuardAmmoSharing.SharingResult sidearmSharing =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(
                    sidearmSharing.shared()
                            && sidearmSharing.transferred() == sidearmMagazine
                            && countAmmo(recipientReserves, NINE_MILLIMETER)
                                    == sidearmMagazine
                            && countAmmo(donorReserves, NINE_MILLIMETER)
                                    == sidearmMagazine,
                    "Active sidearm sharing did not preserve the donor sidearm reserve");

            recipientReserves.clear();
            donorReserves.clear();
            donor.setItemSlot(
                    EquipmentSlot.MAINHAND,
                    gun(GLOCK_17, FireMode.SEMI, 0, false));
            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(sidearmMagazine * 3)
                            .build());
            GuardAmmoSharing.SharingResult sharedCaliber =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(
                    sharedCaliber.shared()
                            && sharedCaliber.transferred() == sidearmMagazine
                            && countAmmo(donorReserves, NINE_MILLIMETER)
                                    == sidearmMagazine * 2,
                    "Donor did not retain one magazine for each compatible weapon role");

            GuardWeaponAmmunition.LoadoutSummary activeSummary =
                    GuardWeaponAmmunition.captureSummary(
                            recipient,
                            GuardAmmoAccess.getTaCZInventoryHandler(recipient));
            helper.assertTrue(
                    activeSummary.activeRole()
                                    == GuardWeaponAmmunition.ActiveRole.SIDEARM
                            && AK_47.equals(activeSummary.primary().gunId())
                            && GLOCK_17.equals(activeSummary.sidearm().gunId()),
                    "Active-sidearm summary lost either stable weapon role");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo
                    .accept(originalPickup);
            ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius
                    .accept(originalPickupRadius);
            ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards
                    .accept(originalSharing);
            ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius
                    .accept(originalSharingRadius);
            ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines
                    .accept(originalRetainedMagazines);
            ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing
                    .accept(originalOwnerMatching);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(originalMobGriefing, helper.getLevel().getServer());
            player.setShiftKeyDown(false);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void droppedAmmoPickupIsBoundedCompatibleAndConservative(
            GameTestHelper helper) {
        boolean originalPickup =
                ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.get();
        double originalRadius =
                ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius.get();
        boolean originalMobGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        Guard guard = spawnGuard(helper);
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));

        try {
            ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.accept(true);
            ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius.accept(1.5D);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            ItemEntity naturallyCollected = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    10);
            naturallyCollected.setNoPickUpDelay();
            naturallyCollected.setNoGravity(true);
            naturallyCollected.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(naturallyCollected);
            naturallyCollected.tickCount = Math.floorMod(
                    -naturallyCollected.getId(),
                    GuardDroppedAmmoPickup.CHECK_INTERVAL_TICKS);
            naturallyCollected.tick();
            helper.assertFalse(naturallyCollected.isAlive(),
                    "Eligible dropped ammunition was not collected on the staggered item tick");
            helper.assertTrue(countAmmo(reserves, NINE_MILLIMETER) == 10,
                    "Passive pickup did not insert the complete compatible stack");

            reserves.clear();
            ItemStack depletedBox = ammoBox(null, 0, false);
            helper.assertFalse(GuardAmmoMaintenance.isPotentialSupply(depletedBox),
                    "A depleted finite ammo box was treated as a restocking supply");
            for (int slot = 0; slot < reserves.getSlots(); slot++) {
                reserves.setStackInSlot(slot, depletedBox.copy());
            }
            ItemEntity restockAfterDepletion = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    6);
            restockAfterDepletion.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult restocked =
                    GuardDroppedAmmoPickup.tryPickup(restockAfterDepletion);
            List<ItemEntity> preservedBoxes = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    guard.getBoundingBox().inflate(2.0D),
                    item -> item.getItem().getItem() instanceof IAmmoBox
                            && !GuardAmmoMaintenance.isPotentialSupply(item.getItem()));
            int preservedBoxItems = preservedBoxes.stream()
                    .mapToInt(item -> item.getItem().getCount())
                    .sum();
            helper.assertTrue(restocked.pickedUp()
                            && restocked.inserted() == 6
                            && countAmmo(reserves, NINE_MILLIMETER) == 6,
                    "Depleted ammo boxes permanently blocked automated restocking");
            helper.assertTrue(preservedBoxItems == GuardAmmoHandler.SLOT_COUNT,
                    "Automated restocking did not preserve every depleted finite ammo box");
            preservedBoxes.forEach(Entity::discard);

            reserves.clear();
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, helper.getLevel().getServer());
            ItemEntity gameruleBlocked = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    4);
            gameruleBlocked.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult gameruleBlockedResult =
                    GuardDroppedAmmoPickup.tryPickup(gameruleBlocked);
            helper.assertTrue(
                    gameruleBlockedResult.status()
                                    == GuardDroppedAmmoPickup.PickupStatus.MOB_GRIEFING_DISABLED
                            && gameruleBlocked.getItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Passive pickup ignored the vanilla mobGriefing rule");
            gameruleBlocked.discard();
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            reserves.clear();
            int stackLimit = AmmoItemBuilder.create()
                    .setId(NINE_MILLIMETER)
                    .setCount(1)
                    .build()
                    .getMaxStackSize();
            for (int slot = 0; slot < reserves.getSlots(); slot++) {
                int count = slot == reserves.getSlots() - 1
                        ? stackLimit - 3
                        : stackLimit;
                reserves.setStackInSlot(
                        slot,
                        AmmoItemBuilder.create()
                                .setId(NINE_MILLIMETER)
                                .setCount(count)
                                .build());
            }
            ItemEntity partialDrop = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    8);
            partialDrop.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult partial =
                    GuardDroppedAmmoPickup.tryPickup(partialDrop);
            helper.assertTrue(partial.pickedUp() && partial.inserted() == 3,
                    "Capacity-limited passive pickup did not commit exactly three rounds");
            helper.assertTrue(partialDrop.isAlive()
                            && partialDrop.getItem().getCount() == 5
                            && countAmmo(reserves, NINE_MILLIMETER) == stackLimit * 3,
                    "Partial passive pickup lost or duplicated ammunition");
            GuardDroppedAmmoPickup.PickupResult full =
                    GuardDroppedAmmoPickup.tryPickup(partialDrop);
            helper.assertTrue(
                    full.status() == GuardDroppedAmmoPickup.PickupStatus.NO_ELIGIBLE_GUARD
                            && partialDrop.getItem().getCount() == 5
                            && countAmmo(reserves, NINE_MILLIMETER) == stackLimit * 3,
                    "Full passive-pickup inventory did not remain a mutation-free no-op");
            partialDrop.discard();

            reserves.clear();
            ItemEntity incompatible = droppedAmmo(
                    helper,
                    guard.position(),
                    DEFAULT_AMMO,
                    5);
            incompatible.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult incompatibleResult =
                    GuardDroppedAmmoPickup.tryPickup(incompatible);
            helper.assertTrue(
                    incompatibleResult.status()
                            == GuardDroppedAmmoPickup.PickupStatus.NO_ELIGIBLE_GUARD
                            && incompatible.getItem().getCount() == 5
                            && countAmmo(reserves, DEFAULT_AMMO) == 0,
                    "Passive pickup accepted wrong-caliber ammunition");
            incompatible.discard();

            ItemEntity delayed = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    4);
            delayed.setDefaultPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult delayedResult =
                    GuardDroppedAmmoPickup.tryPickup(delayed);
            helper.assertTrue(
                    delayedResult.status() == GuardDroppedAmmoPickup.PickupStatus.PICKUP_DELAY
                            && delayed.getItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Passive pickup bypassed the dropped item's vanilla pickup delay");
            delayed.discard();

            ItemStack dummyGun = gun(GLOCK_17, FireMode.SEMI, 0, false);
            ((IGun) dummyGun.getItem()).setDummyAmmoAmount(dummyGun, 20);
            guard.setItemSlot(EquipmentSlot.MAINHAND, dummyGun);
            ItemEntity dummyReserveDrop = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    4);
            dummyReserveDrop.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult dummyReserveResult =
                    GuardDroppedAmmoPickup.tryPickup(dummyReserveDrop);
            helper.assertTrue(
                    dummyReserveResult.status()
                            == GuardDroppedAmmoPickup.PickupStatus.NO_ELIGIBLE_GUARD
                            && dummyReserveDrop.getItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Passive pickup stocked a gun using TaCZ's dummy reserve");
            dummyReserveDrop.discard();

            guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
            guard.setNoAi(true);
            ItemEntity inactiveDrop = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    4);
            inactiveDrop.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult inactiveResult =
                    GuardDroppedAmmoPickup.tryPickup(inactiveDrop);
            helper.assertTrue(
                    inactiveResult.status()
                            == GuardDroppedAmmoPickup.PickupStatus.NO_ELIGIBLE_GUARD
                            && inactiveDrop.getItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Passive pickup bypassed the inactive-guard gate");
            inactiveDrop.discard();
            guard.setNoAi(false);

            ItemEntity playerTargeted = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    4);
            playerTargeted.setNoPickUpDelay();
            playerTargeted.setTarget(UUID.randomUUID());
            playerTargeted.setNoGravity(true);
            playerTargeted.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(playerTargeted);
            playerTargeted.tickCount = Math.floorMod(
                    -playerTargeted.getId(),
                    GuardDroppedAmmoPickup.CHECK_INTERVAL_TICKS);
            playerTargeted.tick();
            helper.assertTrue(playerTargeted.isAlive()
                            && playerTargeted.getItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Guard pickup stole an item reserved for a vanilla target UUID");
            playerTargeted.discard();

            ItemEntity outOfRange = droppedAmmo(
                    helper,
                    guard.position().add(2.0D, 0.0D, 0.0D),
                    NINE_MILLIMETER,
                    4);
            outOfRange.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult outOfRangeResult =
                    GuardDroppedAmmoPickup.tryPickup(outOfRange);
            helper.assertTrue(
                    outOfRangeResult.status()
                            == GuardDroppedAmmoPickup.PickupStatus.NO_ELIGIBLE_GUARD
                            && outOfRange.getItem().getCount() == 4,
                    "Passive pickup exceeded its configured spherical radius");
            outOfRange.discard();

            ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.accept(false);
            ItemEntity disabled = droppedAmmo(
                    helper,
                    guard.position(),
                    NINE_MILLIMETER,
                    4);
            disabled.setNoPickUpDelay();
            GuardDroppedAmmoPickup.PickupResult disabledResult =
                    GuardDroppedAmmoPickup.tryPickup(disabled);
            helper.assertTrue(
                    disabledResult.status() == GuardDroppedAmmoPickup.PickupStatus.DISABLED
                            && disabled.getItem().getCount() == 4
                            && countAmmo(reserves, NINE_MILLIMETER) == 0,
                    "Disabled passive pickup mutated a dropped stack or guard inventory");
            disabled.discard();
            helper.succeed();
        } finally {
            ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.accept(originalPickup);
            ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius.accept(originalRadius);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(originalMobGriefing, helper.getLevel().getServer());
            guard.setNoAi(false);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void dryGuardRequestsOwnerSafeAmmoWithoutDrainingDonorReserve(
            GameTestHelper helper) {
        boolean originalSharing =
                ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.get();
        double originalRadius =
                ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius.get();
        int originalRetainedMagazines =
                ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines.get();
        boolean originalMatchingOwner =
                ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing.get();

        Guard recipient = spawnGuard(helper, new BlockPos(0, 1, 0));
        Guard donor = spawnGuard(helper, new BlockPos(3, 1, 0));
        Guard fartherDonor = spawnGuard(helper, new BlockPos(6, 1, 2));
        GuardAmmoHandler recipientReserves = GuardAmmoAccess.getAmmoHandler(recipient);
        GuardAmmoHandler donorReserves = GuardAmmoAccess.getAmmoHandler(donor);
        GuardAmmoHandler fartherDonorReserves = GuardAmmoAccess.getAmmoHandler(fartherDonor);
        ItemStack recipientGun = gun(GLOCK_17, FireMode.SEMI, 0, false);
        recipient.setItemSlot(EquipmentSlot.MAINHAND, recipientGun);
        GuardSidearmStorage recipientSidearms =
                GuardSidearmGameTestAccess.getStorage(recipient);
        recipientSidearms.setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 7, true));
        donor.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
        fartherDonor.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(GLOCK_17, FireMode.SEMI, 0, false));
        UUID sharedOwner = UUID.randomUUID();
        recipient.setOwnerId(sharedOwner);
        donor.setOwnerId(sharedOwner);
        fartherDonor.setOwnerId(sharedOwner);

        int magazineCapacity = GuardAmmoSummary.capture(
                        recipientGun,
                        TimelessAPI.getCommonGunIndex(GLOCK_17).orElseThrow().getGunData(),
                        recipientReserves)
                .magazineCapacity();
        helper.assertTrue(magazineCapacity > 0,
                "Reviewed Glock magazine capacity was not positive");

        GuardGunCombatGoal goal = null;
        try {
            ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.accept(true);
            ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius.accept(8.0D);
            ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines.accept(1);
            ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing.accept(true);

            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(magazineCapacity * 2)
                            .build());
            fartherDonorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(magazineCapacity * 2)
                            .build());
            Ravager target = helper.spawnWithNoFreeWill(
                    EntityType.RAVAGER,
                    new BlockPos(7, 1, 0));
            target.setInvulnerable(true);
            recipient.setTarget(target);
            goal = new GuardGunCombatGoal(recipient);
            helper.assertTrue(goal.canUse(),
                    "Dry sharing fixture could not enter the gun goal");
            goal.start();
            IGunOperator.fromLivingEntity(recipient).getDataHolder().drawTimestamp =
                    System.currentTimeMillis() - 10_000L;
            goal.tick();

            NativeGunController.Diagnostics sharingDiagnostics =
                    goal.captureDiagnostics().nativeState();
            helper.assertTrue(
                    sharingDiagnostics.lastAmmoSharingStatus()
                            == GuardAmmoSharing.SharingStatus.SHARED
                            && sharingDiagnostics.lastAmmoSharedItems() == magazineCapacity,
                    "Native dry-reload path did not request one emergency magazine");
            helper.assertTrue(
                    countAmmo(recipientReserves, NINE_MILLIMETER) == magazineCapacity
                            && countAmmo(donorReserves, NINE_MILLIMETER) == magazineCapacity
                            && countAmmo(fartherDonorReserves, NINE_MILLIMETER)
                                    == magazineCapacity * 2,
                    "Emergency sharing did not select the nearest donor or conserve ammunition");
            helper.assertTrue(sharingDiagnostics.dryRetryTicks() == 0L,
                    "Successful emergency sharing left the recipient in dry cooldown");
            helper.assertTrue(
                    recipientSidearms.stateAgainst(recipient.getMainHandItem())
                                    == SidearmState.PRIMARY_ACTIVE
                            && gunId(recipient.getMainHandItem()).equals(GLOCK_17)
                            && gunId(recipientSidearms.getStackInSlot(
                                    GuardSidearmStorage.SIDEARM_SLOT)).equals(GLOCK_17),
                    "Successful sharing allowed a premature sidearm switch");
            goal.stop();
            goal = null;
            fartherDonor.discard();

            recipientReserves.clear();
            donorReserves.clear();
            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(magazineCapacity + 3)
                            .build());
            GuardAmmoSharing.SharingResult retainedFloor =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(retainedFloor.shared() && retainedFloor.transferred() == 3,
                    "Donor reserve floor did not limit sharing to its three-round surplus");
            helper.assertTrue(
                    countAmmo(recipientReserves, NINE_MILLIMETER) == 3
                            && countAmmo(donorReserves, NINE_MILLIMETER) == magazineCapacity,
                    "Reserve-limited sharing did not conserve the exact loose-ammo count");

            recipientReserves.clear();
            donorReserves.clear();
            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(magazineCapacity * 2)
                            .build());
            donor.setOwnerId(UUID.randomUUID());
            GuardAmmoSharing.SharingResult ownerMismatch =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(
                    ownerMismatch.status() == GuardAmmoSharing.SharingStatus.NO_ELIGIBLE_DONOR
                            && recipientReserves.getStackInSlot(0).isEmpty()
                            && countAmmo(donorReserves, NINE_MILLIMETER) == magazineCapacity * 2,
                    "Matching-owner policy moved ammunition across different owners");

            ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing.accept(false);
            GuardAmmoSharing.SharingResult unrestricted =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(unrestricted.shared()
                            && unrestricted.transferred() == magazineCapacity,
                    "Disabled owner matching still blocked an otherwise eligible donor");

            recipientReserves.clear();
            donorReserves.clear();
            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(magazineCapacity * 2)
                            .build());
            pinEntity(
                    donor,
                    recipient.getX() + 9.0D,
                    recipient.getY(),
                    recipient.getZ());
            GuardAmmoSharing.SharingResult outOfRange =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(
                    outOfRange.status() == GuardAmmoSharing.SharingStatus.NO_ELIGIBLE_DONOR
                            && countAmmo(donorReserves, NINE_MILLIMETER) == magazineCapacity * 2,
                    "Emergency sharing exceeded its configured spherical radius");

            pinEntity(
                    donor,
                    recipient.getX() + 3.0D,
                    recipient.getY(),
                    recipient.getZ());
            donorReserves.clear();
            donorReserves.setStackInSlot(0, ammoBox(NINE_MILLIMETER, magazineCapacity * 2, false));
            GuardAmmoSharing.SharingResult boxedOnly =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(
                    boxedOnly.status() == GuardAmmoSharing.SharingStatus.NO_ELIGIBLE_DONOR
                            && !donorReserves.getStackInSlot(0).isEmpty(),
                    "Emergency sharing moved an ammo box instead of loose ammunition");

            donorReserves.clear();
            donorReserves.setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .setCount(magazineCapacity * 2)
                            .build());
            ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.accept(false);
            GuardAmmoSharing.SharingResult disabled =
                    GuardAmmoSharing.tryShare(recipient);
            helper.assertTrue(
                    disabled.status() == GuardAmmoSharing.SharingStatus.DISABLED
                            && recipientReserves.getStackInSlot(0).isEmpty()
                            && countAmmo(donorReserves, NINE_MILLIMETER) == magazineCapacity * 2,
                    "Disabled emergency sharing mutated either guard inventory");
            helper.succeed();
        } finally {
            if (goal != null) {
                goal.stop();
            }
            ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.accept(originalSharing);
            ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius.accept(originalRadius);
            ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines
                    .accept(originalRetainedMagazines);
            ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing
                    .accept(originalMatchingOwner);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void disabledConfigPreventsGuardGunGoalFromStarting(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(8, 1, 0));
        guard.setTarget(target);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 1, true));
        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        boolean originalEnabled = ModConfigs.GUARD_GUNNERS.enabled.get();

        try {
            ModConfigs.GUARD_GUNNERS.enabled.accept(false);
            helper.assertFalse(goal.canUse(),
                    "A disabled server config still allowed the guard gun goal to start");

            ModConfigs.GUARD_GUNNERS.enabled.accept(true);
            helper.assertTrue(goal.canUse(),
                    "Re-enabling the server config did not restore the guard gun goal");
            helper.succeed();
        } finally {
            ModConfigs.GUARD_GUNNERS.enabled.accept(originalEnabled);
        }
    }

    @GameTest(batch = "command_diagnostics", templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void administratorCanExecuteGuardInspectionCommand(GameTestHelper helper) {
        // Keep the real registered goal set for command discovery. The shared
        // spawnGuard fixture intentionally uses spawnWithNoFreeWill, which
        // removes every goal and is therefore inappropriate for this test.
        Guard guard = helper.spawn(GuardEntityType.GUARD.get(), new BlockPos(0, 1, 0));
        guard.setNoAi(true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                GLOCK_17,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE);
        NaturalGuardLoadoutIssuance.Result issuance =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(issuance.issued() && issuance.provenance() != null,
                "Could not create natural-loadout inspection state");
        GuardNaturalLoadoutAccess naturalAccess = (GuardNaturalLoadoutAccess) guard;
        naturalAccess.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
        naturalAccess.villagerGuardGunners$setNaturalLoadoutProvenance(
                issuance.provenance());
        naturalAccess.villagerGuardGunners$setNaturalLoadoutDecisionStatus(
                NaturalGuardLoadoutSelector.DecisionStatus.SELECTED);
        naturalAccess.villagerGuardGunners$setNaturalLoadoutIssuanceStatus(
                NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED);
        GuardAmmoAccess.getAmmoHandler(guard).clear();
        guard.setItemSlot(
                EquipmentSlot.OFFHAND,
                AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(3).build());
        GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(5).build());

        GuardAmmoSummary.Snapshot ammunition = GuardAmmoSummary.capture(
                guard.getMainHandItem(),
                GuardAmmoAccess.getTaCZInventoryHandler(guard));
        helper.assertTrue(ammunition.reserveUnits() == 3L,
                "Inspection did not use TaCZ's combined guard inventory view");
        helper.assertTrue(ammunition.incompatibleAmmoStacks() == 1,
                "Inspection did not distinguish incompatible reserve ammunition");
        NaturalGuardLoadoutDiagnostics.Snapshot naturalLoadout =
                NaturalGuardLoadoutDiagnostics.capture(guard);
        helper.assertTrue(naturalLoadout.active()
                        && issuance.provenance().equals(naturalLoadout.provenance())
                        && naturalLoadout.lastDecision()
                                == NaturalGuardLoadoutSelector.DecisionStatus.SELECTED
                        && naturalLoadout.lastIssuance()
                                == NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED,
                "Inspection did not authenticate the guard's natural loadout");

        try {
            int result = helper.getLevel().getServer().getCommands().getDispatcher().execute(
                    "vgg inspect " + guard.getStringUUID(),
                    helper.getLevel().getServer().createCommandSourceStack().withPermission(4));
            helper.assertTrue(result == 1,
                    "The registered administrator inspection command did not succeed for a real guard");
            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            helper.fail("The registered administrator inspection command could not be parsed: "
                    + exception.getMessage());
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void ammoInventoryPersistsThroughGuardSaveAndLoad(GameTestHelper helper) {
        Guard original = spawnGuard(helper);
        ItemStack ammo = AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(24).build();
        GuardAmmoAccess.getAmmoHandler(original).setStackInSlot(0, ammo);
        GuardOrders expectedOrders = GuardOrders.DEFAULT
                .withEngagement(EngagementOrder.DEFENSIVE)
                .withTactics(TacticalOrder.MARKSMAN);
        GuardOrdersAccess.setOrders(original, expectedOrders);

        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        helper.assertTrue(saved.contains(AMMO_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "Guard save omitted the isolated add-on ammo inventory");
        helper.assertTrue(saved.contains("villagerguardgunners:orders", Tag.TAG_COMPOUND),
                "Guard save omitted its non-default per-guard orders");

        Guard restored = Objects.requireNonNull(GuardEntityType.GUARD.get().create(helper.getLevel()));
        restored.readAdditionalSaveData(saved);
        ItemStack restoredAmmo = GuardAmmoAccess.getAmmoHandler(restored).getStackInSlot(0);

        helper.assertTrue(restoredAmmo.getCount() == 24,
                "Guard ammo count changed across save/load");
        helper.assertTrue(restoredAmmo.getItem() instanceof IAmmo ammoItem
                        && DEFAULT_AMMO.equals(ammoItem.getAmmoId(restoredAmmo)),
                "Guard ammo identity changed across save/load");
        helper.assertTrue(expectedOrders.equals(GuardOrdersAccess.getOrders(restored)),
                "Per-guard orders changed across save/load");

        ItemStack ledger = new ItemStack(
                com.gamergaming.villagerguardgunners.init.ModItems.ORDERS_LEDGER.get());
        GuardOrderTemplate.writeTo(ledger.getOrCreateTag(), expectedOrders);
        GuardOrderTemplate.DecodeResult decodedLedger =
                GuardOrderTemplate.readFrom(ledger.getTag());
        helper.assertTrue(ledger.getMaxStackSize() == 1,
                "The registered Orders Ledger was unexpectedly stackable");
        helper.assertTrue(ledger.hasFoil(),
                "A written Orders Ledger did not receive its visual glint");
        helper.assertTrue(decodedLedger.isValid()
                        && expectedOrders.equals(decodedLedger.orders()),
                "The registered Orders Ledger did not preserve all raw guard orders");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void configuredSidearmInventoryCannotExposeTheStowedPrimary(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack primary = gun(AK_47, FireMode.AUTO, 17, true);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 9, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());

        IItemHandler configured = GuardSidearmAccess.getConfiguredSidearmInventory(guard);
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        helper.assertTrue(configured.getSlots() == 1,
                "The public sidearm inventory exposed more than its configured slot");
        helper.assertTrue(configured.insertItem(0, sidearm.copy(), false).isEmpty()
                        && stacksMatch(sidearm, storage.getStackInSlot(
                                GuardSidearmStorage.SIDEARM_SLOT)),
                "The public sidearm slot did not map to the configured weapon");

        ItemStack defensiveCopy = configured.getStackInSlot(0);
        defensiveCopy.setCount(0);
        helper.assertTrue(!storage.getStackInSlot(
                        GuardSidearmStorage.SIDEARM_SLOT).isEmpty(),
                "Mutating a public sidearm snapshot changed private storage");
        try {
            configured.getStackInSlot(GuardSidearmStorage.STOWED_PRIMARY_SLOT);
            helper.fail("The public sidearm inventory exposed the stowed-primary slot");
            return;
        } catch (IndexOutOfBoundsException expected) {
            // The private transaction slot must not be addressable.
        }

        var activated = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.PRIMARY_DRY,
                helper.getLevel().getGameTime(),
                UUID.fromString("70000000-0000-0000-0000-000000000098"),
                0.0D,
                true);
        ItemStack rejectedWhileActive = configured.insertItem(
                0,
                sidearm.copy(),
                false);
        ItemStack extractedWhileActive = configured.extractItem(0, 1, false);
        helper.assertTrue(
                activated.status() == SidearmTransitionStatus.ACTIVATED
                        && stacksMatch(sidearm, rejectedWhileActive)
                        && extractedWhileActive.isEmpty()
                        && gunId(storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT)).equals(AK_47),
                "The public sidearm inventory mutated an active transaction layout");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void privateSidearmStoragePersistsAnActiveLayout(GameTestHelper helper) {
        Guard original = spawnGuard(helper);
        ItemStack primary = gun(AK_47, FireMode.AUTO, 17, true);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 9, true);
        original.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());

        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(original);
        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, sidearm.copy());
        helper.assertTrue(storage.stateAgainst(original.getMainHandItem())
                        == SidearmState.PRIMARY_ACTIVE,
                "Configured sidearm did not retain the ordinary primary-active layout");

        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, ItemStack.EMPTY);
        storage.setStackInSlot(GuardSidearmStorage.STOWED_PRIMARY_SLOT, primary.copy());
        original.setItemSlot(EquipmentSlot.MAINHAND, sidearm.copy());
        UUID activationTarget = UUID.fromString(
                "70000000-0000-0000-0000-000000000099");
        storage.markSidearmActive(
                SidearmActivationReason.PRIMARY_DRY,
                100L,
                activationTarget,
                6.5D);

        CompoundTag serialized = new CompoundTag();
        helper.assertTrue(original.save(serialized),
                "Could not serialize a guard with an active sidearm layout");
        helper.assertTrue(serialized.contains(
                        GuardSidearmStorage.STORAGE_TAG,
                        Tag.TAG_COMPOUND),
                "Guard save omitted the versioned private sidearm record");

        Guard restored = Objects.requireNonNull(
                GuardEntityType.GUARD.get().create(helper.getLevel()));
        restored.load(serialized);
        GuardSidearmStorage restoredStorage = GuardSidearmGameTestAccess.getStorage(restored);

        helper.assertTrue(
                restoredStorage.getLoadStatus() == GuardSidearmStorage.LoadStatus.VALID,
                "A valid sidearm record did not decode successfully");
        helper.assertTrue(
                restoredStorage.stateAgainst(restored.getMainHandItem())
                        == SidearmState.SIDEARM_ACTIVE,
                "Active sidearm ownership changed across save/load");
        helper.assertTrue(
                restoredStorage.getActivationReason()
                                == SidearmActivationReason.PRIMARY_DRY
                        && activationTarget.equals(restoredStorage.getActivationTargetId())
                        && restoredStorage.getActivatedAtGameTick() == 100L
                        && restoredStorage.getPrimaryStandoffDistance() == 6.5D,
                "Active sidearm switch context changed across save/load");
        helper.assertTrue(
                gunId(restored.getMainHandItem()).equals(GLOCK_17)
                        && gunId(restoredStorage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT)).equals(AK_47)
                        && restoredStorage.getStackInSlot(
                                GuardSidearmStorage.SIDEARM_SLOT).isEmpty(),
                "Primary or sidearm ItemStack state moved into the wrong restored role");

        IItemHandler taczInventory = GuardAmmoAccess.getTaCZInventoryHandler(restored);
        helper.assertTrue(
                taczInventory.getSlots()
                        == restored.guardInventory.getContainerSize() + GuardAmmoHandler.SLOT_COUNT,
                "Private weapon storage changed TaCZ's established inventory capability size");
        for (int slot = 0; slot < taczInventory.getSlots(); slot++) {
            helper.assertFalse(
                    gunId(taczInventory.getStackInSlot(slot)).equals(AK_47),
                    "Stowed primary leaked into TaCZ's equipment/ammunition capability");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void sidearmTransactionRoundTripPreservesCompleteWeaponStacks(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack primary = gun(AK_47, FireMode.AUTO, 17, true);
        primary.getOrCreateTag().putString("Step3Marker", "primary");
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 9, true);
        sidearm.getOrCreateTag().putString("Step3Marker", "sidearm");
        guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, sidearm.copy());
        UUID targetId = UUID.fromString("70000000-0000-0000-0000-000000000101");

        var activated = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.PRIMARY_DRY,
                240L,
                targetId,
                true);
        helper.assertTrue(activated.status() == SidearmTransitionStatus.ACTIVATED,
                "A valid primary-to-sidearm transaction did not activate: "
                        + activated.status());
        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem()) == SidearmState.SIDEARM_ACTIVE
                        && gunId(guard.getMainHandItem()).equals(GLOCK_17)
                        && "sidearm".equals(guard.getMainHandItem()
                                .getOrCreateTag().getString("Step3Marker"))
                        && stacksMatch(
                                primary,
                                storage.getStackInSlot(
                                        GuardSidearmStorage.STOWED_PRIMARY_SLOT))
                        && storage.getStackInSlot(
                                GuardSidearmStorage.SIDEARM_SLOT).isEmpty()
                        && storage.getActivationReason()
                                == SidearmActivationReason.PRIMARY_DRY
                        && storage.getActivatedAtGameTick() == 240L
                        && targetId.equals(storage.getActivationTargetId()),
                "Activation did not retain complete stacks and exact engagement context");

        var returned = GuardSidearmTransactions.returnPrimary(guard, true);
        helper.assertTrue(returned.status() == SidearmTransitionStatus.RETURNED,
                "A valid sidearm-to-primary transaction did not return: "
                        + returned.status());
        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem()) == SidearmState.PRIMARY_ACTIVE
                        && stacksMatch(primary, guard.getMainHandItem())
                        && stacksMatch(
                                sidearm,
                                storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT))
                        && storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty()
                        && !storage.isSidearmActive()
                        && storage.getActivationReason() == SidearmActivationReason.NONE
                        && storage.getActivationTargetId() == null,
                "Return did not restore the complete primary/sidearm layout");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void sidearmTransactionsFailClosedWithoutChangingWeapons(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack primary = gun(AK_47, FireMode.AUTO, 17, true);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 9, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        UUID targetId = UUID.fromString("70000000-0000-0000-0000-000000000102");

        var noConfiguredSidearm = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.PRIMARY_DRY,
                259L,
                targetId,
                true);
        helper.assertTrue(
                noConfiguredSidearm.status() == SidearmTransitionStatus.ROLLED_BACK
                        && stacksMatch(primary, guard.getMainHandItem())
                        && storage.getStackInSlot(
                                GuardSidearmStorage.SIDEARM_SLOT).isEmpty()
                        && storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty(),
                "Activating an empty configured slot threw or changed weapon ownership");

        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, sidearm.copy());

        var deferred = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.PRIMARY_DRY,
                260L,
                targetId,
                false);
        helper.assertTrue(
                deferred.status() == SidearmTransitionStatus.ROLLED_BACK
                        && stacksMatch(primary, guard.getMainHandItem())
                        && stacksMatch(
                                sidearm,
                                storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT))
                        && storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty(),
                "An unsafe native action state changed the weapon layout");

        ItemStack unexpectedStowedPrimary = gun(M16_A4, FireMode.SEMI, 11, true);
        storage.setStackInSlot(
                GuardSidearmStorage.STOWED_PRIMARY_SLOT,
                unexpectedStowedPrimary.copy());
        var contradictory = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.PRIMARY_DRY,
                261L,
                targetId,
                true);
        helper.assertTrue(
                contradictory.status() == SidearmTransitionStatus.RECOVERY_REQUIRED
                        && storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.RECOVERY_REQUIRED
                        && stacksMatch(primary, guard.getMainHandItem())
                        && stacksMatch(
                                sidearm,
                                storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT))
                        && stacksMatch(
                                unexpectedStowedPrimary,
                                storage.getStackInSlot(
                                        GuardSidearmStorage.STOWED_PRIMARY_SLOT)),
                "A contradictory layout was mutated or reported as stable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void authorizedSidearmRecoveryReturnsPrivateWeaponsWithoutGuessing(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        Guard guard = spawnGuard(helper);
        Player player = helper.makeMockPlayer();
        player.setPos(guard.getX(), guard.getY(), guard.getZ());
        ItemStack equipped = gun(AK_47, FireMode.AUTO, 17, true);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 9, true);
        ItemStack unexpectedStowed = gun(M16_A4, FireMode.BURST, 11, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, equipped.copy());
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, sidearm.copy());
        storage.setStackInSlot(
                GuardSidearmStorage.STOWED_PRIMARY_SLOT,
                unexpectedStowed.copy());

        try {
            GuardConfig.giveGuardStuffHOTV = false;
            GuardSidearmRecovery.RecoveryResult result =
                    GuardSidearmRecovery.recover(guard, player);
            helper.assertTrue(
                    result.recovered()
                            && result.recoveredStackCount() == 2
                            && storage.stateAgainst(guard.getMainHandItem())
                                    == SidearmState.PRIMARY_ACTIVE
                            && storage.getStackInSlot(
                                    GuardSidearmStorage.SIDEARM_SLOT).isEmpty()
                            && storage.getStackInSlot(
                                    GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty()
                            && stacksMatch(equipped, guard.getMainHandItem()),
                    "Authorized recovery guessed a role, lost a private stack, or stayed locked");
            Set<ResourceLocation> recoveredGunIds = new java.util.HashSet<>();
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty() && stack.getItem() instanceof IGun) {
                    recoveredGunIds.add(gunId(stack));
                }
            }
            helper.assertTrue(
                    recoveredGunIds.contains(GLOCK_17)
                            && recoveredGunIds.contains(M16_A4),
                    "Recovered private weapons were not returned to the player");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void activeSidearmPreservesNaturalPrimaryProvenance(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                NaturalGuardLoadoutDefaults.NORMAL_POOL,
                GLOCK_17,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE);
        NaturalGuardLoadoutIssuance.Result issuance =
                NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
        helper.assertTrue(issuance.issued() && issuance.provenance() != null,
                "Could not create the natural-primary sidearm fixture");
        GuardNaturalLoadoutAccess naturalAccess = (GuardNaturalLoadoutAccess) guard;
        naturalAccess.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
        naturalAccess.villagerGuardGunners$setNaturalLoadoutProvenance(issuance.provenance());

        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(AK_47, FireMode.AUTO, 13, true));
        UUID targetId = UUID.fromString("70000000-0000-0000-0000-000000000103");
        var activated = GuardSidearmTransactions.activate(
                guard,
                SidearmActivationReason.EXPLOSIVE_DISALLOWED,
                280L,
                targetId,
                true);
        helper.assertTrue(activated.status() == SidearmTransitionStatus.ACTIVATED,
                "Could not activate the natural-primary sidearm fixture");

        CompoundTag serialized = new CompoundTag();
        helper.assertTrue(guard.save(serialized),
                "Could not serialize a natural primary while its sidearm was active");
        helper.assertTrue(serialized.contains(
                        NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG,
                        Tag.TAG_COMPOUND),
                "Temporarily stowing the natural primary erased its provenance");

        Guard restored = Objects.requireNonNull(
                GuardEntityType.GUARD.get().create(helper.getLevel()));
        restored.load(serialized);
        GuardNaturalLoadoutAccess restoredNatural = (GuardNaturalLoadoutAccess) restored;
        helper.assertTrue(
                issuance.provenance().equals(
                        restoredNatural.villagerGuardGunners$getNaturalLoadoutProvenance())
                        && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                                restored,
                                restoredNatural.villagerGuardGunners$getNaturalLoadoutProvenance())
                        && NaturalGuardLoadoutDiagnostics.capture(restored).active(),
                "Reload did not authenticate the issued primary in private storage");

        var returned = GuardSidearmTransactions.returnPrimary(restored, true);
        helper.assertTrue(returned.status() == SidearmTransitionStatus.RETURNED,
                "Reloaded natural primary could not return from private storage");
        CompoundTag returnedSave = new CompoundTag();
        restored.addAdditionalSaveData(returnedSave);
        helper.assertTrue(returnedSave.contains(
                        NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG,
                        Tag.TAG_COMPOUND),
                "Returning the natural primary erased its provenance");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void unreadableSidearmStorageIsPreservedAndRecoverable(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        ItemStack recoverableSidearm = gun(GLOCK_17, FireMode.SEMI, 7, true);
        CompoundTag futureRecord = new CompoundTag();
        futureRecord.putInt("Version", 99);
        futureRecord.putBoolean("SidearmActive", false);
        futureRecord.put("Sidearm", recoverableSidearm.save(new CompoundTag()));
        futureRecord.putString("FutureField", "preserve-me");
        CompoundTag serialized = new CompoundTag();
        serialized.put(GuardSidearmStorage.STORAGE_TAG, futureRecord.copy());

        Guard guard = spawnGuard(helper);
        guard.readAdditionalSaveData(serialized);
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        helper.assertTrue(
                storage.getLoadStatus() == GuardSidearmStorage.LoadStatus.UNSUPPORTED_VERSION
                        && storage.getStoredVersion() == 99
                        && storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.RECOVERY_REQUIRED,
                "Future sidearm data did not enter the fail-closed recovery state");
        helper.assertTrue(
                storage.recoverableStacks().size() == 1
                        && gunId(storage.recoverableStacks().get(0)).equals(GLOCK_17),
                "The known weapon in a future record was not recoverable for entity removal");

        boolean mutationRejected = false;
        try {
            storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, ItemStack.EMPTY);
        } catch (IllegalStateException expected) {
            mutationRejected = true;
        }
        helper.assertTrue(mutationRejected,
                "Unreadable sidearm storage accepted a destructive mutation");

        CompoundTag resaved = new CompoundTag();
        guard.addAdditionalSaveData(resaved);
        helper.assertTrue(
                futureRecord.equals(resaved.getCompound(GuardSidearmStorage.STORAGE_TAG)),
                "Future sidearm data was rewritten by an older schema");
        helper.assertTrue(
                GuardAmmoAccess.getTaCZInventoryHandler(guard).getSlots()
                        == guard.guardInventory.getContainerSize() + GuardAmmoHandler.SLOT_COUNT,
                "Unreadable private storage leaked into the TaCZ capability");
        Player player = helper.makeMockPlayer();
        player.setPos(guard.getX(), guard.getY(), guard.getZ());
        try {
            GuardConfig.giveGuardStuffHOTV = false;
            GuardSidearmRecovery.RecoveryResult recovery =
                    GuardSidearmRecovery.recover(guard, player);
            boolean playerReceivedSidearm = player.getInventory().items.stream()
                    .anyMatch(stack -> !stack.isEmpty()
                            && stack.getItem() instanceof IGun
                            && GLOCK_17.equals(gunId(stack)));
            helper.assertTrue(
                    recovery.recovered()
                            && recovery.recoveredStackCount() == 1
                            && playerReceivedSidearm
                            && storage.getLoadStatus() == GuardSidearmStorage.LoadStatus.BLANK
                            && storage.stateAgainst(guard.getMainHandItem())
                                    == SidearmState.PRIMARY_ACTIVE,
                    "Authorized recovery did not safely resolve the future-version record");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void blankAndCorruptSidearmRecordsRemainLossless(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        CompoundTag blankSave = new CompoundTag();
        guard.addAdditionalSaveData(blankSave);
        helper.assertTrue(
                storage.getLoadStatus() == GuardSidearmStorage.LoadStatus.BLANK
                        && storage.isWritable()
                        && !blankSave.contains(GuardSidearmStorage.STORAGE_TAG),
                "Blank private storage created unnecessary or unwritable save data");

        CompoundTag corruptRecord = new CompoundTag();
        corruptRecord.putInt("Version", GuardSidearmStorage.CURRENT_SCHEMA_VERSION);
        corruptRecord.putBoolean("SidearmActive", false);
        corruptRecord.putString("Unexpected", "preserve-me");
        CompoundTag corruptSave = new CompoundTag();
        corruptSave.put(GuardSidearmStorage.STORAGE_TAG, corruptRecord.copy());
        guard.readAdditionalSaveData(corruptSave);

        helper.assertTrue(
                storage.getLoadStatus() == GuardSidearmStorage.LoadStatus.CORRUPT
                        && !storage.isWritable()
                        && storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.RECOVERY_REQUIRED,
                "Malformed current sidearm data did not fail closed");
        CompoundTag resaved = new CompoundTag();
        guard.addAdditionalSaveData(resaved);
        helper.assertTrue(
                corruptRecord.equals(resaved.getCompound(GuardSidearmStorage.STORAGE_TAG)),
                "Malformed current sidearm data was not preserved byte-for-byte");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void sidearmVersionOneMigratesWithoutGuessingAStandoff(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        ItemStack primary = gun(RPG_7, FireMode.SEMI, 1, true);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 7, true);
        UUID targetId = UUID.fromString("70000000-0000-0000-0000-000000000104");

        CompoundTag versionOne = new CompoundTag();
        versionOne.putInt("Version", 1);
        versionOne.putBoolean("SidearmActive", true);
        versionOne.put("StowedPrimary", primary.save(new CompoundTag()));
        versionOne.putString(
                "ActivationReason",
                SidearmActivationReason.EXPLOSIVE_STANDOFF.name());
        versionOne.putLong("ActivatedAt", 300L);
        versionOne.putUUID("Target", targetId);
        CompoundTag parent = new CompoundTag();
        parent.put(GuardSidearmStorage.STORAGE_TAG, versionOne);

        guard.setItemSlot(EquipmentSlot.MAINHAND, sidearm);
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        helper.assertTrue(
                storage.loadFrom(parent) == GuardSidearmStorage.LoadStatus.VALID
                        && storage.getStoredVersion() == 1
                        && storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.SIDEARM_ACTIVE
                        && storage.getPrimaryStandoffDistance() == Double.MAX_VALUE,
                "A version-one active sidearm did not migrate with a fail-closed standoff");

        CompoundTag upgradedParent = new CompoundTag();
        storage.saveTo(upgradedParent, guard.getMainHandItem());
        CompoundTag upgraded = upgradedParent.getCompound(
                GuardSidearmStorage.STORAGE_TAG);
        helper.assertTrue(
                upgraded.getInt("Version") == GuardSidearmStorage.CURRENT_SCHEMA_VERSION
                        && upgraded.contains("PrimaryStandoff", Tag.TAG_DOUBLE)
                        && upgraded.getDouble("PrimaryStandoff") == Double.MAX_VALUE,
                "Saving a version-one sidearm did not upgrade its complete hysteresis context");
        helper.succeed();
    }

    @GameTest(batch = "sidearm_lifecycle", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void dryPrimaryActivatesSidearmAndLifecycleRestoresIt(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 1, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        ItemStack primary = gun(AK_47, FireMode.AUTO, 0, false);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 7, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, sidearm.copy());
        guard.setTarget(target);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The dry-primary sidearm fixture could not enter the gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        forceNativeSwapFixtureIdle(operator);
        goal.tick();

        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem()) == SidearmState.SIDEARM_ACTIVE
                        && gunId(guard.getMainHandItem()).equals(GLOCK_17)
                        && gunId(storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT)).equals(AK_47)
                        && storage.getActivationReason()
                                == SidearmActivationReason.PRIMARY_DRY
                        && target.getUUID().equals(storage.getActivationTargetId()),
                "A confirmed dry primary did not activate its ready pistol sidearm");

        // Remove both the mob target and the goal's retained target reference
        // as an eligible engagement. Merely clearing Guard#getTarget can be
        // repopulated by another selector tick in the integrated fixture.
        target.discard();
        guard.setTarget(null);
        helper.onEachTick(() -> {
            forceNativeSwapFixtureIdle(operator);
            goal.tick();
            if (storage.stateAgainst(guard.getMainHandItem())
                    == SidearmState.PRIMARY_ACTIVE) {
                helper.assertTrue(
                        gunId(guard.getMainHandItem()).equals(AK_47)
                                && gunId(storage.getStackInSlot(
                                        GuardSidearmStorage.SIDEARM_SLOT)).equals(GLOCK_17)
                                && storage.getStackInSlot(
                                        GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty(),
                        "Ending the engagement did not restore both complete weapon roles");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(60, () -> {
            GuardGunCombatGoal.Diagnostics diagnostics = goal.captureDiagnostics();
            goal.stop();
            helper.fail("The active sidearm did not return after its engagement ended; "
                    + "storage="
                    + storage.stateAgainst(guard.getMainHandItem())
                    + ", reload="
                    + diagnostics.nativeState().reloadState()
                    + ", bolting="
                    + diagnostics.nativeState().bolting()
                    + ", drawCooldownMs="
                    + diagnostics.nativeState().drawCooldownMillis()
                    + ", shootCooldownMs="
                    + diagnostics.nativeState().shootCooldownMillis());
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void explosiveSidearmReturnHonorsTimeAndDistanceHysteresis(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 1, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        ItemStack primary = gun(RPG_7, FireMode.SEMI, 1, true);
        ItemStack sidearm = gun(GLOCK_17, FireMode.SEMI, 7, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, sidearm.copy());
        guard.setTarget(target);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The explosive sidearm-hysteresis fixture could not enter the gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        goal.tick();

        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem()) == SidearmState.SIDEARM_ACTIVE
                        && storage.getActivationReason()
                                == SidearmActivationReason.EXPLOSIVE_STANDOFF
                        && storage.getPrimaryStandoffDistance() == 10.0D
                        && gunId(guard.getMainHandItem()).equals(GLOCK_17),
                "The close RPG target did not activate a sidearm with its cached standoff");

        long activatedAt = storage.getActivatedAtGameTick();
        double guardX = guard.getX();
        double guardY = guard.getY();
        double guardZ = guard.getZ();
        helper.onEachTick(() -> {
            pinEntity(guard, guardX, guardY, guardZ);
            pinEntity(target, guardX + 13.0D, target.getY(), target.getZ());
            guard.setTarget(target);
            operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
            operator.getDataHolder().shootTimestamp = -10_000L;
            operator.getDataHolder().lastShootTimestamp = -10_000L;
            goal.tick();

            long activeTicks = helper.getLevel().getGameTime() - activatedAt;
            SidearmState state = storage.stateAgainst(guard.getMainHandItem());
            if (activeTicks < 40L) {
                helper.assertTrue(state == SidearmState.SIDEARM_ACTIVE,
                        "The explosive sidearm returned before its minimum active time");
            } else if (state == SidearmState.PRIMARY_ACTIVE) {
                helper.assertTrue(
                        gunId(guard.getMainHandItem()).equals(RPG_7)
                                && gunId(storage.getStackInSlot(
                                        GuardSidearmStorage.SIDEARM_SLOT)).equals(GLOCK_17),
                        "The hysteresis return did not restore the RPG and sidearm roles");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(100, () -> {
            goal.stop();
            helper.fail("The RPG did not return after clearing time and distance hysteresis");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void activeSidearmDeathDropsEachOwnedWeaponExactlyOnce(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 17, true));
        GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 7, true));
        helper.assertTrue(
                GuardSidearmTransactions.activate(
                                guard,
                                SidearmActivationReason.PRIMARY_DRY,
                                helper.getLevel().getGameTime(),
                                UUID.fromString("70000000-0000-0000-0000-000000000106"),
                                0.0D,
                                true)
                        .status() == SidearmTransitionStatus.ACTIVATED,
                "The active-sidearm death fixture could not activate");
        Vec3 dropCenter = guard.position();
        double originalDropChance = GuardConfig.COMMON.chanceToDropEquipment.get();
        try {
            GuardConfig.COMMON.chanceToDropEquipment.set(1.0D);
            guard.die(helper.getLevel().damageSources().generic());
        } finally {
            GuardConfig.COMMON.chanceToDropEquipment.set(originalDropChance);
        }

        helper.succeedWhen(() -> {
            int droppedSidearms = countDroppedGuns(helper, dropCenter, GLOCK_17);
            int droppedPrimaries = countDroppedGuns(helper, dropCenter, AK_47);
            helper.assertTrue(droppedSidearms == 1 && droppedPrimaries == 1,
                    "Active-sidearm death expected one drop per weapon but found sidearms="
                            + droppedSidearms + ", primaries=" + droppedPrimaries);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guardMenuConfiguresOnlyEligibleAuthorizedSidearms(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        int originalReputationRequirement = GuardConfig.reputationRequirement;
        Guard guard = spawnGuard(helper);
        Player player = helper.makeMockPlayer();
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.SEMI, 7, true));
        GuardGunnerContainer menu = new GuardGunnerContainer(
                7,
                player.getInventory(),
                guard.guardInventory,
                GuardAmmoAccess.getAmmoHandler(guard),
                guard);
        IItemHandler configuredSidearm =
                GuardSidearmAccess.getConfiguredSidearmInventory(guard);
        try {
            GuardConfig.giveGuardStuffHOTV = false;
            player.getInventory().setItem(
                    9,
                    gun(GLOCK_17, FireMode.SEMI, 7, true));
            helper.assertTrue(
                    !menu.quickMoveStack(
                            player,
                            GuardGunnerContainer.ADDON_SLOT_COUNT).isEmpty()
                            && gunId(configuredSidearm.getStackInSlot(0)).equals(GLOCK_17)
                            && gunId(guard.getMainHandItem()).equals(AK_47),
                    "Authorized shift-click did not fill the sidearm after the occupied primary");
            helper.assertFalse(
                    menu.getSlot(GuardGunnerContainer.SIDEARM_SLOT_INDEX)
                            .mayPlace(gun(RPG_7, FireMode.SEMI, 1, true)),
                    "The player-facing sidearm slot accepted an explosive weapon");
            helper.assertFalse(
                    menu.getSlot(GuardGunnerContainer.SIDEARM_SLOT_INDEX)
                            .mayPlace(new ItemStack(Items.IRON_SWORD)),
                    "The player-facing sidearm slot accepted a non-TaCZ weapon");

            GuardConfig.giveGuardStuffHOTV = true;
            GuardConfig.reputationRequirement = Integer.MAX_VALUE;
            ItemStack configuredBefore = configuredSidearm.getStackInSlot(0);
            helper.assertFalse(
                    menu.getSlot(GuardGunnerContainer.SIDEARM_SLOT_INDEX)
                            .mayPickup(player),
                    "An unauthorized player could pick up the configured sidearm");
            helper.assertTrue(
                    menu.quickMoveStack(
                            player,
                            GuardGunnerContainer.SIDEARM_SLOT_INDEX).isEmpty()
                            && stacksMatch(
                                    configuredBefore,
                                    configuredSidearm.getStackInSlot(0)),
                    "Unauthorized shift-click changed the configured sidearm");

            GuardConfig.giveGuardStuffHOTV = false;
            helper.assertTrue(
                    !menu.quickMoveStack(
                            player,
                            GuardGunnerContainer.SIDEARM_SLOT_INDEX).isEmpty()
                            && configuredSidearm.getStackInSlot(0).isEmpty(),
                    "An authorized player could not remove an inactive configured sidearm");
            player.getInventory().clearContent();
            guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.getInventory().setItem(
                    9,
                    gun(GLOCK_17, FireMode.SEMI, 7, true));
            helper.assertTrue(
                    !menu.quickMoveStack(
                            player,
                            GuardGunnerContainer.ADDON_SLOT_COUNT).isEmpty()
                            && gunId(guard.getMainHandItem()).equals(GLOCK_17)
                            && configuredSidearm.getStackInSlot(0).isEmpty(),
                    "Shift-click did not prefer an empty primary before the sidearm slot");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            GuardConfig.reputationRequirement = originalReputationRequirement;
            menu.removed(player);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void explosiveOrdersActivateAReadyNonExplosiveSidearm(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(13, 1, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(RPG_7, FireMode.SEMI, 1, true));
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 7, true));
        GuardOrdersAccess.setOrders(
                guard,
                GuardOrders.DEFAULT.withExplosives(GuardOrders.ToggleOrder.DISABLED));
        guard.setTarget(target);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "An explosive-order fallback could not retain the gun goal");
        goal.start();
        forceNativeSwapFixtureIdle(IGunOperator.fromLivingEntity(guard));
        goal.tick();

        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.SIDEARM_ACTIVE
                        && storage.getActivationReason()
                                == SidearmActivationReason.EXPLOSIVE_DISALLOWED
                        && gunId(guard.getMainHandItem()).equals(GLOCK_17)
                        && gunId(storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT)).equals(RPG_7),
                "Explosive Deny did not replace the RPG with its safe sidearm");
        goal.stop();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE,
            timeoutTicks = 40)
    public static void sidearmSwitchPreservesShieldAndTargetChangeReturnsPrimary(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        Guard guard = spawnGuard(helper);
        Ravager firstTarget = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 1, 0));
        Ravager secondTarget = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 1, 3));
        configureFixedCombatant(guard);
        configureFixedCombatant(firstTarget);
        configureFixedCombatant(secondTarget);
        ItemStack shield = new ItemStack(Items.SHIELD);
        shield.getOrCreateTag().putString("Step7Marker", "preserved-shield");
        guard.setItemSlot(EquipmentSlot.OFFHAND, shield.copy());
        guard.startUsingItem(InteractionHand.OFF_HAND);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 0, false));
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 7, true));
        guard.setTarget(firstTarget);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The shield/target-change sidearm fixture could not enter the gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        forceNativeSwapFixtureIdle(operator);
        goal.tick();
        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.SIDEARM_ACTIVE
                        && stacksMatch(shield, guard.getOffhandItem())
                        && !guard.isUsingItem(),
                "Sidearm activation changed the shield stack or left it raised");

        Player player = helper.makeMockPlayer();
        GuardGunnerContainer menu = new GuardGunnerContainer(
                8,
                player.getInventory(),
                guard.guardInventory,
                GuardAmmoAccess.getAmmoHandler(guard),
                guard);
        try {
            GuardConfig.giveGuardStuffHOTV = false;
            helper.assertFalse(menu.getSlot(5).mayPickup(player),
                    "The active sidearm could be removed through the primary slot");
            helper.assertTrue(menu.quickMoveStack(player, 5).isEmpty()
                            && gunId(guard.getMainHandItem()).equals(GLOCK_17),
                    "Shift-click broke an active sidearm transaction");
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            menu.removed(player);
        }

        guard.setTarget(secondTarget);
        // The runtime controller intentionally evaluates at most once per game
        // tick. Also clear the fixture before TaCZ's next entity-data sync so
        // both native and synchronized draw cooldowns settle before the return.
        forceNativeSwapFixtureIdle(operator);
        helper.succeedWhen(() -> {
            forceNativeSwapFixtureIdle(operator);
            goal.tick();
            GuardGunCombatGoal.Diagnostics diagnostics = goal.captureDiagnostics();
            helper.assertTrue(
                    storage.stateAgainst(guard.getMainHandItem())
                                    == SidearmState.PRIMARY_ACTIVE
                            && gunId(guard.getMainHandItem()).equals(AK_47)
                            && gunId(storage.getStackInSlot(
                                    GuardSidearmStorage.SIDEARM_SLOT)).equals(GLOCK_17)
                            && stacksMatch(shield, guard.getOffhandItem()),
                    "Changing targets did not restore the primary and preserve the shield; "
                            + "state=" + storage.stateAgainst(guard.getMainHandItem())
                            + ", reload=" + diagnostics.nativeState().reloadState()
                            + ", bolting=" + diagnostics.nativeState().bolting()
                            + ", drawCooldownMs="
                            + diagnostics.nativeState().drawCooldownMillis()
                            + ", shootCooldownMs="
                            + diagnostics.nativeState().shootCooldownMillis());
            goal.stop();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void drySidearmAndExternalReplacementFailClosed(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 1, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 0, false));
        GuardSidearmStorage storage = GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 0, false));
        guard.setTarget(target);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The both-weapons-dry fixture could not enter the gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        forceNativeSwapFixtureIdle(operator);
        goal.tick();
        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.PRIMARY_ACTIVE
                        && gunId(guard.getMainHandItem()).equals(AK_47)
                        && gunId(storage.getStackInSlot(
                                GuardSidearmStorage.SIDEARM_SLOT)).equals(GLOCK_17)
                        && storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty(),
                "A dry primary switched to a sidearm with no loaded or reserve ammunition");

        helper.assertTrue(
                GuardSidearmTransactions.activate(
                                guard,
                                SidearmActivationReason.PRIMARY_DRY,
                                helper.getLevel().getGameTime(),
                                target.getUUID(),
                                0.0D,
                                true)
                        .status() == SidearmTransitionStatus.ACTIVATED,
                "The external-replacement recovery fixture could not activate");
        ItemStack displacedSidearm = guard.getMainHandItem().copy();
        ItemStack replacement = gun(M16_A4, FireMode.BURST, 5, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, replacement.copy());
        helper.assertTrue(goal.canContinueToUse(),
                "Recovery-required ownership did not retain protective goal control");
        goal.tick();
        helper.assertTrue(
                storage.stateAgainst(guard.getMainHandItem())
                                == SidearmState.RECOVERY_REQUIRED
                        && gunId(displacedSidearm).equals(GLOCK_17)
                        && stacksMatch(replacement, guard.getMainHandItem())
                        && gunId(storage.getStackInSlot(
                                GuardSidearmStorage.STOWED_PRIMARY_SLOT)).equals(AK_47),
                "External replacement overwrote the retained primary or escaped recovery suppression");
        goal.stop();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void squadMembershipPersistsIndependentlyThroughGuardSaveAndLoad(
            GameTestHelper helper) {
        UUID squadId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        SquadMembership expected = SquadMembership.assigned(squadId);
        Guard original = spawnGuard(helper);

        helper.assertTrue(GuardSquadAccess.getMembership(original) == null,
                "A new guard did not default to an unassigned squad state");
        int revisionBeforeAssignment = GuardSquadAccess.getRevision(original);
        GuardSquadAccess.setMembership(original, expected);
        GuardSquadAccess.setMembership(original, expected);
        helper.assertTrue(
                GuardSquadAccess.getRevision(original) == revisionBeforeAssignment + 1,
                "A squad assignment did not invalidate exactly once");

        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        helper.assertTrue(saved.contains(
                        SquadMembershipCodec.GUARD_MEMBERSHIP_TAG,
                        Tag.TAG_COMPOUND),
                "Guard save omitted its non-default squad membership");
        helper.assertTrue(!saved.contains("villagerguardgunners:orders"),
                "Squad membership was incorrectly serialized as a behavior order");

        Guard restored = Objects.requireNonNull(
                GuardEntityType.GUARD.get().create(helper.getLevel()));
        restored.readAdditionalSaveData(saved);
        helper.assertTrue(expected.equals(GuardSquadAccess.getMembership(restored))
                        && squadId.equals(GuardSquadAccess.getSquadId(restored)),
                "Squad identity changed across guard save/load");

        Zombie focusTarget = helper.spawnWithNoFreeWill(
                EntityType.ZOMBIE,
                new BlockPos(3, 1, 0));
        SquadCommand staleFocus = SquadCommand.focusTarget(
                UUID.randomUUID(),
                helper.getLevel().getGameTime(),
                helper.getLevel().getGameTime() + 100L,
                helper.getLevel().dimension().location(),
                focusTarget.getUUID());
        GuardSquadCommandAccess.setCommand(restored, staleFocus);
        restored.setTarget(focusTarget);
        GuardSquadAccess.setMembership(restored, null);
        CompoundTag unassignedSave = new CompoundTag();
        restored.addAdditionalSaveData(unassignedSave);
        helper.assertTrue(!unassignedSave.contains(
                                SquadMembershipCodec.GUARD_MEMBERSHIP_TAG)
                        && GuardSquadCommandAccess.getCommand(restored) == null
                        && restored.getTarget() == null,
                "Removing squad membership retained stale membership, command, or Focus state");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void squadCommandLedgerManagesStableMembershipSafely(
            GameTestHelper helper) {
        int originalReputationRequirement = GuardConfig.reputationRequirement;
        Guard first = spawnGuard(helper);
        Guard second = spawnGuard(helper, new BlockPos(2, 1, 0));
        Guard third = spawnGuard(helper, new BlockPos(4, 1, 0));
        Guard fourth = spawnGuard(helper, new BlockPos(6, 1, 0));
        Guard unauthorizedGuard = spawnGuard(helper, new BlockPos(8, 1, 0));
        Player player = helper.makeMockPlayer();
        player.setShiftKeyDown(true);
        first.setOwnerId(player.getUUID());
        second.setOwnerId(player.getUUID());
        third.setOwnerId(player.getUUID());
        fourth.setOwnerId(player.getUUID());
        ItemStack preservedWeapon = new ItemStack(Items.CROSSBOW);
        first.setItemSlot(EquipmentSlot.MAINHAND, preservedWeapon.copy());

        SquadRegistrySavedData registry = SquadRegistrySavedData.get(
                Objects.requireNonNull(helper.getLevel().getServer()));
        ItemStack alphaLedger = new ItemStack(
                com.gamergaming.villagerguardgunners.init.ModItems
                        .SQUAD_COMMAND_LEDGER.get());
        alphaLedger.setHoverName(
                net.minecraft.network.chat.Component.literal("Alpha Team"));
        player.setItemInHand(InteractionHand.MAIN_HAND, alphaLedger);

        try {
            player.setPos(first.getX(), first.getY(), first.getZ());
            InteractionResult created = first.interact(
                    player,
                    InteractionHand.MAIN_HAND);
            SquadLedgerBindingCodec.DecodeResult createdBinding =
                    SquadLedgerBindingCodec.readFrom(alphaLedger.getTag());
            SquadLedgerBinding alpha = Objects.requireNonNull(
                    createdBinding.binding());
            UUID alphaId = alpha.squadId();
            helper.assertTrue(created.consumesAction()
                            && createdBinding.isValid()
                            && alphaLedger.getCount() == 1
                            && alphaLedger.getMaxStackSize() == 1
                            && alphaLedger.hasFoil()
                            && alphaId.equals(GuardSquadAccess.getSquadId(first))
                            && "Alpha Team".equals(registry.find(alphaId)
                                    .orElseThrow()
                                    .displayName()),
                    "A named blank Squad Command Ledger did not create and bind one stable squad");
            ItemStack neverUsedStaleCopy = alphaLedger.copy();
            ItemStack workingCopy = alphaLedger.copy();

            player.setPos(second.getX(), second.getY(), second.getZ());
            player.setItemInHand(InteractionHand.MAIN_HAND, workingCopy);
            InteractionResult assigned = second.interact(
                    player,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(assigned.consumesAction()
                            && alphaId.equals(GuardSquadAccess.getSquadId(second))
                            && workingCopy.getCount() == 1,
                    "A copied bound ledger did not add another authorized guard to the same squad");
            second.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(GuardSquadAccess.getMembership(second) == null,
                    "Sneak-using the same squad ledger did not remove an existing member");

            helper.assertTrue(registry.remove(alphaId)
                            && registry.find(alphaId).isEmpty(),
                    "The metadata-recovery fixture could not remove its temporary registry entry");
            ItemStack recoveryLedger = new ItemStack(
                    com.gamergaming.villagerguardgunners.init.ModItems
                            .SQUAD_COMMAND_LEDGER.get());
            recoveryLedger.setHoverName(
                    net.minecraft.network.chat.Component.literal("Recovered Alpha"));
            player.setItemInHand(InteractionHand.MAIN_HAND, recoveryLedger);
            player.setPos(first.getX(), first.getY(), first.getZ());
            first.interact(player, InteractionHand.MAIN_HAND);
            SquadLedgerBinding recovered = Objects.requireNonNull(
                    SquadLedgerBindingCodec.readFrom(recoveryLedger.getTag()).binding());
            helper.assertTrue(alphaId.equals(recovered.squadId())
                            && alphaId.equals(GuardSquadAccess.getSquadId(first))
                            && "Recovered Alpha".equals(registry.find(alphaId)
                                    .orElseThrow()
                                    .displayName()),
                    "A blank ledger did not recover missing metadata from an assigned guard's stable ID");

            player.setItemInHand(InteractionHand.MAIN_HAND, workingCopy);
            player.setPos(second.getX(), second.getY(), second.getZ());
            second.interact(player, InteractionHand.MAIN_HAND);
            SquadLedgerBinding refreshedWorkingCopy = Objects.requireNonNull(
                    SquadLedgerBindingCodec.readFrom(workingCopy.getTag()).binding());
            helper.assertTrue(alphaId.equals(GuardSquadAccess.getSquadId(second))
                            && "Recovered Alpha".equals(
                                    refreshedWorkingCopy.cachedDisplayName())
                            && "Recovered Alpha".equals(
                                    workingCopy.getHoverName().getString()),
                    "A stale copied ledger did not refresh from authoritative recovered metadata");
            second.interact(player, InteractionHand.MAIN_HAND);

            ItemStack bravoLedger = new ItemStack(
                    com.gamergaming.villagerguardgunners.init.ModItems
                            .SQUAD_COMMAND_LEDGER.get());
            bravoLedger.setHoverName(
                    net.minecraft.network.chat.Component.literal("Bravo Team"));
            player.setItemInHand(InteractionHand.MAIN_HAND, bravoLedger);
            player.setPos(third.getX(), third.getY(), third.getZ());
            third.interact(player, InteractionHand.MAIN_HAND);
            UUID bravoId = Objects.requireNonNull(
                    SquadLedgerBindingCodec.readFrom(bravoLedger.getTag()).binding())
                    .squadId();
            helper.assertTrue(!alphaId.equals(bravoId)
                            && bravoId.equals(GuardSquadAccess.getSquadId(third)),
                    "A second blank ledger did not create an independent squad identity");

            player.setItemInHand(InteractionHand.MAIN_HAND, recoveryLedger);
            InteractionResult warning = third.interact(
                    player,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(warning.consumesAction()
                            && bravoId.equals(GuardSquadAccess.getSquadId(third)),
                    "The first cross-squad use moved a guard without explicit confirmation");
            InteractionResult reassigned = third.interact(
                    player,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(reassigned.consumesAction()
                            && alphaId.equals(GuardSquadAccess.getSquadId(third)),
                    "Repeating the exact cross-squad use did not confirm reassignment");

            recoveryLedger.setHoverName(
                    net.minecraft.network.chat.Component.literal("Alpha Renamed"));
            player.setPos(second.getX(), second.getY(), second.getZ());
            recoveryLedger = player.getMainHandItem();
            second.interact(player, InteractionHand.MAIN_HAND);
            SquadLedgerBinding renamed = Objects.requireNonNull(
                    SquadLedgerBindingCodec.readFrom(recoveryLedger.getTag()).binding());
            helper.assertTrue(alphaId.equals(GuardSquadAccess.getSquadId(second))
                            && "Alpha Renamed".equals(renamed.cachedDisplayName())
                            && "Alpha Renamed".equals(registry.find(alphaId)
                                    .orElseThrow()
                                    .displayName()),
                    "An authorized anvil-style ledger rename did not update authoritative metadata");

            player.setItemInHand(InteractionHand.MAIN_HAND, neverUsedStaleCopy);
            second.interact(player, InteractionHand.MAIN_HAND);
            SquadLedgerBinding refreshedStaleCopy = Objects.requireNonNull(
                    SquadLedgerBindingCodec.readFrom(neverUsedStaleCopy.getTag()).binding());
            helper.assertTrue(GuardSquadAccess.getMembership(second) == null
                            && "Alpha Renamed".equals(
                                    refreshedStaleCopy.cachedDisplayName())
                            && "Alpha Renamed".equals(registry.find(alphaId)
                                    .orElseThrow()
                                    .displayName()),
                    "An old copied ledger renamed authoritative squad metadata back to its stale cache");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, neverUsedStaleCopy);
            player.setPos(fourth.getX(), fourth.getY(), fourth.getZ());
            InteractionResult offhand = fourth.interact(
                    player,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(offhand.consumesAction()
                            && alphaId.equals(GuardSquadAccess.getSquadId(fourth))
                            && player.getOffhandItem().getCount() == 1,
                    "An offhand squad ledger with an empty main hand did not manage membership");

            GuardConfig.reputationRequirement = Integer.MAX_VALUE;
            ItemStack deniedLedger = new ItemStack(
                    com.gamergaming.villagerguardgunners.init.ModItems
                            .SQUAD_COMMAND_LEDGER.get());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, deniedLedger);
            player.setPos(
                    unauthorizedGuard.getX(),
                    unauthorizedGuard.getY(),
                    unauthorizedGuard.getZ());
            int registrySizeBeforeDenial = registry.size();
            InteractionResult denied = unauthorizedGuard.interact(
                    player,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(denied.consumesAction()
                            && GuardSquadAccess.getMembership(unauthorizedGuard) == null
                            && deniedLedger.getTag() == null
                            && registry.size() == registrySizeBeforeDenial,
                    "An unauthorized ledger use changed a guard, item, or world registry");
            helper.assertTrue(ItemStack.isSameItemSameTags(
                            preservedWeapon,
                            first.getMainHandItem()),
                    "Squad membership management changed a guard's equipment");
            helper.succeed();
        } finally {
            GuardConfig.reputationRequirement = originalReputationRequirement;
            player.setShiftKeyDown(false);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void squadCommandsDispatchBoundedlyAndPersistIndependently(
            GameTestHelper helper) {
        double originalDispatchRadius =
                ModConfigs.GUARD_GUNNERS.squads.commandDispatchRadius.get();
        int originalMaximumAffected =
                ModConfigs.GUARD_GUNNERS.squads.maximumAffectedGuards.get();
        int originalPositionalDuration = ModConfigs.GUARD_GUNNERS.squads
                .positionalCommandDurationTicks.get();
        int originalFocusDuration =
                ModConfigs.GUARD_GUNNERS.squads.focusCommandDurationTicks.get();
        double originalDefendRadius =
                ModConfigs.GUARD_GUNNERS.squads.defendRadius.get();
        double originalAirRange =
                ModConfigs.GUARD_GUNNERS.squads.airCommandRange.get();
        int originalReputationRequirement = GuardConfig.reputationRequirement;

        Guard nearest = spawnGuard(helper);
        Guard second = spawnGuard(helper, new BlockPos(2, 1, 0));
        Guard unauthorized = spawnGuard(helper, new BlockPos(4, 1, 0));
        Guard otherSquad = spawnGuard(helper, new BlockPos(6, 1, 0));
        Guard distant = spawnGuard(helper, new BlockPos(12, 1, 0));
        Player player = helper.makeMockPlayer();
        player.setPos(nearest.getX(), nearest.getY(), nearest.getZ());
        nearest.setOwnerId(player.getUUID());
        second.setOwnerId(player.getUUID());
        distant.setOwnerId(player.getUUID());
        GuardConfig.reputationRequirement = Integer.MAX_VALUE;

        UUID squadId = UUID.randomUUID();
        UUID otherSquadId = UUID.randomUUID();
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(
                Objects.requireNonNull(helper.getLevel().getServer()));
        registry.register(
                squadId,
                "Dispatch Test",
                player.getUUID(),
                helper.getLevel().getGameTime()).orElseThrow();
        registry.register(
                otherSquadId,
                "Other Squad",
                player.getUUID(),
                helper.getLevel().getGameTime()).orElseThrow();
        GuardSquadAccess.setMembership(nearest, SquadMembership.assigned(squadId));
        GuardSquadAccess.setMembership(second, SquadMembership.assigned(squadId));
        GuardSquadAccess.setMembership(unauthorized, SquadMembership.assigned(squadId));
        GuardSquadAccess.setMembership(distant, SquadMembership.assigned(squadId));
        GuardSquadAccess.setMembership(
                otherSquad,
                SquadMembership.assigned(otherSquadId));

        ItemStack ledger = new ItemStack(
                com.gamergaming.villagerguardgunners.init.ModItems
                        .SQUAD_COMMAND_LEDGER.get());
        SquadLedgerBindingCodec.writeTo(
                ledger.getOrCreateTag(),
                SquadLedgerBinding.bound(squadId, "Dispatch Test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
        ItemStack preservedWeapon = new ItemStack(Items.CROSSBOW);
        nearest.setItemSlot(EquipmentSlot.MAINHAND, preservedWeapon.copy());
        GuardOrders preservedOrders = GuardOrdersAccess.getOrders(nearest);

        try {
            ModConfigs.GUARD_GUNNERS.squads.commandDispatchRadius.accept(8.0D);
            ModConfigs.GUARD_GUNNERS.squads.maximumAffectedGuards.accept(1);
            ModConfigs.GUARD_GUNNERS.squads
                    .positionalCommandDurationTicks.accept(100);
            ModConfigs.GUARD_GUNNERS.squads.focusCommandDurationTicks.accept(80);
            ModConfigs.GUARD_GUNNERS.squads.defendRadius.accept(6.0D);
            ModConfigs.GUARD_GUNNERS.squads.airCommandRange.accept(16.0D);

            BlockPos rallyPoint = nearest.blockPosition().offset(3, 0, 0);
            int revisionBeforeRally =
                    GuardSquadCommandAccess.getRevision(nearest);
            SquadCommandDispatcher.DispatchResult bounded =
                    SquadCommandDispatcher.dispatch(
                            player,
                            ledger,
                            SquadCommandType.RALLY,
                            rallyPoint,
                            null);
            SquadCommand nearestRally =
                    GuardSquadCommandAccess.getCommand(nearest);
            helper.assertTrue(bounded.completed()
                            && bounded.applied() == 1
                            && bounded.unauthorized() == 1
                            && bounded.capped() == 1
                            && bounded.matched() == 3,
                    "A bounded dispatch did not report applied, unauthorized, and capped members exactly");
            helper.assertTrue(nearestRally != null
                            && nearestRally.type() == SquadCommandType.RALLY
                            && rallyPoint.equals(nearestRally.position())
                            && nearestRally.issuedBy().equals(player.getUUID())
                            && nearestRally.dimension().equals(
                                    helper.getLevel().dimension().location())
                            && nearestRally.expiresGameTime()
                                    - nearestRally.issuedGameTime() == 100L
                            && GuardSquadCommandAccess.getRevision(nearest)
                                    == revisionBeforeRally + 1,
                    "The deterministic nearest member did not receive complete versioned Rally data");
            GuardSquadCommandAccess.setCommand(nearest, nearestRally);
            helper.assertTrue(
                    GuardSquadCommandAccess.getRevision(nearest)
                            == revisionBeforeRally + 1,
                    "Writing an identical temporary command caused a redundant revision");
            helper.assertTrue(GuardSquadCommandAccess.getCommand(second) == null
                            && GuardSquadCommandAccess.getCommand(unauthorized) == null
                            && GuardSquadCommandAccess.getCommand(distant) == null
                            && GuardSquadCommandAccess.getCommand(otherSquad) == null,
                    "A capped, unauthorized, distant, or different-squad guard received Rally");

            ModConfigs.GUARD_GUNNERS.squads.maximumAffectedGuards.accept(64);
            SquadCommandDispatcher.DispatchResult expanded =
                    SquadCommandDispatcher.dispatch(
                            player,
                            ledger,
                            SquadCommandType.RALLY,
                            rallyPoint,
                            null);
            helper.assertTrue(expanded.applied() == 2
                            && expanded.unauthorized() == 1
                            && expanded.capped() == 0
                            && GuardSquadCommandAccess.getCommand(second) != null
                            && GuardSquadCommandAccess.getCommand(distant) == null
                            && GuardSquadCommandAccess.getCommand(otherSquad) == null,
                    "Expanding the hard-bounded cap changed the wrong squad or radius boundary");

            BlockPos ignoredHoldPoint = nearest.blockPosition().offset(1, 0, 0);
            SquadCommandDispatcher.dispatch(
                    player,
                    ledger,
                    SquadCommandType.HOLD,
                    ignoredHoldPoint,
                    null);
            helper.assertTrue(nearest.blockPosition().equals(
                                    Objects.requireNonNull(
                                            GuardSquadCommandAccess.getCommand(nearest))
                                            .position())
                            && second.blockPosition().equals(
                                    Objects.requireNonNull(
                                            GuardSquadCommandAccess.getCommand(second))
                                            .position()),
                    "Hold did not capture a separate anchor for each affected guard");

            player.setShiftKeyDown(true);
            InteractionResultHolder<ItemStack> cycled = ledger.getItem().use(
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            helper.assertTrue(cycled.getResult().consumesAction()
                            && SquadLedgerModeCodec.readFrom(ledger.getTag()).mode()
                                    == SquadCommandType.DEFEND,
                    "Sneak-use in air did not cycle the default Rally mode to Defend");
            InteractionResultHolder<ItemStack> airDispatch = ledger.getItem().use(
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND);
            SquadCommand defend = GuardSquadCommandAccess.getCommand(nearest);
            helper.assertTrue(airDispatch.getResult().consumesAction()
                            && defend != null
                            && defend.type() == SquadCommandType.DEFEND
                            && defend.radius() == 6.0D,
                    "Normal air use did not dispatch the selected Defend command");

            SquadLedgerModeCodec.writeTo(
                    ledger.getOrCreateTag(),
                    SquadCommandType.RALLY);
            BlockPos blockPoint = nearest.blockPosition().offset(2, 0, 1);
            InteractionResult blockDispatch = ledger.getItem().useOn(
                    new UseOnContext(
                            player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(blockPoint),
                                    Direction.UP,
                                    blockPoint,
                                    false)));
            helper.assertTrue(blockDispatch.consumesAction()
                            && blockPoint.equals(Objects.requireNonNull(
                                    GuardSquadCommandAccess.getCommand(nearest))
                                    .position()),
                    "Block use did not dispatch Rally to the clicked command point");

            Zombie target = helper.spawnWithNoFreeWill(
                    EntityType.ZOMBIE,
                    new BlockPos(3, 1, 2));
            SquadLedgerModeCodec.writeTo(
                    ledger.getOrCreateTag(),
                    SquadCommandType.FOCUS_TARGET);
            InteractionResult focusInteraction = ledger.getItem().interactLivingEntity(
                    ledger,
                    player,
                    target,
                    InteractionHand.MAIN_HAND);
            SquadCommand focus = GuardSquadCommandAccess.getCommand(nearest);
            helper.assertTrue(focusInteraction.consumesAction()
                            && focus != null
                            && focus.type() == SquadCommandType.FOCUS_TARGET
                            && target.getUUID().equals(focus.targetId())
                            && focus.position() == null
                            && focus.expiresGameTime() - focus.issuedGameTime() == 80L,
                    "Entity use did not dispatch a bounded Focus Target identity");

            SquadLedgerModeCodec.writeTo(
                    ledger.getOrCreateTag(),
                    SquadCommandType.RESUME_ORDERS);
            InteractionResultHolder<ItemStack> resumed = ledger.getItem().use(
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND);
            helper.assertTrue(resumed.getResult().consumesAction()
                            && GuardSquadCommandAccess.getCommand(nearest) == null
                            && GuardSquadCommandAccess.getCommand(second) == null,
                    "Resume Orders did not clear temporary commands from authorized loaded members");

            SquadCommandDispatcher.dispatch(
                    player,
                    ledger,
                    SquadCommandType.FALL_BACK,
                    rallyPoint,
                    null);
            SquadCommand savedCommand = Objects.requireNonNull(
                    GuardSquadCommandAccess.getCommand(nearest));
            CompoundTag savedGuard = new CompoundTag();
            nearest.addAdditionalSaveData(savedGuard);
            Guard restored = Objects.requireNonNull(
                    GuardEntityType.GUARD.get().create(helper.getLevel()));
            restored.readAdditionalSaveData(savedGuard);
            helper.assertTrue(savedCommand.equals(
                                    GuardSquadCommandAccess.getCommand(restored))
                            && squadId.equals(GuardSquadAccess.getSquadId(restored)),
                    "Temporary command or squad identity changed across guard save/load");

            ItemStack corruptLedger = ledger.copy();
            corruptLedger.getOrCreateTag().put(
                    SquadLedgerBindingCodec.BINDING_TAG,
                    new CompoundTag());
            SquadCommand beforeCorruptDispatch =
                    GuardSquadCommandAccess.getCommand(nearest);
            SquadCommandDispatcher.DispatchResult rejected =
                    SquadCommandDispatcher.dispatch(
                            player,
                            corruptLedger,
                            SquadCommandType.RALLY,
                            rallyPoint,
                            null);
            helper.assertTrue(rejected.status()
                                    == SquadCommandDispatcher.DispatchStatus.CORRUPT_LEDGER
                            && Objects.equals(
                                    beforeCorruptDispatch,
                                    GuardSquadCommandAccess.getCommand(nearest)),
                    "A corrupt ledger changed an existing temporary command");
            helper.assertTrue(ItemStack.isSameItemSameTags(
                                    preservedWeapon,
                                    nearest.getMainHandItem())
                            && preservedOrders.equals(
                                    GuardOrdersAccess.getOrders(nearest)),
                    "Squad command dispatch changed permanent orders or equipment");
            helper.succeed();
        } finally {
            ModConfigs.GUARD_GUNNERS.squads.commandDispatchRadius
                    .accept(originalDispatchRadius);
            ModConfigs.GUARD_GUNNERS.squads.maximumAffectedGuards
                    .accept(originalMaximumAffected);
            ModConfigs.GUARD_GUNNERS.squads.positionalCommandDurationTicks
                    .accept(originalPositionalDuration);
            ModConfigs.GUARD_GUNNERS.squads.focusCommandDurationTicks
                    .accept(originalFocusDuration);
            ModConfigs.GUARD_GUNNERS.squads.defendRadius
                    .accept(originalDefendRadius);
            ModConfigs.GUARD_GUNNERS.squads.airCommandRange
                    .accept(originalAirRange);
            GuardConfig.reputationRequirement = originalReputationRequirement;
            player.setShiftKeyDown(false);
        }
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE,
            timeoutTicks = 40)
    public static void temporarySquadCommandsControlMovementFocusAndExpiry(
            GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        guard.setPatrolling(true);
        guard.setFollowing(false);
        boolean preservedPatrolling = guard.isPatrolling();
        boolean preservedFollowing = guard.isFollowing();
        long gameTime = helper.getLevel().getGameTime();
        ResourceLocation dimension = helper.getLevel().dimension().location();
        UUID issuer = UUID.randomUUID();
        SquadCommandMovementGoal movementGoal = new SquadCommandMovementGoal(guard);
        SquadCommandMovementController movement =
                new SquadCommandMovementController(guard);
        GuardSquadAccess.setMembership(
                guard,
                SquadMembership.assigned(UUID.randomUUID()));

        SquadCommand rally = SquadCommand.positional(
                SquadCommandType.RALLY,
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                guard.blockPosition(),
                0.0D);
        GuardSquadCommandAccess.setCommand(guard, rally);
        helper.assertTrue(movementGoal.canUse(),
                "An in-range Rally command did not activate the outside-combat movement goal");
        movementGoal.tick();
        helper.assertTrue(GuardSquadCommandAccess.getCommand(guard) == null,
                "Rally did not clear itself after the guard reached its command point");

        SquadCommand hold = SquadCommand.positional(
                SquadCommandType.HOLD,
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                guard.blockPosition(),
                0.0D);
        GuardSquadCommandAccess.setCommand(guard, hold);
        movementGoal.tick();
        helper.assertTrue(GuardSquadCommandAccess.getCommand(guard) == hold,
                "Hold cleared instead of retaining the guard's per-command anchor");

        SquadCommand defendOutside = SquadCommand.positional(
                SquadCommandType.DEFEND,
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                guard.blockPosition().offset(4, 0, 0),
                2.0D);
        GuardSquadCommandAccess.setCommand(guard, defendOutside);
        movement.synchronize(false);
        helper.assertTrue(movement.apply(
                                defendOutside,
                                false,
                                true,
                                ModConfigs.GUARD_GUNNERS.movement.approachSpeed.get())
                        == SquadCommandMovementController.MovementOutcome.MOVING_TO_COMMAND,
                "Defend did not return a guard that was outside its safe command radius");

        SquadCommand defendInside = SquadCommand.positional(
                SquadCommandType.DEFEND,
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                guard.blockPosition(),
                6.0D);
        GuardSquadCommandAccess.setCommand(guard, defendInside);
        movement.synchronize(false);
        helper.assertTrue(movement.apply(
                                defendInside,
                                false,
                                true,
                                ModConfigs.GUARD_GUNNERS.movement.approachSpeed.get())
                        == SquadCommandMovementController.MovementOutcome.NOT_HANDLED,
                "Defend suppressed ordinary combat tactics while already inside its radius");

        SquadCommand fallBack = SquadCommand.positional(
                SquadCommandType.FALL_BACK,
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                guard.blockPosition().offset(3, 0, 0),
                0.0D);
        GuardSquadCommandAccess.setCommand(guard, fallBack);
        movement.synchronize(false);
        helper.assertTrue(movement.apply(
                                fallBack,
                                false,
                                true,
                                ModConfigs.GUARD_GUNNERS.movement.approachSpeed.get())
                        == SquadCommandMovementController.MovementOutcome.MOVING_TO_COMMAND,
                "Fall Back did not override tactical movement toward its retreat point");

        Zombie hostile = helper.spawnWithNoFreeWill(
                EntityType.ZOMBIE,
                new BlockPos(3, 1, 0));
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(GLOCK_17, FireMode.SEMI, 10, false));
        guard.setTarget(null);
        SquadCommand focus = SquadCommand.focusTarget(
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                hostile.getUUID());
        GuardSquadCommandAccess.setCommand(guard, focus);
        GuardGunCombatGoal gunGoal = new GuardGunCombatGoal(guard);
        gunGoal.canUse();
        helper.assertTrue(guard.getTarget() == hostile
                        && GuardSquadCommandAccess.getCommand(guard) == focus,
                "A permitted loaded Focus Target was not prioritized by the gun goal");
        SquadCommandRuntime.replaceCommand(guard, null);
        helper.assertTrue(guard.getTarget() == null,
                "Resume/replacement did not release the target installed by Focus Target");

        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        GuardSquadCommandAccess.setCommand(guard, focus);
        helper.assertTrue(!movementGoal.canUse()
                        && guard.getTarget() == hostile
                        && GuardSquadCommandAccess.getCommand(guard) == focus,
                "The shared squad goal did not synchronize Focus for a non-TaCZ guard");
        SquadCommandRuntime.replaceCommand(guard, null);

        Villager protectedVillager = helper.spawnWithNoFreeWill(
                EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        guard.setTarget(null);
        SquadCommand protectedFocus = SquadCommand.focusTarget(
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                protectedVillager.getUUID());
        GuardSquadCommandAccess.setCommand(guard, protectedFocus);
        helper.assertTrue(SquadCommandRuntime.synchronizeFocusTarget(
                                guard,
                                Objects.requireNonNull(
                                        SquadCommandRuntime.activeCommand(guard)))
                                == null
                        && guard.getTarget() == null
                        && GuardSquadCommandAccess.getCommand(guard) == null,
                "Focus Target bypassed ally protection for a villager");

        GuardOrders originalOrders = GuardOrdersAccess.getOrders(guard);
        GuardOrdersAccess.setOrders(
                guard,
                originalOrders.withEngagement(EngagementOrder.HOLD_FIRE));
        SquadCommand heldFireFocus = SquadCommand.focusTarget(
                issuer,
                gameTime,
                gameTime + 100L,
                dimension,
                hostile.getUUID());
        GuardSquadCommandAccess.setCommand(guard, heldFireFocus);
        helper.assertTrue(SquadCommandRuntime.synchronizeFocusTarget(
                                guard,
                                Objects.requireNonNull(
                                        SquadCommandRuntime.activeCommand(guard)))
                                == null
                        && guard.getTarget() == null
                        && GuardSquadCommandAccess.getCommand(guard) == null,
                "Focus Target bypassed the guard's hold-fire rule of engagement");
        GuardOrdersAccess.setOrders(guard, originalOrders);

        helper.assertTrue(guard.isPatrolling() == preservedPatrolling
                        && guard.isFollowing() == preservedFollowing,
                "Temporary commands changed Guard Villagers' permanent patrol or follow state");

        SquadCommand expiring = SquadCommand.focusTarget(
                issuer,
                gameTime,
                gameTime + 1L,
                dimension,
                hostile.getUUID());
        GuardSquadCommandAccess.setCommand(guard, expiring);
        CompoundTag saved = new CompoundTag();
        guard.addAdditionalSaveData(saved);
        Guard restored = Objects.requireNonNull(
                GuardEntityType.GUARD.get().create(helper.getLevel()));
        restored.moveTo(guard.getX(), guard.getY(), guard.getZ());
        restored.readAdditionalSaveData(saved);
        helper.assertTrue(GuardSquadCommandAccess.getCommand(restored) != null,
                "A still-current temporary command was lost while reloading a guard");
        SquadCommandRuntime.synchronizeFocusTarget(
                restored,
                Objects.requireNonNull(SquadCommandRuntime.activeCommand(restored)));
        helper.assertTrue(restored.getTarget() == hostile,
                "A restored current Focus Target could not recover its loaded target");

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(SquadCommandRuntime.activeCommand(restored) == null
                            && GuardSquadCommandAccess.getCommand(restored) == null
                            && restored.getTarget() == null,
                    "A Focus command or its owned target survived expiry after guard restoration");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void orderTemplateServiceCapturesAndAppliesWithoutMovingItems(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        int originalReputationRequirement = GuardConfig.reputationRequirement;
        Guard source = spawnGuard(helper);
        Guard destination = spawnGuard(helper, new BlockPos(2, 1, 0));
        Player player = helper.makeMockPlayer();
        player.setPos(source.getX(), source.getY(), source.getZ());
        source.setOwnerId(player.getUUID());
        destination.setOwnerId(player.getUUID());

        GuardOrders sourceOrders = new GuardOrders(
                EngagementOrder.DEFENSIVE,
                TacticalOrder.MARKSMAN,
                GuardOrders.ToggleOrder.DISABLED,
                GuardOrders.ToggleOrder.ENABLED,
                GuardOrders.ToggleOrder.INHERIT,
                GuardOrders.MovementOrder.HOLD_POSITION,
                RangeOrder.BLOCKS_48);
        GuardOrdersAccess.setOrders(source, sourceOrders);
        ItemStack sourceGun = gun(GLOCK_17, FireMode.SEMI, 7, true);
        source.setItemSlot(EquipmentSlot.MAINHAND, sourceGun.copy());
        GuardAmmoAccess.getAmmoHandler(source).setStackInSlot(
                0,
                AmmoItemBuilder.create()
                        .setId(NINE_MILLIMETER)
                        .setCount(13)
                        .build());

        ItemStack ledger = new ItemStack(
                com.gamergaming.villagerguardgunners.init.ModItems.ORDERS_LEDGER.get());
        ledger.setHoverName(net.minecraft.network.chat.Component.literal("Marksman"));
        player.setItemInHand(InteractionHand.MAIN_HAND, ledger);

        try {
            GuardConfig.giveGuardStuffHOTV = false;
            GuardOrderTemplateService.OperationResult captured =
                    GuardOrderTemplateService.capture(
                            player,
                            source,
                            InteractionHand.MAIN_HAND,
                            false);
            helper.assertTrue(captured.status() == OperationStatus.CAPTURED
                            && captured.changedLedger()
                            && captured.completed(),
                    "A blank authorized ledger did not capture the source guard's orders");
            helper.assertTrue(ledger.getCount() == 1
                            && "Marksman".equals(ledger.getHoverName().getString()),
                    "Capturing orders changed the ledger count or custom name");
            helper.assertTrue(ItemStack.isSameItemSameTags(
                            sourceGun,
                            source.getMainHandItem())
                            && countAmmo(
                                    GuardAmmoAccess.getAmmoHandler(source),
                                    NINE_MILLIMETER) == 13,
                    "Capturing orders changed the source guard's equipment or ammunition");

            GuardOrderTemplateService.OperationResult protectedCapture =
                    GuardOrderTemplateService.capture(
                            player,
                            source,
                            InteractionHand.MAIN_HAND,
                            false);
            helper.assertTrue(
                    protectedCapture.status() == OperationStatus.TEMPLATE_ALREADY_WRITTEN,
                    "A normal capture unexpectedly overwrote an existing template");

            ItemStack destinationGun = gun(AK_47, FireMode.AUTO, 9, true);
            destination.setItemSlot(EquipmentSlot.MAINHAND, destinationGun.copy());
            ItemStack destinationHelmet = new ItemStack(Items.IRON_HELMET);
            destination.setItemSlot(EquipmentSlot.HEAD, destinationHelmet.copy());
            GuardAmmoAccess.getAmmoHandler(destination).setStackInSlot(
                    0,
                    AmmoItemBuilder.create()
                            .setId(DEFAULT_AMMO)
                            .setCount(11)
                            .build());
            CompoundTag ledgerTagBeforeApply = Objects.requireNonNull(ledger.getTag()).copy();
            int revisionBeforeApply = GuardOrdersAccess.getRevision(destination);
            GuardOrderTemplateService.OperationResult applied =
                    GuardOrderTemplateService.apply(
                            player,
                            destination,
                            InteractionHand.MAIN_HAND);
            helper.assertTrue(applied.status() == OperationStatus.APPLIED
                            && applied.changedGuard()
                            && sourceOrders.equals(GuardOrdersAccess.getOrders(destination)),
                    "A valid template did not atomically replace all destination orders");
            helper.assertTrue(
                    GuardOrdersAccess.getRevision(destination) == revisionBeforeApply + 1,
                    "Applying a changed template did not invalidate combat state exactly once");
            helper.assertTrue(ItemStack.isSameItemSameTags(
                            destinationGun,
                            destination.getMainHandItem())
                            && ItemStack.isSameItemSameTags(
                                    destinationHelmet,
                                    destination.getItemBySlot(EquipmentSlot.HEAD))
                            && countAmmo(
                                    GuardAmmoAccess.getAmmoHandler(destination),
                                    DEFAULT_AMMO) == 11,
                    "Applying orders changed destination equipment or ammunition");
            helper.assertTrue(ledger.getCount() == 1
                            && ledgerTagBeforeApply.equals(ledger.getTag()),
                    "Applying orders consumed or modified the reusable ledger");

            int revisionBeforeNoOp = GuardOrdersAccess.getRevision(destination);
            GuardOrderTemplateService.OperationResult alreadyMatches =
                    GuardOrderTemplateService.apply(
                            player,
                            destination,
                            InteractionHand.MAIN_HAND);
            helper.assertTrue(alreadyMatches.status() == OperationStatus.ALREADY_MATCHES
                            && alreadyMatches.completed()
                            && GuardOrdersAccess.getRevision(destination) == revisionBeforeNoOp,
                    "Applying matching orders caused a redundant combat-state revision");

            ItemStack blank = new ItemStack(
                    com.gamergaming.villagerguardgunners.init.ModItems.ORDERS_LEDGER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, blank);
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    player,
                                    destination,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.BLANK_TEMPLATE,
                    "A blank template did not fail closed");

            CompoundTag corruptPayload = new CompoundTag();
            blank.getOrCreateTag().put(
                    GuardOrderTemplate.TEMPLATE_TAG,
                    corruptPayload);
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    player,
                                    destination,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.CORRUPT_TEMPLATE,
                    "A corrupt template did not fail closed");
            helper.assertTrue(
                    GuardOrderTemplateService.capture(
                                    player,
                                    source,
                                    InteractionHand.MAIN_HAND,
                                    true)
                            .status() == OperationStatus.OVERWRITTEN,
                    "An explicit overwrite could not repair a corrupt template");

            GuardConfig.giveGuardStuffHOTV = true;
            GuardConfig.reputationRequirement = Integer.MAX_VALUE;
            destination.setOwnerId(null);
            GuardOrders destinationBeforeDeniedApply =
                    GuardOrdersAccess.getOrders(destination);
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    player,
                                    destination,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.UNAUTHORIZED
                            && destinationBeforeDeniedApply.equals(
                                    GuardOrdersAccess.getOrders(destination)),
                    "An unauthorized template request changed destination orders");

            GuardConfig.giveGuardStuffHOTV = false;
            destination.setOwnerId(player.getUUID());
            player.setPos(destination.getX() + 9.0D, destination.getY(), destination.getZ());
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    player,
                                    destination,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.OUT_OF_RANGE,
                    "A template request beyond the bounded interaction distance was accepted");

            GuardConfig.giveGuardStuffHOTV = false;
            player.setPos(source.getX(), source.getY(), source.getZ());
            player.setShiftKeyDown(false);
            ItemStack interactionLedger = new ItemStack(
                    com.gamergaming.villagerguardgunners.init.ModItems.ORDERS_LEDGER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, interactionLedger);
            InteractionResult captureInteraction =
                    source.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(captureInteraction.consumesAction()
                            && GuardOrderTemplate.readFrom(interactionLedger.getTag())
                                    .isValid()
                            && sourceOrders.equals(
                                    GuardOrderTemplate.readFrom(interactionLedger.getTag())
                                            .orders()),
                    "Normal use of a blank ledger did not capture through Guard.mobInteract");

            GuardOrders replacementOrders = sourceOrders
                    .withEngagement(EngagementOrder.HOLD_FIRE)
                    .withMaximumRange(RangeOrder.BLOCKS_16);
            GuardOrdersAccess.setOrders(source, replacementOrders);
            player.setShiftKeyDown(true);
            InteractionResult overwriteInteraction =
                    source.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(overwriteInteraction.consumesAction()
                            && replacementOrders.equals(
                                    GuardOrderTemplate.readFrom(interactionLedger.getTag())
                                            .orders()),
                    "Sneak-use of a written ledger did not overwrite through Guard.mobInteract");

            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, interactionLedger);
            player.setPos(destination.getX(), destination.getY(), destination.getZ());
            GuardOrdersAccess.setOrders(destination, GuardOrders.DEFAULT);
            InteractionResult offhandApply =
                    destination.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(offhandApply.consumesAction()
                            && replacementOrders.equals(
                                    GuardOrdersAccess.getOrders(destination))
                            && ItemStack.isSameItemSameTags(
                                    interactionLedger,
                                    player.getOffhandItem())
                            && player.getOffhandItem().getCount() == 1,
                    "An offhand ledger with an empty main hand did not apply before the guard inventory opened");
            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(Items.STICK));
            helper.assertTrue(
                    GuardOrderTemplateInteractions.findLedgerHand(
                                    player,
                                    InteractionHand.MAIN_HAND)
                            == null,
                    "The offhand-ledger fallback overrode a non-empty main-hand item");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            GuardConfig.reputationRequirement = originalReputationRequirement;
            player.setShiftKeyDown(false);
        }
    }

    @GameTest(batch = "order_template_safety", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE)
    public static void orderTemplatesFailClosedPersistAndPreserveServerAuthority(
            GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        int originalReputationRequirement = GuardConfig.reputationRequirement;
        EngagementMode originalEngagementMode =
                ModConfigs.GUARD_GUNNERS.engagement.mode.get();
        double originalMaximumRange =
                ModConfigs.GUARD_GUNNERS.combat.maximumEngagementRange.get();
        Guard guard = spawnGuard(helper);
        Player editor = helper.makeMockPlayer();
        editor.setPos(guard.getX(), guard.getY(), guard.getZ());
        ItemStack ledger = new ItemStack(
                com.gamergaming.villagerguardgunners.init.ModItems.ORDERS_LEDGER.get());
        GuardOrders permissiveTemplate = GuardOrders.DEFAULT
                .withEngagement(EngagementOrder.GUARD_TARGETS)
                .withTactics(TacticalOrder.GENERALIST)
                .withMaximumRange(RangeOrder.BLOCKS_64);
        GuardOrderTemplate.writeTo(ledger.getOrCreateTag(), permissiveTemplate);
        editor.setItemInHand(InteractionHand.MAIN_HAND, ledger);
        try {
            GuardConfig.giveGuardStuffHOTV = false;
            GuardConfig.reputationRequirement = Integer.MAX_VALUE;

            GuardOrders ordersBeforeUnauthorizedRequest =
                    GuardOrdersAccess.getOrders(guard);
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    editor,
                                    guard,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.UNAUTHORIZED
                            && ordersBeforeUnauthorizedRequest.equals(
                                    GuardOrdersAccess.getOrders(guard)),
                    "The broad item-supply permission allowed an unrelated player to change guard orders");

            guard.setOwnerId(editor.getUUID());

            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    editor,
                                    guard,
                                    InteractionHand.OFF_HAND)
                            .status() == OperationStatus.NOT_LEDGER,
                    "A template request accepted a hand that was not holding the ledger");

            GuardOrders ordersBeforeHostileRequest = GuardOrdersAccess.getOrders(guard);
            guard.setTarget(editor);
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    editor,
                                    guard,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.UNAUTHORIZED
                            && ordersBeforeHostileRequest.equals(
                                    GuardOrdersAccess.getOrders(guard)),
                    "A guard targeting the editor accepted an order template");
            guard.setTarget(null);

            CompoundTag templatePayload = Objects.requireNonNull(ledger.getTag())
                    .getCompound(GuardOrderTemplate.TEMPLATE_TAG);
            templatePayload.putInt("Version", GuardOrderTemplate.DATA_VERSION + 1);
            GuardOrderTemplateService.OperationResult unsupported =
                    GuardOrderTemplateService.apply(
                            editor,
                            guard,
                            InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    unsupported.status() == OperationStatus.UNSUPPORTED_VERSION
                            && unsupported.storedVersion()
                                    == GuardOrderTemplate.DATA_VERSION + 1
                            && ordersBeforeHostileRequest.equals(
                                    GuardOrdersAccess.getOrders(guard)),
                    "A future template version did not fail closed with its version intact");
            GuardOrderTemplate.writeTo(ledger.getOrCreateTag(), permissiveTemplate);

            Guard removedGuard = spawnGuard(helper, new BlockPos(5, 1, 0));
            removedGuard.discard();
            helper.assertTrue(
                    GuardOrderTemplateService.apply(
                                    editor,
                                    removedGuard,
                                    InteractionHand.MAIN_HAND)
                            .status() == OperationStatus.INVALID_GUARD,
                    "A removed guard accepted an order template");

            FakePlayer spectator = new FakePlayer(
                    helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "vgg-spectator-test")) {
                @Override
                public boolean isSpectator() {
                    return true;
                }
            };
            spectator.setPos(guard.getX(), guard.getY(), guard.getZ());
            spectator.setItemInHand(InteractionHand.MAIN_HAND, ledger.copy());
            helper.assertTrue(spectator.isSpectator()
                            && GuardOrderTemplateService.apply(
                                            spectator,
                                            guard,
                                            InteractionHand.MAIN_HAND)
                                    .status() == OperationStatus.UNAUTHORIZED,
                    "A spectator was able to apply an order template");

            ModConfigs.GUARD_GUNNERS.engagement.mode.accept(EngagementMode.HOLD_FIRE);
            ModConfigs.GUARD_GUNNERS.combat.maximumEngagementRange.accept(8.0D);
            GuardOrderTemplateService.OperationResult applied =
                    GuardOrderTemplateService.apply(
                            editor,
                            guard,
                            InteractionHand.MAIN_HAND);
            GuardOrders appliedOrders = GuardOrdersAccess.getOrders(guard);
            helper.assertTrue(applied.status() == OperationStatus.APPLIED
                            && permissiveTemplate.equals(appliedOrders),
                    "A valid safety fixture template was not applied");
            helper.assertTrue(
                    appliedOrders.resolveEngagementMode(
                                    ModConfigs.GUARD_GUNNERS.engagement.mode.get())
                                    == EngagementMode.HOLD_FIRE
                            && appliedOrders.resolveMaximumRange(
                                    ModConfigs.GUARD_GUNNERS.combat
                                            .maximumEngagementRange.get()) == 8.0D,
                    "A permissive template widened global hold-fire or range limits");

            CompoundTag savedGuard = new CompoundTag();
            guard.addAdditionalSaveData(savedGuard);
            Guard restored = Objects.requireNonNull(
                    GuardEntityType.GUARD.get().create(helper.getLevel()));
            restored.readAdditionalSaveData(savedGuard);
            helper.assertTrue(
                    permissiveTemplate.equals(GuardOrdersAccess.getOrders(restored)),
                    "Applied template orders did not persist through guard save/load");

            ModConfigs.GUARD_GUNNERS.engagement.mode.accept(
                    EngagementMode.GUARD_TARGETS);
            GuardOrders holdFireTemplate = permissiveTemplate
                    .withEngagement(EngagementOrder.HOLD_FIRE);
            GuardOrderTemplate.writeTo(ledger.getOrCreateTag(), holdFireTemplate);
            GuardOrderTemplateService.OperationResult holdFireApplied =
                    GuardOrderTemplateService.apply(
                            editor,
                            guard,
                            InteractionHand.MAIN_HAND);
            Ravager target = helper.spawnWithNoFreeWill(
                    EntityType.RAVAGER,
                    new BlockPos(3, 1, 0));
            GuardBurstSafetyAccess.setSafetyCheck(
                    guard,
                    () -> GuardRulesOfEngagement.mayEngage(guard, target));
            ItemStack burstGun = gun(M16_A4, FireMode.BURST, 9, true);
            helper.assertTrue(holdFireApplied.status() == OperationStatus.APPLIED
                            && MinecraftForge.EVENT_BUS.post(new GunFireEvent(
                                    guard,
                                    burstGun,
                                    LogicalSide.SERVER)),
                    "Applying Hold Fire did not cancel the next native burst round");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            GuardConfig.reputationRequirement = originalReputationRequirement;
            ModConfigs.GUARD_GUNNERS.engagement.mode.accept(originalEngagementMode);
            ModConfigs.GUARD_GUNNERS.combat.maximumEngagementRange
                    .accept(originalMaximumRange);
            guard.setTarget(null);
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void mixedFormatRollbackSaveMergesWithoutDuplicating(GameTestHelper helper) {
        Guard restored = spawnGuard(helper);
        GuardAmmoHandler serializedHandler = new GuardAmmoHandler();
        ItemStack authoritativeAmmo = AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(10).build();
        serializedHandler.setStackInSlot(0, authoritativeAmmo);

        CompoundTag mixedSave = new CompoundTag();
        mixedSave.put(AMMO_INVENTORY_TAG, serializedHandler.serializeNBT());
        ListTag legacyInventory = new ListTag();
        legacyInventory.add(saveInLegacySlot(authoritativeAmmo, 6));
        legacyInventory.add(saveInLegacySlot(
                AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(5).build(),
                7));
        mixedSave.put("Inventory", legacyInventory);

        restored.readAdditionalSaveData(mixedSave);
        GuardAmmoHandler restoredHandler = GuardAmmoAccess.getAmmoHandler(restored);

        helper.assertTrue(countAmmo(restoredHandler, DEFAULT_AMMO) == 10,
                "Mixed-format migration duplicated the authoritative ammo stack");
        helper.assertTrue(countAmmo(restoredHandler, NINE_MILLIMETER) == 5,
                "Mixed-format migration discarded ammo present only in legacy slots");
        helper.assertTrue(mixedSave.getList("Inventory", Tag.TAG_COMPOUND).isEmpty(),
                "Legacy add-on slots were left in Guard Villagers' base inventory");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guardConversionsDropDedicatedAmmoAndPendingOverflow(GameTestHelper helper) {
        Difficulty originalDifficulty = helper.getLevel().getServer().getWorldData().getDifficulty();
        helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
        try {
            Guard zombieGuard = helper.spawnWithNoFreeWill(
                    GuardEntityType.GUARD.get(),
                    new BlockPos(0, 1, 0));
            prepareConversionInventory(zombieGuard, 11, 3);
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 0));
            zombieGuard.die(helper.getLevel().damageSources().mobAttack(zombie));

            helper.assertTrue(zombieGuard.isRemoved(),
                    "Zombie conversion did not remove the original guard");
            helper.assertTrue(countAmmo(GuardAmmoAccess.getAmmoHandler(zombieGuard), DEFAULT_AMMO) == 0,
                    "Zombie conversion left ammunition in the discarded guard");
            helper.assertTrue(
                    GuardSidearmGameTestAccess.getStorage(zombieGuard).recoverableStacks().isEmpty(),
                    "Zombie conversion left a sidearm in the discarded guard");
            Vec3 zombieDropCenter = zombieGuard.position();

            Guard lightningGuard = helper.spawnWithNoFreeWill(
                    GuardEntityType.GUARD.get(),
                    new BlockPos(8, 1, 0));
            prepareConversionInventory(lightningGuard, 7, 5);
            LightningBolt lightning = Objects.requireNonNull(
                    EntityType.LIGHTNING_BOLT.create(helper.getLevel()));
            lightningGuard.thunderHit(helper.getLevel(), lightning);

            helper.assertTrue(lightningGuard.isRemoved(),
                    "Lightning conversion did not remove the original guard");
            helper.assertTrue(countAmmo(GuardAmmoAccess.getAmmoHandler(lightningGuard), DEFAULT_AMMO) == 0,
                    "Lightning conversion left ammunition in the discarded guard");
            helper.assertTrue(
                    GuardSidearmGameTestAccess.getStorage(lightningGuard).recoverableStacks().isEmpty(),
                    "Lightning conversion left a sidearm in the discarded guard");
            Vec3 lightningDropCenter = lightningGuard.position();

            // Forge may finish publishing captured conversion drops after the
            // conversion call returns. Retry only the world-visibility query;
            // the exact totals and emptied source inventories remain strict.
            helper.succeedWhen(() -> {
                int zombieDrops = countDroppedAmmo(helper, zombieDropCenter, DEFAULT_AMMO);
                helper.assertTrue(zombieDrops == 14,
                        "Zombie conversion expected 14 dropped rounds but found " + zombieDrops);
                int lightningDrops = countDroppedAmmo(helper, lightningDropCenter, DEFAULT_AMMO);
                helper.assertTrue(lightningDrops == 12,
                        "Lightning conversion expected 12 dropped rounds but found " + lightningDrops);
                int zombieSidearms = countDroppedGuns(helper, zombieDropCenter, GLOCK_17);
                helper.assertTrue(zombieSidearms == 1,
                        "Zombie conversion expected one dropped sidearm but found " + zombieSidearms);
                int lightningSidearms = countDroppedGuns(helper, lightningDropCenter, GLOCK_17);
                helper.assertTrue(lightningSidearms == 1,
                        "Lightning conversion expected one dropped sidearm but found " + lightningSidearms);
            });
        } finally {
            helper.getLevel().getServer().setDifficulty(originalDifficulty, true);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void suppressedGuardBurstRoundsAreCanceledBeforeNativeFire(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(4, 1, 0));
        ItemStack gun = gun(M16_A4, FireMode.BURST, 9, true);

        GuardBurstSafetyAccess.setSuppressed(guard, true);
        helper.assertTrue(MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                "A suppressed TaCZ burst round was not canceled");

        GuardBurstSafetyAccess.setSuppressed(guard, false);
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                "A safe TaCZ burst round was canceled");

        GuardBurstSafetyAccess.setSafetyCheck(guard, () -> false);
        helper.assertTrue(MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                "A freshly unsafe TaCZ burst corridor was not canceled");

        GuardBurstSafetyAccess.setSafetyCheck(guard, () -> true);
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                "A freshly validated TaCZ burst corridor was canceled");
        EngagementMode previousMode = ModConfigs.GUARD_GUNNERS.engagement.mode.get();
        try {
            GuardBurstSafetyAccess.setSafetyCheck(
                    guard,
                    () -> GuardRulesOfEngagement.mayEngage(guard, target));
            ModConfigs.GUARD_GUNNERS.engagement.mode.accept(EngagementMode.HOLD_FIRE);
            helper.assertTrue(
                    MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                    "A live HOLD_FIRE change did not cancel the next native burst round");
            ModConfigs.GUARD_GUNNERS.engagement.mode.accept(EngagementMode.GUARD_TARGETS);
            helper.assertFalse(
                    MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                    "Restoring Guard Villagers targeting did not reopen the burst validator");

            GuardOrdersAccess.setOrders(
                    guard,
                    GuardOrders.DEFAULT.withEngagement(EngagementOrder.HOLD_FIRE));
            helper.assertTrue(
                    MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                    "A live per-guard HOLD_FIRE order did not cancel the next native burst round");
            GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
            helper.assertFalse(
                    MinecraftForge.EVENT_BUS.post(new GunFireEvent(guard, gun, LogicalSide.SERVER)),
                    "Clearing a per-guard HOLD_FIRE order did not reopen the burst validator");
        } finally {
            ModConfigs.GUARD_GUNNERS.engagement.mode.accept(previousMode);
            GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void realTaCZGunInitializesNativeOperatorAndMenu(GameTestHelper helper) {
        helper.assertTrue(TimelessAPI.getCommonGunIndex(GLOCK_17).isPresent(),
                "TaCZ default Glock 17 definition was not loaded");

        Guard guard = spawnGuard(helper);
        ItemStack gun = GunItemBuilder.create()
                .setId(GLOCK_17)
                .setFireMode(FireMode.SEMI)
                .setAmmoCount(1)
                .setAmmoInBarrel(true)
                .build();
        helper.assertFalse(gun.isEmpty(), "TaCZ failed to construct its default Glock 17 stack");
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun);

        NativeGunController controller = new NativeGunController(guard);
        controller.startAiming();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);

        helper.assertTrue(NativeGunController.isGun(guard.getMainHandItem()),
                "Native controller did not recognize a real TaCZ gun stack");
        helper.assertTrue(operator.getDataHolder().currentGunItem != null,
                "TaCZ operator did not capture the guard's native gun supplier");
        helper.assertTrue(operator.getCacheProperty() != null,
                "TaCZ operator did not build its attachment/property cache for the guard");

        Player player = helper.makeMockPlayer();
        player.setPos(guard.getX(), guard.getY(), guard.getZ());
        guard.setOwnerId(player.getUUID());
        GuardGunnerContainer menu = new GuardGunnerContainer(
                1,
                player.getInventory(),
                guard.guardInventory,
                GuardAmmoAccess.getAmmoHandler(guard),
                guard);
        boolean previousHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        try {
            GuardConfig.giveGuardStuffHOTV = false;
            helper.assertTrue(menu.slots.size() == GuardGunnerContainer.ADDON_SLOT_COUNT + 36,
                    "Guard gunner menu has an incorrect slot layout");
            helper.assertTrue(GuardOrders.DEFAULT.equals(menu.getOrders()),
                    "A newly spawned guard did not inherit all server defaults");
            helper.assertTrue(menu.clickMenuButton(player, OrderField.ENGAGEMENT.buttonId()),
                    "An authorized order button request was rejected");
            helper.assertTrue(
                    GuardOrdersAccess.getOrders(guard).engagement()
                            == EngagementOrder.GUARD_TARGETS,
                    "The server did not apply the requested per-guard order");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = previousHotvRequirement;
            menu.removed(player);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void nativeControllerCachesTaCZTacticalMetadata(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        NativeGunController controller = new NativeGunController(guard);

        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 1, true));
        controller.startAiming();
        NativeGunController.CachedTacticalState pistol =
                controller.captureDiagnostics().tacticalState();
        helper.assertTrue(pistol.metadataReadable(),
                "The Glock tactical metadata cache was not marked ready");
        helper.assertTrue(GLOCK_17.equals(pistol.gunId()),
                "The Glock tactical cache did not retain its TaCZ gun ID");
        helper.assertTrue("pistol".equals(pistol.resolution().normalizedGunType()),
                "The Glock did not retain its TaCZ gun type");
        helper.assertTrue(pistol.resolution().profile() == TacticalProfile.SKIRMISHER
                        && pistol.resolution().source()
                                == WeaponTacticalResolver.Source.TYPE_MAPPING,
                "The Glock did not resolve through the configured pistol mapping");
        helper.assertTrue(!pistol.traits().explosive()
                        && pistol.traits().projectileCount() == 1,
                "The Glock received incorrect structural weapon traits");
        helper.assertTrue(controller.getPreferredApproachDistance()
                        == pistol.envelope().preferredApproachDistance()
                        && controller.getRetreatDistance()
                                == pistol.envelope().retreatDistance()
                        && controller.getRequiredVisibleTicks()
                                == pistol.envelope().requiredVisibleTicks()
                        && controller.getAimToleranceDegrees()
                                == pistol.envelope().aimToleranceDegrees(),
                "The native controller did not expose the Glock's cached tactical envelope");
        helper.assertTrue(controller.hasValidFiringWindow()
                        && controller.canFireAtDistanceSquared(
                                pistol.envelope().maximumFiringDistance()
                                        * pistol.envelope().maximumFiringDistance()),
                "The Glock's inclusive cached firing window was not live");

        GuardOrdersAccess.setOrders(
                guard,
                GuardOrders.DEFAULT
                        .withTactics(TacticalOrder.MARKSMAN)
                        .withMaximumRange(RangeOrder.BLOCKS_8));
        helper.assertTrue(!controller.canFireAtDistanceSquared(4.0D * 4.0D),
                "A changed guard order left the previous burst firing window live");
        controller.startAiming();
        NativeGunController.CachedTacticalState orderedPistol =
                controller.captureDiagnostics().tacticalState();
        helper.assertTrue(
                orderedPistol.resolution().profile() == TacticalProfile.MARKSMAN
                        && orderedPistol.resolution().source()
                                == WeaponTacticalResolver.Source.PER_GUARD_ORDER,
                "The per-guard tactical profile did not refresh the active controller");
        helper.assertTrue(orderedPistol.envelope().maximumFiringDistance() <= 8.0D,
                "The per-guard maximum range widened its eight-block cap");
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
        controller.startAiming();

        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(M870, FireMode.SEMI, 1, true));
        helper.assertTrue(controller.getPreferredApproachDistance() == 16.0D
                        && controller.getRetreatDistance() == 4.0D
                        && controller.getRequiredVisibleTicks() == 5
                        && controller.getAimToleranceDegrees() == 10.0D
                        && !controller.hasValidFiringWindow()
                        && !controller.canFireAtDistanceSquared(16.0D * 16.0D),
                "A hand swap exposed the previous gun's stale tactical requirements");
        controller.startAiming();
        NativeGunController.CachedTacticalState shotgun =
                controller.captureDiagnostics().tacticalState();
        helper.assertTrue(shotgun.metadataReadable()
                        && shotgun.resolution().profile() == TacticalProfile.BREACHER
                        && shotgun.traits().projectileCount() == 9
                        && shotgun.traits().multiProjectile(),
                "The M870 did not cache its TaCZ shotgun profile and pellet count");

        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(RPG_7, FireMode.SEMI, 1, true));
        controller.startAiming();
        NativeGunController.CachedTacticalState launcher =
                controller.captureDiagnostics().tacticalState();
        helper.assertTrue(launcher.metadataReadable()
                        && launcher.resolution().profile() == TacticalProfile.DEMOLITIONS
                        && launcher.traits().explosive()
                        && Mth.equal((float) launcher.traits().explosionRadius(), 3.0F),
                "The RPG-7 did not cache its TaCZ explosive profile and blast radius");
        helper.assertTrue(launcher.envelope().minimumFiringDistance() == 10.0D,
                "The cached RPG-7 envelope did not include the default explosive standoff");
        helper.assertTrue(!controller.canFireAtDistanceSquared(99.99D)
                        && controller.canFireAtDistanceSquared(100.0D),
                "The RPG-7's inclusive minimum firing boundary was not live");
        helper.assertTrue(controller.getRetreatDistance() == 10.0D
                        && controller.getPreferredApproachDistance()
                                == launcher.envelope().preferredApproachDistance()
                        && controller.getRequiredVisibleTicks() == 8
                        && controller.getAimToleranceDegrees() == 6.5D,
                "The native controller did not expose the RPG-7's cached tactical envelope");
        helper.succeed();
    }

    @GameTest(batch = "tactical_movement", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void tacticalProfilesControlCachedApproachAndRetreat(GameTestHelper helper) {
        for (int x = 0; x <= 16; x++) {
            for (int z = -1; z <= 21; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        Guard skirmisher = spawnGuard(helper, new BlockPos(0, 1, 0));
        Ravager distantTarget = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(16, 1, 0));
        configureFixedCombatant(skirmisher);
        configureFixedCombatant(distantTarget);
        skirmisher.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(GLOCK_17, FireMode.SEMI, 12, true));
        skirmisher.setTarget(distantTarget);
        GuardGunCombatGoal skirmisherGoal = new GuardGunCombatGoal(skirmisher);
        helper.assertTrue(skirmisherGoal.canUse(),
                "The Skirmisher movement fixture could not start its gun goal");
        skirmisherGoal.start();

        Guard marksman = spawnGuard(helper, new BlockPos(0, 1, 20));
        Ravager closeTarget = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(6, 1, 20));
        configureFixedCombatant(marksman);
        configureFixedCombatant(closeTarget);
        marksman.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(M95, FireMode.SEMI, 12, true));
        marksman.setTarget(closeTarget);
        GuardGunCombatGoal marksmanGoal = new GuardGunCombatGoal(marksman);
        helper.assertTrue(marksmanGoal.canUse(),
                "The Marksman movement fixture could not start its gun goal");
        marksmanGoal.start();

        double skirmisherX = skirmisher.getX();
        double skirmisherY = skirmisher.getY();
        double skirmisherZ = skirmisher.getZ();
        double distantX = distantTarget.getX();
        double distantY = distantTarget.getY();
        double distantZ = distantTarget.getZ();
        double marksmanX = marksman.getX();
        double marksmanY = marksman.getY();
        double marksmanZ = marksman.getZ();
        double closeX = closeTarget.getX();
        double closeY = closeTarget.getY();
        double closeZ = closeTarget.getZ();

        helper.onEachTick(() -> {
            pinEntity(skirmisher, skirmisherX, skirmisherY, skirmisherZ);
            pinEntity(distantTarget, distantX, distantY, distantZ);
            pinEntity(marksman, marksmanX, marksmanY, marksmanZ);
            pinEntity(closeTarget, closeX, closeY, closeZ);
            skirmisher.setTarget(distantTarget);
            marksman.setTarget(closeTarget);
            skirmisher.getSensing().tick();
            marksman.getSensing().tick();
            faceTarget(skirmisher, distantTarget);
            faceTarget(marksman, closeTarget);
            skirmisherGoal.tick();
            marksmanGoal.tick();
        });
        helper.runAtTickTime(10, () -> {
            GuardGunCombatGoal.Diagnostics skirmisherState =
                    skirmisherGoal.captureDiagnostics();
            GuardGunCombatGoal.Diagnostics marksmanState = marksmanGoal.captureDiagnostics();
            helper.assertTrue(skirmisherState.nativeState().tacticalState()
                            .resolution().profile() == TacticalProfile.SKIRMISHER
                            && skirmisherState.distance() > skirmisherState.preferredRange()
                            && "APPROACH".equals(skirmisherState.movementState()),
                    "The Skirmisher did not approach beyond its cached preferred distance");
            helper.assertTrue(marksmanState.nativeState().tacticalState()
                            .resolution().profile() == TacticalProfile.MARKSMAN
                            && marksmanState.distance() < marksmanState.retreatDistance()
                            && "RETREAT".equals(marksmanState.movementState()),
                    "The Marksman did not retreat inside its profile-adjusted distance");
            helper.succeed();
        });
    }

    @GameTest(batch = "tactical_firing", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void marksmanProfileControlsVisibilityAndAimGates(GameTestHelper helper) {
        for (int x = 0; x <= 16; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(16, 1, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        target.setInvulnerable(true);
        ItemStack gunStack = gun(M95, FireMode.SEMI, 4, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
        guard.setTarget(target);
        guard.setPatrolling(true);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The Marksman firing fixture could not start its gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;

        double guardX = guard.getX();
        double guardY = guard.getY();
        double guardZ = guard.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        boolean[] visibilityGateObserved = {false};
        boolean[] aimGateObserved = {false};

        helper.onEachTick(() -> {
            pinEntity(guard, guardX, guardY, guardZ);
            pinEntity(target, targetX, targetY, targetZ);
            guard.setTarget(target);
            guard.getSensing().tick();
            faceTarget(guard, target);
            if (!aimGateObserved[0]) {
                float offsetYaw = guard.getYHeadRot() + 8.0F;
                guard.setYRot(offsetYaw);
                guard.setYHeadRot(offsetYaw);
                guard.yBodyRot = offsetYaw;
            }

            long relativeNow = System.currentTimeMillis()
                    - operator.getDataHolder().baseTimestamp;
            operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
            operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
            goal.tick();

            GuardGunCombatGoal.Diagnostics state = goal.captureDiagnostics();
            helper.assertTrue(state.nativeState().tacticalState()
                            .resolution().profile() == TacticalProfile.MARKSMAN,
                    "The firing fixture did not retain its Marksman profile");
            helper.assertTrue(state.requiredVisibleTicks() == 8,
                    "The Marksman visibility adjustment was not used by the live goal");
            helper.assertTrue(state.aimToleranceDegrees() == 6.5D,
                    "The Marksman aim multiplier was not used by the live goal");

            if (state.visibleTicks() < state.requiredVisibleTicks()) {
                helper.assertTrue(state.nativeState().lastShootResult() != ShootResult.SUCCESS
                                && state.nativeState().chambered(),
                        "The Marksman fired before satisfying its profile visibility requirement");
                if (state.visibleTicks() > 0) {
                    visibilityGateObserved[0] = true;
                }
                return;
            }

            if (!aimGateObserved[0]) {
                helper.assertTrue(visibilityGateObserved[0],
                        "The test never observed the Marksman visibility gate");
                helper.assertTrue(state.decision() == GuardGunCombatGoal.FireDecision.AIMING,
                        "The stricter Marksman tolerance did not hold an eight-degree aim error");
                helper.assertTrue(state.nativeState().lastShootResult() != ShootResult.SUCCESS
                                && state.nativeState().chambered(),
                        "The Marksman fired despite exceeding its profile aim tolerance");
                aimGateObserved[0] = true;
                return;
            }

            if (state.nativeState().lastShootResult() == ShootResult.SUCCESS
                    && !state.nativeState().chambered()) {
                helper.assertTrue(visibilityGateObserved[0] && aimGateObserved[0],
                        "The shot was accepted before both profile gates were exercised");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(60, () -> {
            GuardGunCombatGoal.Diagnostics stalled = goal.captureDiagnostics();
            helper.fail("Marksman did not fire after satisfying its profile gates: decision="
                    + stalled.decision()
                    + ", visibleTicks=" + stalled.visibleTicks()
                    + "/" + stalled.requiredVisibleTicks()
                    + ", yawError=" + stalled.yawErrorDegrees()
                    + ", tolerance=" + stalled.aimToleranceDegrees()
                    + ", safety=" + stalled.safety().status());
        });
    }

    @GameTest(batch = "tactical_range_cap", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void profileMaximumFiringRangeControlsLiveGoal(GameTestHelper helper) {
        for (int x = 0; x <= 32; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(30, 1, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        ItemStack gunStack = gun(GLOCK_17, FireMode.SEMI, 4, true);
        IGun gunItem = (IGun) gunStack.getItem();
        int initialAmmo = gunItem.getCurrentAmmoCount(gunStack);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
        guard.setTarget(target);
        guard.setPatrolling(true);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The profile range-cap fixture could not start its gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;

        GuardGunCombatGoal.Diagnostics initial = goal.captureDiagnostics();
        helper.assertTrue(initial.validFiringWindow()
                        && initial.minimumRange() == 0.0D
                        && initial.maximumRange() < initial.distance()
                        && initial.maximumRange()
                                < ModConfigs.GUARD_GUNNERS.combat.maximumEngagementRange.get(),
                "The Glock fixture did not resolve a narrower Skirmisher firing cap");
        double insideDistance = initial.maximumRange() - 1.0D;
        double guardX = guard.getX();
        double guardY = guard.getY();
        double guardZ = guard.getZ();
        double targetY = target.getY();
        double targetZ = target.getZ();
        boolean[] outsideGateObserved = {false};

        helper.onEachTick(() -> {
            long testTick = helper.getTick();
            pinEntity(guard, guardX, guardY, guardZ);
            pinEntity(
                    target,
                    testTick < 20L ? guardX + 30.0D : guardX + insideDistance,
                    targetY,
                    targetZ);
            guard.setTarget(target);
            guard.getSensing().tick();
            faceTarget(guard, target);
            long relativeNow = System.currentTimeMillis()
                    - operator.getDataHolder().baseTimestamp;
            operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
            operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
            goal.tick();

            if (testTick == 15L) {
                GuardGunCombatGoal.Diagnostics outside = goal.captureDiagnostics();
                helper.assertTrue(outside.decision() == GuardGunCombatGoal.FireDecision.OUT_OF_RANGE
                                && !outside.withinFiringWindow()
                                && gunItem.getCurrentAmmoCount(gunStack) == initialAmmo,
                        "The Skirmisher fired beyond its profile-specific maximum");
                outsideGateObserved[0] = true;
            }
            if (testTick >= 20L && gunItem.getCurrentAmmoCount(gunStack) < initialAmmo) {
                GuardGunCombatGoal.Diagnostics fired = goal.captureDiagnostics();
                helper.assertTrue(outsideGateObserved[0]
                                && fired.withinFiringWindow()
                                && fired.distance() < fired.maximumRange(),
                        "The Skirmisher did not recover after entering its profile window");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(100, () -> helper.fail(
                "The Skirmisher did not fire after moving inside its profile range"));
    }

    @GameTest(batch = "tactical_standoff", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void explosiveMinimumFiringDistanceControlsLiveGoal(GameTestHelper helper) {
        for (int x = 0; x <= 16; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 24; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        // Keep the wide explosive safety envelope vertically isolated from
        // other GameTest fixtures that share this server world.
        Guard guard = spawnGuard(helper, new BlockPos(0, 20, 0));
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 20, 0));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        ItemStack gunStack = gun(RPG_7, FireMode.SEMI, 1, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
        guard.setTarget(target);
        guard.setPatrolling(true);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The explosive-standoff fixture could not start its gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;

        GuardGunCombatGoal.Diagnostics initial = goal.captureDiagnostics();
        helper.assertTrue(initial.validFiringWindow()
                        && initial.minimumRange() == 10.0D
                        && initial.distance() < initial.minimumRange(),
                "The RPG fixture did not resolve its ten-block explosive standoff");
        double safeDistance = initial.minimumRange() + 2.0D;
        double guardX = guard.getX();
        double guardY = guard.getY();
        double guardZ = guard.getZ();
        double targetY = target.getY();
        double targetZ = target.getZ();
        boolean[] standoffGateObserved = {false};

        helper.onEachTick(() -> {
            long testTick = helper.getTick();
            pinEntity(guard, guardX, guardY, guardZ);
            pinEntity(
                    target,
                    testTick < 20L ? guardX + 8.0D : guardX + safeDistance,
                    targetY,
                    targetZ);
            guard.setTarget(target);
            guard.getSensing().tick();
            faceTarget(guard, target);
            long relativeNow = System.currentTimeMillis()
                    - operator.getDataHolder().baseTimestamp;
            operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
            operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
            goal.tick();

            GuardGunCombatGoal.Diagnostics state = goal.captureDiagnostics();
            if (testTick == 15L) {
                helper.assertTrue(state.decision() == GuardGunCombatGoal.FireDecision.TOO_CLOSE
                                && !state.withinFiringWindow()
                                && state.nativeState().lastShootResult() != ShootResult.SUCCESS,
                        "The RPG fired inside its explosive standoff distance");
                standoffGateObserved[0] = true;
            }
            if (testTick >= 20L
                    && state.nativeState().lastShootResult() == ShootResult.SUCCESS) {
                helper.assertTrue(standoffGateObserved[0]
                                && state.withinFiringWindow()
                                && state.distance() > state.minimumRange(),
                        "The RPG did not recover after reaching a safe standoff");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(100, () -> {
            GuardGunCombatGoal.Diagnostics stalled = goal.captureDiagnostics();
            helper.fail("The RPG did not fire after moving beyond its explosive standoff: decision="
                    + stalled.decision()
                    + ", distance=" + stalled.distance()
                    + ", minimum=" + stalled.minimumRange()
                    + ", maximum=" + stalled.maximumRange()
                    + ", validWindow=" + stalled.validFiringWindow()
                    + ", withinWindow=" + stalled.withinFiringWindow()
                    + ", visibleTicks=" + stalled.visibleTicks()
                    + "/" + stalled.requiredVisibleTicks()
                    + ", yawError=" + stalled.yawErrorDegrees()
                    + ", pitchError=" + stalled.pitchErrorDegrees()
                    + ", safety=" + stalled.safety().status()
                    + ", native=" + stalled.nativeState());
        });
    }

    @GameTest(batch = "explosive_muzzle_clearance", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void explosiveSafetyNarrowsOnlyNearTheShooter(GameTestHelper helper) {
        for (int x = 0; x <= 24; x++) {
            for (int z = 0; z <= 10; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        Guard guard = helper.spawn(
                GuardEntityType.GUARD.get(),
                new Vec3(4.0D, 1.0D, 5.0D));
        Ravager target = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new Vec3(20.0D, 1.0D, 5.0D));
        Guard protectedGuard = helper.spawnWithNoFreeWill(
                GuardEntityType.GUARD.get(),
                new Vec3(12.0D, 1.0D, 9.0D));
        configureFixedCombatant(guard);
        configureFixedCombatant(target);
        configureFixedCombatant(protectedGuard);
        target.setInvulnerable(true);
        ItemStack gunStack = gun(RPG_7, FireMode.SEMI, 1, true);
        IGun gunItem = (IGun) gunStack.getItem();
        int initialAmmo = gunItem.getCurrentAmmoCount(gunStack);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
        guard.setTarget(target);
        guard.setPatrolling(true);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(),
                "The explosive muzzle-clearance fixture could not start its gun goal");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        boolean[] forwardBlastGateObserved = {false};
        double guardX = guard.getX();
        double guardY = guard.getY();
        double guardZ = guard.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();

        helper.onEachTick(() -> {
            long testTick = helper.getTick();
            pinEntity(guard, guardX, guardY, guardZ);
            pinEntity(target, targetX, targetY, targetZ);
            if (testTick < 20L) {
                // Outside the direct projectile corridor but within the RPG's
                // forward blast envelope.
                pinEntity(
                        protectedGuard,
                        guardX + 8.0D,
                        guardY,
                        guardZ + 4.0D);
            } else {
                // About 3.5 blocks beside the shooter, outside the direct
                // muzzle line but previously caught by its wide end cap.
                pinEntity(
                        protectedGuard,
                        guardX,
                        guardY,
                        guardZ + 3.5D);
            }
            guard.setTarget(target);
            guard.getSensing().tick();
            faceTarget(guard, target);
            long relativeNow = System.currentTimeMillis()
                    - operator.getDataHolder().baseTimestamp;
            // This isolated batch begins before enough wall-clock time has
            // elapsed for TaCZ's draw animation. Keep only that native clock
            // due; ordinary gameplay advances it naturally.
            operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
            operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
            operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
            goal.tick();

            GuardGunCombatGoal.Diagnostics state = goal.captureDiagnostics();
            if (testTick == 15L) {
                helper.assertTrue(
                        state.decision() == GuardGunCombatGoal.FireDecision.UNSAFE_SHOT
                                && state.safety().status()
                                        == GuardFriendlyFireSafety.DiagnosticStatus
                                                .BLOCKED_BY_PROTECTED_ENTITY
                                && state.projectileSafetyPadding() > state.muzzleSafetyPadding()
                                && state.blastSafetyPadding() > state.projectileSafetyPadding()
                                && gunItem.getCurrentAmmoCount(gunStack) == initialAmmo,
                        "The RPG did not retain its full forward blast protection: decision="
                                + state.decision()
                                + ", safety=" + state.safety().status()
                                + ", muzzlePadding=" + state.muzzleSafetyPadding()
                                + ", projectilePadding=" + state.projectileSafetyPadding()
                                + ", blastPadding=" + state.blastSafetyPadding());
                forwardBlastGateObserved[0] = true;
            }
            if (testTick >= 20L
                    && gunItem.getCurrentAmmoCount(gunStack) < initialAmmo) {
                helper.assertTrue(
                        forwardBlastGateObserved[0]
                                && state.safety().status()
                                        == GuardFriendlyFireSafety.DiagnosticStatus.CLEAR,
                        "The RPG fired without proving the narrowed muzzle envelope");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(80, () -> {
            GuardGunCombatGoal.Diagnostics stalled = goal.captureDiagnostics();
            goal.stop();
            helper.fail("The RPG stayed blocked after the protected guard moved behind the shooter: "
                    + "decision=" + stalled.decision()
                    + ", safety=" + stalled.safety().status()
                    + ", muzzlePadding=" + stalled.muzzleSafetyPadding()
                    + ", projectilePadding=" + stalled.projectileSafetyPadding()
                    + ", blastPadding=" + stalled.blastSafetyPadding());
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guardMenuQuickMoveRespectsChangedPickupAuthorization(GameTestHelper helper) {
        boolean originalHotvRequirement = GuardConfig.giveGuardStuffHOTV;
        int originalReputationRequirement = GuardConfig.reputationRequirement;
        Guard guard = spawnGuard(helper);
        Player player = helper.makeMockPlayer();
        GuardGunnerContainer menu = new GuardGunnerContainer(
                2,
                player.getInventory(),
                guard.guardInventory,
                GuardAmmoAccess.getAmmoHandler(guard),
                guard);
        try {
            GuardConfig.giveGuardStuffHOTV = true;
            GuardConfig.reputationRequirement = Integer.MAX_VALUE;
            ItemStack equipment = new ItemStack(Items.IRON_SWORD);
            guard.guardInventory.setItem(5, equipment);

            helper.assertFalse(menu.getSlot(5).mayPickup(player),
                    "The test player unexpectedly retained guard pickup authorization");
            helper.assertTrue(menu.quickMoveStack(player, 5).isEmpty(),
                    "Shift-click bypassed the guard slot's pickup authorization");
            helper.assertTrue(ItemStack.isSameItemSameTags(equipment, guard.guardInventory.getItem(5)),
                    "Unauthorized shift-click changed the guard's equipment");
            helper.assertFalse(menu.clickMenuButton(player, OrderField.ENGAGEMENT.buttonId()),
                    "An unauthorized player changed a guard order through a raw menu request");
            helper.assertTrue(GuardOrders.DEFAULT.equals(GuardOrdersAccess.getOrders(guard)),
                    "An unauthorized menu request changed the guard's orders");
            helper.succeed();
        } finally {
            GuardConfig.giveGuardStuffHOTV = originalHotvRequirement;
            GuardConfig.reputationRequirement = originalReputationRequirement;
            menu.removed(player);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void nativeSemiAutomaticFireMutatesTheBoundTaCZGun(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(8, 1, 0));
        target.setInvulnerable(true);
        ItemStack gunStack = gun(GLOCK_17, FireMode.SEMI, 12, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);

        NativeGunController controller = new NativeGunController(guard);
        controller.startAiming();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        IGun gunItem = (IGun) gunStack.getItem();
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        long originalDrawTimestamp = operator.getDataHolder().drawTimestamp;

        helper.onEachTick(() -> {
            long relativeNow = System.currentTimeMillis()
                    - operator.getDataHolder().baseTimestamp;
            operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
            operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
            int ammoBeforeTick = gunItem.getCurrentAmmoCount(gunStack);
            controller.tick(target, true, () -> true);
            if (gunItem.getCurrentAmmoCount(gunStack) < ammoBeforeTick) {
                helper.assertTrue(operator.getDataHolder().shootTimestamp >= 0L,
                        "TaCZ consumed ammunition without accepting a native trigger");
                helper.assertTrue(operator.getDataHolder().currentGunItem.get() == gunStack,
                        "TaCZ's operator is no longer bound to the held gun stack");
                helper.assertTrue(operator.getDataHolder().drawTimestamp == originalDrawTimestamp,
                        "The unchanged gun was redrawn and rebuilt during firing");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "range_boundary", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void guardGoalFiresBeyondPreferredRangeWithinConfiguredMaximum(GameTestHelper helper) {
        // Clear a full-width lane rather than only the centerline so the
        // fixture remains independent of structure walls at either endpoint.
        for (int x = 0; x <= 20; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        Guard guard = spawnGuard(helper);
        // This test has its own batch so its long horizontal corridor cannot
        // cross the other correctness fixtures that Forge runs concurrently.
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(20, 1, 0));
        target.setInvulnerable(true);
        target.setNoGravity(true);
        guard.setNoGravity(true);
        ItemStack gunStack = gun(GLOCK_17, FireMode.SEMI, 12, true);
        IGun gunItem = (IGun) gunStack.getItem();
        guard.setTarget(target);
        // Patrolling is a supported combat state that deliberately holds the
        // guard's position, isolating the hard firing cap from approach logic.
        guard.setPatrolling(true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);

        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
        helper.assertTrue(goal.canUse(), "The configured guard gun goal could not start");
        goal.start();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;

        GuardGunCombatGoal.Diagnostics initial = goal.captureDiagnostics();
        helper.assertTrue(initial.distance() > initial.preferredRange(),
                "The fixture is not beyond the Glock's preferred TaCZ effective range");
        helper.assertTrue(initial.distance() <= initial.maximumRange(),
                "The fixture is outside the configured maximum firing range");
        double guardX = guard.getX();
        double guardY = guard.getY();
        double guardZ = guard.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();

        helper.onEachTick(() -> {
            // Keep both endpoints exact while exercising the real goal. This
            // prevents navigation or collision resolution from turning a
            // firing-range regression into a movement-speed test.
            guard.teleportTo(guardX, guardY, guardZ);
            guard.setDeltaMovement(0.0D, 0.0D, 0.0D);
            guard.getNavigation().stop();
            target.teleportTo(targetX, targetY, targetZ);
            target.setDeltaMovement(0.0D, 0.0D, 0.0D);
            guard.setTarget(target);
            // spawnWithNoFreeWill deliberately skips the normal Mob AI step,
            // which would otherwise clear Sensing's per-tick result cache.
            guard.getSensing().tick();
            faceTarget(guard, target);
            // The GameTest server advances ticks without real-time pacing,
            // while TaCZ intentionally uses wall-clock shot cooldowns. Make
            // only that public cooldown clock due so this range assertion is
            // deterministic; firing still goes through the real goal and
            // native controller.
            long relativeNow = System.currentTimeMillis() - operator.getDataHolder().baseTimestamp;
            operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
            operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
            int ammoBeforeGoalTick = gunItem.getCurrentAmmoCount(gunStack);
            goal.tick();
            if (gunItem.getCurrentAmmoCount(gunStack) < ammoBeforeGoalTick) {
                GuardGunCombatGoal.Diagnostics fired = goal.captureDiagnostics();
                helper.assertTrue(fired.distance() > initial.preferredRange(),
                        "The accepted shot was not beyond the initial preferred range: distance="
                                + fired.distance() + ", preferred=" + initial.preferredRange());
                helper.assertTrue(fired.withinMaximumRange(),
                        "The accepted native shot was not inside the configured maximum range");
                helper.assertTrue(operator.getDataHolder().shootTimestamp >= 0L,
                        "TaCZ did not accept the beyond-preferred-range native shot");
                goal.stop();
                helper.succeed();
            }
        });
        helper.runAtTickTime(100, () -> {
            GuardGunCombatGoal.Diagnostics stalled = goal.captureDiagnostics();
            helper.fail("Long-range goal did not fire: decision=" + stalled.decision()
                    + ", distance=" + stalled.distance()
                    + ", preferred=" + stalled.preferredRange()
                    + ", maximum=" + stalled.maximumRange()
                    + ", lineOfSight=" + stalled.cachedLineOfSight()
                    + ", visibleTicks=" + stalled.visibleTicks()
                    + ", yawError=" + stalled.yawErrorDegrees()
                    + ", pitchError=" + stalled.pitchErrorDegrees()
                    + ", safety=" + stalled.safety().status()
                    + ", muzzlePadding=" + stalled.muzzleSafetyPadding()
                    + ", projectilePadding=" + stalled.projectileSafetyPadding()
                    + ", blastPadding=" + stalled.blastSafetyPadding()
                    + ", native=" + stalled.nativeState());
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 180)
    public static void targetDeathDuringNativeBurstStopsTrackingSafely(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(10, 1, 0));
        target.setInvulnerable(true);
        ItemStack burstGun = gun(M16_A4, FireMode.BURST, 9, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, burstGun);

        NativeGunController controller = new NativeGunController(guard);
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        IGun gunItem = (IGun) burstGun.getItem();
        boolean[] targetRemoved = {false};
        long[] removalTick = {-1L};
        int[] ammoAtRemoval = {-1};
        controller.startAiming();
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;

        helper.onEachTick(() -> {
            if (!targetRemoved[0]) {
                controller.tick(target, true, () -> true);
                if (operator.getDataHolder().shootTimestamp >= 0L) {
                    ammoAtRemoval[0] = gunItem.getCurrentAmmoCount(burstGun);
                    target.discard();
                    controller.stopAiming();
                    targetRemoved[0] = true;
                    removalTick[0] = helper.getTick();
                }
                return;
            }

            if (helper.getTick() - removalTick[0] >= 20L) {
                helper.assertFalse(target.isAlive(),
                        "The burst target unexpectedly survived its forced removal");
                helper.assertFalse(operator.getDataHolder().isAiming,
                        "The guard kept native aiming active after its burst target died");
                helper.assertTrue(gunItem.getCurrentAmmoCount(burstGun) == ammoAtRemoval[0],
                        "A canceled native burst round still consumed magazine ammunition");
                helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void weaponSwapCancelsReloadAndChargeBeforeRebinding(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(8, 1, 0));
        ItemStack emptyGun = gun(GLOCK_17, FireMode.SEMI, 0, false);
        ItemStack replacement = gun(AK_47, FireMode.AUTO, 20, true);
        GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(32).build());
        guard.setItemSlot(EquipmentSlot.MAINHAND, emptyGun);

        NativeGunController controller = new NativeGunController(guard);
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        boolean[] swapped = {false};
        controller.startAiming();
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        helper.onEachTick(() -> {
            controller.tick(target, false, () -> true);
            if (!swapped[0] && operator.getDataHolder().reloadStateType.isReloading()) {
                operator.getDataHolder().chargeProgress = 0.75F;
                guard.setItemSlot(EquipmentSlot.MAINHAND, replacement);
                controller.tick(target, false, () -> true);
                swapped[0] = true;

                helper.assertFalse(operator.getDataHolder().reloadStateType.isReloading(),
                        "Weapon replacement left the old TaCZ reload active");
                helper.assertFalse(operator.getDataHolder().isBolting,
                        "Weapon replacement inherited an old bolt action");
                helper.assertTrue(operator.getDataHolder().chargeProgress == 0.0F,
                        "Weapon replacement inherited native charge progress");
                helper.assertTrue(operator.getDataHolder().currentGunItem.get() == replacement,
                        "TaCZ's operator did not bind the replacement gun");
                helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void weaponSwapCancelsManualBoltBeforeRebinding(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(8, 1, 0));
        ItemStack manualActionGun = gun(M95, FireMode.SEMI, 1, false);
        ItemStack replacement = gun(GLOCK_17, FireMode.SEMI, 4, true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, manualActionGun);

        NativeGunController controller = new NativeGunController(guard);
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        boolean[] swapped = {false};
        controller.startAiming();
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        helper.onEachTick(() -> {
            controller.tick(target, false, () -> true);
            if (!swapped[0] && operator.getDataHolder().isBolting) {
                guard.setItemSlot(EquipmentSlot.MAINHAND, replacement);
                controller.tick(target, false, () -> true);
                swapped[0] = true;

                helper.assertFalse(operator.getDataHolder().isBolting,
                        "Weapon replacement left the old manual bolt active");
                helper.assertTrue(operator.getDataHolder().currentGunItem.get() == replacement,
                        "TaCZ's operator did not bind after a bolt-state replacement");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "dry_melee_fallback", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void permanentlyDryGunYieldsToTheUpstreamMeleeGoal(GameTestHelper helper) {
        // The padded isolation template is intentionally all air. Give the
        // upstream pathfinding goal a small, explicit surface so this test
        // measures goal ownership instead of whether both entities fell into
        // the void before MeleeAttackGoal could construct a path.
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Guard guard = helper.spawn(GuardEntityType.GUARD.get(), new BlockPos(2, 1, 2));
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(6, 1, 2));
        target.setInvulnerable(true);
        guard.setTarget(target);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
        GuardGunCombatGoal gunGoal = guard.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(GuardGunCombatGoal.class::isInstance)
                .map(GuardGunCombatGoal.class::cast)
                .findFirst()
                .orElseThrow();
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        helper.assertTrue(guard.goalSelector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal() instanceof MeleeAttackGoal),
                "Guard Villagers' lower-priority melee goal is unavailable");
        helper.assertTrue(gunGoal.canUse(),
                "A newly equipped dry gun was not given its bounded reload attempt");
        gunGoal.start();
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        gunGoal.tick();
        helper.assertTrue(gunGoal.captureDiagnostics().nativeState().dryRetryTicks() > 0L,
                "The dry gun did not enter its bounded retry state");
        helper.assertFalse(gunGoal.canContinueToUse(),
                "A permanently dry gun did not yield MOVE/LOOK ownership");
        gunGoal.stop();
        // Our integration contract ends when MOVE/LOOK ownership is released.
        // Waiting for vanilla MeleeAttackGoal to start makes this fixture depend
        // on its independent 20-tick canUse throttle and lets later GameTest
        // batches that alter the global ROE settings race the assertion.
        helper.assertFalse(IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming,
                "A permanently dry gun remained aimed after yielding to melee");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void impossibleTacticalWindowYieldsWithoutGoalThrashing(GameTestHelper helper) {
        double originalMaximumRange = ModConfigs.GUARD_GUNNERS.combat
                .maximumEngagementRange.get();
        double originalAdditionalStandoff = ModConfigs.GUARD_GUNNERS.safety
                .explosiveStandoffAdditionalBlocks.get();
        GuardGunCombatGoal goal = null;
        try {
            ModConfigs.GUARD_GUNNERS.combat.maximumEngagementRange.accept(8.0D);
            ModConfigs.GUARD_GUNNERS.safety.explosiveStandoffAdditionalBlocks.accept(32.0D);

            Guard guard = spawnGuard(helper);
            Ravager target = helper.spawnWithNoFreeWill(
                    EntityType.RAVAGER,
                    new BlockPos(4, 1, 0));
            target.setInvulnerable(true);
            guard.setTarget(target);
            guard.setItemSlot(
                    EquipmentSlot.MAINHAND,
                    gun(RPG_7, FireMode.SEMI, 1, true));

            goal = new GuardGunCombatGoal(guard);
            helper.assertTrue(goal.canUse(),
                    "A newly held RPG was not given one metadata-resolution attempt");
            goal.start();
            GuardGunCombatGoal.Diagnostics diagnostics = goal.captureDiagnostics();
            helper.assertFalse(diagnostics.validFiringWindow(),
                    "The contradictory RPG standoff unexpectedly produced a valid window");
            helper.assertFalse(goal.canContinueToUse(),
                    "An impossible tactical window retained ownership of the guard");

            goal.stop();
            helper.assertFalse(goal.canUse(),
                    "The invalid gun goal immediately restarted instead of waiting for its refresh slot");
            helper.assertFalse(IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming,
                    "The invalid tactical window left the guard aiming after yielding");
            helper.succeed();
        } finally {
            if (goal != null) {
                goal.stop();
            }
            ModConfigs.GUARD_GUNNERS.combat.maximumEngagementRange.accept(originalMaximumRange);
            ModConfigs.GUARD_GUNNERS.safety.explosiveStandoffAdditionalBlocks
                    .accept(originalAdditionalStandoff);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void gunGuardDoesNotEnterUpstreamShieldGoal(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
        // Creepers are in Guard Villagers' default target blacklist, so use a
        // nearby ravager to exercise the upstream shield predicate itself.
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(2, 1, 0));
        guard.setTarget(target);
        guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        RaiseShieldGoal shieldGoal = new RaiseShieldGoal(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(shieldGoal.canUse(),
                "Phase 4 compatibility unexpectedly disabled ordinary shield behavior");
        shieldGoal.start();
        helper.assertTrue(guard.isUsingItem()
                        && guard.getUsedItemHand() == InteractionHand.OFF_HAND,
                "The shield fixture did not enter the raised offhand state");

        ItemStack gun = GunItemBuilder.create()
                .setId(GLOCK_17)
                .setFireMode(FireMode.SEMI)
                .setAmmoCount(1)
                .setAmmoInBarrel(true)
                .build();
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
        helper.assertTrue(shieldGoal.canUse(),
                "A non-aiming gun unexpectedly disabled shield/melee fallback");

        GuardGunCombatGoal gunGoal = new GuardGunCombatGoal(guard);
        helper.assertTrue(gunGoal.canUse(),
                "An engageable gun could not take over from a raised shield");
        gunGoal.start();
        helper.assertFalse(guard.isUsingItem(),
                "Gun-goal startup left the guard trapped behind its raised shield");
        helper.assertFalse(shieldGoal.canUse(),
                "Guard Villagers' priority-zero shield goal still preempted an aimed TaCZ gun");
        helper.assertFalse(shieldGoal.canContinueToUse(),
                "An active shield goal did not yield after the guard aimed a TaCZ gun");
        helper.assertTrue(IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming,
                "TaCZ did not enter its native aiming state after the shield was released");
        gunGoal.stop();
        shieldGoal.stop();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void phaseFiveUsesOneProtectedEntityPredicate(GameTestHelper helper) {
        Guard shooter = spawnGuard(helper);
        Guard otherGuard = helper.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), new BlockPos(1, 1, 0));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 0));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 1, 0));
        Ravager hostile = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(4, 1, 0));
        Ravager allied = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(5, 1, 0));
        Player owner = helper.makeMockPlayer();
        shooter.setOwnerId(owner.getUUID());
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam allyTeam = scoreboard.getPlayerTeam("vgg_phase_five_allies");
        if (allyTeam == null) {
            allyTeam = scoreboard.addPlayerTeam("vgg_phase_five_allies");
        }
        scoreboard.addPlayerToTeam(shooter.getScoreboardName(), allyTeam);
        scoreboard.addPlayerToTeam(allied.getScoreboardName(), allyTeam);

        helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, owner),
                "The guard owner was not protected");
        helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, villager),
                "A villager was not protected");
        helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, otherGuard),
                "Another guard was not protected");
        helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, golem),
                "An iron golem was not protected");
        helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, allied),
                "A Minecraft-allied living entity was not protected");
        helper.assertFalse(GuardFriendlyFireSafety.isProtected(shooter, hostile),
                "A hostile target was incorrectly protected");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void modpackAllyProtectionHonorsOverridesOwnershipTeamsAndProviders(
            GameTestHelper helper) {
        Guard shooter = spawnGuard(helper);
        Guard otherGuard = helper.spawnWithNoFreeWill(
                GuardEntityType.GUARD.get(),
                new BlockPos(1, 1, 0));
        Villager villager = helper.spawnWithNoFreeWill(
                EntityType.VILLAGER,
                new BlockPos(2, 1, 0));
        Ravager configured = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(3, 1, 0));
        Ravager teamed = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(4, 1, 0));
        Wolf owned = helper.spawnWithNoFreeWill(
                EntityType.WOLF,
                new BlockPos(5, 1, 0));
        LivingEntity providerProtected = helper.spawnWithNoFreeWill(
                EntityType.COW,
                new BlockPos(6, 1, 0));
        LivingEntity tagged = helper.spawnWithNoFreeWill(
                EntityType.SNOW_GOLEM,
                new BlockPos(7, 1, 0));
        Player owner = helper.makeMockPlayer();
        shooter.setOwnerId(owner.getUUID());
        owned.setTame(true);
        owned.setOwnerUUID(UUID.randomUUID());

        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam allyTeam = scoreboard.addPlayerTeam(
                "vggma" + shooter.getId());
        scoreboard.addPlayerToTeam(shooter.getScoreboardName(), allyTeam);
        scoreboard.addPlayerToTeam(teamed.getScoreboardName(), allyTeam);

        var settings = ModConfigs.GUARD_GUNNERS.safety.allies;
        boolean previousOwned = settings.protectOwnedEntities.get();
        boolean previousAllied = settings.protectAlliedEntities.get();
        Set<ResourceLocation> previousAdditional = Set.copyOf(
                settings.additionalProtectedEntities.get());
        Set<ResourceLocation> previousExcluded = Set.copyOf(settings.excludedEntities.get());
        ResourceLocation ravagerId = ResourceLocation.withDefaultNamespace("ravager");
        ResourceLocation villagerId = ResourceLocation.withDefaultNamespace("villager");
        ResourceLocation guardId = GuardEntityType.GUARD.getId();
        ResourceLocation playerId = ResourceLocation.withDefaultNamespace("player");
        try {
            settings.additionalProtectedEntities.accept(Set.of(ravagerId));
            settings.excludedEntities.accept(Set.of());
            helper.assertTrue(
                    GuardAllyProtection.classify(shooter, configured)
                            == ProtectionReason.EXPLICIT_ADDITION,
                    "An explicitly added modpack entity was not protected");

            settings.excludedEntities.accept(Set.of(ravagerId, villagerId));
            helper.assertTrue(
                    GuardAllyProtection.classify(shooter, configured)
                            == ProtectionReason.EXPLICIT_EXCLUSION,
                    "An explicit exclusion did not override an addition");
            helper.assertFalse(GuardFriendlyFireSafety.isProtected(shooter, villager),
                    "An explicit exclusion did not override broad villager protection");

            settings.excludedEntities.accept(Set.of(guardId, playerId));
            helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, otherGuard),
                    "A config exclusion bypassed non-disableable guard protection");
            helper.assertTrue(GuardFriendlyFireSafety.isProtected(shooter, owner),
                    "A config exclusion bypassed non-disableable owner protection");

            settings.additionalProtectedEntities.accept(Set.of());
            settings.excludedEntities.accept(Set.of());
            helper.assertTrue(
                    GuardAllyProtection.classify(shooter, tagged)
                            == ProtectionReason.ENTITY_TYPE_TAG,
                    "The GameTest datapack ally tag did not protect its entity type");

            settings.protectOwnedEntities.accept(true);
            helper.assertTrue(
                    GuardAllyProtection.classify(shooter, owned)
                            == ProtectionReason.OWNED_ENTITY,
                    "An owned mod-compatible entity was not protected");
            settings.protectOwnedEntities.accept(false);
            helper.assertFalse(GuardFriendlyFireSafety.isProtected(shooter, owned),
                    "Owned-entity protection did not honor its config control");

            settings.protectAlliedEntities.accept(true);
            helper.assertTrue(
                    GuardAllyProtection.classify(shooter, teamed)
                            == ProtectionReason.SCOREBOARD_TEAM,
                    "A same-team entity was not protected");
            settings.protectAlliedEntities.accept(false);
            helper.assertFalse(GuardFriendlyFireSafety.isProtected(shooter, teamed),
                    "Allied-entity protection did not honor its config control");

            try (GuardAllyProtection.ProviderRegistration ignored =
                    GuardAllyProtection.registerScopedProvider(
                            ResourceLocation.fromNamespaceAndPath(
                                    VillagerGuardGunners.MODID,
                                    "gametest_cow_allies"),
                            (guard, candidate) -> candidate.getType() == EntityType.COW)) {
                helper.assertTrue(
                        GuardAllyProtection.classify(shooter, providerProtected)
                                == ProtectionReason.COMPATIBILITY_PROVIDER,
                        "A registered compatibility provider did not protect its entity");
            }
            helper.assertFalse(GuardAllyProtection.isProtected(shooter, providerProtected),
                    "A scoped compatibility provider leaked beyond its test fixture");
        } finally {
            settings.protectOwnedEntities.accept(previousOwned);
            settings.protectAlliedEntities.accept(previousAllied);
            settings.additionalProtectedEntities.accept(previousAdditional);
            settings.excludedEntities.accept(previousExcluded);
        }
        helper.succeed();
    }

    @GameTest(batch = "rules_of_engagement", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void rulesOfEngagementHonorModesOverridesAlliesAndLiveGoal(
            GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        Guard guard = spawnGuard(helper);
        Ravager hostile = helper.spawnWithNoFreeWill(
                EntityType.RAVAGER,
                new BlockPos(8, 1, 0));
        LivingEntity neutral = helper.spawnWithNoFreeWill(
                EntityType.PIG,
                new BlockPos(4, 1, 2));
        Villager ally = helper.spawnWithNoFreeWill(
                EntityType.VILLAGER,
                new BlockPos(4, 1, -2));
        configureFixedCombatant(guard);
        configureFixedCombatant(hostile);
        configureFixedCombatant(neutral);
        configureFixedCombatant(ally);

        var settings = ModConfigs.GUARD_GUNNERS.engagement;
        EngagementMode previousMode = settings.mode.get();
        int previousMemory = settings.defensiveMemoryTicks.get();
        Set<ResourceLocation> previousAdditional = Set.copyOf(settings.additionalTargets.get());
        Set<ResourceLocation> previousExcluded = Set.copyOf(settings.excludedEntities.get());
        ResourceLocation hostileId = ResourceLocation.withDefaultNamespace("ravager");
        ResourceLocation neutralId = ResourceLocation.withDefaultNamespace("pig");

        ItemStack gunStack = gun(GLOCK_17, FireMode.SEMI, 6, true);
        IGun gunItem = (IGun) gunStack.getItem();
        guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
        guard.setTarget(hostile);
        guard.setPatrolling(true);
        GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);

        try {
            settings.additionalTargets.accept(Set.of());
            settings.excludedEntities.accept(Set.of());
            settings.mode.accept(EngagementMode.GUARD_TARGETS);
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, neutral)
                            == EngagementReason.GUARD_TARGET,
                    "GUARD_TARGETS did not preserve Guard Villagers' selected neutral target");

            settings.mode.accept(EngagementMode.HOSTILE_MOBS_ONLY);
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.HOSTILE_MOB,
                    "HOSTILE_MOBS_ONLY rejected a Minecraft hostile mob");
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, neutral)
                            == EngagementReason.NOT_HOSTILE,
                    "HOSTILE_MOBS_ONLY accepted a neutral mob");

            settings.additionalTargets.accept(Set.of(neutralId));
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, neutral)
                            == EngagementReason.EXPLICIT_ADDITION,
                    "An explicit ROE addition did not authorize a restricted target");
            settings.additionalTargets.accept(Set.of(hostileId));
            settings.excludedEntities.accept(Set.of(hostileId));
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.EXPLICIT_EXCLUSION,
                    "An ROE exclusion did not override an addition and hostile classification");

            settings.excludedEntities.accept(Set.of());
            settings.additionalTargets.accept(Set.of(
                    hostileId,
                    ResourceLocation.withDefaultNamespace("villager")));
            settings.mode.accept(EngagementMode.HOLD_FIRE);
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.HOLD_FIRE,
                    "An explicit addition bypassed HOLD_FIRE");
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, ally)
                            == EngagementReason.PROTECTED_ALLY,
                    "An explicit target addition bypassed ally protection");

            settings.additionalTargets.accept(Set.of());
            settings.mode.accept(EngagementMode.DEFENSIVE);
            settings.defensiveMemoryTicks.accept(20);
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.NO_RECENT_AGGRESSION,
                    "DEFENSIVE authorized a target without recent aggression");
            guard.setLastHurtByMob(hostile);
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.RECENT_GUARD_ATTACKER,
                    "DEFENSIVE did not authorize the guard's recent attacker");
            guard.setLastHurtByMob(null);
            hostile.setLastHurtMob(ally);
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.RECENT_ALLY_ATTACKER,
                    "DEFENSIVE did not authorize an ally's recent attacker");
            hostile.tickCount += 21;
            helper.assertTrue(
                    GuardRulesOfEngagement.classify(guard, hostile)
                            == EngagementReason.NO_RECENT_AGGRESSION,
                    "DEFENSIVE retained aggression after its configured memory expired");

            settings.mode.accept(EngagementMode.HOSTILE_MOBS_ONLY);
            guard.setTarget(neutral);
            GuardGunCombatGoal releaseGoal = new GuardGunCombatGoal(guard);
            helper.assertTrue(releaseGoal.canUse(),
                    "A denied neutral target did not enter the gun goal safely");
            releaseGoal.start();
            releaseGoal.tick();
            helper.assertTrue(guard.getTarget() == null,
                    "A restricted target was not released to the upstream target selector");
            releaseGoal.stop();

            settings.mode.accept(EngagementMode.HOLD_FIRE);
            guard.setTarget(hostile);
            ItemStack dryGunStack = gun(GLOCK_17, FireMode.SEMI, 0, false);
            guard.setItemSlot(EquipmentSlot.MAINHAND, dryGunStack);
            GuardGunCombatGoal dryGoal = new GuardGunCombatGoal(guard);
            helper.assertTrue(dryGoal.canUse(),
                    "HOLD_FIRE did not acquire a target with a newly equipped dry gun");
            dryGoal.start();
            dryGoal.tick();
            helper.assertTrue(dryGoal.captureDiagnostics().nativeState().dryRetryTicks() > 0L,
                    "The empty gun fixture did not enter its bounded dry retry state");
            helper.assertTrue(dryGoal.canContinueToUse(),
                    "A dry gun yielded HOLD_FIRE ownership to the melee goal");
            settings.mode.accept(EngagementMode.HOSTILE_MOBS_ONLY);
            guard.setTarget(neutral);
            helper.assertTrue(dryGoal.canContinueToUse(),
                    "A denied target could not be retained long enough for safe release");
            dryGoal.tick();
            helper.assertTrue(guard.getTarget() == null,
                    "A denied target was not released while the gun was dry");
            dryGoal.stop();

            settings.mode.accept(EngagementMode.HOLD_FIRE);
            guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack);
            guard.setTarget(hostile);
            helper.assertTrue(goal.canUse(),
                    "HOLD_FIRE yielded the gun goal and exposed melee fallback");
            goal.start();
        } catch (RuntimeException | AssertionError failure) {
            goal.stop();
            restoreEngagementSettings(
                    settings,
                    previousMode,
                    previousMemory,
                    previousAdditional,
                    previousExcluded);
            throw failure;
        }

        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10_000L;
        int startingAmmo = gunItem.getCurrentAmmoCount(gunStack);
        helper.onEachTick(() -> {
            try {
                pinEntity(guard, guard.getX(), guard.getY(), guard.getZ());
                guard.setTarget(hostile);
                guard.getSensing().tick();
                faceTarget(guard, hostile);
                long relativeNow = System.currentTimeMillis() - operator.getDataHolder().baseTimestamp;
                operator.getDataHolder().shootTimestamp = relativeNow - 10_000L;
                operator.getDataHolder().lastShootTimestamp = relativeNow - 10_000L;
                goal.tick();

                if (helper.getTick() < 20L) {
                    GuardGunCombatGoal.Diagnostics held = goal.captureDiagnostics();
                    helper.assertTrue(gunItem.getCurrentAmmoCount(gunStack) == startingAmmo,
                            "HOLD_FIRE allowed the live goal to consume ammunition");
                    helper.assertTrue(held.decision()
                                    == GuardGunCombatGoal.FireDecision.RULES_OF_ENGAGEMENT
                                    && "HOLD".equals(held.movementState()),
                            "HOLD_FIRE did not hold movement and report its firing decision");
                    return;
                }

                settings.mode.accept(EngagementMode.GUARD_TARGETS);
                if (gunItem.getCurrentAmmoCount(gunStack) < startingAmmo) {
                    goal.stop();
                    restoreEngagementSettings(
                            settings,
                            previousMode,
                            previousMemory,
                            previousAdditional,
                            previousExcluded);
                    helper.succeed();
                }
            } catch (RuntimeException | AssertionError failure) {
                goal.stop();
                restoreEngagementSettings(
                        settings,
                        previousMode,
                        previousMemory,
                        previousAdditional,
                        previousExcluded);
                throw failure;
            }
        });
        helper.runAtTickTime(70, () -> {
            GuardGunCombatGoal.Diagnostics stalled = goal.captureDiagnostics();
            goal.stop();
            restoreEngagementSettings(
                    settings,
                    previousMode,
                    previousMemory,
                    previousAdditional,
                    previousExcluded);
            helper.fail("The live goal did not resume after ROE changed: decision="
                    + stalled.decision() + ", reason=" + stalled.engagementReason());
        });
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guardGunHitEventCancelsProtectedHitsOnly(GameTestHelper helper) {
        Guard shooter = spawnGuard(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 0));
        Ravager hostile = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(3, 1, 0));
        Snowball bullet = new Snowball(helper.getLevel(), shooter);

        EntityHurtByGunEvent.Pre protectedHit = gunHit(bullet, villager, null);
        EntityHurtByGunEvent.Pre hostileHit = gunHit(bullet, hostile, shooter);

        helper.assertTrue(MinecraftForge.EVENT_BUS.post(protectedHit),
                "A protected direct gun hit was not canceled");
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(hostileHit),
                "A hostile direct gun hit was incorrectly canceled");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guardProjectileExplosionPreservesAlliesAndConfiguredBlocks(GameTestHelper helper) {
        Guard shooter = spawnGuard(helper);
        Guard otherGuard = helper.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), new BlockPos(1, 1, 0));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 0));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 1, 0));
        Ravager hostile = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(4, 1, 0));
        Snowball bullet = new Snowball(helper.getLevel(), shooter);
        ProjectileExplosion explosion = new ProjectileExplosion(
                helper.getLevel(),
                shooter,
                bullet,
                null,
                null,
                bullet.getX(),
                bullet.getY(),
                bullet.getZ(),
                10.0F,
                3.0F,
                true,
                Explosion.BlockInteraction.DESTROY);
        List<Entity> affected = new ArrayList<>(List.of(shooter, otherGuard, villager, golem, hostile));
        BlockPos affectedBlock = helper.absolutePos(new BlockPos(5, 1, 0));
        boolean originalPreservation = ModConfigs.GUARD_GUNNERS.safety
                .preserveBlocksFromGuardExplosions.get();

        try {
            ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.accept(true);
            explosion.getToBlow().add(affectedBlock);
            MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, affected));

            helper.assertTrue(affected.size() == 1 && affected.get(0) == hostile,
                    "The TaCZ explosion backstop did not preserve exactly the hostile entity");
            helper.assertTrue(explosion.getToBlow().isEmpty(),
                    "A guard-owned TaCZ explosion retained blocks while preservation was enabled");

            ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.accept(false);
            explosion.getToBlow().add(affectedBlock);
            MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(
                    helper.getLevel(), explosion, new ArrayList<>()));
            helper.assertTrue(explosion.getToBlow().contains(affectedBlock),
                    "A guard-owned TaCZ explosion lost blocks while preservation was disabled");

            ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.accept(true);
            Player player = helper.makeMockPlayer();
            Snowball playerBullet = new Snowball(helper.getLevel(), player);
            ProjectileExplosion playerExplosion = new ProjectileExplosion(
                    helper.getLevel(),
                    player,
                    playerBullet,
                    null,
                    null,
                    playerBullet.getX(),
                    playerBullet.getY(),
                    playerBullet.getZ(),
                    10.0F,
                    3.0F,
                    true,
                    Explosion.BlockInteraction.DESTROY);
            playerExplosion.getToBlow().add(affectedBlock);
            MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(
                    helper.getLevel(), playerExplosion, new ArrayList<>()));
            helper.assertTrue(playerExplosion.getToBlow().contains(affectedBlock),
                    "Guard terrain preservation incorrectly changed a player-owned TaCZ explosion");
            helper.succeed();
        } finally {
            ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions
                    .accept(originalPreservation);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void armoryStoresAutomatesLocksPersistsAndDropsSafely(
            GameTestHelper helper) {
        BlockPos localPosition = new BlockPos(2, 1, 2);
        helper.setBlock(
                localPosition,
                com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
        BlockPos absolutePosition = helper.absolutePos(localPosition);
        ArmoryBlockEntity armory = (ArmoryBlockEntity) Objects.requireNonNull(
                helper.getLevel().getBlockEntity(absolutePosition));
        IItemHandler inventory = armory.getInventory();

        IItemHandler firstCapability = null;
        for (Direction direction : Direction.values()) {
            IItemHandler sidedCapability = armory
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, direction)
                    .orElse(null);
            helper.assertTrue(
                    sidedCapability != null
                            && sidedCapability.getSlots() == 9
                            && !(sidedCapability instanceof net.minecraftforge.items.IItemHandlerModifiable)
                            && (firstCapability == null || firstCapability == sidedCapability),
                    "The armory did not expose the same non-modifiable bounded item handler on "
                            + direction.getName());
            firstCapability = sidedCapability;
        }
        ItemStack ammunition = AmmoItemBuilder.create()
                .setId(DEFAULT_AMMO)
                .setCount(24)
                .build();
        ItemStack paper = new ItemStack(Items.PAPER, 5);
        helper.assertTrue(inventory.insertItem(0, ammunition.copy(), false).isEmpty(),
                "The armory rejected real TaCZ loose ammunition");
        helper.assertTrue(inventory.insertItem(1, paper.copy(), false).getCount() == 5
                        && armory.comparatorOutput() > 0,
                "The armory accepted a non-ammunition item or failed to update comparator output");

        Player player = helper.makeMockPlayer();
        player.setPos(
                absolutePosition.getX() + 0.5D,
                absolutePosition.getY() + 0.5D,
                absolutePosition.getZ() + 0.5D);
        player.setShiftKeyDown(true);
        UUID squadId = UUID.randomUUID();
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(
                Objects.requireNonNull(helper.getLevel().getServer()));
        registry.register(
                squadId,
                "Armory Test Squad",
                player.getUUID(),
                helper.getLevel().getGameTime()).orElseThrow();
        ItemStack ledger = new ItemStack(
                com.gamergaming.villagerguardgunners.init.ModItems
                        .SQUAD_COMMAND_LEDGER.get());
        SquadLedgerBindingCodec.writeTo(
                ledger.getOrCreateTag(),
                SquadLedgerBinding.bound(squadId, "Armory Test Squad"));
        player.setItemInHand(InteractionHand.MAIN_HAND, ledger);

        try {
            InteractionResult lockUse = ledger.getItem().useOn(new UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(absolutePosition),
                            Direction.UP,
                            absolutePosition,
                            false)));
            helper.assertTrue(lockUse.consumesAction()
                            && armory.maySupply(squadId)
                            && !armory.maySupply(UUID.randomUUID())
                            && !armory.maySupply(null),
                    "Crouch-use through the bound ledger item did not apply a fail-closed squad restriction");
            helper.assertTrue(
                    ArmoryLockInteractions.interact(player, ledger, armory)
                            == ArmoryLockInteractions.InteractionResult.UNLOCKED
                            && armory.maySupply(null),
                    "Repeating the same ledger interaction did not restore public armory access");
            ArmoryLockInteractions.interact(player, ledger, armory);

            CompoundTag saved = armory.saveWithoutMetadata();
            ArmoryBlockEntity restored = new ArmoryBlockEntity(
                    absolutePosition,
                    armory.getBlockState());
            restored.load(saved);
            ItemStack restoredAmmo = restored.getInventory().getStackInSlot(0);
            ArmorySquadLock restoredLock = restored.getSquadLock();
            helper.assertTrue(restoredAmmo.getCount() == 24
                            && restoredAmmo.getItem() instanceof IAmmo restoredType
                            && DEFAULT_AMMO.equals(restoredType.getAmmoId(restoredAmmo))
                            && restoredLock != null
                            && squadId.equals(restoredLock.squadId())
                            && restored.maySupply(squadId),
                    "Armory inventory or squad-lock identity changed across NBT save/load");

            CompoundTag corruptLock = new CompoundTag();
            corruptLock.putString(ArmorySquadLockCodec.LOCK_TAG, "damaged");
            ArmoryBlockEntity corrupt = new ArmoryBlockEntity(
                    absolutePosition,
                    armory.getBlockState());
            corrupt.load(corruptLock);
            CompoundTag corruptResaved = corrupt.saveWithoutMetadata();
            ArmoryBlockEntity corruptRestored = new ArmoryBlockEntity(
                    absolutePosition,
                    armory.getBlockState());
            corruptRestored.load(corruptResaved);
            helper.assertTrue(
                    corruptRestored.getLockStatus()
                                    == ArmorySquadLockCodec.DecodeStatus.CORRUPT
                            && !corruptRestored.maySupply(null)
                            && corruptResaved.contains(
                                    ArmorySquadLockCodec.LOCK_TAG,
                                    Tag.TAG_STRING),
                    "A damaged armory lock became public after save/load");

            CompoundTag futureLock = new CompoundTag();
            CompoundTag futureRecord = new CompoundTag();
            futureRecord.putInt("Version", 99);
            futureLock.put(ArmorySquadLockCodec.LOCK_TAG, futureRecord);
            ArmoryBlockEntity future = new ArmoryBlockEntity(
                    absolutePosition,
                    armory.getBlockState());
            future.load(futureLock);
            CompoundTag futureResaved = future.saveWithoutMetadata();
            ArmoryBlockEntity futureRestored = new ArmoryBlockEntity(
                    absolutePosition,
                    armory.getBlockState());
            futureRestored.load(futureResaved);
            helper.assertTrue(
                    futureRestored.getLockStatus()
                                    == ArmorySquadLockCodec.DecodeStatus.UNSUPPORTED_VERSION
                            && !futureRestored.maySupply(squadId)
                            && futureResaved.getCompound(
                                            ArmorySquadLockCodec.LOCK_TAG)
                                    .getInt("Version") == 99,
                    "A future armory lock was erased or downgraded after save/load");

            ArmoryMenu menu = new ArmoryMenu(1, player.getInventory(), armory);
            helper.assertTrue(menu.slots.size() == 45 && menu.stillValid(player),
                    "The armory menu did not expose nine storage and thirty-six player slots");

            armory.dropContents();
            helper.assertTrue(armory.comparatorOutput() == 0
                            && countDroppedAmmo(
                                    helper,
                                    Vec3.atCenterOf(absolutePosition),
                                    DEFAULT_AMMO) == 24,
                    "Breaking/ejecting the armory did not preserve its physical ammunition exactly once");
            helper.setBlock(localPosition, Blocks.AIR);
            helper.setBlock(
                    localPosition,
                    com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
            helper.assertTrue(!menu.stillValid(player),
                    "An open armory menu remained attached to a replaced block entity");
            helper.succeed();
        } finally {
            player.setShiftKeyDown(false);
            registry.remove(squadId);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void armoryReservationTransfersOnlyLiveAvailableLooseAmmo(
            GameTestHelper helper) {
        BlockPos armoryLocal = new BlockPos(2, 1, 2);
        helper.setBlock(
                armoryLocal,
                com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
        ArmoryBlockEntity armory = (ArmoryBlockEntity) Objects.requireNonNull(
                helper.getLevel().getBlockEntity(helper.absolutePos(armoryLocal)));
        Guard guard = spawnGuard(helper, new BlockPos(0, 1, 2));
        guard.setNoAi(false);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 0, false));
        GuardAmmoAccess.getAmmoHandler(guard).clear();

        int stackSize = TimelessAPI.getCommonAmmoIndex(DEFAULT_AMMO)
                .orElseThrow()
                .getStackSize();
        ItemStack supplied = AmmoItemBuilder.create()
                .setId(DEFAULT_AMMO)
                .setCount(stackSize)
                .build();
        helper.assertTrue(armory.getInventory().insertItem(0, supplied, false).isEmpty(),
                "The Armory rejected the loose-ammunition transaction fixture");
        long gameTick = helper.getLevel().getGameTime();
        ArmoryResupplyRequest request = armoryRequest(guard, 1L, gameTick);
        ArmorySupplyCandidate.Inspection inspection = ArmorySupplyCandidate.inspect(
                request,
                helper.getLevel().dimension().location(),
                armory,
                guard.getMainHandItem(),
                0,
                gameTick);
        ArmoryReservationBook book = new ArmoryReservationBook();
        helper.assertTrue(inspection.ready()
                        && book.reserve(
                                        request,
                                        inspection.candidate(),
                                        gameTick,
                                        ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS)
                                .reserved(),
                "A live compatible Armory slot could not be reserved");

        ItemStack externallyRemoved = armory.getInventory().extractItem(
                0, stackSize - 10, false);
        ArmoryAmmunitionWithdrawal.WithdrawalResult withdrawal =
                ArmoryAmmunitionWithdrawal.withdraw(
                        book, request.key(), armory, guard);
        helper.assertTrue(withdrawal.transferred()
                        && withdrawal.transferredItems() == 10
                        && withdrawal.transferredUnits() == 10L,
                "Withdrawal did not revalidate the smaller live Armory stack: "
                        + withdrawal.status());
        helper.assertTrue(externallyRemoved.getCount() == stackSize - 10
                        && armory.getInventory().getStackInSlot(0).isEmpty()
                        && countAmmo(GuardAmmoAccess.getAmmoHandler(guard), DEFAULT_AMMO) == 10
                        && externallyRemoved.getCount()
                                + countAmmo(GuardAmmoAccess.getAmmoHandler(guard), DEFAULT_AMMO)
                                == stackSize,
                "The partial Armory transaction lost or duplicated loose ammunition");
        helper.assertTrue(book.activeCount(gameTick) == 0
                        && ArmoryAmmunitionWithdrawal.withdraw(
                                                book,
                                                request.key(),
                                                armory,
                                                guard)
                                        .status()
                                == ArmoryAmmunitionWithdrawal.WithdrawalStatus.NO_RESERVATION,
                "A consumed reservation permitted a second withdrawal");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void armoryReservationMovesFiniteAmmoBoxAsOneItem(
            GameTestHelper helper) {
        BlockPos armoryLocal = new BlockPos(2, 1, 2);
        helper.setBlock(
                armoryLocal,
                com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
        ArmoryBlockEntity armory = (ArmoryBlockEntity) Objects.requireNonNull(
                helper.getLevel().getBlockEntity(helper.absolutePos(armoryLocal)));
        Guard guard = spawnGuard(helper, new BlockPos(0, 1, 2));
        guard.setNoAi(false);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 0, false));
        GuardAmmoHandler reserves = GuardAmmoAccess.getAmmoHandler(guard);
        reserves.clear();
        ItemStack finiteBox = ammoBox(DEFAULT_AMMO, 120, false);
        helper.assertTrue(!finiteBox.isEmpty()
                        && armory.getInventory().insertItem(0, finiteBox, false).isEmpty(),
                "The Armory rejected the finite ammo-box transaction fixture");

        long gameTick = helper.getLevel().getGameTime();
        ArmoryResupplyRequest request = armoryRequest(guard, 2L, gameTick);
        ArmorySupplyCandidate.Inspection inspection = ArmorySupplyCandidate.inspect(
                request,
                helper.getLevel().dimension().location(),
                armory,
                guard.getMainHandItem(),
                0,
                gameTick);
        ArmoryReservationBook book = new ArmoryReservationBook();
        ArmoryReservationBook.ReservationResult reservation = book.reserve(
                request,
                Objects.requireNonNull(inspection.candidate()),
                gameTick,
                ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS);
        ArmoryAmmunitionWithdrawal.WithdrawalResult withdrawal =
                ArmoryAmmunitionWithdrawal.withdraw(
                        book, request.key(), armory, guard);

        ItemStack storedBox = reserves.getStackInSlot(0);
        helper.assertTrue(inspection.ready()
                        && reservation.reserved()
                        && reservation.reservation().reservedItems() == 1
                        && reservation.reservation().reservedUnits() == 120L
                        && withdrawal.transferred()
                        && withdrawal.transferredItems() == 1
                        && withdrawal.transferredUnits() == 120L
                        && armory.getInventory().getStackInSlot(0).isEmpty()
                        && storedBox.getItem() instanceof IAmmoBox storedType
                        && storedType.getAmmoCount(storedBox) == 120,
                "A finite TaCZ ammo box was split, rejected, or changed during withdrawal");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void armoryReservationRejectsReplacedSourceWithoutMovingItems(
            GameTestHelper helper) {
        BlockPos armoryLocal = new BlockPos(2, 1, 2);
        helper.setBlock(
                armoryLocal,
                com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
        ArmoryBlockEntity armory = (ArmoryBlockEntity) Objects.requireNonNull(
                helper.getLevel().getBlockEntity(helper.absolutePos(armoryLocal)));
        Guard guard = spawnGuard(helper, new BlockPos(0, 1, 2));
        guard.setNoAi(false);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 0, false));
        GuardAmmoAccess.getAmmoHandler(guard).clear();
        armory.getInventory().setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(32).build());

        long gameTick = helper.getLevel().getGameTime();
        ArmoryResupplyRequest request = armoryRequest(guard, 3L, gameTick);
        ArmorySupplyCandidate.Inspection inspection = ArmorySupplyCandidate.inspect(
                request,
                helper.getLevel().dimension().location(),
                armory,
                guard.getMainHandItem(),
                0,
                gameTick);
        ArmoryReservationBook book = new ArmoryReservationBook();
        helper.assertTrue(inspection.ready()
                        && book.reserve(request, inspection.candidate(), gameTick, 100).reserved(),
                "The source-replacement fixture could not reserve its initial stack");
        armory.getInventory().setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(NINE_MILLIMETER).setCount(32).build());

        ArmoryAmmunitionWithdrawal.WithdrawalResult withdrawal =
                ArmoryAmmunitionWithdrawal.withdraw(
                        book, request.key(), armory, guard);
        ItemStack replacement = armory.getInventory().getStackInSlot(0);
        helper.assertTrue(
                withdrawal.status()
                                == ArmoryAmmunitionWithdrawal.WithdrawalStatus.SOURCE_CHANGED
                        && replacement.getCount() == 32
                        && replacement.getItem() instanceof IAmmo replacementType
                        && NINE_MILLIMETER.equals(replacementType.getAmmoId(replacement))
                        && isAmmoHandlerEmpty(GuardAmmoAccess.getAmmoHandler(guard))
                        && book.activeCount(gameTick) == 0,
                "A replaced Armory source bypassed fingerprint revalidation or retained its claim");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void armoryWithdrawalYieldsToLiveCombat(GameTestHelper helper) {
        BlockPos armoryLocal = new BlockPos(2, 1, 2);
        helper.setBlock(
                armoryLocal,
                com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
        ArmoryBlockEntity armory = (ArmoryBlockEntity) Objects.requireNonNull(
                helper.getLevel().getBlockEntity(helper.absolutePos(armoryLocal)));
        Guard guard = spawnGuard(helper, new BlockPos(0, 1, 2));
        guard.setNoAi(false);
        guard.setItemSlot(
                EquipmentSlot.MAINHAND,
                gun(AK_47, FireMode.AUTO, 0, false));
        GuardAmmoAccess.getAmmoHandler(guard).clear();
        armory.getInventory().setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(20).build());

        long gameTick = helper.getLevel().getGameTime();
        ArmoryResupplyRequest request = armoryRequest(guard, 4L, gameTick);
        ArmorySupplyCandidate.Inspection inspection = ArmorySupplyCandidate.inspect(
                request,
                helper.getLevel().dimension().location(),
                armory,
                guard.getMainHandItem(),
                0,
                gameTick);
        ArmoryReservationBook book = new ArmoryReservationBook();
        helper.assertTrue(inspection.ready()
                        && book.reserve(request, inspection.candidate(), gameTick, 100).reserved(),
                "The combat-preemption fixture could not reserve its initial stack");
        Zombie target = helper.spawnWithNoFreeWill(
                EntityType.ZOMBIE,
                new BlockPos(0, 1, 4));
        target.setInvulnerable(true);
        guard.setTarget(target);

        ArmoryAmmunitionWithdrawal.WithdrawalResult withdrawal =
                ArmoryAmmunitionWithdrawal.withdraw(
                        book, request.key(), armory, guard);
        helper.assertTrue(
                withdrawal.status()
                                == ArmoryAmmunitionWithdrawal.WithdrawalStatus.LOGISTICS_PREEMPTED
                        && armory.getInventory().getStackInSlot(0).getCount() == 20
                        && isAmmoHandlerEmpty(GuardAmmoAccess.getAmmoHandler(guard))
                        && book.activeCount(gameTick) == 0,
                "Live combat did not preempt Armory withdrawal without moving ammunition");
        helper.succeed();
    }

    private static Guard spawnGuard(GameTestHelper helper) {
        return spawnGuard(helper, new BlockPos(0, 1, 0));
    }

    private static ArmoryResupplyRequest armoryRequest(
            Guard guard,
            long generation,
            long gameTick) {
        ArmoryResupplyNeed.Assessment need = ArmoryResupplyNeed.assess(
                GuardWeaponAmmunition.captureSummary(
                        guard,
                        GuardAmmoAccess.getAmmoHandler(guard)),
                ArmoryResupplyLimits.DEFAULT_LOW_RESERVE_MAGAZINES,
                ArmoryResupplyLimits.DEFAULT_TARGET_RESERVE_MAGAZINES);
        if (!need.requestRequired()) {
            throw new IllegalStateException(
                    "Armory GameTest fixture did not produce an ammunition need: "
                            + need.status());
        }
        return ArmoryResupplyRequest.create(
                generation,
                guard.getUUID(),
                guard.level().dimension().location(),
                guard.blockPosition(),
                GuardSquadAccess.getSquadId(guard),
                need,
                gameTick,
                ArmoryResupplyLimits.DEFAULT_REQUEST_LIFETIME_TICKS);
    }

    private static void restoreEngagementSettings(
            com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig.EngagementSettings settings,
            EngagementMode mode,
            int defensiveMemoryTicks,
            Set<ResourceLocation> additionalTargets,
            Set<ResourceLocation> excludedEntities) {
        settings.mode.accept(mode);
        settings.defensiveMemoryTicks.accept(defensiveMemoryTicks);
        settings.additionalTargets.accept(additionalTargets);
        settings.excludedEntities.accept(excludedEntities);
    }

    private static Guard spawnGuard(GameTestHelper helper, BlockPos position) {
        return helper.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), position);
    }

    private static Guard newNaturalSpawnGuard(GameTestHelper helper, BlockPos position) {
        Guard guard = Objects.requireNonNull(
                GuardEntityType.GUARD.get().create(helper.getLevel()));
        BlockPos absolute = helper.absolutePos(position);
        guard.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        guard.setNoAi(true);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        NaturalGuardSpawnIntegration.markInitialSpawn(
                guard,
                NaturalGuardSpawnSource.STRUCTURE);
        return guard;
    }

    private static void configureFixedCombatant(LivingEntity entity) {
        entity.setInvulnerable(true);
        entity.setNoGravity(true);
    }

    /** Clears wall-clock TaCZ actions that are irrelevant to swap-lifecycle fixtures. */
    private static void forceNativeSwapFixtureIdle(IGunOperator operator) {
        var data = operator.getDataHolder();
        data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
        data.reloadTimestamp = -1L;
        data.isBolting = false;
        data.boltTimestamp = -1L;
        data.meleePrepTickCount = -1;
        data.meleeTimestamp = -1L;
        data.chargeProgress = 0.0F;
        data.drawTimestamp = -1L;
        data.shootTimestamp = -1L;
        data.lastShootTimestamp = -1L;
    }

    private static void pinEntity(
            LivingEntity entity,
            double x,
            double y,
            double z) {
        entity.teleportTo(x, y, z);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static void faceTarget(Guard guard, Entity target) {
        double deltaX = target.getX() - guard.getX();
        double deltaY = target.getEyeY() - guard.getEyeY();
        double deltaZ = target.getZ() - guard.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Mth.atan2(deltaZ, deltaX) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(deltaY, horizontalDistance) * Mth.RAD_TO_DEG);
        guard.setYRot(yaw);
        guard.setYHeadRot(yaw);
        guard.yBodyRot = yaw;
        guard.setXRot(pitch);
    }

    private static ItemStack gun(ResourceLocation id, FireMode fireMode, int ammoCount, boolean ammoInBarrel) {
        return GunItemBuilder.create()
                .setId(id)
                .setFireMode(fireMode)
                .setAmmoCount(ammoCount)
                .setAmmoInBarrel(ammoInBarrel)
                .build();
    }

    private static ItemStack ammoBox(ResourceLocation ammoId, int ammoCount, boolean allTypeCreative) {
        ItemStack stack = new ItemStack(ModItems.AMMO_BOX.get());
        if (!(stack.getItem() instanceof IAmmoBox ammoBox)) {
            return ItemStack.EMPTY;
        }
        if (allTypeCreative) {
            return ammoBox.setCreative(stack, true);
        }
        if (ammoId != null) {
            ammoBox.setAmmoId(stack, ammoId);
            ammoBox.setAmmoCount(stack, ammoCount);
        }
        return stack;
    }

    private static ItemEntity droppedAmmo(
            GameTestHelper helper,
            Vec3 position,
            ResourceLocation ammoId,
            int count) {
        ItemStack stack = AmmoItemBuilder.create()
                .setId(ammoId)
                .setCount(count)
                .build();
        return new ItemEntity(
                helper.getLevel(),
                position.x,
                position.y,
                position.z,
                stack);
    }

    private static EntityHurtByGunEvent.Pre gunHit(Entity bullet, Entity target, Guard attacker) {
        return new EntityHurtByGunEvent.Pre(
                bullet,
                target,
                attacker,
                GLOCK_17,
                GLOCK_17,
                5.0F,
                null,
                false,
                1.0F,
                LogicalSide.SERVER);
    }

    private static CompoundTag saveInLegacySlot(ItemStack stack, int slot) {
        CompoundTag serialized = stack.save(new CompoundTag());
        serialized.putByte("Slot", (byte) slot);
        return serialized;
    }

    private static int countAmmo(GuardAmmoHandler handler, ResourceLocation ammoId) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammoId.equals(ammo.getAmmoId(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean isAmmoHandlerEmpty(GuardAmmoHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean stacksMatch(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    private static void prepareConversionInventory(Guard guard, int storedCount, int overflowCount) {
        CompoundTag saved = new CompoundTag();
        ListTag overflow = new ListTag();
        overflow.add(AmmoItemBuilder.create()
                .setId(DEFAULT_AMMO)
                .setCount(overflowCount)
                .build()
                .save(new CompoundTag()));
        saved.put(MIGRATION_OVERFLOW_TAG, overflow);
        guard.readAdditionalSaveData(saved);
        GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(
                0,
                AmmoItemBuilder.create().setId(DEFAULT_AMMO).setCount(storedCount).build());
        GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(
                GuardSidearmStorage.SIDEARM_SLOT,
                gun(GLOCK_17, FireMode.SEMI, 7, true));
    }

    private static int countDroppedAmmo(
            GameTestHelper helper,
            net.minecraft.world.phys.Vec3 center,
            ResourceLocation ammoId) {
        return helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(center, center).inflate(2.0D))
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.getItem() instanceof IAmmo ammo && ammoId.equals(ammo.getAmmoId(stack)))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static int countDroppedGuns(
            GameTestHelper helper,
            Vec3 center,
            ResourceLocation gunId) {
        return (int) helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(center, center).inflate(2.0D))
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> gunId.equals(gunId(stack)))
                .count();
    }

    private static ResourceLocation gunId(ItemStack stack) {
        return stack.getItem() instanceof IGun gun
                ? gun.getGunId(stack)
                : ResourceLocation.fromNamespaceAndPath("minecraft", "empty");
    }

    private record NaturalLoadoutConfigSnapshot(
            Difficulty difficulty,
            boolean addonEnabled,
            boolean naturalLoadoutsEnabled,
            Set<NaturalGuardSpawnSource> enabledSources,
            boolean replaceCrossbowsOnly,
            double nearbyCapRadius,
            double normalUpgradeChance,
            int normalMaximumNearby,
            ResourceLocation normalPoolId) {
        private static NaturalLoadoutConfigSnapshot capture(GameTestHelper helper) {
            var config = ModConfigs.GUARD_GUNNERS;
            return new NaturalLoadoutConfigSnapshot(
                    helper.getLevel().getServer().getWorldData().getDifficulty(),
                    config.enabled.get(),
                    config.naturalLoadouts.enabled.get(),
                    Set.copyOf(config.naturalLoadouts.enabledSpawnSources.get()),
                    config.naturalLoadouts.replaceCrossbowsOnly.get(),
                    config.naturalLoadouts.nearbyCapRadius.get(),
                    config.naturalLoadouts.normal.upgradeChance.get(),
                    config.naturalLoadouts.normal.maximumNearby.get(),
                    config.naturalLoadouts.normal.poolId.get());
        }

        private void restore(GameTestHelper helper) {
            helper.getLevel().getServer().setDifficulty(difficulty, true);
            var config = ModConfigs.GUARD_GUNNERS;
            config.enabled.accept(addonEnabled);
            config.naturalLoadouts.enabled.accept(naturalLoadoutsEnabled);
            config.naturalLoadouts.enabledSpawnSources.accept(enabledSources);
            config.naturalLoadouts.replaceCrossbowsOnly.accept(replaceCrossbowsOnly);
            config.naturalLoadouts.nearbyCapRadius.accept(nearbyCapRadius);
            config.naturalLoadouts.normal.upgradeChance.accept(normalUpgradeChance);
            config.naturalLoadouts.normal.maximumNearby.accept(normalMaximumNearby);
            config.naturalLoadouts.normal.poolId.accept(normalPoolId);
        }
    }

    private static final class SimulationOnlyDestination extends ItemStackHandler {
        private SimulationOnlyDestination() {
            super(1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return simulate ? ItemStack.EMPTY : stack.copy();
        }
    }

    private static final class MutatingSimulationDestination extends ItemStackHandler {
        private MutatingSimulationDestination() {
            super(1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate) {
                setStackInSlot(slot, stack.copy());
                return ItemStack.EMPTY;
            }
            return super.insertItem(slot, stack, false);
        }
    }
}
