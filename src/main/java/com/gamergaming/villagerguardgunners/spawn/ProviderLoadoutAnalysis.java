package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.guns.GunDescriptor;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.Analysis;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Builds a loaded gun and a full stack of its ammo through a provider's
 * general-purpose methods. Used by every gun mod that needs nothing more
 * specific; a provider may replace it with its own analysis.
 */
public final class ProviderLoadoutAnalysis {
    private ProviderLoadoutAnalysis() {
    }

    public static Analysis analyze(GunProvider provider, @Nullable LivingEntity context,
            ResourceLocation gunId, ExplosivePolicy explosivePolicy) {
        if (!provider.knowsGunId(gunId)) {
            return failed(CompatibilityStatus.GUN_DEFINITION_UNAVAILABLE, gunId, null, 0);
        }
        ItemStack gun;
        try {
            gun = provider.createGun(gunId, context == null ? null : context.registryAccess(), true)
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            gun = ItemStack.EMPTY;
        }
        if (gun.isEmpty() || gun.getCount() != 1 || !provider.isGun(gun)) {
            return failed(CompatibilityStatus.GUN_BUILD_FAILED, gunId, null, 0);
        }

        // A gun the provider cannot fire for a guard is never issued.
        GunDescriptor descriptor = provider.describe(gun).orElse(null);
        if (descriptor == null || !descriptor.firingDataPresent() || descriptor.ammoId() == null) {
            return failed(CompatibilityStatus.GUN_DATA_UNREADABLE, gunId, null, 0);
        }
        ResourceLocation ammoId = descriptor.ammoId();
        if (!provider.knowsAmmoId(ammoId)) {
            return failed(CompatibilityStatus.AMMO_DEFINITION_UNAVAILABLE, gunId, ammoId, 0);
        }

        ItemStack ammo;
        int fullStack;
        try {
            fullStack = provider.createAmmo(ammoId, 1).map(ItemStack::getMaxStackSize).orElse(0);
            if (fullStack <= 0) {
                return failed(CompatibilityStatus.AMMO_STACK_INVALID, gunId, ammoId, 0);
            }
            ammo = provider.createAmmo(ammoId, fullStack).orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            return failed(CompatibilityStatus.AMMO_BUILD_FAILED, gunId, ammoId, 0);
        }
        if (ammo.isEmpty() || ammo.getCount() != fullStack) {
            return failed(CompatibilityStatus.AMMO_STACK_INVALID, gunId, ammoId, fullStack);
        }

        AmmoCompatibility.Assessment compatibility = AmmoCompatibility.assess(gun, ammo);
        if (compatibility.compatibility() == AmmoCompatibility.Compatibility.UNKNOWN) {
            return failed(CompatibilityStatus.AMMO_COMPATIBILITY_UNREADABLE, gunId, ammoId, fullStack);
        }
        if (!compatibility.isCompatible()
                || compatibility.kind() != AmmoCompatibility.AmmoKind.LOOSE
                || compatibility.infinite()
                || compatibility.units() != fullStack) {
            return failed(CompatibilityStatus.AMMO_INCOMPATIBLE, gunId, ammoId, fullStack);
        }

        ExplosionState explosionState = ExplosionState.NOT_EVALUATED;
        if (explosivePolicy != ExplosivePolicy.IGNORE) {
            Optional<Boolean> explosive;
            try {
                explosive = provider.isExplosive(context instanceof Guard guard ? guard : null, gun);
            } catch (RuntimeException exception) {
                explosive = Optional.empty();
            }
            if (explosive.isEmpty()) {
                return new Analysis(CompatibilityStatus.EXPLOSIVE_STATE_UNREADABLE, gunId, null, ammoId,
                        fullStack, ExplosionState.UNREADABLE, 0.0F, ItemStack.EMPTY, ItemStack.EMPTY);
            }
            explosionState = explosive.get() ? ExplosionState.EXPLOSIVE : ExplosionState.NON_EXPLOSIVE;
            if (explosive.get() && explosivePolicy == ExplosivePolicy.REJECT) {
                return new Analysis(CompatibilityStatus.EXPLOSIVE_DISALLOWED, gunId, null, ammoId,
                        fullStack, explosionState, 0.0F, ItemStack.EMPTY, ItemStack.EMPTY);
            }
        }
        return Analysis.compatible(gunId, descriptor.type(), ammoId, fullStack, explosionState, 0.0F, gun, ammo);
    }

    private static Analysis failed(CompatibilityStatus status, ResourceLocation gunId,
            @Nullable ResourceLocation ammoId, int ammunitionItems) {
        return Analysis.failed(status, gunId, ammoId, ammunitionItems, ExplosionState.NOT_EVALUATED, 0.0F);
    }
}
