package com.gamergaming.villagerguardgunners.sidearm;

import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded tactical bridge between the pure sidearm policy and stack
 * transactions. Ordinary ticks use only primitive values and native cached
 * weapon state. Stored-gun metadata and the nine-slot TaCZ inventory are read
 * only after a real activation reason exists, with failed readiness checks
 * rate-limited.
 */
public final class GuardSidearmRuntimeController {
    private static final int READY_RECHECK_TICKS = 20;
    private static final int NOT_READY_RECHECK_TICKS = 5;
    private static final int INELIGIBLE_RECHECK_TICKS = 100;

    private final Guard guard;
    private long lastEvaluationGameTick = Long.MIN_VALUE;
    private RuntimeOutcome lastEvaluationOutcome = RuntimeOutcome.NO_TRANSITION;
    private int assessedStorageRevision = Integer.MIN_VALUE;
    private long nextAssessmentGameTick = Long.MIN_VALUE;
    private SidearmRuntimeAssessment.Assessment cachedAssessment =
            new SidearmRuntimeAssessment.Assessment(
                    SidearmEligibility.EligibilityStatus.NOT_GUN,
                    false);

    public GuardSidearmRuntimeController(Guard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    public RuntimeOutcome evaluate(
            @Nullable LivingEntity target,
            NativeGunController nativeController,
            boolean featureEnabled,
            boolean targetMayEngage) {
        Objects.requireNonNull(nativeController, "nativeController");
        long gameTick = guard.level().getGameTime();
        if (guard.level().isClientSide
                || guard.getServer() == null
                || !guard.getServer().isSameThread()) {
            return RuntimeOutcome.NO_TRANSITION;
        }
        if (gameTick == lastEvaluationGameTick) {
            return lastEvaluationOutcome;
        }
        lastEvaluationGameTick = gameTick;

        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        SidearmState state = storage.stateAgainst(guard.getMainHandItem());
        if (state == SidearmState.RECOVERY_REQUIRED) {
            lastEvaluationOutcome = RuntimeOutcome.RECOVERY_REQUIRED;
            return lastEvaluationOutcome;
        }
        lastEvaluationOutcome = state == SidearmState.SIDEARM_ACTIVE
                ? evaluateReturn(
                        target,
                        nativeController,
                        featureEnabled,
                        targetMayEngage,
                        storage,
                        gameTick)
                : evaluateActivation(
                        target,
                        nativeController,
                        featureEnabled,
                        targetMayEngage,
                        storage,
                        gameTick);
        return lastEvaluationOutcome;
    }

    public boolean requiresLifecycleCleanup(
            @Nullable LivingEntity target,
            boolean featureEnabled,
            boolean targetMayEngage) {
        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        if (storage.stateAgainst(guard.getMainHandItem()) != SidearmState.SIDEARM_ACTIVE) {
            return false;
        }
        return !featureEnabled
                || !targetMayEngage
                || target == null
                || !target.isAlive()
                || !target.getUUID().equals(storage.getActivationTargetId());
    }

    public boolean isSidearmActive() {
        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        return storage.stateAgainst(guard.getMainHandItem()) == SidearmState.SIDEARM_ACTIVE;
    }

    /**
     * Contradictory ownership is intentionally inert until an administrator or
     * player recovers the retained stacks. No combat or command goal may treat
     * whichever main-hand item happened to remain as an ordinary weapon.
     */
    public boolean requiresRecoverySuppression() {
        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        return storage.stateAgainst(guard.getMainHandItem())
                == SidearmState.RECOVERY_REQUIRED;
    }

    private RuntimeOutcome evaluateActivation(
            @Nullable LivingEntity target,
            NativeGunController nativeController,
            boolean featureEnabled,
            boolean targetMayEngage,
            GuardSidearmStorage storage,
            long gameTick) {
        if (storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT).isEmpty()) {
            return RuntimeOutcome.NO_TRANSITION;
        }
        boolean targetActive = targetMayEngage && target != null && target.isAlive();
        boolean dry = nativeController.isCurrentWeaponDryConfirmed();
        boolean primaryExplosive = nativeController.isCurrentWeaponExplosive();
        boolean primaryAllowed = nativeController.isWeaponAllowedByOrders();
        double primaryStandoff = primaryExplosive
                ? nativeController.getMinimumFiringRange()
                : 0.0D;
        double distanceSquared = targetActive
                ? guard.distanceToSqr(target)
                : Double.NaN;
        boolean insideStandoff = primaryExplosive
                && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0D
                && Double.isFinite(primaryStandoff)
                && primaryStandoff >= 0.0D
                && distanceSquared < squareOrInfinity(primaryStandoff);
        SidearmActivationReason reason = SidearmDecisionPolicy.activationReason(
                targetActive,
                dry,
                primaryExplosive,
                primaryAllowed,
                insideStandoff);
        if (reason == SidearmActivationReason.NONE || target == null) {
            return RuntimeOutcome.NO_TRANSITION;
        }
        // Feature disablement is known before the only potentially expensive
        // step: reading stored-gun metadata and the bounded ammo inventory.
        if (!featureEnabled) {
            return RuntimeOutcome.BLOCKED_FEATURE_DISABLED;
        }

        SidearmRuntimeAssessment.Assessment assessment = assessStoredSidearm(
                storage,
                gameTick);
        boolean nativeSwapSafe = nativeController.isSidearmSwapSafe();
        SidearmDecision decision = SidearmDecisionPolicy.activationDecision(
                reason,
                featureEnabled,
                assessment.eligible(),
                assessment.ammunitionReady(),
                nativeSwapSafe);
        if (decision != SidearmDecision.ACTIVATE) {
            return mapDecision(decision, false);
        }

        // Ammo inventory changes do not increment the private weapon-storage
        // revision. Recheck the bounded inventory at the actual commit point so
        // a deferred swap cannot equip a sidearm whose reserve was just removed.
        SidearmRuntimeAssessment.Assessment finalAssessment =
                SidearmRuntimeAssessment.assess(
                        guard,
                        storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT));
        cacheAssessment(storage, gameTick, finalAssessment);
        SidearmDecision finalDecision = SidearmDecisionPolicy.activationDecision(
                reason,
                featureEnabled,
                finalAssessment.eligible(),
                finalAssessment.ammunitionReady(),
                true);
        if (finalDecision != SidearmDecision.ACTIVATE) {
            return mapDecision(finalDecision, false);
        }

        SidearmTransitionResult result = GuardSidearmTransactions.activate(
                guard,
                reason,
                gameTick,
                target.getUUID(),
                primaryStandoff,
                true);
        nativeController.refreshAfterSidearmTransaction();
        return mapTransition(result);
    }

    private RuntimeOutcome evaluateReturn(
            @Nullable LivingEntity target,
            NativeGunController nativeController,
            boolean featureEnabled,
            boolean targetMayEngage,
            GuardSidearmStorage storage,
            long gameTick) {
        boolean targetActive = targetMayEngage && target != null && target.isAlive();
        boolean sameTarget = targetActive
                && target.getUUID().equals(storage.getActivationTargetId());
        boolean sidearmUsable = nativeController.isCurrentWeaponSidearmCompatible();
        long activatedAt = storage.getActivatedAtGameTick();
        long activeTicks = gameTick >= activatedAt ? gameTick - activatedAt : 0L;
        double targetDistance = targetActive
                ? finiteDistance(guard.distanceToSqr(target))
                : Double.NaN;
        SidearmActivationReason activationReason = storage.getActivationReason();
        boolean explosivePrimary = activationReason
                == SidearmActivationReason.EXPLOSIVE_DISALLOWED
                || activationReason == SidearmActivationReason.EXPLOSIVE_STANDOFF;
        boolean primaryAllowed = GuardOrdersAccess.getOrders(guard).permitsExplosiveWeapons();
        SidearmReturnReason reason = SidearmDecisionPolicy.returnReason(
                featureEnabled,
                targetActive,
                sameTarget,
                sidearmUsable,
                activationReason,
                activeTicks,
                SidearmDecisionPolicy.DEFAULT_MINIMUM_ACTIVE_TICKS,
                explosivePrimary,
                primaryAllowed,
                targetDistance,
                storage.getPrimaryStandoffDistance(),
                SidearmDecisionPolicy.DEFAULT_RETURN_MARGIN_BLOCKS);
        if (reason == SidearmReturnReason.NONE) {
            return RuntimeOutcome.NO_TRANSITION;
        }
        boolean nativeSwapSafe = nativeController.isSidearmSwapSafe();
        SidearmDecision decision = SidearmDecisionPolicy.returnDecision(
                reason,
                nativeSwapSafe);
        if (decision != SidearmDecision.RETURN) {
            return mapDecision(decision, true);
        }

        SidearmTransitionResult result = GuardSidearmTransactions.returnPrimary(guard, true);
        nativeController.refreshAfterSidearmTransaction();
        return mapTransition(result);
    }

    private SidearmRuntimeAssessment.Assessment assessStoredSidearm(
            GuardSidearmStorage storage,
            long gameTick) {
        int revision = storage.getContentRevision();
        if (revision == assessedStorageRevision && gameTick < nextAssessmentGameTick) {
            return cachedAssessment;
        }
        ItemStack sidearm = storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT);
        cacheAssessment(storage, gameTick, SidearmRuntimeAssessment.assess(guard, sidearm));
        return cachedAssessment;
    }

    private void cacheAssessment(
            GuardSidearmStorage storage,
            long gameTick,
            SidearmRuntimeAssessment.Assessment assessment) {
        cachedAssessment = assessment;
        assessedStorageRevision = storage.getContentRevision();
        int retryTicks = !cachedAssessment.eligible()
                ? INELIGIBLE_RECHECK_TICKS
                : cachedAssessment.ammunitionReady()
                        ? READY_RECHECK_TICKS
                        : NOT_READY_RECHECK_TICKS;
        nextAssessmentGameTick = saturatingAdd(gameTick, retryTicks);
    }

    private static RuntimeOutcome mapDecision(SidearmDecision decision, boolean returning) {
        return switch (decision) {
            case BLOCKED_FEATURE_DISABLED -> RuntimeOutcome.BLOCKED_FEATURE_DISABLED;
            case BLOCKED_SIDEARM_INELIGIBLE -> RuntimeOutcome.BLOCKED_SIDEARM_INELIGIBLE;
            case BLOCKED_SIDEARM_NOT_READY -> RuntimeOutcome.BLOCKED_SIDEARM_NOT_READY;
            case DEFERRED_NATIVE_BUSY -> returning
                    ? RuntimeOutcome.DEFERRED_RETURN_NATIVE_BUSY
                    : RuntimeOutcome.DEFERRED_ACTIVATION_NATIVE_BUSY;
            case NO_TRANSITION, ACTIVATE, RETURN -> RuntimeOutcome.NO_TRANSITION;
        };
    }

    private static RuntimeOutcome mapTransition(SidearmTransitionResult result) {
        return switch (result.status()) {
            case ACTIVATED -> RuntimeOutcome.ACTIVATED;
            case RETURNED -> RuntimeOutcome.RETURNED;
            case ROLLED_BACK -> RuntimeOutcome.ROLLED_BACK;
            case RECOVERY_REQUIRED -> RuntimeOutcome.RECOVERY_REQUIRED;
            case ROLLBACK_FAILED -> RuntimeOutcome.ROLLBACK_FAILED;
        };
    }

    private static double finiteDistance(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
                ? Math.sqrt(distanceSquared)
                : Double.NaN;
    }

    private static double squareOrInfinity(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return Double.NaN;
        }
        double squared = value * value;
        return Double.isFinite(squared) ? squared : Double.POSITIVE_INFINITY;
    }

    private static long saturatingAdd(long value, int addition) {
        return value > Long.MAX_VALUE - addition ? Long.MAX_VALUE : value + addition;
    }

    public enum RuntimeOutcome {
        NO_TRANSITION,
        ACTIVATED,
        RETURNED,
        BLOCKED_FEATURE_DISABLED,
        BLOCKED_SIDEARM_INELIGIBLE,
        BLOCKED_SIDEARM_NOT_READY,
        DEFERRED_ACTIVATION_NATIVE_BUSY,
        DEFERRED_RETURN_NATIVE_BUSY,
        ROLLED_BACK,
        RECOVERY_REQUIRED,
        ROLLBACK_FAILED;

        public boolean changedWeapon() {
            return this == ACTIVATED || this == RETURNED;
        }

        /** A requested return failed or must wait, so no new sidearm shot may begin. */
        public boolean suppressSidearmFireForReturn() {
            return this == DEFERRED_RETURN_NATIVE_BUSY
                    || this == ROLLED_BACK
                    || this == RECOVERY_REQUIRED
                    || this == ROLLBACK_FAILED;
        }
    }
}
