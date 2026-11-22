package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.entities.Guard;

/** Keep the vanilla village-return goal from pulling guards off valid defense posts. */
@Mixin(RandomStrollGoal.class)
public abstract class GuardVillageReturnGoalMixin {
    @Shadow @Final protected PathfinderMob mob;

    // MoveBackToVillageGoal calls the base canUse and inherits canContinueToUse.
    // Check both so a return trip already underway also yields to a new duty.
    @Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
    private void villagerguardgunners$yieldVillageReturnToPost(CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof MoveBackToVillageGoal)
                || !(mob instanceof Guard guard) || guard.level().isClientSide) return;
        for (var wrapped : guard.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GuardAutomaticDutyMovementGoal duty
                    && duty.prioritizesDefensePost()) {
                callback.setReturnValue(false);
                return;
            }
        }
    }
}
