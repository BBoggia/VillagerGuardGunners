package com.gamergaming.villagerguardgunners.api;

import javax.annotation.Nullable;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCandidate;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutIssuance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutSelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;

/** Internal one-time spawn state mixed into Guard Villagers guards. */
public interface GuardNaturalLoadoutAccess {

    /** Records the first recognized creation source before the guard joins a level. */
    void villagerGuardGunners$markNaturalLoadoutSpawnSource(NaturalGuardSpawnSource source);

    /**
     * Atomically consumes the pending source and marks this guard's one-time
     * selection attempt as claimed.
     */
    @Nullable
    NaturalGuardSpawnSource villagerGuardGunners$claimNaturalLoadoutSpawnSource();

    boolean villagerGuardGunners$hasNaturalLoadoutSelectionAttempted();

    @Nullable
    NaturalGuardLoadoutSelector.DecisionStatus
            villagerGuardGunners$getNaturalLoadoutDecisionStatus();

    void villagerGuardGunners$setNaturalLoadoutDecisionStatus(
            NaturalGuardLoadoutSelector.DecisionStatus status);

    @Nullable
    NaturalGuardLoadoutCandidate villagerGuardGunners$getNaturalLoadoutCandidate();

    void villagerGuardGunners$setNaturalLoadoutCandidate(
            @Nullable NaturalGuardLoadoutCandidate candidate);

    @Nullable
    NaturalGuardLoadoutProvenance villagerGuardGunners$getNaturalLoadoutProvenance();

    void villagerGuardGunners$setNaturalLoadoutProvenance(
            @Nullable NaturalGuardLoadoutProvenance provenance);

    @Nullable
    NaturalGuardLoadoutIssuance.IssuanceStatus
            villagerGuardGunners$getNaturalLoadoutIssuanceStatus();

    void villagerGuardGunners$setNaturalLoadoutIssuanceStatus(
            NaturalGuardLoadoutIssuance.IssuanceStatus status);
}
