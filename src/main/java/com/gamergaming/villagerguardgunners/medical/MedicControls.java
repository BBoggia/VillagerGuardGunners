package com.gamergaming.villagerguardgunners.medical;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import tallestegg.guardvillagers.entities.Guard;
public final class MedicControls {
    public static boolean change(Player player, Guard guard, int button) {
        if (player.level().isClientSide || player.level() != guard.level() || !player.isAlive() || player.isSpectator()
                || !guard.isAlive() || guard.isRemoved() || player.distanceToSqr(guard) >= 64 || !GuardOrderPermissions.mayManage(player, guard)) return false;
        var state = GuardMedicAccess.get(guard); if (!state.known()) return false;
        if (button == 310) {
            state.medic(!state.medic());
            if (state.medic() && GuardRoleAccess.getRoleId(guard) == null) GuardRoleAccess.setRoleId(guard, ResourceLocation.parse("villagerguardgunners:medic"));
        } else if (button == 311) state.retreat(!state.retreat()); else return false;
        MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED); MedicRuntime.stopRetreat(guard);
        state.nextAssessment = 0; state.status = MedicState.Status.IDLE; return true;
    }
}
