package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostAssignment;
import javax.annotation.Nullable;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed handoff for one generated guard's bounded post integration. */
public interface GuardPostAssignmentAccess {
    @Nullable
    GuardPostAssignment villagerGuardGunners$getGuardPostAssignment();

    void villagerGuardGunners$setGuardPostAssignment(
            @Nullable GuardPostAssignment assignment);

    @Nullable
    GuardPostAssignment villagerGuardGunners$claimGuardPostAssignment();

    static boolean set(Guard guard, GuardPostAssignment assignment) {
        if (!(guard instanceof GuardPostAssignmentAccess access)) {
            return false;
        }
        access.villagerGuardGunners$setGuardPostAssignment(assignment);
        return true;
    }

    static @Nullable GuardPostAssignment get(Guard guard) {
        return guard instanceof GuardPostAssignmentAccess access
                ? access.villagerGuardGunners$getGuardPostAssignment()
                : null;
    }

    static @Nullable GuardPostAssignment claim(Guard guard) {
        return guard instanceof GuardPostAssignmentAccess access
                ? access.villagerGuardGunners$claimGuardPostAssignment()
                : null;
    }
}
