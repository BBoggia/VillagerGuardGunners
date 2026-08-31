package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.sidearm.SidearmEligibility;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Shared strict compatibility boundary for format-3 natural sidearms. */
public final class NaturalGuardSidearmCompatibility {
    private NaturalGuardSidearmCompatibility() {
    }

    /** Context-free administrative analysis; explosive safety remains explicit. */
    public static Analysis analyze(@Nullable ResourceLocation sidearmId) {
        return analyze(
                null,
                sidearmId,
                NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REPORT);
    }

    /** Runtime analysis. REJECT is required before issuing a natural sidearm. */
    public static Analysis analyze(
            @Nullable LivingEntity context,
            @Nullable ResourceLocation sidearmId,
            NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy explosivePolicy) {
        Objects.requireNonNull(explosivePolicy, "explosivePolicy");
        NaturalGuardLoadoutCompatibilityAnalyzer.Analysis weapon =
                NaturalGuardLoadoutCompatibilityAnalyzer.analyze(
                        context,
                        sidearmId,
                        explosivePolicy);
        if (!weapon.compatible()) {
            return new Analysis(
                    weapon,
                    SidearmEligibility.EligibilityStatus.DEFINITION_UNAVAILABLE,
                    false);
        }

        boolean contextRequired = weapon.explosionState()
                == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState.CONTEXT_REQUIRED;
        boolean explosiveStateReadable = switch (weapon.explosionState()) {
            case NON_EXPLOSIVE, EXPLOSIVE -> true;
            default -> contextRequired;
        };
        SidearmEligibility.EligibilityStatus eligibility = SidearmEligibility.evaluate(
                sidearmId == null ? null : sidearmId.toString(),
                weapon.gunType(),
                true,
                weapon.preparedGun().getCount(),
                true,
                explosiveStateReadable,
                weapon.explosionState()
                        == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState.EXPLOSIVE,
                SidearmEligibility.DEFAULT_ELIGIBLE_GUN_TYPES,
                Set.of(),
                Set.of());
        return new Analysis(weapon, eligibility, contextRequired);
    }

    public record Analysis(
            NaturalGuardLoadoutCompatibilityAnalyzer.Analysis weapon,
            SidearmEligibility.EligibilityStatus eligibility,
            boolean contextRequired) {
        public Analysis {
            weapon = Objects.requireNonNull(weapon, "weapon");
            eligibility = Objects.requireNonNull(eligibility, "eligibility");
            if (contextRequired
                    != (weapon.compatible()
                            && weapon.explosionState()
                                    == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState
                                            .CONTEXT_REQUIRED)) {
                throw new IllegalArgumentException("Sidearm context state is inconsistent");
            }
        }

        public boolean eligible() {
            return weapon.compatible() && eligibility.eligible() && !contextRequired;
        }

        public boolean administrativelyCompatible() {
            return weapon.compatible() && eligibility.eligible();
        }
    }
}
