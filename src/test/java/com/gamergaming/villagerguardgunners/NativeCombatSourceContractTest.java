package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NativeCombatSourceContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void combatControllerDelegatesEveryGunActionToTaCZ() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");

        assertAll(
                () -> assertTrue(controller.contains("IGunOperator.fromLivingEntity")),
                () -> assertTrue(controller.contains("operator.shoot(")),
                () -> assertTrue(controller.contains("operator.shoot(pitchSupplier, yawSupplier, timestamp)")),
                () -> assertFalse(controller.contains("operator.shoot(pitchSupplier, yawSupplier);")),
                () -> assertTrue(controller.contains("operator.reload()")),
                () -> assertTrue(controller.contains("operator.bolt()")),
                () -> assertTrue(controller.contains("operator.draw(")),
                () -> assertTrue(controller.contains("operator.aim(")),
                () -> assertFalse(controller.contains("addFreshEntity")),
                () -> assertFalse(controller.contains("SoundManager")));
    }

    @Test
    void phaseThreeHandlesEveryTaCZShootResultAndBoundsAutomaticFireWork() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String resultSwitch = controller.substring(controller.indexOf("private void handleShootResult"));
        List<String> shootResults = List.of(
                "SUCCESS",
                "UNKNOWN_FAIL",
                "COOL_DOWN",
                "NO_AMMO",
                "NOT_DRAW",
                "NOT_GUN",
                "ID_NOT_EXIST",
                "NEED_BOLT",
                "IS_RELOADING",
                "IS_DRAWING",
                "IS_BOLTING",
                "IS_MELEE",
                "IS_SPRINTING",
                "NETWORK_FAIL",
                "FORGE_EVENT_CANCEL",
                "OVERHEATED");

        assertAll(
                () -> shootResults.forEach(result -> assertTrue(
                        resultSwitch.contains(result),
                        () -> "Missing explicit state transition for ShootResult." + result)),
                () -> assertTrue(controller.contains("weaponState.markDry(")),
                () -> assertTrue(controller.contains("weaponState.suspend()")),
                () -> assertTrue(controller.contains("NativeFireCadence.MAX_SHOTS_PER_TICK")),
                () -> assertFalse(controller.contains("ScheduledExecutor")),
                () -> assertFalse(controller.contains("new Thread(")));
    }

    @Test
    void dryGunRetainsOwnershipAndNativeReloadSafety() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(goal.contains("gunController.canUseGunGoal(guard.getMainHandItem())")),
                () -> assertTrue(controller.contains("operator.getSynShootCoolDown() > 0L")),
                () -> assertTrue(controller.contains("gunItem.canReload(guard, gunStack)")),
                () -> assertTrue(controller.contains("GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard)")),
                () -> assertTrue(controller.contains(
                        "failedReloadHasUsableReserve(nativeCanReload, ammunition)")),
                () -> assertTrue(controller.contains("ammunition.hasCompatibleReserve()")),
                () -> assertTrue(controller.contains("ammunition.reserveScanComplete()")));
    }

    @Test
    void safetyTracksNativeBurstAndFailsClosedForUnboundedEnvelopes() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String safety = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireSafety.java");
        String events = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireEvents.java");
        String explosionResolver = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/TaCZExplosionResolver.java");
        String activeBurstBranch = controller.substring(
                controller.indexOf("private void updateNativeBurstSafety("),
                controller.indexOf("public float getProjectileSafetyPadding("));

        assertAll(
                () -> assertTrue(controller.contains("isNativeBurstActive(nowTimestamp)")),
                () -> assertTrue(controller.contains(
                        "boolean burstSafetyArmed = hasNativeBurstSafetyState()")),
                () -> assertTrue(controller.contains("GuardBurstSafetyAccess.setSuppressed(guard, true)")),
                () -> assertFalse(activeBurstBranch.contains("safeToFire.getAsBoolean()")),
                () -> assertFalse(controller.contains("suppliedAimPitch")),
                () -> assertFalse(controller.contains("-90.0F")),
                () -> assertTrue(events.contains("onGuardGunFire(GunFireEvent event)")),
                () -> assertTrue(events.contains("GuardBurstSafetyAccess.shouldSuppress(guard)")),
                () -> assertTrue(controller.contains("GuardBurstSafetyAccess.setSafetyCheck(guard, burstSafetyCheck)")),
                () -> assertTrue(controller.contains("resolveMaximumInaccuracy()")),
                () -> assertTrue(controller.contains("resolveCurrentExplosionTraits()")),
                () -> assertTrue(explosionResolver.contains("DEFAULT_EXPLOSION_DATA")),
                () -> assertTrue(controller.contains("MAX_SAFETY_PADDING")),
                () -> assertTrue(safety.contains(
                        "validPaddings(muzzlePadding, projectilePadding, blastPadding)")));
    }

    @Test
    void phaseFourBoundsAndStaggersCombatAiWork() throws IOException {
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String tactics = read("src/main/java/com/gamergaming/villagerguardgunners/ai/tactics/GuardTacticalController.java");
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String shieldMixin = read("src/main/java/com/gamergaming/villagerguardgunners/mixin/RaiseShieldGoalMixin.java");
        String mixinConfig = read("src/main/resources/mixins.villagerguardgunners.json");

        assertAll(
                () -> assertTrue(tactics.contains(".performance.movementDecisionIntervalTicks.get()")),
                () -> assertTrue(read("src/main/java/com/gamergaming/villagerguardgunners/ai/SquadCommandMovementController.java").contains("config.performance.pathRecalculationIntervalTicks.get()")),
                () -> assertTrue(goal.contains("GuardCombatMovement.firstDueTick")),
                () -> assertTrue(goal.contains("commandMovement.moveTowardCombatTarget(")),
                () -> assertTrue(goal.contains("config.movement.approachSpeed.get()")),
                () -> assertTrue(goal.contains("tacticalNavigation.tick(")),
                () -> assertTrue(read("src/main/java/com/gamergaming/villagerguardgunners/ai/tactics/TacticalNavigationController.java").contains("performance.repositionProgressCheckTicks.get()")),
                () -> assertTrue(read("src/main/java/com/gamergaming/villagerguardgunners/ai/tactics/TacticalNavigationController.java").contains("remainingDistance(")),
                () -> assertTrue(mixinConfig.contains("TacticalPathNavigationMixin")),
                () -> assertTrue(goal.contains("gunController.getPreferredApproachDistance()")),
                () -> assertTrue(goal.contains("gunController.getRetreatDistance()")),
                () -> assertTrue(goal.contains("gunController.getMaximumFiringRange()")),
                () -> assertTrue(goal.contains(
                        "gunController.canFireAtDistanceSquared(distanceSquared)")),
                () -> assertTrue(goal.contains("gunController.hasCurrentWeaponData(guard.getMainHandItem())")),
                () -> assertFalse(goal.contains("createPath(")),
                () -> assertFalse(goal.contains("DefaultRandomPos")),
                () -> assertTrue(controller.contains("GunProperties.EFFECTIVE_RANGE")),
                () -> assertTrue(goal.contains("guard.getUsedItemHand() == InteractionHand.OFF_HAND")),
                () -> assertTrue(goal.contains("guard.stopUsingItem()")),
                () -> assertTrue(shieldMixin.contains("instanceof IGun")),
                () -> assertTrue(shieldMixin.contains("getDataHolder().isAiming")),
                () -> assertTrue(mixinConfig.contains("RaiseShieldGoalMixin")));
    }

    @Test
    void tacticalMetadataUsesLowFrequencyRefreshWithShotCriticalExplosionUpdates()
            throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String refresh = controller.substring(
                controller.indexOf("private boolean refreshGunData("),
                controller.indexOf("private boolean refreshEngagementRange("));
        String tick = controller.substring(
                controller.indexOf("private void tickSafely("),
                controller.indexOf("public float getProjectileSafetyPadding"));

        assertAll(
                () -> assertTrue(controller.contains("private CachedTacticalState cachedTacticalState")),
                () -> assertTrue(refresh.contains(
                        "refreshCachedTacticalState(refreshedIndex, engagementRangeReadable)")),
                () -> assertTrue(controller.contains(
                        "boolean metadataReadable = engagementRangeReadable")),
                () -> assertTrue(controller.contains("gunIndex.getType()")),
                () -> assertTrue(controller.contains("GunProperties.RuntimeOnly.BULLET_AMOUNT")),
                () -> assertTrue(controller.contains("resolveCurrentExplosionTraits()")),
                () -> assertTrue(controller.contains(
                        "refreshDynamicExplosionTacticalState(true)")),
                () -> assertTrue(controller.contains("dynamicExplosionRefreshGameTick")),
                () -> assertTrue(controller.contains("config.tactics.gunOverrides.get().get(trackedGunId)")),
                () -> assertTrue(controller.contains("config.tactics.typeProfiles.get()")),
                () -> assertTrue(controller.contains("disableFiringWindow(envelope)")),
                () -> assertFalse(tick.contains("WeaponTacticalResolver.resolve(")),
                () -> assertFalse(tick.contains("config.tactics")));
    }

    @Test
    void unreadableOrContradictoryTacticsBlockOffensiveMovement() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(controller.contains("if (!hasValidFiringWindow())")),
                () -> assertTrue(controller.contains("gameTime >= nextGunDataRefreshGameTick")),
                () -> assertTrue(goal.contains(
                        "!gunController.captureReadiness().permitsOffensiveMovement()")),
                () -> assertTrue(goal.contains("engagementReason.releaseTarget()")),
                () -> assertTrue(controller.contains("private boolean refreshEngagementRange()")),
                () -> assertTrue(controller.contains("return false;")));
    }

    @Test
    void tacticalMovementAndFiringGatesReadOnlyTheCachedEnvelope() throws IOException {
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String preferredAccessor = controller.substring(
                controller.indexOf("public double getPreferredApproachDistance()"),
                controller.indexOf("static float normalizeMaximumFiringRange("));
        String movementLoop = goal.substring(
                goal.indexOf("private void updateMovement("),
                goal.indexOf("private void transitionMovementState("));
        String burstGate = goal.substring(
                goal.indexOf("private boolean hasClearScheduledBurstShot("),
                goal.indexOf("private void recordShotSafety("));
        String tactics = read("src/main/java/com/gamergaming/villagerguardgunners/ai/tactics/GuardTacticalController.java");

        assertAll(
                () -> assertTrue(goal.contains("gunController.getRetreatDistance()")),
                () -> assertTrue(goal.contains(
                        "gunController.getPreferredApproachDistance()")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().preferredApproachDistance()")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().retreatDistance()")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().requiredVisibleTicks()")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().aimToleranceDegrees()")),
                () -> assertTrue(preferredAccessor.contains("hasUsableCachedTacticalState()")),
                () -> assertFalse(preferredAccessor.contains("TacticalEnvelope.resolve(")),
                () -> assertTrue(tactics.contains("now < nextDecision && !urgent")),
                () -> assertTrue(movementLoop.contains("switch (intent)")),
                () -> assertFalse(movementLoop.contains("GuardCombatMovement.selectState(")),
                () -> assertTrue(goal.contains(
                        "gunController.canFireAtDistanceSquared(distanceSquared)")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().minimumFiringDistance()")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().maximumFiringDistance()")),
                () -> assertTrue(preferredAccessor.contains(
                        "cachedTacticalState.envelope().canFireAtDistanceSquared(distanceSquared)")),
                () -> assertTrue(goal.contains(
                        "int requiredVisibleTicks = gunController.getRequiredVisibleTicks()")),
                () -> assertTrue(goal.contains(
                        "double aimToleranceDegrees = gunController.getAimToleranceDegrees()")),
                () -> assertTrue(goal.contains("isFacingTarget(aimToleranceDegrees)")),
                () -> assertTrue(burstGate.contains(
                        "int requiredVisibleTicks = gunController.getRequiredVisibleTicks()")),
                () -> assertTrue(burstGate.contains(
                        "double aimToleranceDegrees = gunController.getAimToleranceDegrees()")),
                () -> assertTrue(burstGate.contains("!canSeeTarget")),
                () -> assertTrue(burstGate.contains("visibleTicks < requiredVisibleTicks")),
                () -> assertTrue(burstGate.contains(
                        "isFacingTarget(expectedTarget, aimToleranceDegrees)")),
                () -> assertTrue(burstGate.contains(
                        "gunController.canFireAtDistanceSquared(distanceSquared)")),
                () -> assertTrue(burstGate.contains("guard.hasLineOfSight(expectedTarget)")),
                () -> assertFalse(goal.contains(
                        "config.combat.requiredVisibleTicks.get()")),
                () -> assertFalse(goal.contains(
                        "config.combat.aimToleranceDegrees.get()")));
    }

    @Test
    void malformedThirdPartyGunCallbacksSuspendWithoutEscapingTheAiTick() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");

        assertAll(
                () -> assertTrue(controller.contains("private void tickSafely(")),
                () -> assertTrue(controller.contains("catch (RuntimeException exception)")),
                () -> assertTrue(controller.contains("suspendInvalidWeapon();")),
                () -> assertTrue(controller.contains("private void suspendInvalidWeapon()")),
                () -> assertTrue(controller.contains("hasValidChargeData(chargeData)")),
                () -> assertTrue(controller.contains("catch (RuntimeException ignored)")));
    }

    @Test
    void phaseFiveHasPreFireAndNativeDamageBackstopsWithoutExtraRayTracing() throws IOException {
        String safety = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireSafety.java");
        String allyProtection = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/GuardAllyProtection.java");
        String events = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireEvents.java");
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(safety.contains("getEntitiesOfClass(")),
                () -> assertTrue(safety.contains("getBoundingBox()")),
                () -> assertTrue(safety.contains("protectedBounds.clip(start, end)")),
                () -> assertTrue(safety.contains("GuardAllyProtection.isProtected(")),
                () -> assertTrue(allyProtection.contains("shooter.isAlliedTo(living)")),
                () -> assertFalse(safety.contains("level().clip(")),
                () -> assertFalse(safety.contains("BlockRayTrace")),
                () -> assertTrue(events.contains("EntityHurtByGunEvent.Pre")),
                () -> assertTrue(events.contains("event.setCanceled(true)")),
                () -> assertTrue(events.contains("instanceof ProjectileExplosion")),
                () -> assertTrue(events.contains("getIndirectSourceEntity() instanceof Guard")),
                () -> assertTrue(events.contains("getAffectedEntities().removeIf")),
                () -> assertTrue(events.contains("preserveBlocksFromGuardExplosions.get()")),
                () -> assertTrue(events.contains("getAffectedBlocks().clear()")),
                () -> assertFalse(events.contains("java.lang.reflect")),
                () -> assertFalse(goal.contains("cacheAge == 0L && endpointsUnchanged")),
                () -> assertFalse(goal.contains("cacheAge <= config.performance.unsafeShotCacheTicks.get()")));
    }

    @Test
    void phaseSixReappliesNativeAnimationFromNarrowClientModelMixins() throws IOException {
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/client/GuardModelMixin.java");
        String steveMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/client/GuardSteveModelMixin.java");
        String animation = read(
                "src/main/java/com/gamergaming/villagerguardgunners/client/GuardThirdPersonAnimation.java");
        String mixinConfig = read("src/main/resources/mixins.villagerguardgunners.json");

        assertAll(
                () -> assertFalse(Files.exists(projectDirectory.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardRendererMixin.java"))),
                () -> assertTrue(mixinConfig.contains("\"client\"")),
                () -> assertTrue(mixinConfig.contains("client.GuardModelMixin")),
                () -> assertTrue(mixinConfig.contains("client.GuardSteveModelMixin")),
                () -> assertTrue(guardMixin.contains("@Mixin(GuardModel.class)")),
                () -> assertTrue(steveMixin.contains("@Mixin(GuardSteveModel.class)")),
                () -> assertTrue(guardMixin.contains("at = @At(\"TAIL\")")),
                () -> assertTrue(steveMixin.contains("at = @At(\"TAIL\")")),
                () -> assertTrue(guardMixin.contains("GuardThirdPersonAnimation.reapply(")),
                () -> assertTrue(steveMixin.contains("GuardThirdPersonAnimation.reapply(")),
                () -> assertTrue(animation.contains("instanceof IGun")),
                () -> assertTrue(animation.contains("InnerThirdPersonManager.setRotationAnglesHead(")),
                () -> assertTrue(animation.contains("float rightX = rightArm.x")),
                () -> assertTrue(animation.contains("rightArm.x = rightX")),
                () -> assertTrue(animation.contains("float leftZ = leftArm.z")),
                () -> assertTrue(animation.contains("leftArm.z = leftZ")),
                () -> assertFalse(animation.contains("getEntities")),
                () -> assertFalse(animation.contains("ThirdPersonManager.getAnimation")),
                () -> assertFalse(animation.contains("PlayerAnimatorCompat")));
    }

    @Test
    void customProjectileAndWeaponRebalanceAreGone() throws IOException {
        String config = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/VillagerGuardGunnersConfig.java");
        String metadata = read("src/main/resources/META-INF/mods.toml");

        assertAll(
                () -> assertFalse(Files.exists(projectDirectory.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/entity/GuardBulletEntity.java"))),
                () -> assertFalse(Files.exists(projectDirectory.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/util/GuardGunHelper.java"))),
                () -> assertFalse(Files.exists(projectDirectory.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardRendererMixin.java"))),
                () -> assertFalse(config.contains("infiniteGuardAmmo")),
                () -> assertFalse(config.contains("reloadTimeMultiplier")),
                () -> assertFalse(config.contains("accuracy")),
                () -> assertFalse(config.contains("damageMultiplier")),
                () -> assertTrue(metadata.contains("modId=\"fzzy_config\"")),
                () -> assertTrue(metadata.contains("modId=\"kotlinforforge\"")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
