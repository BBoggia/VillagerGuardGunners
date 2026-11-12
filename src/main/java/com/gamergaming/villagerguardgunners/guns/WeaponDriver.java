package com.gamergaming.villagerguardgunners.guns;

import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Operates one guard's held gun on behalf of the combat goal.
 *
 * <p>The goal decides where to look, where to stand, and whether the trigger
 * may be pressed. A driver turns that into its gun mod's firing, reloading,
 * and timing, and reports the weapon's state back in gun-mod-neutral terms.
 * Every method must be safe to call when the guard holds no gun.</p>
 */
public interface WeaponDriver {
    // Trigger

    void startAiming();

    void stopAiming();

    /**
     * Advances the weapon by one server tick. Maintenance such as reloading
     * continues without a target or an aimed pose.
     *
     * @param safeToFire evaluated before a trigger pull
     * @param burstSafetyCheck evaluated again before each later round of the same pull
     */
    void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck, boolean aimAtTarget);

    default void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck) {
        tick(target, mayFire, safeToFire, burstSafetyCheck, true);
    }

    default void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire) {
        tick(target, mayFire, safeToFire, safeToFire);
    }

    // Readiness

    /** Cheap snapshot of bound state. Never scans reserves. */
    WeaponReadiness captureReadiness();

    boolean hasCurrentWeaponData(ItemStack heldStack);

    /** True only after reloading and ammo sharing have both failed. */
    boolean isCurrentWeaponDryConfirmed();

    /** Status shown in the guard inventory. Reads only; never changes combat state. */
    GuardCombatStatus.WeaponActivity getPlayerFacingActivity();

    // Tactical envelope

    double getPreferredApproachDistance();

    double getRetreatDistance();

    int getRequiredVisibleTicks();

    double getAimToleranceDegrees();

    double getMinimumFiringRange();

    float getMaximumFiringRange();

    boolean hasValidFiringWindow();

    /** Hot-loop gate. Missing or stale weapon data must return false. */
    boolean canFireAtDistanceSquared(double distanceSquared);

    boolean isCurrentWeaponExplosive();

    boolean isWeaponAllowedByOrders();

    // Aim and shot safety

    Vec3 intentionalAimPoint(LivingEntity target);

    /** Recent safety result for display. Never permission to fire. */
    GuardShotSafety.Result currentShotSafety(LivingEntity target);

    GuardShotSafety.Result lastShotSafety();

    LivingEntity lastShotTarget();

    long lastShotGameTick();

    // Gates for sidearms, Quartermaster service, and coverage movement

    boolean isEquipmentServiceSafe();

    boolean isCoverageMovementSafe();

    boolean isSidearmSwapSafe();

    boolean isCurrentWeaponSidearmCompatible();

    void prepareForSidearmTransition();

    void refreshAfterSidearmTransaction();
}
