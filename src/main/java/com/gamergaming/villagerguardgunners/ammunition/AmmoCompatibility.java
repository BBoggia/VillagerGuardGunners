package com.gamergaming.villagerguardgunners.ammunition;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.world.item.ItemStack;

/**
 * Fail-soft adapter around TaCZ's authoritative ammunition callbacks.
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

        if (candidate.getItem() instanceof IAmmo looseAmmo) {
            try {
                // Third-party implementations receive defensive copies. A
                // compatibility probe must never mutate the guard's gun or a
                // live stack returned by its item handler.
                if (looseAmmo.isAmmoOfGun(gunStack.copy(), candidate.copy())) {
                    return new Assessment(
                            Compatibility.COMPATIBLE,
                            AmmoKind.LOOSE,
                            candidate.getCount(),
                            false);
                }
            } catch (RuntimeException exception) {
                return Assessment.unknown(AmmoKind.LOOSE);
            }

            if (!(candidate.getItem() instanceof IAmmoBox)) {
                return Assessment.incompatible(AmmoKind.LOOSE);
            }
        }

        if (candidate.getItem() instanceof IAmmoBox ammoBox) {
            try {
                ItemStack checkedBox = candidate.copy();
                boolean creative = ammoBox.isAllTypeCreative(checkedBox)
                        || ammoBox.isCreative(checkedBox);
                int units = creative ? 0 : ammoBox.getAmmoCount(checkedBox);
                if (!creative && units <= 0) {
                    return Assessment.incompatible(AmmoKind.BOX);
                }
                if (!ammoBox.isAmmoBoxOfGun(gunStack.copy(), checkedBox)) {
                    return Assessment.incompatible(AmmoKind.BOX);
                }
                return new Assessment(
                        Compatibility.COMPATIBLE,
                        AmmoKind.BOX,
                        units,
                        creative);
            } catch (RuntimeException exception) {
                return Assessment.unknown(AmmoKind.BOX);
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

        private static Assessment notAmmunition() {
            return new Assessment(Compatibility.INCOMPATIBLE, AmmoKind.NONE, 0L, false);
        }

        private static Assessment incompatible(AmmoKind kind) {
            return new Assessment(Compatibility.INCOMPATIBLE, kind, 0L, false);
        }

        private static Assessment unknown(AmmoKind kind) {
            return new Assessment(Compatibility.UNKNOWN, kind, 0L, false);
        }
    }
}
