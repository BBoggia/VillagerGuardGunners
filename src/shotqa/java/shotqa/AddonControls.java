package shotqa;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.common.entities.Guard;

final class AddonControls {
    static void setup(Guard guard, ItemStack ammo) {
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION)
                .withEngagement(GuardOrders.EngagementOrder.GUARD_TARGETS));
        GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0, ammo);
    }

    static String diagnostics(Guard guard) {
        for (var wrapped : guard.goalSelector.getAvailableGoals())
            if (wrapped.getGoal() instanceof GuardGunCombatGoal goal) return goal.captureDiagnostics().toString();
        return "missing goal";
    }
}
