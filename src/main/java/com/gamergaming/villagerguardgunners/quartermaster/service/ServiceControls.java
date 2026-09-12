package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import tallestegg.guardvillagers.entities.Guard;

/** Native menu buttons and commands share the same explicit authorization boundary. */
public final class ServiceControls {
    public static final int POLICY_BUTTON = 300, PROFILE_BUTTON = 301, REPLACE_BUTTON = 302;
    public static boolean change(Player player, Guard guard, int button) {
        if (player.level().isClientSide || player.level() != guard.level() || !player.isAlive() || player.isSpectator()
                || ServiceTransactions.mutating(guard) || !GuardOrderPermissions.mayManage(player, guard)
                || player.distanceToSqr(guard) >= 64 || !guard.isAlive() || guard.isRemoved()) return false;
        var state = GuardEquipmentServiceAccess.get(guard); if (!state.readable()) return false;
        List<ResourceLocation> ids = EquipmentProfiles.snapshot().keySet().stream().sorted().toList();
        if (button == POLICY_BUTTON) {
            var values = GuardServiceState.Policy.values(); state.policy(values[(state.policy().ordinal() + 1) % values.length]);
            if (state.policy() == GuardServiceState.Policy.PROFILE && state.profile() == null && !ids.isEmpty()) {
                var assignedRole = GuardRoleAccess.getRoleId(guard);
                var role = assignedRole == null ? ResourceLocation.parse("villagerguardgunners:generalist") : assignedRole;
                state.profile(ids.stream().filter(id -> role != null && EquipmentProfiles.snapshot().get(id).roles().contains(role.toString())).findFirst().orElse(ids.get(0)));
            }
        } else if (button == PROFILE_BUTTON && !ids.isEmpty()) state.profile(ids.get((ids.indexOf(state.profile()) + 1) % ids.size()));
        else if (button == REPLACE_BUTTON) state.replaceExisting(!state.replaceExisting());
        else return false;
        QuartermasterServiceRuntime.cancel(guard, ServiceStatus.RETRY); state.nextAttempt = 0; state.status = state.policy() == GuardServiceState.Policy.MANUAL ? ServiceStatus.MANUAL : ServiceStatus.READY; return true;
    }
}
