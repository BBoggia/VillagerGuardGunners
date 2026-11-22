package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import net.minecraft.world.item.ItemStack;

/**
 * Asks the owning gun mod whether a stack is ammo for a gun.
 * Compatibility is never inferred from registry IDs or item classes.
 */
public final class AmmoCompatibility {
    private AmmoCompatibility() {
    }

    public static Assessment assess(ItemStack gunStack, ItemStack candidate) {
        if (gunStack == null
                || gunStack.isEmpty()
                || candidate == null
                || candidate.isEmpty()) {
            return Assessment.notAmmunition();
        }
        // The gun's provider decides. A stack no provider owns as a gun is still
        // judged by the provider that owns the candidate as ammo, so its kind is reported.
        for (GunProvider provider : GunProviders.all()) {
            if (provider.isGun(gunStack)) {
                return provider.assessAmmo(gunStack, candidate);
            }
        }
        for (GunProvider provider : GunProviders.all()) {
            if (provider.isAmmoItem(candidate)) {
                return provider.assessAmmo(gunStack, candidate);
            }
        }
        return Assessment.notAmmunition();
    }

    public enum Compatibility {
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }

    public enum AmmoKind {
        NONE,
        LOOSE,
        BOX
    }

    public record Assessment(
            Compatibility compatibility,
            AmmoKind kind,
            long units,
            boolean infinite) {

        public Assessment {
            if (compatibility == null || kind == null) {
                throw new IllegalArgumentException("Compatibility assessment fields cannot be null");
            }
            if (units < 0L) {
                throw new IllegalArgumentException("Ammunition units cannot be negative");
            }
        }

        public boolean isAmmunition() {
            return kind != AmmoKind.NONE;
        }

        public boolean isCompatible() {
            return compatibility == Compatibility.COMPATIBLE;
        }

        public static Assessment notAmmunition() {
            return new Assessment(Compatibility.INCOMPATIBLE, AmmoKind.NONE, 0L, false);
        }

        public static Assessment incompatible(AmmoKind kind) {
            return new Assessment(Compatibility.INCOMPATIBLE, kind, 0L, false);
        }

        public static Assessment unknown(AmmoKind kind) {
            return new Assessment(Compatibility.UNKNOWN, kind, 0L, false);
        }
    }
}
