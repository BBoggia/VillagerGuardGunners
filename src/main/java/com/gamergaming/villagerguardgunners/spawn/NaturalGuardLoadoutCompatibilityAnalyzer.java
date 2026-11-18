package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Builds and validates a proposed natural gun/ammunition pair without
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
            // The gun mod that defines this ID builds and judges it.
            Optional<GunProvider> provider = GunProviders.forGunId(gunId);
            if (provider.isEmpty()) {
                return Analysis.failed(
                        CompatibilityStatus.GUN_DEFINITION_UNAVAILABLE,
                        gunId,
                        null,
                        0,
                        ExplosionState.NOT_EVALUATED,
                        0.0F);
            }
            return provider.get().analyzeLoadout(context, gunId, explosivePolicy);
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

        public static Analysis compatible(
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

        public static Analysis failed(
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
