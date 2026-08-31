package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIdentity;
import javax.annotation.Nullable;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed persistent access to a generated guard's optional post provenance. */
public interface GuardPostAccess {
    @Nullable
    GuardPostIdentity villagerGuardGunners$getGuardPostIdentity();

    void villagerGuardGunners$setGuardPostIdentity(@Nullable GuardPostIdentity identity);

    public static @Nullable GuardPostIdentity getIdentity(Guard guard) {
        return guard instanceof GuardPostAccess access
                ? access.villagerGuardGunners$getGuardPostIdentity()
                : null;
    }
}
