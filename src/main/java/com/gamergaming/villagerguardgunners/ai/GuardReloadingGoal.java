package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.IGuardReloading;
import com.gamergaming.villagerguardgunners.util.GuardGunHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

import java.util.EnumSet;

public class GuardReloadingGoal extends Goal {
    private final Guard guard;

    public GuardReloadingGoal(Guard guard, double reloadThreshold) {
        this.guard = guard;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Check if the guard's main hand item is a gun
        ItemStack mainHandItem = guard.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun gun)) return false;

        int currentAmmo = gun.getCurrentAmmoCount(mainHandItem);
        boolean needsReload = currentAmmo <= 0 && !GuardGunHelper.isMagFull(guard);

        boolean hasAmmo = GuardGunHelper.hasAmmoInInventory(guard);

        return needsReload && hasAmmo && !((IGuardReloading) guard).isReloading();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.guard instanceof IGuardReloading reloadingGuard) {
            return reloadingGuard.isReloading();
        }
        return false;
    }

    @Override
    public void start() {
        // VillagerGuardGunners.LOGGER.info("ReloadWeaponGoal started");
        ItemStack mainHandItem = guard.getMainHandItem();
        IGun gun = (IGun) mainHandItem.getItem();

        if (gun instanceof AbstractGunItem) {
            // ((AbstractGunItem) gun).doReload(guard, mainHandItem, true);
            int reloadDuration = GuardGunHelper.getReloadTime((AbstractGunItem) gun, mainHandItem);

            if (this.guard instanceof IGuardReloading reloadingGuard) {
                reloadingGuard.startReloading(reloadDuration);
            }

            // Call reloadGun from GuardGunHelper
            GuardGunHelper.reloadGun(guard, mainHandItem);
        }

        moveAwayFromTarget();
    }

    @Override
    public void stop() {
        // Ensure the guard stops reloading if the goal is interrupted
        guard.stopUsingItem();
    }

    @Override
    public void tick() {
        // Optionally, handle any animations or state checks during reloading
        guard.getLookControl().setLookAt(guard.getX(), guard.getEyeY(), guard.getZ());
    }

    private void moveAwayFromTarget() {
        LivingEntity target = guard.getTarget();
        if (target != null) {
            // Calculate a position away from the target
            double dx = guard.getX() - target.getX();
            double dz = guard.getZ() - target.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            // Normalize vector (dx, dz) and scale
            double scale = 1.0 / distance;
            double moveX = guard.getX() + dx * scale * 3.0; // move 3 blocks away
            double moveZ = guard.getZ() + dz * scale * 3.0;

            guard.getNavigation().moveTo(moveX, guard.getY(), moveZ, 1.0);
        }
    }
}