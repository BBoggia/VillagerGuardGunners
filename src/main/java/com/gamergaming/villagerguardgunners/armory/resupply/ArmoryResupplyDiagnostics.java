package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import java.util.Objects;
import javax.annotation.Nullable;
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

    /**
     * Captures the presently available Step 1/5 view. The loaded-Armory index
     * and runtime orchestrator are not registered yet, so the authoritative
     * runtime state is deliberately reported as dormant rather than inferred.
     */
    public static Snapshot captureDormant(
            Guard guard,
            ArmoryResupplySettings settings) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(settings, "settings");

        ArmoryResupplyGate.GateStatus gate =
                ArmoryResupplyLiveGate.evaluate(settings.enabled(), guard);
        if (!gate.ready()) {
            return Snapshot.notEvaluated(settings, gate);
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
            return Snapshot.stateUnavailable(settings, gate);
        }
        return Snapshot.evaluated(settings, gate, need);
    }

    public enum RuntimeStatus {
        /** Step 2 and the future goal/orchestrator have not been registered. */
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
            long nextActionGameTick) {
        public Snapshot {
            Objects.requireNonNull(runtimeStatus, "runtimeStatus");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(gateStatus, "gateStatus");
            Objects.requireNonNull(needEvaluationStatus, "needEvaluationStatus");
            boolean evaluated = needEvaluationStatus == NeedEvaluationStatus.EVALUATED;
            if (runtimeStatus != RuntimeStatus.NOT_REGISTERED
                    || state != ArmoryResupplyState.IDLE
                    || evaluated != (needStatus != null)
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
                    || candidateInspections != 0
                    || pathAttempts != 0
                    || nextActionGameTick != -1L) {
                throw new IllegalArgumentException(
                        "Dormant Armory diagnostic fields contradict their status");
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

        private static Snapshot notEvaluated(
                ArmoryResupplySettings settings,
                ArmoryResupplyGate.GateStatus gateStatus) {
            return empty(settings, gateStatus, NeedEvaluationStatus.NOT_EVALUATED);
        }

        private static Snapshot stateUnavailable(
                ArmoryResupplySettings settings,
                ArmoryResupplyGate.GateStatus gateStatus) {
            return empty(settings, gateStatus, NeedEvaluationStatus.STATE_UNAVAILABLE);
        }

        private static Snapshot empty(
                ArmoryResupplySettings settings,
                ArmoryResupplyGate.GateStatus gateStatus,
                NeedEvaluationStatus evaluationStatus) {
            return new Snapshot(
                    RuntimeStatus.NOT_REGISTERED,
                    ArmoryResupplyState.IDLE,
                    settings,
                    gateStatus,
                    evaluationStatus,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0,
                    0,
                    -1L);
        }

        private static Snapshot evaluated(
                ArmoryResupplySettings settings,
                ArmoryResupplyGate.GateStatus gateStatus,
                ArmoryResupplyNeed.Assessment need) {
            return new Snapshot(
                    RuntimeStatus.NOT_REGISTERED,
                    ArmoryResupplyState.IDLE,
                    settings,
                    gateStatus,
                    NeedEvaluationStatus.EVALUATED,
                    need.status(),
                    need.weaponRole(),
                    need.gunId(),
                    need.urgency(),
                    need.observedReserveUnits(),
                    need.targetReserveUnits(),
                    need.requestedUnits(),
                    0,
                    0,
                    -1L);
        }
    }
}
