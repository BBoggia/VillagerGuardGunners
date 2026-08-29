package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;
import java.util.Objects;
import javax.annotation.Nullable;
import tallestegg.guardvillagers.entities.Guard;

/** On-demand natural-loadout state used by explicit administrator diagnostics. */
public final class NaturalGuardLoadoutDiagnostics {
    private NaturalGuardLoadoutDiagnostics() {
    }

    public static Snapshot capture(Guard guard) {
        Objects.requireNonNull(guard, "guard");
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        NaturalGuardLoadoutProvenance provenance =
                access.villagerGuardGunners$getNaturalLoadoutProvenance();
        AuthenticationState authentication = provenance == null
                ? AuthenticationState.NO_RECORD
                : NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                        guard.getMainHandItem(),
                        provenance)
                        ? AuthenticationState.ACTIVE
                        : AuthenticationState.GUN_MISMATCH;
        return new Snapshot(
                authentication,
                access.villagerGuardGunners$hasNaturalLoadoutSelectionAttempted(),
                access.villagerGuardGunners$getNaturalLoadoutDecisionStatus(),
                access.villagerGuardGunners$getNaturalLoadoutIssuanceStatus(),
                provenance);
    }

    public enum AuthenticationState {
        NO_RECORD,
        ACTIVE,
        GUN_MISMATCH
    }

    public record Snapshot(
            AuthenticationState authentication,
            boolean selectionAttempted,
            @Nullable NaturalGuardLoadoutSelector.DecisionStatus lastDecision,
            @Nullable NaturalGuardLoadoutIssuance.IssuanceStatus lastIssuance,
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        public Snapshot {
            authentication = Objects.requireNonNull(authentication, "authentication");
            if ((authentication == AuthenticationState.NO_RECORD) != (provenance == null)) {
                throw new IllegalArgumentException(
                        "Natural-loadout diagnostic authentication and provenance disagree");
            }
        }

        public boolean active() {
            return authentication == AuthenticationState.ACTIVE;
        }
    }
}
