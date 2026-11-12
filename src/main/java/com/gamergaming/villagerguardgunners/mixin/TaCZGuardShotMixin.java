package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess;
import com.gamergaming.villagerguardgunners.ammunition.GuardInfiniteAmmo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.common.entities.Guard;

/** Per-discharge hooks, before ammo/heat and after successful projectile insertion. */
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

    @WrapOperation(method = "shootOnce", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/CycleTaskHelper;addCycleTask(Ljava/util/function/BooleanSupplier;JI)V"))
    private void villagerGuardGunners$bindRoundIdentity(BooleanSupplier task, long interval, int count,
            Operation<Void> original) {
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
        // Preserve other addons' scheduling hooks while binding each guard round.
        original.call(task, interval, count);
    }

    @Inject(method = {"removeAmmoFromMagazine", "putAmmoInMagazine", "consumeAmmoFromPlayer"},
            at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$preserveScriptAmmo(int count, CallbackInfoReturnable<Integer> cir) {
        if (villagerGuardGunners$roundRefused) cir.setReturnValue(0);
    }

    @Inject(method = "consumeAmmoFromPlayer", at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$virtualConsumption(int count, CallbackInfoReturnable<Integer> cir) {
        if (GuardInfiniteAmmo.virtualReserve(shooter)) {
            cir.setReturnValue(villagerGuardGunners$roundRefused ? 0 : Math.max(0, count));
        }
    }

    @Inject(method = "removeAmmoFromMagazine", at = @At("RETURN"))
    private void villagerGuardGunners$replenishScriptMagazine(int count, CallbackInfoReturnable<Integer> cir) {
        if (!villagerGuardGunners$roundRefused && cir.getReturnValue() > 0) {
            GuardInfiniteAmmo.replenishMagazine(shooter, itemStack);
        }
    }

    @Inject(method = "reduceAmmoOnce", at = @At("RETURN"))
    private void villagerGuardGunners$replenishNativeMagazine(CallbackInfoReturnable<Boolean> cir) {
        if (!villagerGuardGunners$roundRefused && cir.getReturnValue()) {
            GuardInfiniteAmmo.replenishMagazine(shooter, itemStack);
        }
    }

    @Inject(method = "setAmmoInBarrel", at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$preserveScriptChamber(boolean chambered, CallbackInfo ci) {
        if (villagerGuardGunners$roundRefused) ci.cancel();
    }

    @Inject(method = "reduceAmmoOnce", at = @At("HEAD"), cancellable = true)
    private void villagerGuardGunners$preserveScriptRound(CallbackInfoReturnable<Boolean> cir) {
        if (villagerGuardGunners$roundRefused) cir.setReturnValue(false);
    }

    // 1.2.0 adds a nested script guard and captures the attachment cache as well.
    // Match the firing lambda by its operation, not javac's numbering or full argument list.
    // In both versions the captured floats are damage multiplier, speed, and spread.
    @WrapOperation(method = "/^lambda\\$shootOnce\\$[0-9]+$/", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event villagerGuardGunners$validateRound(IEventBus bus, Event rawEvent,
            Operation<Event> original, @Local(argsOnly = true) GunData gunData,
            @Local(argsOnly = true) BulletData bulletData,
            @Local(argsOnly = true, ordinal = 1) float speed,
            @Local(argsOnly = true, ordinal = 2) float spread) {
        if (!(rawEvent instanceof com.tacz.guns.api.event.common.GunFireEvent event)) {
            return original.call(bus, rawEvent);
        }
        boolean allowed = true;
        if (shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            allowed = controller != null && controller.prepareNativeRound(itemStack, gunData, bulletData, speed, spread);
            if (!allowed) event.setCanceled(true);
        }
        var posted = (com.tacz.guns.api.event.common.GunFireEvent) original.call(bus, event);
        boolean canceled = posted.isCanceled();
        if (allowed && !canceled && shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            allowed = controller != null && controller.preparedNativeRoundCurrent();
        }
        // Another subscriber cannot revive a refused guard shot by clearing the event flag.
        if (!allowed) posted.setCanceled(true);
        // Native callbacks return true even when their event was canceled: that
        // return value controls burst continuation, not whether a round fired.
        if (shooter instanceof Guard) villagerGuardGunners$roundRefused = canceled || !allowed;
        return posted;
    }

    @WrapOperation(method = "/^lambda\\$shootOnce\\$[0-9]+$/", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean villagerGuardGunners$observeProjectile(Level level, Entity projectile,
            Operation<Boolean> original) {
        // An addon or the level may reject insertion; only successful insertions commit aim.
        boolean added = original.call(level, projectile);
        if (added && shooter instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            if (controller != null) controller.nativeProjectileAdded();
        }
        return added;
    }
}
