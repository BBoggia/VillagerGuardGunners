package com.gamergaming.villagerguardgunners.compat.pointblank;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.guns.FeedKind;
import com.gamergaming.villagerguardgunners.guns.GunDescriptor;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunRef;
import com.gamergaming.villagerguardgunners.guns.WeaponDriver;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedDrivers;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedWeaponDriver;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Vic's Point Blank or Point Blank: Jelly, whichever is installed. Both use
 * the mod ID {@code pointblank}, so data files and saves name one provider.
 * Each gun is its own item; ammo is loose items only.
 */
public final class PointBlankGunProvider implements GunProvider {
    public static final String ID = "pointblank";

    private final PointBlankAccess access;
    private final PointBlankGunBackend backend;

    PointBlankGunProvider(PointBlankAccess access) {
        this.access = access;
        this.backend = new PointBlankGunBackend(access);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isGun(ItemStack stack) {
        return stack != null && access.isGun(stack);
    }

    @Override
    public boolean isAmmoItem(ItemStack stack) {
        return stack != null && access.isAmmo(stack);
    }

    @Override
    public Optional<GunRef> ref(ItemStack gunStack) {
        return isGun(gunStack) ? Optional.of(new GunRef(ID, itemId(gunStack))) : Optional.empty();
    }

    @Override
    public Optional<GunDescriptor> describe(ItemStack gunStack) {
        if (!isGun(gunStack)) {
            return Optional.empty();
        }
        try {
            PointBlankAccess.Facts facts = access.facts(gunStack).orElse(null);
            if (facts == null) {
                return Optional.empty();
            }
            String type = facts.launcher()
                    ? "rpg"
                    : PointBlankTraits.inferType(facts.pellets(), facts.capacity(), facts.rpm(),
                            facts.automatic(), facts.singleOnly(), facts.damage());
            return Optional.of(new GunDescriptor(
                    new GunRef(ID, itemId(gunStack)), type, access.firstAmmoId(gunStack), !facts.launcher()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Launchers are the mod's only explosive guns. Unreadable guns stay unknown, which callers treat as unsafe. */
    @Override
    public Optional<Boolean> isExplosive(Guard guard, ItemStack gunStack) {
        try {
            return isGun(gunStack)
                    ? access.facts(gunStack).map(PointBlankAccess.Facts::launcher)
                    : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResourceLocation> ammoId(ItemStack ammoStack) {
        return isAmmoItem(ammoStack) ? Optional.of(itemId(ammoStack)) : Optional.empty();
    }

    @Override
    public AmmoCompatibility.Assessment assessAmmo(ItemStack gunStack, ItemStack candidate) {
        if (!isAmmoItem(candidate)) {
            return AmmoCompatibility.Assessment.notAmmunition();
        }
        if (!isGun(gunStack)) {
            return AmmoCompatibility.Assessment.incompatible(AmmoCompatibility.AmmoKind.LOOSE);
        }
        try {
            return access.acceptsAmmo(gunStack, candidate)
                    ? new AmmoCompatibility.Assessment(
                            AmmoCompatibility.Compatibility.COMPATIBLE,
                            AmmoCompatibility.AmmoKind.LOOSE,
                            candidate.getCount(),
                            false)
                    : AmmoCompatibility.Assessment.incompatible(AmmoCompatibility.AmmoKind.LOOSE);
        } catch (RuntimeException exception) {
            return AmmoCompatibility.Assessment.unknown(AmmoCompatibility.AmmoKind.LOOSE);
        }
    }

    @Override
    public GuardAmmoSummary.Snapshot captureAmmo(ItemStack gunStack, @Nullable IItemHandler reserveHandler) {
        ResourceLocation gunId = itemId(gunStack);
        int loaded;
        int capacity;
        try {
            PointBlankAccess.Facts facts = access.facts(gunStack).orElse(null);
            if (facts == null) {
                return GuardAmmoSummary.Snapshot.unavailable(GuardAmmoSummary.Status.UNKNOWN_GUN_DATA, gunId);
            }
            loaded = Math.max(0, access.loaded(gunStack));
            capacity = facts.capacity();
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
                GuardAmmoSummary.ReserveSource.PHYSICAL,
                reserve.compatibleStacks(),
                reserve.incompatibleAmmoStacks(),
                reserve.unknownAmmoStacks(),
                reserve.complete());
    }

    @Override
    public boolean knowsGunId(ResourceLocation gunId) {
        return BuiltInRegistries.ITEM.getOptional(gunId).map(item -> isGun(new ItemStack(item))).orElse(false);
    }

    @Override
    public boolean knowsAmmoId(ResourceLocation ammoId) {
        return BuiltInRegistries.ITEM.getOptional(ammoId).map(item -> isAmmoItem(new ItemStack(item))).orElse(false);
    }

    @Override
    public Optional<ItemStack> createGun(ResourceLocation gunId, @Nullable HolderLookup.Provider registries,
            boolean loaded) {
        if (!knowsGunId(gunId)) {
            return Optional.empty();
        }
        ItemStack gun = new ItemStack(BuiltInRegistries.ITEM.get(gunId));
        access.prepare(gun);
        if (loaded) {
            PointBlankAccess.Facts facts = access.facts(gun).orElse(null);
            if (facts == null) {
                return Optional.empty();
            }
            access.setLoaded(gun, facts.capacity());
        }
        return Optional.of(gun);
    }

    @Override
    public Optional<ItemStack> createAmmo(ResourceLocation ammoId, int count) {
        ItemStack ammo = knowsAmmoId(ammoId) ? new ItemStack(BuiltInRegistries.ITEM.get(ammoId)) : ItemStack.EMPTY;
        if (ammo.isEmpty() || count < 1 || count > ammo.getMaxStackSize()) {
            return Optional.empty();
        }
        ammo.setCount(count);
        return Optional.of(ammo);
    }

    @Override
    public boolean usesGenericHeldPose() {
        return true;
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

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
