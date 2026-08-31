package com.gamergaming.villagerguardgunners.sidearm;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.combat.TaCZExplosionResolver;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** On-demand compatibility and ammunition check for one stored sidearm. */
public final class SidearmRuntimeAssessment {
    private SidearmRuntimeAssessment() {
    }

    public static Assessment assess(Guard guard, ItemStack sidearm) {
        WeaponAssessment weapon = assessWeapon(guard, sidearm);
        if (!weapon.eligibility().eligible() || weapon.gunData() == null) {
            return new Assessment(weapon.eligibility(), false);
        }

        GuardAmmoSummary.Snapshot ammunition = GuardAmmoSummary.capture(
                sidearm.copy(),
                weapon.gunData(),
                GuardAmmoAccess.getTaCZInventoryHandler(guard));
        boolean ammunitionReady = ammunition.isReady()
                && (ammunition.loadedAmmo() > 0
                        || ammunition.chambered()
                        || ammunition.hasCompatibleReserve());
        return new Assessment(weapon.eligibility(), ammunitionReady);
    }

    /**
     * Performs only the structural, definition, type, and explosive checks.
     * Inventory slots use this event-driven boundary without scanning reserve
     * ammunition merely to decide whether one gun may be configured.
     */
    public static SidearmEligibility.EligibilityStatus assessEligibility(
            Guard guard,
            ItemStack sidearm) {
        return assessWeapon(guard, sidearm).eligibility();
    }

    private static WeaponAssessment assessWeapon(Guard guard, ItemStack sidearm) {
        if (guard == null
                || sidearm == null
                || sidearm.isEmpty()
                || !(sidearm.getItem() instanceof IGun gun)) {
            return new WeaponAssessment(
                    SidearmEligibility.EligibilityStatus.NOT_GUN,
                    null);
        }

        ResourceLocation gunId;
        String gunType;
        GunData gunData;
        try {
            gunId = gun.getGunId(sidearm);
            var index = gunId == null
                    ? null
                    : TimelessAPI.getCommonGunIndex(gunId).orElse(null);
            gunType = index == null ? null : index.getType();
            gunData = index == null ? null : index.getGunData();
        } catch (RuntimeException exception) {
            return new WeaponAssessment(
                    SidearmEligibility.EligibilityStatus.DEFINITION_UNAVAILABLE,
                    null);
        }

        if (gunId == null || gunData == null) {
            return new WeaponAssessment(
                    SidearmEligibility.EligibilityStatus.DEFINITION_UNAVAILABLE,
                    null);
        }

        final TaCZExplosionResolver.Traits explosion;
        try {
            ItemStack checkedSidearm = sidearm.copy();
            AttachmentCacheProperty cache = new AttachmentCacheProperty();
            cache.eval(checkedSidearm, gunData);
            ShooterDataHolder isolatedData = new ShooterDataHolder();
            isolatedData.currentGunItem = () -> checkedSidearm;
            isolatedData.cacheProperty = cache;
            explosion = TaCZExplosionResolver.resolveWithDataHolder(
                    isolatedData,
                    guard,
                    checkedSidearm,
                    gun,
                    gunData,
                    cache);
        } catch (RuntimeException exception) {
            return new WeaponAssessment(
                    SidearmEligibility.EligibilityStatus.EXPLOSIVE_STATE_UNREADABLE,
                    null);
        }

        SidearmEligibility.EligibilityStatus eligibility = SidearmEligibility.evaluate(
                gunId.toString(),
                gunType,
                true,
                sidearm.getCount(),
                true,
                true,
                explosion.enabled(),
                SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                Set.of(),
                Set.of());
        if (!eligibility.eligible()) {
            return new WeaponAssessment(eligibility, null);
        }
        return new WeaponAssessment(eligibility, gunData);
    }

    private record WeaponAssessment(
            SidearmEligibility.EligibilityStatus eligibility,
            GunData gunData) {
    }

    public record Assessment(
            SidearmEligibility.EligibilityStatus eligibility,
            boolean ammunitionReady) {
        public boolean eligible() {
            return eligibility != null && eligibility.eligible();
        }
    }
}
