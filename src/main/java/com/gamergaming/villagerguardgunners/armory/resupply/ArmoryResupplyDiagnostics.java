package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Explicit, on-demand Armory resupply inspection. Capturing a snapshot has no
 * recurring hook, performs no Armory query, creates no request or path, and
 * never loads a chunk.
 */
public final class ArmoryResupplyDiagnostics {
    private ArmoryResupplyDiagnostics() {
    }

    /** Captures current need plus the mixin-owned Step 6 runtime state. */
    public static Snapshot capture(
            Guard guard,
            ArmoryResupplySettings settings) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(settings, "settings");

        GuardArmoryResupplyController controller =
                GuardArmoryResupplyAccess.getController(guard);
        if (controller == null) {
            return captureDormant(guard, settings);
        }
        CurrentNeed current = currentNeed(guard, settings);
        return Snapshot.registered(settings, current, controller.snapshot());
    }

    public static Snapshot captureDormant(
            Guard guard,
            ArmoryResupplySettings settings) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(settings, "settings");

        CurrentNeed current = currentNeed(guard, settings);
        return Snapshot.dormant(settings, current);
    }

    private static CurrentNeed currentNeed(
            Guard guard,
            ArmoryResupplySettings settings) {

        ArmoryResupplyGate.GateStatus gate =
                ArmoryResupplyLiveGate.evaluate(settings.enabled(), guard);
        if (!gate.ready()) {
            return CurrentNeed.notEvaluated(gate);
        }

        final ArmoryResupplyNeed.Assessment need;
        try {
            GuardWeaponAmmunition.LoadoutSummary loadout =
                    GuardWeaponAmmunition.captureSummary(
                            guard,
                            GuardAmmoAccess.getTaCZInventoryHandler(guard));
            need = ArmoryResupplyNeed.assess(
                    loadout,
                    settings.lowReserveMagazines(),
                    settings.targetReserveMagazines());
        } catch (RuntimeException exception) {
            return CurrentNeed.stateUnavailable(gate);
        }
        return CurrentNeed.evaluated(gate, need);
    }

    public enum RuntimeStatus {
        REGISTERED,
        NOT_REGISTERED
    }

    public enum NeedEvaluationStatus {
        NOT_EVALUATED,
        EVALUATED,
        STATE_UNAVAILABLE
    }

    public record Snapshot(
            RuntimeStatus runtimeStatus,
            ArmoryResupplyState state,
            ArmoryResupplySettings settings,
            ArmoryResupplyGate.GateStatus gateStatus,
            NeedEvaluationStatus needEvaluationStatus,
            @Nullable ArmoryResupplyNeed.NeedStatus needStatus,
            @Nullable ArmoryResupplyWeaponRole weaponRole,
            @Nullable ResourceLocation gunId,
            @Nullable ArmoryResupplyUrgency urgency,
            long observedReserveUnits,
            long targetReserveUnits,
            long requestedUnits,
            int candidateInspections,
            int pathAttempts,
            long nextActionGameTick,
            @Nullable GuardArmoryResupplyController.RuntimeOutcome lastOutcome,
            @Nullable BlockPos armoryPosition,
            int sourceSlot,
            long lastTransferredUnits) {
        public Snapshot {
            Objects.requireNonNull(runtimeStatus, "runtimeStatus");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(gateStatus, "gateStatus");
            Objects.requireNonNull(needEvaluationStatus, "needEvaluationStatus");
            boolean evaluated = needEvaluationStatus == NeedEvaluationStatus.EVALUATED;
            armoryPosition = armoryPosition == null ? null : armoryPosition.immutable();
            if (evaluated != (needStatus != null)
                    || (!evaluated
                            && (weaponRole != null
                                    || gunId != null
                                    || urgency != null
                                    || observedReserveUnits != 0L
                                    || targetReserveUnits != 0L
                                    || requestedUnits != 0L))
                    || observedReserveUnits < 0L
                    || targetReserveUnits < 0L
                    || requestedUnits < 0L
                    || candidateInspections < 0
                    || candidateInspections > ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES
                    || pathAttempts < 0
                    || pathAttempts > ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS
                    || nextActionGameTick < -1L
                    || ((armoryPosition == null) != (sourceSlot < 0))
                    || lastTransferredUnits < 0L) {
                throw new IllegalArgumentException(
                        "Armory diagnostic fields contradict their status");
            }
            if (runtimeStatus == RuntimeStatus.NOT_REGISTERED
                    && (state != ArmoryResupplyState.IDLE
                            || candidateInspections != 0
                            || pathAttempts != 0
                            || nextActionGameTick != -1L
                            || lastOutcome != null
                            || armoryPosition != null
                            || sourceSlot != -1
                            || lastTransferredUnits != 0L)) {
                throw new IllegalArgumentException(
                        "Dormant Armory diagnostic fields imply a runtime");
            }
            if (runtimeStatus == RuntimeStatus.REGISTERED && lastOutcome == null) {
                throw new IllegalArgumentException(
                        "Registered Armory diagnostics require a runtime outcome");
            }
            if (evaluated) {
                boolean identified = weaponRole != null && gunId != null;
                boolean needIdentifiesWeapon = needStatus
                        == ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED
                        || needStatus == ArmoryResupplyNeed.NeedStatus.SUFFICIENT_RESERVE;
                if (identified != needIdentifiesWeapon
                        || (needStatus == ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED)
                                != (urgency != null && requestedUnits > 0L)
                        || (needStatus != ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED
                                && (urgency != null || requestedUnits != 0L))) {
                    throw new IllegalArgumentException(
                            "Armory need details contradict their diagnostic status");
                }
            }
        }

        public boolean requestRequired() {
            return needStatus == ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED;
        }

        private static Snapshot dormant(
                ArmoryResupplySettings settings,
                CurrentNeed current) {
            return new Snapshot(
                    RuntimeStatus.NOT_REGISTERED,
                    ArmoryResupplyState.IDLE,
                    settings,
                    current.gateStatus(),
                    current.evaluationStatus(),
                    current.needStatus(),
                    current.weaponRole(),
                    current.gunId(),
                    current.urgency(),
                    current.observedReserveUnits(),
                    current.targetReserveUnits(),
                    current.requestedUnits(),
                    0,
                    0,
                    -1L,
                    null,
                    null,
                    -1,
                    0L);
        }

        private static Snapshot registered(
                ArmoryResupplySettings settings,
                CurrentNeed current,
                GuardArmoryResupplyController.RuntimeSnapshot runtime) {
            ArmoryReservationTarget target = runtime.selectedTarget();
            return new Snapshot(
                    RuntimeStatus.REGISTERED,
                    runtime.state(),
                    settings,
                    current.gateStatus(),
                    current.evaluationStatus(),
                    current.needStatus(),
                    current.weaponRole(),
                    current.gunId(),
                    current.urgency(),
                    current.observedReserveUnits(),
                    current.targetReserveUnits(),
                    current.requestedUnits(),
                    runtime.candidateInspections(),
                    runtime.pathAttempts(),
                    runtime.nextActionGameTick(),
                    runtime.lastOutcome(),
                    target == null ? null : target.position(),
                    target == null ? -1 : target.sourceSlot(),
                    runtime.lastTransferredUnits());
        }
    }

    private record CurrentNeed(
            ArmoryResupplyGate.GateStatus gateStatus,
            NeedEvaluationStatus evaluationStatus,
            @Nullable ArmoryResupplyNeed.NeedStatus needStatus,
            @Nullable ArmoryResupplyWeaponRole weaponRole,
            @Nullable ResourceLocation gunId,
            @Nullable ArmoryResupplyUrgency urgency,
            long observedReserveUnits,
            long targetReserveUnits,
            long requestedUnits) {
        private CurrentNeed {
            Objects.requireNonNull(gateStatus, "gateStatus");
            Objects.requireNonNull(evaluationStatus, "evaluationStatus");
        }

        private static CurrentNeed notEvaluated(
                ArmoryResupplyGate.GateStatus gateStatus) {
            return empty(gateStatus, NeedEvaluationStatus.NOT_EVALUATED);
        }

        private static CurrentNeed stateUnavailable(
                ArmoryResupplyGate.GateStatus gateStatus) {
            return empty(gateStatus, NeedEvaluationStatus.STATE_UNAVAILABLE);
        }

        private static CurrentNeed empty(
                ArmoryResupplyGate.GateStatus gateStatus,
                NeedEvaluationStatus evaluationStatus) {
            return new CurrentNeed(
                    gateStatus,
                    evaluationStatus,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    0L,
                    0L);
        }

        private static CurrentNeed evaluated(
                ArmoryResupplyGate.GateStatus gateStatus,
                ArmoryResupplyNeed.Assessment need) {
            return new CurrentNeed(
                    gateStatus,
                    NeedEvaluationStatus.EVALUATED,
                    need.status(),
                    need.weaponRole(),
                    need.gunId(),
                    need.urgency(),
                    need.observedReserveUnits(),
                    need.targetReserveUnits(),
                    need.requestedUnits());
        }
    }
}
