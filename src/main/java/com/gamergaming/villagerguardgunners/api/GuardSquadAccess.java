package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.squad.SquadCommandRuntime;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed persistent access to one guard's optional squad identity. */
public interface GuardSquadAccess {
    @Nullable
    SquadMembership villagerGuardGunners$getSquadMembership();

    void villagerGuardGunners$setSquadMembership(@Nullable SquadMembership membership);

    int villagerGuardGunners$getSquadRevision();

    @Nullable
    static SquadMembership getMembership(Guard guard) {
        return guard instanceof GuardSquadAccess access
                ? access.villagerGuardGunners$getSquadMembership()
                : null;
    }

    @Nullable
    static UUID getSquadId(Guard guard) {
        SquadMembership membership = getMembership(guard);
        return membership == null ? null : membership.squadId();
    }

    static boolean isMemberOf(Guard guard, UUID squadId) {
        return squadId != null && squadId.equals(getSquadId(guard));
    }

    static boolean setMembership(
            Guard guard,
            @Nullable SquadMembership membership) {
        if (!(guard instanceof GuardSquadAccess access)) {
            return false;
        }
        if (Objects.equals(access.villagerGuardGunners$getSquadMembership(), membership)) {
            return true;
        }
        // A temporary command belongs to the membership under which it was
        // issued. Release it transactionally before removing or replacing that
        // identity so a guard cannot continue obeying its former squad.
        if (!SquadCommandRuntime.replaceCommand(guard, null)) {
            return false;
        }
        access.villagerGuardGunners$setSquadMembership(membership);
        return true;
    }

    static int getRevision(Guard guard) {
        return guard instanceof GuardSquadAccess access
                ? access.villagerGuardGunners$getSquadRevision()
                : 0;
    }
}
