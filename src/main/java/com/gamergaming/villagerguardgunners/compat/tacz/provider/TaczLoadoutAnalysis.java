package com.gamergaming.villagerguardgunners.compat.tacz.provider;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.combat.TaCZExplosionResolver;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.Analysis;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Builds and validates a TaCZ gun and a full stack of its ammo for a natural loadout. */
final class TaczLoadoutAnalysis {
    private TaczLoadoutAnalysis() {
    }

    static Analysis analyze(
            @Nullable LivingEntity context,
            ResourceLocation gunId,
            ExplosivePolicy explosivePolicy) {
        GunData gunData;
        String gunType;
        try {
            var resolvedGun = TimelessAPI.getCommonGunIndex(gunId);
            if (resolvedGun.isEmpty()) {
                return Analysis.failed(
                        CompatibilityStatus.GUN_DEFINITION_UNAVAILABLE,
                        gunId,
                        null,
                        0,
                        ExplosionState.NOT_EVALUATED,
                        0.0F);
            }
            gunType = resolvedGun.get().getType();
            gunData = resolvedGun.get().getGunData();
        } catch (RuntimeException exception) {
            return Analysis.failed(
                    CompatibilityStatus.GUN_DATA_UNREADABLE,
                    gunId,
                    null,
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        if (!hasRequiredGunData(gunData)) {
            return Analysis.failed(
                    CompatibilityStatus.GUN_DATA_UNREADABLE,
                    gunId,
                    null,
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        FireMode initialFireMode = gunData.getFireModeSet().get(0);
        ItemStack gunStack;
        try {
            gunStack = GunItemBuilder.create()
                    .setCount(1)
                    .setId(gunId)
                    .setFireMode(initialFireMode)
                    .setAmmoCount(gunData.getAmmoAmount())
                    .setAmmoInBarrel(true)
                    .setHeatData(gunData.hasHeatData())
                    .build(context == null ? null : context.registryAccess());
        } catch (RuntimeException exception) {
            return Analysis.failed(
                    CompatibilityStatus.GUN_BUILD_FAILED,
                    gunId,
                    gunData.getAmmoId(),
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        IGun gun = validateGun(gunStack, gunId, initialFireMode, gunData);
        if (gun == null) {
            return Analysis.failed(
                    CompatibilityStatus.GUN_BUILD_FAILED,
                    gunId,
                    gunData.getAmmoId(),
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        GuardAmmoSummary.Snapshot ammoUse = TaczAmmoSummary.capture(
                gunStack.copy(),
                gunData,
                null);
        if (!ammoUse.isReady()) {
            return Analysis.failed(
                    CompatibilityStatus.GUN_DATA_UNREADABLE,
                    gunId,
                    gunData.getAmmoId(),
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }
        if (ammoUse.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL) {
            return Analysis.failed(
                    CompatibilityStatus.PHYSICAL_AMMUNITION_UNSUPPORTED,
                    gunId,
                    gunData.getAmmoId(),
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        ResourceLocation ammoId = gunData.getAmmoId();
        CommonAmmoIndex ammoIndex;
        try {
            Optional<CommonAmmoIndex> resolvedAmmo = TimelessAPI.getCommonAmmoIndex(ammoId);
            if (resolvedAmmo.isEmpty()) {
                return Analysis.failed(
                        CompatibilityStatus.AMMO_DEFINITION_UNAVAILABLE,
                        gunId,
                        ammoId,
                        0,
                        ExplosionState.NOT_EVALUATED,
                        0.0F);
            }
            ammoIndex = resolvedAmmo.get();
        } catch (RuntimeException exception) {
            return Analysis.failed(
                    CompatibilityStatus.AMMO_DEFINITION_UNAVAILABLE,
                    gunId,
                    ammoId,
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        int fullStackSize;
        ItemStack ammoStack;
        try {
            fullStackSize = ammoIndex.getStackSize();
            if (fullStackSize <= 0) {
                return Analysis.failed(
                        CompatibilityStatus.AMMO_STACK_INVALID,
                        gunId,
                        ammoId,
                        0,
                        ExplosionState.NOT_EVALUATED,
                        0.0F);
            }
            ammoStack = AmmoItemBuilder.create()
                    .setId(ammoId)
                    .setCount(fullStackSize)
                    .build();
        } catch (RuntimeException exception) {
            return Analysis.failed(
                    CompatibilityStatus.AMMO_BUILD_FAILED,
                    gunId,
                    ammoId,
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        if (!isValidFullAmmoStack(ammoStack, ammoId, fullStackSize)) {
            return Analysis.failed(
                    CompatibilityStatus.AMMO_STACK_INVALID,
                    gunId,
                    ammoId,
                    fullStackSize,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }
        AmmoCompatibility.Assessment compatibility =
                AmmoCompatibility.assess(gunStack, ammoStack);
        if (compatibility.compatibility() == AmmoCompatibility.Compatibility.UNKNOWN) {
            return Analysis.failed(
                    CompatibilityStatus.AMMO_COMPATIBILITY_UNREADABLE,
                    gunId,
                    ammoId,
                    fullStackSize,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }
        if (!compatibility.isCompatible()
                || compatibility.kind() != AmmoCompatibility.AmmoKind.LOOSE
                || compatibility.infinite()
                || compatibility.units() != fullStackSize) {
            return Analysis.failed(
                    CompatibilityStatus.AMMO_INCOMPATIBLE,
                    gunId,
                    ammoId,
                    fullStackSize,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        ExplosionState explosionState = ExplosionState.NOT_EVALUATED;
        float explosionRadius = 0.0F;
        if (explosivePolicy != ExplosivePolicy.IGNORE) {
            if (context == null) {
                explosionState = ExplosionState.CONTEXT_REQUIRED;
                if (explosivePolicy == ExplosivePolicy.REJECT) {
                    return Analysis.failed(
                            CompatibilityStatus.EXPLOSIVE_CONTEXT_REQUIRED,
                            gunId,
                            ammoId,
                            fullStackSize,
                            explosionState,
                            explosionRadius);
                }
            } else {
                final TaCZExplosionResolver.Traits explosion;
                try {
                    explosion = resolveExplosion(context, gunStack, gun, gunData);
                } catch (RuntimeException exception) {
                    return Analysis.failed(
                            CompatibilityStatus.EXPLOSIVE_STATE_UNREADABLE,
                            gunId,
                            ammoId,
                            fullStackSize,
                            ExplosionState.UNREADABLE,
                            0.0F);
                }
                explosionState = explosion.enabled()
                        ? ExplosionState.EXPLOSIVE
                        : ExplosionState.NON_EXPLOSIVE;
                explosionRadius = explosion.radius();
                if (explosion.enabled() && explosivePolicy == ExplosivePolicy.REJECT) {
                    return Analysis.failed(
                            CompatibilityStatus.EXPLOSIVE_DISALLOWED,
                            gunId,
                            ammoId,
                            fullStackSize,
                            explosionState,
                            explosionRadius);
                }
            }
        }

        return Analysis.compatible(
                gunId,
                gunType,
                ammoId,
                fullStackSize,
                explosionState,
                explosionRadius,
                gunStack,
                ammoStack);
    }

    private static boolean hasRequiredGunData(@Nullable GunData gunData) {
        if (gunData == null
                || gunData.getAmmoId() == null
                || gunData.getAmmoAmount() < 1
                || gunData.getFireModeSet() == null
                || gunData.getFireModeSet().isEmpty()) {
            return false;
        }
        for (FireMode fireMode : gunData.getFireModeSet()) {
            if (fireMode == null || fireMode == FireMode.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable IGun validateGun(
            @Nullable ItemStack gunStack,
            ResourceLocation expectedGunId,
            FireMode expectedFireMode,
            GunData gunData) {
        if (gunStack == null
                || gunStack.isEmpty()
                || gunStack.getCount() != 1
                || !(gunStack.getItem() instanceof IGun gun)) {
            return null;
        }
        try {
            if (!expectedGunId.equals(gun.getGunId(gunStack))
                    || gun.getFireMode(gunStack) != expectedFireMode
                    || gun.getCurrentAmmoCount(gunStack) != gunData.getAmmoAmount()) {
                return null;
            }
            return gun;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isValidFullAmmoStack(
            @Nullable ItemStack ammoStack,
            ResourceLocation expectedAmmoId,
            int expectedCount) {
        if (ammoStack == null
                || ammoStack.isEmpty()
                || ammoStack.getCount() != expectedCount
                || expectedCount > ammoStack.getMaxStackSize()
                || !(ammoStack.getItem() instanceof IAmmo ammo)) {
            return false;
        }
        try {
            return expectedAmmoId.equals(ammo.getAmmoId(ammoStack));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Resolves attachment and runtime-script explosion behavior on a copy. */
    private static TaCZExplosionResolver.Traits resolveExplosion(
            LivingEntity context,
            ItemStack gunStack,
            IGun gun,
            GunData gunData) {
        ItemStack checkedGun = gunStack.copy();
        AttachmentCacheProperty cache = new AttachmentCacheProperty();
        cache.eval(checkedGun, gunData);
        return TaCZExplosionResolver.resolve(
                context,
                checkedGun,
                gun,
                gunData,
                cache);
    }
}
