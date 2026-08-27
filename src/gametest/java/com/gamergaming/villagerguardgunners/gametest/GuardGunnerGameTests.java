package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.util.block.ProjectileExplosion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;
import tallestegg.guardvillagers.entities.ai.goals.RaiseShieldGoal;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
/** Development-only Forge integration tests; excluded from the production JAR. */
public final class GuardGunnerGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final ResourceLocation GLOCK_17 = ResourceLocation.fromNamespaceAndPath("tacz", "glock_17");
    private static final ResourceLocation AK_47 = ResourceLocation.fromNamespaceAndPath("tacz", "ak47");
    private static final ResourceLocation M16_A4 = ResourceLocation.fromNamespaceAndPath("tacz", "m16a4");
    private static final ResourceLocation M95 = ResourceLocation.fromNamespaceAndPath("tacz", "m95");
    private static final ResourceLocation NINE_MILLIMETER = ResourceLocation.fromNamespaceAndPath("tacz", "9mm");
    private static final ResourceLocation DEFAULT_AMMO = ResourceLocation.fromNamespaceAndPath("tacz", "762x39");
    private static final String AMMO_INVENTORY_TAG = "villagerguardgunners:ammo_inventory";
    private static final String MIGRATION_OVERFLOW_TAG = "villagerguardgunners:ammo_overflow";

    private GuardGunnerGameTests() {
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
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 1, true));

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

        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        helper.assertTrue(saved.contains(AMMO_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "Guard save omitted the isolated add-on ammo inventory");

        Guard restored = Objects.requireNonNull(GuardEntityType.GUARD.get().create(helper.getLevel()));
        restored.readAdditionalSaveData(saved);
        ItemStack restoredAmmo = GuardAmmoAccess.getAmmoHandler(restored).getStackInSlot(0);

        helper.assertTrue(restoredAmmo.getCount() == 24,
                "Guard ammo count changed across save/load");
        helper.assertTrue(restoredAmmo.getItem() instanceof IAmmo ammoItem
                        && DEFAULT_AMMO.equals(ammoItem.getAmmoId(restoredAmmo)),
                "Guard ammo identity changed across save/load");
        helper.succeed();
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
            int zombieDrops = countDroppedAmmo(helper, zombieGuard.position(), DEFAULT_AMMO);
            helper.assertTrue(zombieDrops == 14,
                    "Zombie conversion expected 14 dropped rounds but found " + zombieDrops);

            Guard lightningGuard = helper.spawnWithNoFreeWill(
                    GuardEntityType.GUARD.get(),
                    new BlockPos(8, 1, 0));
            prepareConversionInventory(lightningGuard, 7, 5);
            LightningBolt lightning = Objects.requireNonNull(
                    EntityType.LIGHTNING_BOLT.create(helper.getLevel()));
            lightningGuard.thunderHit(helper.getLevel(), lightning);

            helper.assertTrue(lightningGuard.isRemoved(),
                    "Lightning conversion did not remove the original guard");
            int lightningDrops = countDroppedAmmo(helper, lightningGuard.position(), DEFAULT_AMMO);
            helper.assertTrue(lightningDrops == 12,
                    "Lightning conversion expected 12 dropped rounds but found " + lightningDrops);
            helper.succeed();
        } finally {
            helper.getLevel().getServer().setDifficulty(originalDifficulty, true);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void suppressedGuardBurstRoundsAreCanceledBeforeNativeFire(GameTestHelper helper) {
        Guard guard = spawnGuard(helper);
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
        GuardBurstSafetyAccess.clearSafetyCheck(guard);
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
        GuardGunnerContainer menu = new GuardGunnerContainer(
                1,
                player.getInventory(),
                guard.guardInventory,
                GuardAmmoAccess.getAmmoHandler(guard),
                guard);
        helper.assertTrue(menu.slots.size() == GuardGunnerContainer.ADDON_SLOT_COUNT + 36,
                "Guard gunner menu has an incorrect slot layout");
        helper.succeed();
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
        long originalDrawTimestamp = operator.getDataHolder().drawTimestamp;

        helper.onEachTick(() -> controller.tick(target, true, () -> true));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(gunItem.getCurrentAmmoCount(gunStack) < 12,
                    "The native semi-automatic controller did not consume TaCZ magazine state");
            helper.assertTrue(operator.getDataHolder().shootTimestamp >= 0L,
                    "TaCZ did not accept a native server-side trigger");
            helper.assertTrue(operator.getDataHolder().currentGunItem.get() == gunStack,
                    "TaCZ's operator is no longer bound to the held gun stack");
            helper.assertTrue(operator.getDataHolder().drawTimestamp == originalDrawTimestamp,
                    "The unchanged gun was redrawn and rebuilt during firing");
            helper.succeed();
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
                    + ", padding=" + stalled.safetyPadding()
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

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void permanentlyDryGunYieldsToTheUpstreamMeleeGoal(GameTestHelper helper) {
        Guard guard = helper.spawn(GuardEntityType.GUARD.get(), new BlockPos(0, 1, 0));
        Ravager target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(4, 1, 0));
        target.setInvulnerable(true);
        guard.setTarget(target);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun(GLOCK_17, FireMode.SEMI, 0, false));
        helper.assertTrue(guard.goalSelector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal() instanceof MeleeAttackGoal),
                "Guard Villagers' lower-priority melee goal is unavailable");
        helper.succeedWhen(() -> {
            helper.assertTrue(guard.goalSelector.getRunningGoals()
                            .anyMatch(wrapped -> wrapped.getGoal() instanceof MeleeAttackGoal),
                    "A gun with no compatible ammunition did not yield to Guard Villagers' melee goal");
            helper.assertFalse(IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming,
                    "A permanently dry gun remained aimed after yielding to melee");
        });
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

        ItemStack gun = GunItemBuilder.create()
                .setId(GLOCK_17)
                .setFireMode(FireMode.SEMI)
                .setAmmoCount(1)
                .setAmmoInBarrel(true)
                .build();
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
        helper.assertTrue(shieldGoal.canUse(),
                "A non-aiming gun unexpectedly disabled shield/melee fallback");

        NativeGunController controller = new NativeGunController(guard);
        controller.startAiming();
        helper.assertFalse(shieldGoal.canUse(),
                "Guard Villagers' priority-zero shield goal still preempted an aimed TaCZ gun");
        helper.assertFalse(shieldGoal.canContinueToUse(),
                "An active shield goal did not yield after the guard aimed a TaCZ gun");
        controller.stopAiming();
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
    public static void guardProjectileExplosionRemovesProtectedEntitiesOnly(GameTestHelper helper) {
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
                Explosion.BlockInteraction.KEEP);
        List<Entity> affected = new ArrayList<>(List.of(shooter, otherGuard, villager, golem, hostile));

        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, affected));

        helper.assertTrue(affected.size() == 1 && affected.get(0) == hostile,
                "The TaCZ explosion backstop did not preserve exactly the hostile entity");
        helper.succeed();
    }

    private static Guard spawnGuard(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), new BlockPos(0, 1, 0));
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
}
