package com.gamergaming.villagerguardgunners.guns;

import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * The combat goal's single handle on a guard's weapon.
 *
 * <p>Every call is forwarded to the driver whose provider owns the stack in the
 * guard's main hand at that moment. A guard can therefore be handed a gun from
 * a different mod, or swap to a sidearm from one, without the goal noticing.</p>
 */
public final class GuardWeaponControl implements WeaponDriver {
    private final Guard guard;
    private final DriverSwitch drivers;
    private ItemStack resolvedStack;

    public GuardWeaponControl(Guard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
        // The fallback driver exists from construction, as the goal's controller always has.
        this.drivers = new DriverSwitch(GunProviders.fallback(), provider -> provider.createDriver(guard));
        ((GuardWeaponControlAccess) guard).villagerGuardGunners$setWeaponControl(this);
    }

    /** Gun ownership is independent of any driver's retry or refresh timers. */
    public boolean canUseGunGoal(ItemStack heldStack) {
        return GunProviders.isGun(heldStack);
    }

    /** The driver for the stack currently in the guard's main hand. */
    public WeaponDriver active() {
        ItemStack held = guard.getMainHandItem();
        if (held != resolvedStack) {
            resolvedStack = held;
            drivers.select(GunProviders.find(held));
        }
        return drivers.current();
    }

    /** A driver this guard has already created, for callers that need one provider's own detail. */
    public <T extends WeaponDriver> Optional<T> driver(Class<T> type) {
        return drivers.driver(type);
    }

    /** The driver this guard has already created for a provider, for that provider's own lifecycle calls. */
    public Optional<WeaponDriver> driverFor(String providerId) {
        return drivers.driver(providerId);
    }

    @Override
    public void startAiming() {
        active().startAiming();
    }

    @Override
    public void stopAiming() {
        active().stopAiming();
    }

    @Override
    public void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck, boolean aimAtTarget) {
        active().tick(target, mayFire, safeToFire, burstSafetyCheck, aimAtTarget);
    }

    @Override
    public WeaponReadiness captureReadiness() {
        return active().captureReadiness();
    }

    @Override
    public boolean hasCurrentWeaponData(ItemStack heldStack) {
        return active().hasCurrentWeaponData(heldStack);
    }

    @Override
    public boolean isCurrentWeaponDryConfirmed() {
        return active().isCurrentWeaponDryConfirmed();
    }

    @Override
    public GuardCombatStatus.WeaponActivity getPlayerFacingActivity() {
        return active().getPlayerFacingActivity();
    }

    @Override
    public double getPreferredApproachDistance() {
        return active().getPreferredApproachDistance();
    }

    @Override
    public double getRetreatDistance() {
        return active().getRetreatDistance();
    }

    @Override
    public int getRequiredVisibleTicks() {
        return active().getRequiredVisibleTicks();
    }

    @Override
    public double getAimToleranceDegrees() {
        return active().getAimToleranceDegrees();
    }

    @Override
    public double getMinimumFiringRange() {
        return active().getMinimumFiringRange();
    }

    @Override
    public float getMaximumFiringRange() {
        return active().getMaximumFiringRange();
    }

    @Override
    public boolean hasValidFiringWindow() {
        return active().hasValidFiringWindow();
    }

    @Override
    public boolean canFireAtDistanceSquared(double distanceSquared) {
        return active().canFireAtDistanceSquared(distanceSquared);
    }

    @Override
    public boolean isCurrentWeaponExplosive() {
        return active().isCurrentWeaponExplosive();
    }

    @Override
    public boolean isWeaponAllowedByOrders() {
        return active().isWeaponAllowedByOrders();
    }

    @Override
    public Vec3 intentionalAimPoint(LivingEntity target) {
        return active().intentionalAimPoint(target);
    }

    @Override
    public GuardShotSafety.Result currentShotSafety(LivingEntity target) {
        return active().currentShotSafety(target);
    }

    @Override
    public GuardShotSafety.Result lastShotSafety() {
        return active().lastShotSafety();
    }

    @Override
    public LivingEntity lastShotTarget() {
        return active().lastShotTarget();
    }

    @Override
    public long lastShotGameTick() {
        return active().lastShotGameTick();
    }

    @Override
    public boolean isEquipmentServiceSafe() {
        return active().isEquipmentServiceSafe();
    }

    @Override
    public boolean isCoverageMovementSafe() {
        return active().isCoverageMovementSafe();
    }

    @Override
    public boolean isSidearmSwapSafe() {
        return active().isSidearmSwapSafe();
    }

    @Override
    public boolean isCurrentWeaponSidearmCompatible() {
        return active().isCurrentWeaponSidearmCompatible();
    }

    @Override
    public void prepareForSidearmTransition() {
        active().prepareForSidearmTransition();
    }

    @Override
    public void refreshAfterSidearmTransaction() {
        active().refreshAfterSidearmTransaction();
    }
}
