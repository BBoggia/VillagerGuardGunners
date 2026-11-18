package com.gamergaming.villagerguardgunners.compat.tacz.provider;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.TaCZExplosionResolver;
import com.gamergaming.villagerguardgunners.guns.GunDescriptor;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunRef;
import com.gamergaming.villagerguardgunners.guns.WeaponDriver;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import tallestegg.guardvillagers.common.entities.Guard;

/** Timeless and Classics Zero. Guards fire through TaCZ's own gun operator. */
public final class TaczGunProvider implements GunProvider {
    public static final String ID = "tacz";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof IGun;
    }

    /** True for item classes TaCZ treats as loose ammo or an ammo box. */
    public static boolean isAmmoType(Class<?> itemType) {
        return IAmmo.class.isAssignableFrom(itemType) || IAmmoBox.class.isAssignableFrom(itemType);
    }

    @Override
    public boolean isAmmoItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof IAmmo || stack.getItem() instanceof IAmmoBox);
    }

    @Override
    public Optional<GunRef> ref(ItemStack gunStack) {
        if (!(gunStack.getItem() instanceof IGun gun)) {
            return Optional.empty();
        }
        try {
            ResourceLocation gunId = gun.getGunId(gunStack);
            return gunId == null ? Optional.empty() : Optional.of(new GunRef(ID, gunId));
        } catch (RuntimeException exception) {
            // Malformed gun data from a third-party pack reads as unidentified, not as a crash.
            return Optional.empty();
        }
    }

    @Override
    public AmmoCompatibility.Assessment assessAmmo(ItemStack gunStack, ItemStack candidate) {
        return TaczAmmoCompatibility.assess(gunStack, candidate);
    }

    @Override
    public Optional<GunDescriptor> describe(ItemStack gunStack) {
        if (!(gunStack.getItem() instanceof IGun gun)) {
            return Optional.empty();
        }
        try {
            ResourceLocation gunId = gun.getGunId(gunStack);
            var index = gunId == null ? null : TimelessAPI.getCommonGunIndex(gunId).orElse(null);
            if (index == null) {
                return Optional.empty();
            }
            GunData gunData = index.getGunData();
            return Optional.of(new GunDescriptor(
                    new GunRef(ID, gunId),
                    index.getType(),
                    gunData == null ? null : gunData.getAmmoId(),
                    gunData != null));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Uses an isolated data holder so gun scripts cannot inspect the guard's
     * currently equipped weapon while a stored one is being judged.
     */
    @Override
    public Optional<Boolean> isExplosive(Guard guard, ItemStack gunStack) {
        if (!(gunStack.getItem() instanceof IGun gun)) {
            return Optional.empty();
        }
        try {
            GunData gunData = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack)).orElseThrow().getGunData();
            ItemStack checkedGun = gunStack.copy();
            AttachmentCacheProperty cache = new AttachmentCacheProperty();
            cache.eval(checkedGun, gunData);
            ShooterDataHolder isolatedData = new ShooterDataHolder();
            isolatedData.currentGunItem = () -> checkedGun;
            isolatedData.cacheProperty = cache;
            return Optional.of(TaCZExplosionResolver.resolveWithDataHolder(
                    isolatedData,
                    guard,
                    checkedGun,
                    gun,
                    gunData,
                    cache).enabled());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResourceLocation> ammoId(ItemStack ammoStack) {
        if (ammoStack.getItem() instanceof IAmmo ammo) {
            return Optional.ofNullable(ammo.getAmmoId(ammoStack));
        }
        if (ammoStack.getItem() instanceof IAmmoBox box) {
            return Optional.ofNullable(box.getAmmoId(ammoStack));
        }
        return Optional.empty();
    }

    @Override
    public boolean isKnownUsableSupply(ItemStack stack) {
        ResourceLocation ammoId;
        if (stack.getItem() instanceof IAmmo ammo) {
            ammoId = ammo.getAmmoId(stack);
        } else if (stack.getItem() instanceof IAmmoBox box) {
            if (box.isAllTypeCreative(stack)) {
                return true;
            }
            if (!box.isCreative(stack) && box.getAmmoCount(stack) <= 0) {
                return false;
            }
            ammoId = box.getAmmoId(stack);
        } else {
            return false;
        }
        return ammoId != null && TimelessAPI.getCommonAmmoIndex(ammoId).isPresent();
    }

    @Override
    public boolean isLooseAmmo(ItemStack stack) {
        return stack.getItem() instanceof IAmmo && !(stack.getItem() instanceof IAmmoBox);
    }

    /**
     * Depleted finite boxes are excluded, while TaCZ's loose-ammo precedence is
     * kept for unusual items that implement both ammunition interfaces.
     */
    @Override
    public boolean isUsableSupply(ItemStack stack) {
        if (stack.getItem() instanceof IAmmo) {
            return true;
        }
        if (!(stack.getItem() instanceof IAmmoBox ammoBox)) {
            return false;
        }
        try {
            ItemStack checkedBox = stack.copy();
            return ammoBox.isAllTypeCreative(checkedBox)
                    || ammoBox.isCreative(checkedBox)
                    || ammoBox.getAmmoCount(checkedBox) > 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** TaCZ keeps a depleted finite ammo box as an empty item. */
    @Override
    public boolean isSpentAmmoContainer(ItemStack stack) {
        if (stack.getItem() instanceof IAmmo || !(stack.getItem() instanceof IAmmoBox ammoBox)) {
            return false;
        }
        try {
            ItemStack checkedBox = stack.copy();
            return !ammoBox.isAllTypeCreative(checkedBox)
                    && !ammoBox.isCreative(checkedBox)
                    && ammoBox.getAmmoCount(checkedBox) <= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public GuardAmmoSummary.Snapshot captureAmmo(ItemStack gunStack, @Nullable IItemHandler reserveHandler) {
        return TaczAmmoSummary.capture(gunStack, reserveHandler);
    }

    @Override
    public boolean supportsCreativeSupply() {
        return true;
    }

    @Override
    public Optional<ItemStack> creativeSupply(ItemStack gunStack) {
        return Optional.of(TaczInfiniteAmmo.creativeBox(gunStack));
    }

    @Override
    public WeaponDriver createDriver(Guard guard) {
        return new NativeGunController(guard);
    }

    @Override
    public boolean knowsGunId(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId).isPresent();
    }

    @Override
    public boolean knowsAmmoId(ResourceLocation ammoId) {
        return TimelessAPI.getCommonAmmoIndex(ammoId).isPresent();
    }

    @Override
    public Optional<ItemStack> createGun(ResourceLocation gunId, @Nullable HolderLookup.Provider registries,
            boolean loaded) {
        var index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) {
            return Optional.empty();
        }
        GunItemBuilder builder = GunItemBuilder.create().setId(gunId).setCount(1);
        if (loaded) {
            GunData data = index.getGunData();
            builder.setFireMode(data.getFireModeSet().get(0))
                    .setAmmoCount(data.getAmmoAmount())
                    .setAmmoInBarrel(true)
                    .setHeatData(data.hasHeatData());
        } else {
            builder.setAmmoCount(0).setAmmoInBarrel(false);
        }
        return Optional.of(builder.build(registries));
    }

    @Override
    public Optional<ItemStack> createAmmo(ResourceLocation ammoId, int count) {
        if (TimelessAPI.getCommonAmmoIndex(ammoId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(AmmoItemBuilder.create().setId(ammoId).setCount(count).build());
    }

    @Override
    public boolean isDefined(ItemStack gunOrAmmo) {
        if (gunOrAmmo.getItem() instanceof IGun gun) {
            return TimelessAPI.getCommonGunIndex(gun.getGunId(gunOrAmmo)).isPresent();
        }
        if (gunOrAmmo.getItem() instanceof IAmmo ammo) {
            return TimelessAPI.getCommonAmmoIndex(ammo.getAmmoId(gunOrAmmo)).isPresent();
        }
        return true;
    }

    @Override
    public NaturalGuardLoadoutCompatibilityAnalyzer.Analysis analyzeLoadout(@Nullable LivingEntity context,
            ResourceLocation gunId, NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy explosivePolicy) {
        return TaczLoadoutAnalysis.analyze(context, gunId, explosivePolicy);
    }

    @Override
    public void onGuardCreated(Guard guard) {
        IGunOperator.fromLivingEntity(guard).initialData();
    }

    @Override
    public void interrupt(Guard guard) {
        IGunOperator operator = IGunOperator.fromLivingEntity(guard);
        operator.cancelReload();
        operator.aim(false);
    }

    @Override
    public void clearPendingActions(Guard guard) {
        var data = IGunOperator.fromLivingEntity(guard).getDataHolder();
        data.meleePrepTickCount = -1;
        data.isBolting = false;
        data.boltTimestamp = -1;
    }

    @Override
    public boolean isAiming(Guard guard) {
        // The synchronized accessor is updated after AI goals tick;
        // the server-owned holder changes immediately in aim(...).
        return IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming;
    }

    /** Unreadable TaCZ state is busy so logistics never interrupts it. */
    @Override
    public boolean isWeaponBusy(Guard guard) {
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(guard);
            var data = operator.getDataHolder();
            return data.reloadStateType == null
                    || data.reloadStateType.isReloading()
                    || data.isBolting
                    || data.meleePrepTickCount >= 0
                    || data.chargeProgress > 0.0F
                    || operator.getSynReloadState().getStateType().isReloading()
                    || operator.getSynIsBolting()
                    || operator.getSynMeleeCoolDown() > 0L
                    || operator.getSynDrawCoolDown() > 0L
                    || operator.getSynShootCoolDown() > 0L;
        } catch (RuntimeException exception) {
            return true;
        }
    }
}
