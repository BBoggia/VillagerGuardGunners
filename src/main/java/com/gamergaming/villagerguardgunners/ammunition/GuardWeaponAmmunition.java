package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.tacz.guns.api.item.IGun;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded ammunition view of a guard's primary and configured sidearm.
 *
 * <p>The view is captured only from player handoff, dropped-item pickup,
 * emergency sharing, or diagnostics. It reads at most two weapon stacks and
 * the existing three ammunition slots and never performs an entity query.</p>
 */
public final class GuardWeaponAmmunition {
    private GuardWeaponAmmunition() {
    }

    /** Resolves weapon roles without exposing the private stowed-primary slot. */
    public static Loadout captureLoadout(Guard guard) {
        Objects.requireNonNull(guard, "guard");
        try {
            SidearmState state = GuardSidearmAccess.getState(guard);
            if (state == SidearmState.RECOVERY_REQUIRED) {
                return Loadout.recovery();
            }

            ItemStack primary = GuardSidearmAccess.getEffectivePrimaryCopy(guard);
            ItemStack sidearm;
            ActiveRole activeRole;
            if (state == SidearmState.SIDEARM_ACTIVE) {
                sidearm = guard.getMainHandItem().copy();
                activeRole = ActiveRole.SIDEARM;
            } else {
                IItemHandler configured =
                        GuardSidearmAccess.getConfiguredSidearmInventory(guard);
                sidearm = configured.getSlots() == 1
                        ? configured.getStackInSlot(0).copy()
                        : ItemStack.EMPTY;
                activeRole = ActiveRole.PRIMARY;
            }
            return new Loadout(state, activeRole, primary, sidearm);
        } catch (RuntimeException exception) {
            return Loadout.recovery();
        }
    }

    /**
     * Classifies one physical supply against both configured weapon roles.
     * Compatibility succeeds when either readable physical-ammo weapon accepts
     * the candidate; one broken weapon cannot hide a valid match on the other.
     */
    public static SupplyAssessment assessPhysicalSupply(
            Guard guard,
            ItemStack candidate) {
        if (guard == null) {
            return SupplyAssessment.failed(SupplyStatus.NO_GUN);
        }
        return assessPhysicalSupply(captureLoadout(guard), candidate);
    }

    static SupplyAssessment assessPhysicalSupply(
            Loadout loadout,
            ItemStack candidate) {
        if (loadout == null || loadout.recoveryRequired()) {
            return SupplyAssessment.failed(SupplyStatus.RECOVERY_REQUIRED);
        }
        if (candidate == null || candidate.isEmpty()) {
            return SupplyAssessment.failed(SupplyStatus.INCOMPATIBLE);
        }

        WeaponAssessment primary = assessWeapon(loadout.primary(), candidate);
        WeaponAssessment sidearm = assessWeapon(loadout.sidearm(), candidate);
        boolean primaryCompatible = primary == WeaponAssessment.COMPATIBLE;
        boolean sidearmCompatible = sidearm == WeaponAssessment.COMPATIBLE;
        if (primaryCompatible || sidearmCompatible) {
            return new SupplyAssessment(
                    SupplyStatus.COMPATIBLE,
                    primaryCompatible,
                    sidearmCompatible);
        }
        if (primary == WeaponAssessment.UNKNOWN_AMMUNITION
                || sidearm == WeaponAssessment.UNKNOWN_AMMUNITION) {
            return SupplyAssessment.failed(SupplyStatus.UNREADABLE_AMMUNITION);
        }
        if (primary == WeaponAssessment.PHYSICAL_INCOMPATIBLE
                || sidearm == WeaponAssessment.PHYSICAL_INCOMPATIBLE) {
            return SupplyAssessment.failed(SupplyStatus.INCOMPATIBLE);
        }
        if (primary == WeaponAssessment.NON_PHYSICAL
                || sidearm == WeaponAssessment.NON_PHYSICAL) {
            return SupplyAssessment.failed(SupplyStatus.NO_PHYSICAL_AMMO_WEAPON);
        }
        if (primary == WeaponAssessment.UNREADABLE
                || sidearm == WeaponAssessment.UNREADABLE) {
            return SupplyAssessment.failed(SupplyStatus.UNREADABLE_GUN);
        }
        return SupplyAssessment.failed(SupplyStatus.NO_GUN);
    }

    /** Captures separate primary/sidearm reserve summaries for diagnostics. */
    public static LoadoutSummary captureSummary(
            Guard guard,
            IItemHandler reserveHandler) {
        Loadout loadout = captureLoadout(guard);
        return new LoadoutSummary(
                loadout.state(),
                loadout.activeRole(),
                GuardAmmoSummary.capture(loadout.primary(), reserveHandler),
                GuardAmmoSummary.capture(loadout.sidearm(), reserveHandler));
    }

    /**
     * Calculates donor surplus for the candidate after retaining the configured
     * magazine floor for every donor weapon that can consume it.
     */
    static DonorReserve assessDonorReserve(
            Loadout loadout,
            IItemHandler reserveHandler,
            ItemStack candidate,
            int retainedMagazines) {
        if (loadout == null
                || loadout.recoveryRequired()
                || reserveHandler == null
                || candidate == null
                || candidate.isEmpty()
                || retainedMagazines < 0) {
            return DonorReserve.unavailable();
        }

        long availableUnits = Long.MAX_VALUE;
        long retainedUnits = 0L;
        int compatibleWeapons = 0;
        ItemStack[] weapons = {loadout.primary(), loadout.sidearm()};
        for (ItemStack weapon : weapons) {
            if (weapon.isEmpty()
                    || !(weapon.getItem() instanceof IGun)
                    || !AmmoCompatibility.assess(weapon, candidate).isCompatible()) {
                continue;
            }
            GuardAmmoSummary.Snapshot summary =
                    GuardAmmoSummary.capture(weapon, reserveHandler);
            if (!summary.isReady()
                    || summary.reserveSource()
                            != GuardAmmoSummary.ReserveSource.PHYSICAL
                    || !summary.reserveScanComplete()) {
                return DonorReserve.unavailable();
            }
            compatibleWeapons++;
            if (!summary.infiniteReserve()) {
                availableUnits = Math.min(availableUnits, summary.reserveUnits());
            }
            retainedUnits = saturatingAdd(
                    retainedUnits,
                    retainedReserveUnits(
                            summary.magazineCapacity(),
                            retainedMagazines));
        }
        if (compatibleWeapons == 0) {
            return DonorReserve.unavailable();
        }
        if (availableUnits == Long.MAX_VALUE) {
            return new DonorReserve(true, Long.MAX_VALUE, compatibleWeapons);
        }
        long shareable = availableUnits <= retainedUnits
                ? 0L
                : availableUnits - retainedUnits;
        return new DonorReserve(true, shareable, compatibleWeapons);
    }

    private static WeaponAssessment assessWeapon(
            ItemStack weapon,
            ItemStack candidate) {
        if (weapon.isEmpty() || !(weapon.getItem() instanceof IGun)) {
            return WeaponAssessment.NO_GUN;
        }
        GuardAmmoSummary.Snapshot summary = GuardAmmoSummary.capture(weapon, null);
        if (!summary.isReady()) {
            return WeaponAssessment.UNREADABLE;
        }
        if (summary.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL) {
            return WeaponAssessment.NON_PHYSICAL;
        }
        AmmoCompatibility.Assessment compatibility =
                AmmoCompatibility.assess(weapon, candidate);
        return switch (compatibility.compatibility()) {
            case COMPATIBLE -> WeaponAssessment.COMPATIBLE;
            case INCOMPATIBLE -> WeaponAssessment.PHYSICAL_INCOMPATIBLE;
            case UNKNOWN -> WeaponAssessment.UNKNOWN_AMMUNITION;
        };
    }

    static long retainedReserveUnits(
            int magazineCapacity,
            int retainedMagazines) {
        if (magazineCapacity < 0 || retainedMagazines < 0) {
            return Long.MAX_VALUE;
        }
        long capacity = Math.max(1, magazineCapacity);
        if (retainedMagazines != 0
                && capacity > Long.MAX_VALUE / retainedMagazines) {
            return Long.MAX_VALUE;
        }
        return capacity * retainedMagazines;
    }

    private static long saturatingAdd(long current, long addition) {
        if (current < 0L
                || addition < 0L
                || Long.MAX_VALUE - current < addition) {
            return Long.MAX_VALUE;
        }
        return current + addition;
    }

    private enum WeaponAssessment {
        NO_GUN,
        UNREADABLE,
        NON_PHYSICAL,
        PHYSICAL_INCOMPATIBLE,
        UNKNOWN_AMMUNITION,
        COMPATIBLE
    }

    public enum ActiveRole {
        PRIMARY,
        SIDEARM,
        RECOVERY_REQUIRED
    }

    public enum SupplyStatus {
        COMPATIBLE,
        NO_GUN,
        UNREADABLE_GUN,
        NO_PHYSICAL_AMMO_WEAPON,
        INCOMPATIBLE,
        UNREADABLE_AMMUNITION,
        RECOVERY_REQUIRED
    }

    public record SupplyAssessment(
            SupplyStatus status,
            boolean primaryCompatible,
            boolean sidearmCompatible) {
        public boolean compatible() {
            return status == SupplyStatus.COMPATIBLE;
        }

        private static SupplyAssessment failed(SupplyStatus status) {
            return new SupplyAssessment(status, false, false);
        }
    }

    public record LoadoutSummary(
            SidearmState state,
            ActiveRole activeRole,
            GuardAmmoSummary.Snapshot primary,
            GuardAmmoSummary.Snapshot sidearm) {
    }

    public record DonorReserve(
            boolean compatible,
            long shareableUnits,
            int compatibleWeapons) {
        private static DonorReserve unavailable() {
            return new DonorReserve(false, 0L, 0);
        }
    }

    public static final class Loadout {
        private final SidearmState state;
        private final ActiveRole activeRole;
        private final ItemStack primary;
        private final ItemStack sidearm;

        private Loadout(
                SidearmState state,
                ActiveRole activeRole,
                ItemStack primary,
                ItemStack sidearm) {
            this.state = Objects.requireNonNull(state, "state");
            this.activeRole = Objects.requireNonNull(activeRole, "activeRole");
            this.primary = primary == null ? ItemStack.EMPTY : primary.copy();
            this.sidearm = sidearm == null ? ItemStack.EMPTY : sidearm.copy();
        }

        private static Loadout recovery() {
            return new Loadout(
                    SidearmState.RECOVERY_REQUIRED,
                    ActiveRole.RECOVERY_REQUIRED,
                    ItemStack.EMPTY,
                    ItemStack.EMPTY);
        }

        public SidearmState state() {
            return state;
        }

        public ActiveRole activeRole() {
            return activeRole;
        }

        public ItemStack primary() {
            return primary.copy();
        }

        public ItemStack sidearm() {
            return sidearm.copy();
        }

        public boolean recoveryRequired() {
            return state == SidearmState.RECOVERY_REQUIRED;
        }
    }
}
