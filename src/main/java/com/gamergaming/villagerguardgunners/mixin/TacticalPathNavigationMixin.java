package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.entities.Guard;

/** Terrain notifications invalidate owned tactical paths instead of bypassing their planning budget. */
@Mixin(PathNavigation.class)
public abstract class TacticalPathNavigationMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected boolean hasDelayedRecomputation;

    @Inject(method = "recomputePath", at = @At("HEAD"), cancellable = true)
    private void villagerguardgunners$invalidateTacticalRoute(CallbackInfo callback) {
        if (!(mob instanceof Guard guard)) return;
        var transport = com.gamergaming.villagerguardgunners.medical.TransportRuntime.lease(guard);
        if (transport != null && transport.carrier == guard) {
            hasDelayedRecomputation = false;
            guard.getNavigation().stop();
            for (var wrapped : guard.goalSelector.getAvailableGoals())
                if (wrapped.getGoal() instanceof com.gamergaming.villagerguardgunners.medical.TransportMovementGoal goal)
                    goal.invalidateForWorldChange();
            callback.cancel();
            return;
        }
        var resupply = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        if (resupply != null && resupply.ownsEmergencyPath()) {
            hasDelayedRecomputation = false;
            resupply.invalidateEmergencyPath(); callback.cancel(); return;
        }
        for (var wrapped : guard.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal duty
                    && duty.ownsScreenedGroundPath()) {
                hasDelayedRecomputation = false;
                duty.invalidateGroundPath(); callback.cancel(); return;
            }
            if (!(wrapped.getGoal() instanceof GuardGunCombatGoal goal)) continue;
            if (goal.investigation().ownsPath()) {
                hasDelayedRecomputation = false;
                goal.investigation().invalidate(); callback.cancel(); return;
            }
            if (goal.ownsScreenedCombatApproach()) {
                hasDelayedRecomputation = false;
                goal.invalidateCombatApproach();
                callback.cancel();
                return;
            }
            if (goal.tacticalNavigation().ownsPath()) {
                // The controller now owns the retry; do not leave a native retry queued.
                hasDelayedRecomputation = false;
                goal.tacticalNavigation().invalidateForWorldChange();
                callback.cancel();
                return;
            }
        }
    }
}
