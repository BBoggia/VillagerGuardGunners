package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRuntimePolicy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.entities.Guard;
import tallestegg.guardvillagers.entities.ai.goals.WalkBackToCheckPointGoal;

/** Lets an explicit VGG route temporarily supersede the upstream checkpoint goal. */
@Mixin(WalkBackToCheckPointGoal.class)
public abstract class WalkBackToCheckPointGoalMixin {
    @Shadow(remap = false)
    @Final
    private Guard guard;

    @Inject(
            method = {"canUse", "canContinueToUse"},
            at = @At("HEAD"),
            cancellable = true)
    private void yieldToExplicitSquadRoute(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (SquadPatrolRuntimePolicy.executionEnabled()
                    && SquadPatrolRouteResolver.hasLoadedWaypoint(guard)) {
                cir.setReturnValue(false);
            }
        } catch (RuntimeException ignored) {
            // Fail open to Guard Villagers' original checkpoint behavior.
        }
    }
}
