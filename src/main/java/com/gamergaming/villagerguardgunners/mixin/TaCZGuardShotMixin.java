package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.CycleTaskHelper;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.entities.Guard;

/** TaCZ 1.1.8 per-discharge hooks, before ammo/heat and after successful projectile insertion. */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class TaCZGuardShotMixin {
    @Shadow private LivingEntity shooter;
    @Shadow private ItemStack itemStack;
    // TaCZ creates a separate API instance for each shoot/reload/bolt invocation.
    // Only this shooting script's follow-up ammo operations are suppressed.
    @Unique private boolean villagerGuardGunners$roundRefused;

    @Inject(method = "shootOnce", at = @At("HEAD"))
    private void villagerGuardGunners$beginAttempt(CallbackInfo ci) {
        villagerGuardGunners$roundRefused = shooter instanceof Guard;
    }

    @Redirect(method = "shootOnce", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/CycleTaskHelper;addCycleTask(Ljava/util/function/BooleanSupplier;JI)V"))
    private void villagerGuardGunners$bindRoundIdentity(BooleanSupplier task, long interval, int count) {
        if (shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            BooleanSupplier bound = controller == null ? () -> false : controller.bindNativeRounds(task, itemStack);
            task = () -> {
                // Native ammo consumption during an accepted round must remain available.
                villagerGuardGunners$roundRefused = false;
                boolean continued = false;
                try {
                    continued = bound.getAsBoolean();
                    return continued;
                } finally {
                    villagerGuardGunners$roundRefused |= !continued;
                }
            };
        }
        CycleTaskHelper.addCycleTask(task, interval, count);
    }

    @Inject(method = {"removeAmmoFromMagazine", "putAmmoInMagazine", "consumeAmmoFromPlayer"},
            at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$preserveScriptAmmo(int count, CallbackInfoReturnable<Integer> cir) {
        if (villagerGuardGunners$roundRefused) cir.setReturnValue(0);
    }

    @Inject(method = "setAmmoInBarrel", at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$preserveScriptChamber(boolean chambered, CallbackInfo ci) {
        if (villagerGuardGunners$roundRefused) ci.cancel();
    }

    @Inject(method = "reduceAmmoOnce", at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$preserveScriptRound(CallbackInfoReturnable<Boolean> cir) {
        if (villagerGuardGunners$roundRefused) cir.setReturnValue(false);
    }

    @Redirect(method = "lambda$shootOnce$2", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private boolean villagerGuardGunners$validateRound(IEventBus bus, Event event,
            boolean consumeAmmo, GunData gunData, int pellets, BulletData bulletData,
            IGunOperator operator, float damage, float speed, float spread, int soundDistance, boolean silence) {
        boolean allowed = true;
        if (shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            allowed = controller != null && controller.prepareNativeRound(itemStack, gunData, bulletData, speed, spread);
            if (!allowed) event.setCanceled(true);
        }
        boolean canceled = bus.post(event);
        if (allowed && !canceled && shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            allowed = controller != null && controller.preparedNativeRoundCurrent();
        }
        // Another subscriber cannot revive a refused guard shot by clearing the event flag.
        if (!allowed) event.setCanceled(true);
        // Native callbacks return true even when their event was canceled: that
        // return value controls burst continuation, not whether a round fired.
        if (shooter instanceof Guard) villagerGuardGunners$roundRefused = canceled || !allowed;
        return canceled || !allowed;
    }

    @Redirect(method = "lambda$shootOnce$2", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean villagerGuardGunners$observeProjectile(Level level, Entity projectile) {
        boolean added = level.addFreshEntity(projectile);
        if (added && shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            if (controller != null) controller.nativeProjectileAdded();
        }
        return added;
    }
}
