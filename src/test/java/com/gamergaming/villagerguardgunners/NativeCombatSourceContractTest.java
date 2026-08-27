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
    void dryGunYieldsTheGunGoalSoGuardVillagersCanSelectMelee() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(goal.contains("gunController.canUseGunGoal(guard.getMainHandItem())")),
                () -> assertTrue(controller.contains("operator.getSynShootCoolDown() > 0L")),
                () -> assertTrue(controller.contains("gunItem.canReload(guard, gunStack)")));
    }

    @Test
    void safetyTracksNativeBurstAndFailsClosedForUnboundedEnvelopes() throws IOException {
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String safety = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireSafety.java");
        String events = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireEvents.java");

        assertAll(
                () -> assertTrue(controller.contains("isNativeBurstActive(nowTimestamp)")),
                () -> assertTrue(controller.contains("GuardBurstSafetyAccess.setSuppressed(guard, true)")),
                () -> assertFalse(controller.contains("suppliedAimPitch")),
                () -> assertFalse(controller.contains("-90.0F")),
                () -> assertTrue(events.contains("onGuardGunFire(GunFireEvent event)")),
                () -> assertTrue(events.contains("GuardBurstSafetyAccess.shouldSuppress(guard)")),
                () -> assertTrue(controller.contains("GuardBurstSafetyAccess.setSafetyCheck(guard, burstSafetyCheck)")),
                () -> assertTrue(controller.contains("resolveMaximumInaccuracy()")),
                () -> assertTrue(controller.contains("resolveExplosionRadius()")),
                () -> assertTrue(controller.contains("MAX_SAFETY_PADDING")),
                () -> assertTrue(safety.contains("!Float.isFinite(padding)")));
    }

    @Test
    void phaseFourBoundsAndStaggersCombatAiWork() throws IOException {
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String controller = read("src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String shieldMixin = read("src/main/java/com/gamergaming/villagerguardgunners/mixin/RaiseShieldGoalMixin.java");
        String mixinConfig = read("src/main/resources/mixins.villagerguardgunners.json");

        assertAll(
                () -> assertTrue(goal.contains("config.performance.movementDecisionIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.pathRecalculationIntervalTicks.get()")),
                () -> assertTrue(goal.contains("GuardCombatMovement.firstDueTick")),
                () -> assertTrue(goal.contains("navigation.moveTo(target, config.movement.approachSpeed.get())")),
                () -> assertTrue(goal.contains("getMoveControl().strafe")),
                () -> assertTrue(goal.contains("config.performance.repositionProgressCheckTicks.get()")),
                () -> assertTrue(goal.contains("madeLateralProgress(")),
                () -> assertTrue(goal.contains("repositionDirection = -repositionDirection")),
                () -> assertTrue(goal.contains("gunController.getPreferredEngagementRange()")),
                () -> assertTrue(goal.contains("gunController.getMaximumFiringRange()")),
                () -> assertTrue(goal.contains("GuardCombatMovement.isWithinEngagementRange(distanceSquared, maximumFiringRange)")),
                () -> assertTrue(goal.contains("gunController.hasCurrentWeaponData(guard.getMainHandItem())")),
                () -> assertFalse(goal.contains("createPath(")),
                () -> assertFalse(goal.contains("DefaultRandomPos")),
                () -> assertTrue(controller.contains("GunProperties.EFFECTIVE_RANGE")),
                () -> assertTrue(shieldMixin.contains("instanceof IGun")),
                () -> assertTrue(shieldMixin.contains("getDataHolder().isAiming")),
                () -> assertTrue(mixinConfig.contains("RaiseShieldGoalMixin")));
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
        String events = read("src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireEvents.java");
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(safety.contains("getEntitiesOfClass(")),
                () -> assertTrue(safety.contains("getBoundingBox()")),
                () -> assertTrue(safety.contains("protectedBounds.clip(start, end)")),
                () -> assertTrue(safety.contains("shooter.isAlliedTo(entity)")),
                () -> assertFalse(safety.contains("level().clip(")),
                () -> assertFalse(safety.contains("BlockRayTrace")),
                () -> assertTrue(events.contains("EntityHurtByGunEvent.Pre")),
                () -> assertTrue(events.contains("event.setCanceled(true)")),
                () -> assertTrue(events.contains("instanceof ProjectileExplosion")),
                () -> assertTrue(events.contains("getIndirectSourceEntity() instanceof Guard")),
                () -> assertTrue(events.contains("getAffectedEntities().removeIf")),
                () -> assertFalse(events.contains("java.lang.reflect")),
                () -> assertTrue(goal.contains("cacheAge == 0L && endpointsUnchanged")),
                () -> assertTrue(goal.contains("cacheAge <= config.performance.unsafeShotCacheTicks.get()")));
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
