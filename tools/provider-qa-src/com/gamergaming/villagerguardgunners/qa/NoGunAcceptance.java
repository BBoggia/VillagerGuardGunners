package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.guns.GuardWeaponControlAccess;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPools;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import tallestegg.guardvillagers.GuardEntityType;

final class NoGunAcceptance {
    static void started(ServerStartedEvent event) {
        var level = event.getServer().overworld();
        var guard = GuardEntityType.GUARD.get().create(level);
        if (guard == null || !GunProviders.all().isEmpty() || !NaturalGuardLoadoutPools.snapshot().isEmpty()) {
            throw new IllegalStateException("Expected an ordinary guard without any bundled gun loadouts");
        }
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        guard.moveTo(0.5, 160, 0.5);
        if (!level.addFreshEntity(guard)) throw new IllegalStateException("Guard spawn failed");
        var control = GuardWeaponControlAccess.control(guard).orElseThrow();
        control.startAiming();
        if (GunProviders.isGun(guard.getMainHandItem()) || control.controllerDiagnostics().weaponDataReady()
                || GuardAmmoAccess.getAmmoHandler(guard).getSlots() != 3) {
            throw new IllegalStateException("Unexpected guard inventory or weapon state without gun mods");
        }
        LogUtils.getLogger().info("VGG_NO_GUN_QA_PASS");
    }
}
