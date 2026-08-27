package com.gamergaming.villagerguardgunners.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;

import net.minecraft.world.InteractionHand;
import tallestegg.guardvillagers.entities.Guard;
import tallestegg.guardvillagers.entities.ai.goals.RaiseShieldGoal;

/** Prevents Guard Villagers' priority-zero shield goal from starving aimed gun use. */
@Mixin(RaiseShieldGoal.class)
public abstract class RaiseShieldGoalMixin {
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void doNotRaiseShieldWhileHoldingGun(CallbackInfoReturnable<Boolean> cir) {
        if (villagerGuardGunners$isActivelyAimingGun()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void stopShieldingAfterEquippingGun(CallbackInfoReturnable<Boolean> cir) {
        if (villagerGuardGunners$isActivelyAimingGun()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void releaseShieldForGunEvenWhenAlwaysShieldIsEnabled(CallbackInfo ci) {
        Guard guard = villagerGuardGunners$getGuard();
        if (villagerGuardGunners$isActivelyAimingGun()
                && guard.isUsingItem()
                && guard.getUsedItemHand() == InteractionHand.OFF_HAND) {
            guard.stopUsingItem();
        }
    }

    @Unique
    private boolean villagerGuardGunners$isActivelyAimingGun() {
        Guard guard = villagerGuardGunners$getGuard();
        return guard.getMainHandItem().getItem() instanceof IGun
                // The synchronized accessor is updated after AI goals tick;
                // the server-owned holder changes immediately in aim(...).
                && IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming;
    }

    @Unique
    private Guard villagerGuardGunners$getGuard() {
        return ((RaiseShieldGoal) (Object) this).guard;
    }
}
