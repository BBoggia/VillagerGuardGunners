package com.gamergaming.villagerguardgunners.sidearm;

import com.gamergaming.villagerguardgunners.guns.GunDescriptor;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.common.entities.Guard;

/** On-demand compatibility and ammunition check for one stored sidearm. */
public final class SidearmRuntimeAssessment {
    private SidearmRuntimeAssessment() {
    }

    public static Assessment assess(Guard guard, ItemStack sidearm) {
        SidearmEligibility.EligibilityStatus eligibility = assessEligibility(guard, sidearm);
        if (!eligibility.eligible()) {
            return new Assessment(eligibility, false);
        }

        GuardAmmoSummary.Snapshot ammunition = GuardAmmoSummary.captureForGuard(guard,
                sidearm.copy(),
                GuardAmmoAccess.getTaCZInventoryHandler(guard));
        boolean ammunitionReady = ammunition.isReady()
                && (ammunition.loadedAmmo() > 0
                        || ammunition.chambered()
                        || ammunition.hasCompatibleReserve());
        return new Assessment(eligibility, ammunitionReady);
    }

    /**
     * Performs only the structural, definition, type, and explosive checks.
     * Inventory slots use this event-driven boundary without scanning reserve
     * ammunition merely to decide whether one gun may be configured.
     */
    public static SidearmEligibility.EligibilityStatus assessEligibility(
            Guard guard,
            ItemStack sidearm) {
        if (guard == null || sidearm == null || sidearm.isEmpty()) {
            return SidearmEligibility.EligibilityStatus.NOT_GUN;
        }
        GunProvider provider = GunProviders.find(sidearm).orElse(null);
        if (provider == null) {
            return SidearmEligibility.EligibilityStatus.NOT_GUN;
        }
        GunDescriptor gun = provider.describe(sidearm).orElse(null);
        if (gun == null || !gun.firingDataPresent()) {
            return SidearmEligibility.EligibilityStatus.DEFINITION_UNAVAILABLE;
        }
        Boolean explosive = provider.isExplosive(guard, sidearm).orElse(null);
        if (explosive == null) {
            return SidearmEligibility.EligibilityStatus.EXPLOSIVE_STATE_UNREADABLE;
        }
        return SidearmEligibility.evaluate(
                gun.ref().id().toString(),
                gun.type(),
                true,
                sidearm.getCount(),
                true,
                true,
                explosive,
                SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                Set.of(),
                Set.of());
    }

    public record Assessment(
            SidearmEligibility.EligibilityStatus eligibility,
            boolean ammunitionReady) {
        public boolean eligible() {
            return eligibility != null && eligibility.eligible();
        }
    }
}
