package com.gamergaming.villagerguardgunners.guns;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticsState;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Stands in when no supported gun mod is installed. It owns no item, and its
 * driver reports an unbound weapon, so guards behave as ordinary Guard
 * Villagers. It is never listed among the registered providers.
 */
final class NoGunProvider implements GunProvider {
    static final NoGunProvider INSTANCE = new NoGunProvider();

    private NoGunProvider() {
    }

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean isGun(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isAmmoItem(ItemStack stack) {
        return false;
    }

    @Override
    public Optional<GunRef> ref(ItemStack gunStack) {
        return Optional.empty();
    }

    @Override
    public Optional<GunDescriptor> describe(ItemStack gunStack) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> isExplosive(Guard guard, ItemStack gunStack) {
        return Optional.empty();
    }

    @Override
    public AmmoCompatibility.Assessment assessAmmo(ItemStack gunStack, ItemStack candidate) {
        return AmmoCompatibility.Assessment.notAmmunition();
    }

    @Override
    public GuardAmmoSummary.Snapshot captureAmmo(ItemStack gunStack, @Nullable IItemHandler reserveHandler) {
        return GuardAmmoSummary.Snapshot.unavailable(GuardAmmoSummary.Status.NO_GUN, null);
    }

    @Override
    public WeaponDriver createDriver(Guard guard) {
        return new NoWeaponDriver(guard);
    }

    /** Never fires. Movement and timing values are the configured fallbacks. */
    private static final class NoWeaponDriver implements WeaponDriver {
        private static final GuardShotSafety.Result UNCHECKED_SHOT =
                new GuardShotSafety.Result(GuardShotSafety.Status.NOT_EVALUATED, -1, null, null);

        private final WeaponTacticsState tactics;

        NoWeaponDriver(Guard guard) {
            this.tactics = WeaponTacticsState.forGuard(guard, ModConfigs.GUARD_GUNNERS);
        }

        @Override
        public void startAiming() {
        }

        @Override
        public void stopAiming() {
        }

        @Override
        public void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire,
                BooleanSupplier burstSafetyCheck, boolean aimAtTarget) {
        }

        @Override
        public WeaponReadiness captureReadiness() {
            return WeaponReadiness.UNBOUND;
        }

        @Override
        public boolean hasCurrentWeaponData(ItemStack heldStack) {
            return false;
        }

        @Override
        public boolean isCurrentWeaponDryConfirmed() {
            return false;
        }

        @Override
        public GuardCombatStatus.WeaponActivity getPlayerFacingActivity() {
            return GuardCombatStatus.WeaponActivity.WEAPON_DATA_NOT_READY;
        }

        @Override
        public double getPreferredApproachDistance() {
            return tactics.preferredApproachDistance(false, null);
        }

        @Override
        public double getRetreatDistance() {
            return tactics.retreatDistance(false, null);
        }

        @Override
        public int getRequiredVisibleTicks() {
            return tactics.requiredVisibleTicks(false, null);
        }

        @Override
        public double getAimToleranceDegrees() {
            return tactics.aimToleranceDegrees(false, null);
        }

        @Override
        public double getMinimumFiringRange() {
            return tactics.minimumFiringRange(false, null);
        }

        @Override
        public float getMaximumFiringRange() {
            return tactics.maximumFiringRange(false, null);
        }

        @Override
        public boolean hasValidFiringWindow() {
            return false;
        }

        @Override
        public boolean canFireAtDistanceSquared(double distanceSquared) {
            return false;
        }

        @Override
        public boolean isCurrentWeaponExplosive() {
            return false;
        }

        @Override
        public boolean isWeaponAllowedByOrders() {
            return true;
        }

        @Override
        public Vec3 intentionalAimPoint(LivingEntity target) {
            return target.getBoundingBox().getCenter();
        }

        @Override
        public GuardShotSafety.Result currentShotSafety(LivingEntity target) {
            return UNCHECKED_SHOT;
        }

        @Override
        public GuardShotSafety.Result lastShotSafety() {
            return UNCHECKED_SHOT;
        }

        @Override
        public LivingEntity lastShotTarget() {
            return null;
        }

        @Override
        public long lastShotGameTick() {
            return Long.MIN_VALUE;
        }

        @Override
        public boolean isEquipmentServiceSafe() {
            return true;
        }

        @Override
        public boolean isCoverageMovementSafe() {
            return true;
        }

        @Override
        public boolean isSidearmSwapSafe() {
            return true;
        }

        @Override
        public boolean isCurrentWeaponSidearmCompatible() {
            return false;
        }

        @Override
        public void prepareForSidearmTransition() {
        }

        @Override
        public void refreshAfterSidearmTransaction() {
        }
    }
}
