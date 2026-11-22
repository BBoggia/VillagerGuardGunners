package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed persistent access to one guard's optional datapack role. */
public interface GuardRoleAccess {
    @Nullable
    ResourceLocation villagerGuardGunners$getRoleId();

    void villagerGuardGunners$setRoleId(@Nullable ResourceLocation roleId);

    int villagerGuardGunners$getRoleRevision();

    GuardOrders villagerGuardGunners$resolveEffectiveOrders(
            GuardOrders roleDefaults,
            long roleGeneration);

    static @Nullable ResourceLocation getRoleId(Guard guard) {
        return guard instanceof GuardRoleAccess access
                ? access.villagerGuardGunners$getRoleId()
                : null;
    }

    static boolean setRoleId(Guard guard, @Nullable ResourceLocation roleId) {
        if (!(guard instanceof GuardRoleAccess access)) {
            return false;
        }
        access.villagerGuardGunners$setRoleId(roleId);
        return true;
    }

    static int getRevision(Guard guard) {
        return guard instanceof GuardRoleAccess access
                ? access.villagerGuardGunners$getRoleRevision()
                : 0;
    }
}
