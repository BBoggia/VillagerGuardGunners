package com.gamergaming.villagerguardgunners.gametest.combat;

import java.util.List;
import java.util.UUID;
import com.gamergaming.villagerguardgunners.ai.*;
import com.gamergaming.villagerguardgunners.ai.tactics.*;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.squad.*;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.gametest.*;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class CombatHandoffGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";
    private static final java.util.List<net.minecraft.world.entity.Entity> ACTORS = new java.util.ArrayList<>();

    @GameTest(batch = "combat_handoff_commands", templateNamespace = "minecraft", template = ARENA)
    public static void commandReplacementWinsInEveryReadinessAndSharesBudget(GameTestHelper h) {
        Guard guard = guard(h);
        var enemy = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 1, 8)); ACTORS.add(enemy);
        var tactics = new GuardTacticalController(guard); tactics.observe(enemy, true);
        guard.setHealth(guard.getMaxHealth() * .2f);
        for (var readiness : WeaponReadiness.values()) {
            for (var type : List.of(SquadCommandType.HOLD, SquadCommandType.RALLY, SquadCommandType.FALL_BACK, SquadCommandType.DEFEND)) {
                var command = command(guard, type);
                GuardSquadCommandAccess.setCommand(guard, command);
                var decision = tactics.decide(enemy, true, true, readiness, command, false, 4);
                h.assertTrue(decision.reason() == CombatIntentPolicy.Reason.MOVEMENT_ORDER,
                        type + " did not interrupt " + readiness + " withdrawal");
            }
            long tick = guard.level().getGameTime();
            var focus = SquadCommand.focusTarget(UUID.randomUUID(), tick, tick + 600,
                    guard.level().dimension().location(), enemy.getUUID());
            var focused = tactics.decide(enemy, true, true, readiness, focus, false, 4);
            h.assertTrue(focused.intent() == CombatIntentPolicy.Intent.WITHDRAW,
                    "Focus target overrode injury withdrawal for " + readiness);
        }
        var first = new SquadCommandMovementController(guard);
        var second = new SquadCommandMovementController(guard);
        long now = guard.level().getGameTime();
        h.assertTrue(first.takePathSlot(now), "Initial path slot unavailable");
        GuardSquadCommandAccess.setCommand(guard, command(guard, SquadCommandType.HOLD)); second.synchronize(false);
        h.assertFalse(second.takePathSlot(now), "Goal handoff or command replacement reset shared path budget");
        GuardSquadCommandAccess.setCommand(guard, null); first.synchronize(false);
        h.assertFalse(first.takePathSlot(now), "Command revocation reset path budget");
        h.succeed();
    }

    @GameTest(batch = "combat_handoff_gap", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void recentSightRetainsCombatUntilCommandsCanResume(GameTestHelper h) {
        Guard guard = guard(h); var goal = goal(guard);
        var enemy = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 1, 8)); ACTORS.add(enemy);
        // A direct sight sample must survive independently of the slower nearby scan.
        goal.tacticalController().decide(enemy, true, true, WeaponReadiness.READY, null, false, 4);
        guard.setTarget(null);
        GuardSquadCommandAccess.setCommand(guard, command(guard, SquadCommandType.HOLD));
        var commandGoal = new SquadCommandMovementGoal(guard);
        h.assertFalse(commandGoal.canUse(), "Positional goal stole a live target gap");
        h.assertFalse(goal.equipmentServiceSafe(), "Equipment service ignored continuing combat");
        h.assertFalse(goal.coverageMovementSafe(), "Duty movement ignored continuing combat");
        h.runAtTickTime(45, () -> {
            h.assertFalse(goal.tacticalController().hasContinuation(), "Sight evidence never expired");
            h.assertTrue(commandGoal.canUse(), "Command could not resume after actual combat exit");
            var data = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).getDataHolder();
            data.drawTimestamp = data.shootTimestamp = -1; data.meleePrepTickCount = -1;
            com.tacz.guns.entity.sync.ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(guard, 0L);
            com.tacz.guns.entity.sync.ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(guard, 0L);
            h.assertTrue(goal.equipmentServiceSafe() && goal.coverageMovementSafe(),
                    "Settled combat kept service or automatic duty locked out");
            h.succeed();
        });
    }

    @GameTest(batch = "combat_handoff_medical", templateNamespace = "minecraft", template = ARENA)
    public static void downingInterruptsCombatAndBlocksCommandTakeover(GameTestHelper h) {
        Guard guard = guard(h); var goal = goal(guard);
        var enemy = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 1, 8)); ACTORS.add(enemy);
        guard.setTarget(enemy); goal.start(); goal.tacticalController().observe(enemy, true);
        GuardSquadCommandAccess.setCommand(guard, command(guard, SquadCommandType.RALLY));
        GuardLifeAccess.get(guard).down(guard.damageSources().generic(), guard.level().getGameTime());
        goal.tick();
        h.assertFalse(goal.canContinueToUse() || goal.retainsCombatOwnership(), "Downed guard retained combat ownership");
        h.assertTrue(goal.tacticalController().tracker().observations().isEmpty()
                && !goal.tacticalNavigation().isMoving(), "Medical preemption retained tactical work");
        h.assertFalse(new SquadCommandMovementGoal(guard).canUse(), "Command started on a casualty");
        GuardLifeAccess.get(guard).load(new net.minecraft.nbt.CompoundTag());
        guard.setTarget(null);
        h.assertTrue(new SquadCommandMovementGoal(guard).canUse(), "Recovered guard could not resume its command");
        h.succeed();
    }

    @GameTest(batch = "combat_handoff_lifecycle", templateNamespace = "minecraft", template = ARENA)
    public static void disableAndCapabilityInvalidationClearTransientCombat(GameTestHelper h) throws Exception {
        Guard guard = guard(h); var goal = goal(guard);
        var enemy = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 1, 8)); ACTORS.add(enemy);
        guard.setTarget(enemy); goal.start(); goal.tacticalController().observe(enemy, true);
        boolean enabled = ModConfigs.GUARD_GUNNERS.enabled.get();
        try {
            ModConfigs.GUARD_GUNNERS.enabled.accept(false); goal.tick();
            h.assertFalse(goal.tacticalController().hasContinuation() || goal.tacticalNavigation().isMoving(),
                    "Disablement left a tactical continuation");
        } finally { ModConfigs.GUARD_GUNNERS.enabled.accept(enabled); }
        goal.start(); goal.tacticalController().observe(enemy, true);
        goal.tick();
        guard.invalidateCaps();
        var cachedTarget = GuardGunCombatGoal.class.getDeclaredField("lastEngagementTarget");
        cachedTarget.setAccessible(true);
        h.assertTrue(cachedTarget.get(goal) == null, "Lifecycle cleanup retained the engagement cache target");
        h.assertTrue(goal.tacticalController().tracker().observations().isEmpty()
                && !goal.retainsCombatOwnership(), "Capability invalidation retained combat references");
        var saved = new net.minecraft.nbt.CompoundTag(); guard.saveWithoutId(saved);
        Guard restored = guard(h); restored.load(saved);
        h.assertFalse(goal(restored).tacticalController().hasContinuation(), "Reload restored transient combat memory");
        h.succeed();
    }

    @GameTest(batch = "combat_handoff_dimension", templateNamespace = "minecraft", template = ARENA)
    public static void dimensionTransferDropsOldCombatAndCommandContext(GameTestHelper h) {
        Guard guard = guard(h); var goal = goal(guard);
        var enemy = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 1, 8)); ACTORS.add(enemy);
        guard.setTarget(enemy); goal.start(); goal.tacticalController().observe(enemy, true);
        GuardSquadCommandAccess.setCommand(guard, command(guard, SquadCommandType.HOLD));
        var destination = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(destination != null, "Dimension fixture has no Nether");
        var moved = guard.changeDimension(destination, new net.minecraftforge.common.util.ITeleporter() {
            @Override public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity entity,
                    net.minecraft.server.level.ServerLevel current, net.minecraft.server.level.ServerLevel next,
                    float yaw, java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> reposition) {
                var result = reposition.apply(false); result.setPos(0.5, 120, 0.5); return result;
            }
        });
        h.assertTrue(moved instanceof Guard, "Guard did not transfer dimensions"); ACTORS.add(moved);
        Guard transferred = (Guard) moved;
        h.assertTrue(!goal.retainsCombatOwnership() && goal.tacticalController().tracker().observations().isEmpty(),
                "Old dimension retained a combat owner or observations");
        h.assertFalse(goal(transferred).tacticalController().hasContinuation(), "Transfer copied tactical observations");
        h.assertTrue(SquadCommandRuntime.activeCommand(transferred) == null, "Old dimension command remained active");
        h.succeed();
    }

    @GameTest(batch = "combat_handoff_native", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 40)
    public static void nativeBusyDefersRestorationAndScriptFaultSuspendsActions(GameTestHelper h) throws Exception {
        Guard guard = guard(h);
        var storage = com.gamergaming.villagerguardgunners.sidearm.GuardSidearmGameTestAccess.getStorage(guard);
        storage.setStackInSlot(0, GunItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz", "glock_17"))
                .setFireMode(FireMode.SEMI).setAmmoCount(7).setAmmoInBarrel(true).build());
        com.gamergaming.villagerguardgunners.sidearm.GuardSidearmTransactions.activate(guard,
                com.gamergaming.villagerguardgunners.sidearm.SidearmActivationReason.PRIMARY_DRY,
                guard.level().getGameTime(), UUID.randomUUID(), true);
        var nativeController = new com.gamergaming.villagerguardgunners.combat.NativeGunController(guard);
        nativeController.refreshAfterSidearmTransaction();
        var runtime = new com.gamergaming.villagerguardgunners.sidearm.GuardSidearmRuntimeController(guard);
        Guard unarmed = guard(h);
        unarmed.setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        var unarmedNative = new com.gamergaming.villagerguardgunners.combat.NativeGunController(unarmed);
        var pending = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(unarmed).getDataHolder();
        pending.isBolting = true;
        h.assertFalse(unarmedNative.isCoverageMovementSafe() || unarmedNative.isEquipmentServiceSafe(),
                "Removing a gun bypassed an unfinished native action");
        pending.isBolting = false;
        h.assertTrue(unarmedNative.isCoverageMovementSafe() && unarmedNative.isEquipmentServiceSafe(),
                "Settled unarmed guard could not resume duty or equipment service");
        var original = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard);
        int[] phase = {0};
        var proxy = java.lang.reflect.Proxy.newProxyInstance(original.getClass().getClassLoader(),
                new Class<?>[]{com.tacz.guns.api.entity.IGunOperator.class}, (self, method, args) -> {
                    if (phase[0] == 0 && method.getName().equals("getSynDrawCoolDown")) return 500L;
                    if (phase[0] == 2 && method.getName().equals("aim")) throw new IllegalStateException("fixture native script fault");
                    try { return method.invoke(original, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        var field = nativeController.getClass().getDeclaredField("operator"); field.setAccessible(true); field.set(nativeController, proxy);
        runtime.evaluate(null, nativeController, false, false);
        h.assertTrue(GuardSidearmAccess.getState(guard) == com.gamergaming.villagerguardgunners.sidearm.SidearmState.SIDEARM_ACTIVE,
                "Busy native action allowed an unsafe stack swap");
        h.runAtTickTime(3, () -> {
            phase[0] = 1;
            var data = original.getDataHolder(); data.drawTimestamp = -1; data.shootTimestamp = -10_000;
            data.baseTimestamp = System.currentTimeMillis() - 10_000; data.meleePrepTickCount = -1;
            com.tacz.guns.entity.sync.ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(guard, 0L);
            com.tacz.guns.entity.sync.ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(guard, 0L);
            runtime.evaluate(null, nativeController, false, false);
            h.assertTrue(GuardSidearmAccess.getState(guard) == com.gamergaming.villagerguardgunners.sidearm.SidearmState.PRIMARY_ACTIVE,
                    "Restoration never resumed after native action settled");
            phase[0] = 2; nativeController.startAiming(); nativeController.stopAiming();
            h.assertTrue(nativeController.captureReadiness() == WeaponReadiness.SUSPENDED,
                    "Native exception authorized further actions");
            h.succeed();
        });
    }

    private static Guard guard(GameTestHelper h) {
        Guard guard = h.spawn(GuardEntityType.GUARD.get(), new BlockPos(8, 1, 8));
        GuardSquadAccess.setMembership(guard, SquadMembership.assigned(UUID.randomUUID()));
        guard.setNoAi(true); guard.setNoGravity(true); guard.setInvulnerable(true); ACTORS.add(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, GunItemBuilder.create()
                .setId(ResourceLocation.fromNamespaceAndPath("tacz", "ak47"))
                .setFireMode(FireMode.SEMI).setAmmoCount(0).setAmmoInBarrel(false).build());
        return guard;
    }
    private static GuardGunCombatGoal goal(Guard guard) {
        return guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GuardGunCombatGoal.class::isInstance).map(GuardGunCombatGoal.class::cast).findFirst().orElseThrow();
    }
    private static SquadCommand command(Guard guard, SquadCommandType type) {
        long now = guard.level().getGameTime();
        return SquadCommand.positional(type, UUID.randomUUID(), now, now + 600,
                guard.level().dimension().location(), guard.blockPosition().offset(5, 0, 0), type == SquadCommandType.DEFEND ? 2 : 0);
    }
    @AfterBatch(batch = "combat_handoff_dimension") public static void afterDimension(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "combat_handoff_native") public static void afterNative(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    private static void cleanup() { ACTORS.forEach(net.minecraft.world.entity.Entity::discard); ACTORS.clear(); }
    @AfterBatch(batch = "combat_handoff_commands") public static void afterCommands(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "combat_handoff_gap") public static void afterGap(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "combat_handoff_medical") public static void afterMedical(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "combat_handoff_lifecycle") public static void afterLifecycle(net.minecraft.server.level.ServerLevel level) { cleanup(); }
}
