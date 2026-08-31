package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.combat.TaCZExplosionResolver;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Builds and validates a proposed natural TaCZ gun/ammunition pair without
 * changing an entity, inventory, population index, or provenance record.
 *
 * <p>The returned stacks are defensive copies. Issuance consumes those copies
 * only after compatibility succeeds, then applies its separate capacity,
 * provenance, commit, and rollback boundaries.</p>
 */
public final class NaturalGuardLoadoutCompatibilityAnalyzer {
    private NaturalGuardLoadoutCompatibilityAnalyzer() {
    }

    /** Context-free analysis for future validation commands. */
    public static Analysis analyze(@Nullable ResourceLocation gunId) {
        return analyze(null, gunId, ExplosivePolicy.REPORT);
    }

    /**
     * Analyzes one gun under an explicit explosive-policy requirement.
     *
     * <p>{@link ExplosivePolicy#IGNORE} deliberately skips attachment and
     * runtime-script evaluation. This preserves the existing issuance path
     * when explosive natural loadouts are enabled. REPORT remains compatible
     * without a living context but marks the explosive state context-dependent;
     * REJECT requires a living context so it can fail closed.</p>
     */
    public static Analysis analyze(
            @Nullable LivingEntity context,
            @Nullable ResourceLocation gunId,
            ExplosivePolicy explosivePolicy) {
        Objects.requireNonNull(explosivePolicy, "explosivePolicy");
        if (gunId == null) {
            return Analysis.failed(
                    CompatibilityStatus.INVALID_REQUEST,
                    null,
                    null,
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }

        try {
            return analyzeSafely(context, gunId, explosivePolicy);
        } catch (RuntimeException exception) {
            return Analysis.failed(
                    CompatibilityStatus.GUN_DATA_UNREADABLE,
                    gunId,
                    null,
                    0,
                    ExplosionState.NOT_EVALUATED,
                    0.0F);
        }
    }

    private static Analysis analyzeSafely(
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
                    .build();
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

        GuardAmmoSummary.Snapshot ammoUse = GuardAmmoSummary.capture(
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

    public enum ExplosivePolicy {
        /** Preserve enabled-explosives behavior without evaluating scripts. */
        IGNORE,
        /** Describe the state when context is available; otherwise mark it context-dependent. */
        REPORT,
        /** Resolve the state with a living context and reject explosive guns. */
        REJECT
    }

    public enum ExplosionState {
        NOT_EVALUATED,
        CONTEXT_REQUIRED,
        NON_EXPLOSIVE,
        EXPLOSIVE,
        UNREADABLE
    }

    public enum CompatibilityStatus {
        COMPATIBLE,
        INVALID_REQUEST,
        GUN_DEFINITION_UNAVAILABLE,
        GUN_DATA_UNREADABLE,
        GUN_BUILD_FAILED,
        PHYSICAL_AMMUNITION_UNSUPPORTED,
        AMMO_DEFINITION_UNAVAILABLE,
        AMMO_BUILD_FAILED,
        AMMO_STACK_INVALID,
        AMMO_COMPATIBILITY_UNREADABLE,
        AMMO_INCOMPATIBLE,
        EXPLOSIVE_CONTEXT_REQUIRED,
        EXPLOSIVE_STATE_UNREADABLE,
        EXPLOSIVE_DISALLOWED
    }

    /** Immutable analysis; prepared item accessors always return defensive copies. */
    public record Analysis(
            CompatibilityStatus status,
            @Nullable ResourceLocation gunId,
            @Nullable String gunType,
            @Nullable ResourceLocation ammoId,
            int ammunitionItems,
            ExplosionState explosionState,
            float explosionRadius,
            ItemStack preparedGun,
            ItemStack preparedAmmunition) {
        public Analysis {
            status = Objects.requireNonNull(status, "status");
            explosionState = Objects.requireNonNull(explosionState, "explosionState");
            if (ammunitionItems < 0
                    || !Float.isFinite(explosionRadius)
                    || explosionRadius < 0.0F) {
                throw new IllegalArgumentException("Analysis contains invalid numeric data");
            }
            preparedGun = copyOrEmpty(preparedGun);
            preparedAmmunition = copyOrEmpty(preparedAmmunition);
            boolean compatible = status == CompatibilityStatus.COMPATIBLE;
            if (compatible != (!preparedGun.isEmpty() && !preparedAmmunition.isEmpty())
                    || (compatible && (gunId == null
                            || ammoId == null
                            || preparedGun.getCount() != 1
                            || ammunitionItems <= 0
                            || preparedAmmunition.getCount() != ammunitionItems))
                    || (!compatible && (!preparedGun.isEmpty()
                            || !preparedAmmunition.isEmpty()))) {
                throw new IllegalArgumentException("Analysis has inconsistent prepared items");
            }
            if (explosionState != ExplosionState.EXPLOSIVE && explosionRadius != 0.0F) {
                throw new IllegalArgumentException(
                        "Only an explosive analysis may contain an explosion radius");
            }
        }

        public boolean compatible() {
            return status == CompatibilityStatus.COMPATIBLE;
        }

        @Override
        public ItemStack preparedGun() {
            return preparedGun.copy();
        }

        @Override
        public ItemStack preparedAmmunition() {
            return preparedAmmunition.copy();
        }

        private static Analysis compatible(
                ResourceLocation gunId,
                @Nullable String gunType,
                ResourceLocation ammoId,
                int ammunitionItems,
                ExplosionState explosionState,
                float explosionRadius,
                ItemStack preparedGun,
                ItemStack preparedAmmunition) {
            return new Analysis(
                    CompatibilityStatus.COMPATIBLE,
                    gunId,
                    gunType,
                    ammoId,
                    ammunitionItems,
                    explosionState,
                    explosionRadius,
                    preparedGun,
                    preparedAmmunition);
        }

        private static Analysis failed(
                CompatibilityStatus status,
                @Nullable ResourceLocation gunId,
                @Nullable ResourceLocation ammoId,
                int ammunitionItems,
                ExplosionState explosionState,
                float explosionRadius) {
            if (status == CompatibilityStatus.COMPATIBLE) {
                throw new IllegalArgumentException("A compatible analysis cannot be a failure");
            }
            return new Analysis(
                    status,
                    gunId,
                    null,
                    ammoId,
                    ammunitionItems,
                    explosionState,
                    explosionRadius,
                    ItemStack.EMPTY,
                    ItemStack.EMPTY);
        }

        private static ItemStack copyOrEmpty(@Nullable ItemStack stack) {
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }
}
