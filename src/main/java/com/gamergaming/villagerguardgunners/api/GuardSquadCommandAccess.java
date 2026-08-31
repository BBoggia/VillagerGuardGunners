package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import javax.annotation.Nullable;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed access to one guard's optional temporary squad command. */
public interface GuardSquadCommandAccess {
    @Nullable
    SquadCommand villagerGuardGunners$getSquadCommand();

    void villagerGuardGunners$setSquadCommand(@Nullable SquadCommand command);

    int villagerGuardGunners$getSquadCommandRevision();

    @Nullable
    static SquadCommand getCommand(Guard guard) {
        return guard instanceof GuardSquadCommandAccess access
                ? access.villagerGuardGunners$getSquadCommand()
                : null;
    }

    static boolean setCommand(Guard guard, @Nullable SquadCommand command) {
        if (!(guard instanceof GuardSquadCommandAccess access)) {
            return false;
        }
        access.villagerGuardGunners$setSquadCommand(command);
        return true;
    }

    static int getRevision(Guard guard) {
        return guard instanceof GuardSquadCommandAccess access
                ? access.villagerGuardGunners$getSquadCommandRevision()
                : 0;
    }
}
