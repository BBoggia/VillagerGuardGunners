package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import java.util.Objects;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import tallestegg.guardvillagers.entities.Guard;

/** Coarse, allocation-free weapon classification for staffing decisions. */
public final class VillageDefenseWeaponSuitability {
    private VillageDefenseWeaponSuitability() {
    }

    public static WeaponClass classify(Guard guard) {
        Objects.requireNonNull(guard, "guard");
        return classify(guard.getMainHandItem(), guard.getOffhandItem());
    }

    public static WeaponClass classify(ItemStack mainHand, ItemStack offhand) {
        ItemStack main = Objects.requireNonNull(mainHand, "mainHand");
        ItemStack off = Objects.requireNonNull(offhand, "offhand");
        if (NativeGunController.isGun(main)
                || main.getItem() instanceof CrossbowItem
                || off.getItem() instanceof CrossbowItem) {
            return WeaponClass.RANGED;
        }
        if (main.getItem() instanceof SwordItem
                || main.getItem() instanceof AxeItem
                || main.getItem() instanceof TridentItem) {
            return WeaponClass.MELEE;
        }
        return WeaponClass.UNARMED;
    }

    public static boolean meets(
            WeaponClass weaponClass,
            VillageDefenseDutyWeaponRequirement requirement) {
        Objects.requireNonNull(weaponClass, "weaponClass");
        Objects.requireNonNull(requirement, "requirement");
        return switch (requirement) {
            case ANY -> weaponClass != WeaponClass.UNARMED;
            case RANGED -> weaponClass == WeaponClass.RANGED;
        };
    }

    public enum WeaponClass {
        UNARMED,
        MELEE,
        RANGED
    }
}
