package com.gamergaming.villagerguardgunners.compat.cgm;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.guns.FeedKind;
import com.gamergaming.villagerguardgunners.guns.GunDescriptor;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunRef;
import com.gamergaming.villagerguardgunners.guns.WeaponDriver;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedDrivers;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedWeaponDriver;
import com.mrcrayfish.guns.common.Gun;
import com.mrcrayfish.guns.item.GunItem;
import com.mrcrayfish.guns.item.IAmmo;
import com.mrcrayfish.guns.util.GunEnchantmentHelper;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * MrCrayfish's Gun Mod. Each gun is its own item, so a gun's identity is its
 * item identifier. Ammo is loose items only; the mod has no ammo containers.
 */
public final class CgmGunProvider implements GunProvider {
    public static final String ID = "cgm";

    private final CgmGunBackend backend = new CgmGunBackend();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isGun(ItemStack stack) {
        return stack != null && stack.getItem() instanceof GunItem;
    }

    @Override
    public boolean isAmmoItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IAmmo;
    }

    @Override
    public Optional<GunRef> ref(ItemStack gunStack) {
        return isGun(gunStack)
                ? Optional.of(new GunRef(ID, CgmGunBackend.itemId(gunStack)))
                : Optional.empty();
    }

    @Override
    public Optional<GunDescriptor> describe(ItemStack gunStack) {
        if (!isGun(gunStack)) {
            return Optional.empty();
        }
        try {
            Gun gun = CgmGunBackend.modifiedGun(gunStack);
            if (gun == null) {
                return Optional.empty();
            }
            return Optional.of(new GunDescriptor(
                    new GunRef(ID, CgmGunBackend.itemId(gunStack)),
                    CgmGunBackend.gunType(gun),
                    gun.getProjectile().getItem(),
                    true));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> isExplosive(Guard guard, ItemStack gunStack) {
        try {
            Gun gun = isGun(gunStack) ? CgmGunBackend.modifiedGun(gunStack) : null;
            return gun == null
                    ? Optional.empty()
                    : Optional.of(CgmGunBackend.isExplosiveAmmo(gun.getProjectile().getItem()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResourceLocation> ammoId(ItemStack ammoStack) {
        return isAmmoItem(ammoStack) ? Optional.of(CgmGunBackend.itemId(ammoStack)) : Optional.empty();
    }

    @Override
    public AmmoCompatibility.Assessment assessAmmo(ItemStack gunStack, ItemStack candidate) {
        boolean ammoItem = isAmmoItem(candidate);
        if (!isGun(gunStack)) {
            return ammoItem
                    ? AmmoCompatibility.Assessment.incompatible(AmmoCompatibility.AmmoKind.LOOSE)
                    : AmmoCompatibility.Assessment.notAmmunition();
        }
        ResourceLocation wanted;
        try {
            Gun gun = CgmGunBackend.modifiedGun(gunStack);
            wanted = gun == null ? null : gun.getProjectile().getItem();
        } catch (RuntimeException exception) {
            wanted = null;
        }
        if (wanted == null) {
            return ammoItem
                    ? AmmoCompatibility.Assessment.unknown(AmmoCompatibility.AmmoKind.LOOSE)
                    : AmmoCompatibility.Assessment.notAmmunition();
        }
        // A gun may name any item as its ammo, so the gun's own definition decides.
        if (Gun.isAmmo(candidate, wanted)) {
            return new AmmoCompatibility.Assessment(
                    AmmoCompatibility.Compatibility.COMPATIBLE,
                    AmmoCompatibility.AmmoKind.LOOSE,
                    candidate.getCount(),
                    false);
        }
        return ammoItem
                ? AmmoCompatibility.Assessment.incompatible(AmmoCompatibility.AmmoKind.LOOSE)
                : AmmoCompatibility.Assessment.notAmmunition();
    }

    @Override
    public GuardAmmoSummary.Snapshot captureAmmo(ItemStack gunStack, @Nullable IItemHandler reserveHandler) {
        ResourceLocation gunId = CgmGunBackend.itemId(gunStack);
        int loaded;
        int capacity;
        try {
            Gun gun = CgmGunBackend.modifiedGun(gunStack);
            if (gun == null) {
                return GuardAmmoSummary.Snapshot.unavailable(GuardAmmoSummary.Status.UNKNOWN_GUN_DATA, gunId);
            }
            loaded = backend.loaded(gunStack);
            capacity = GunEnchantmentHelper.getAmmoCapacity(gunStack, gun);
        } catch (RuntimeException exception) {
            return GuardAmmoSummary.Snapshot.unavailable(GuardAmmoSummary.Status.UNREADABLE_GUN, gunId);
        }
        GuardAmmoSummary.ReserveScan reserve = GuardAmmoSummary.scanPhysicalReserve(gunStack, reserveHandler);
        return GuardAmmoSummary.Snapshot.ready(
                gunId,
                FeedKind.MAGAZINE,
                loaded,
                capacity,
                false,
                false,
                reserve.units(),
                reserve.infinite(),
                reserve.compatibleStacks() > 0
                        ? GuardAmmoSummary.ReserveSource.PHYSICAL
                        : GuardAmmoSummary.ReserveSource.NONE,
                reserve.compatibleStacks(),
                reserve.incompatibleAmmoStacks(),
                reserve.unknownAmmoStacks(),
                reserve.complete());
    }

    @Override
    public WeaponDriver createDriver(Guard guard) {
        return new ManagedWeaponDriver(guard, this, backend);
    }

    @Override
    public void interrupt(Guard guard) {
        ManagedDrivers.find(guard, ID).ifPresent(ManagedWeaponDriver::interrupt);
    }

    @Override
    public boolean isAiming(Guard guard) {
        return ManagedDrivers.find(guard, ID).map(ManagedWeaponDriver::isAiming).orElse(false);
    }

    @Override
    public boolean isWeaponBusy(Guard guard) {
        return ManagedDrivers.find(guard, ID).map(ManagedWeaponDriver::isBusy).orElse(false);
    }
}
