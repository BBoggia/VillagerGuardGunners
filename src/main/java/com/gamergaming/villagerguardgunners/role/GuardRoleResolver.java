package com.gamergaming.villagerguardgunners.role;

import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.entities.Guard;

/** Constant-time role lookup and explicit-order precedence for live guard behavior. */
public final class GuardRoleResolver {
    private GuardRoleResolver() {
    }

    public static GuardOrders effectiveOrders(Guard guard) {
        GuardOrders explicit = GuardOrdersAccess.getOrders(guard);
        ResourceLocation roleId = GuardRoleAccess.getRoleId(guard);
        GuardRoleReloadSnapshot snapshot = GuardRoles.reloadSnapshot();
        GuardRoleDefinition role = roleId == null ? null : snapshot.get(roleId);
        GuardOrders defaults = role == null
                ? GuardOrders.DEFAULT
                : role.effectiveDefaults();
        return guard instanceof GuardRoleAccess access
                ? access.villagerGuardGunners$resolveEffectiveOrders(
                        defaults,
                        snapshot.generation())
                : defaults.equals(GuardOrders.DEFAULT)
                        ? explicit
                        : explicit.withFallback(defaults);
    }

    public static @Nullable GuardRoleDefinition definition(Guard guard) {
        ResourceLocation roleId = GuardRoleAccess.getRoleId(guard);
        return roleId == null ? null : GuardRoles.get(roleId);
    }

    /**
     * Revision used by low-frequency combat metadata caches. A datapack reload
     * or role assignment invalidates the cache lazily, without scanning guards.
     */
    public static int effectiveRevision(Guard guard) {
        int result = GuardOrdersAccess.getRevision(guard);
        result = 31 * result + GuardRoleAccess.getRevision(guard);
        result = 31 * result + Long.hashCode(GuardRoles.generation());
        return result;
    }
}
