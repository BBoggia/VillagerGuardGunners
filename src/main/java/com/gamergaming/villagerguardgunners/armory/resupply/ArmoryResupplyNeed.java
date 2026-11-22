package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Converts the existing bounded TaCZ ammunition view into one physical need. */
public final class ArmoryResupplyNeed {
    private ArmoryResupplyNeed() {
    }

    public static Assessment assess(
            @Nullable GuardWeaponAmmunition.LoadoutSummary loadout,
            int lowReserveMagazines,
            int targetReserveMagazines) {
        if (!validThresholds(lowReserveMagazines, targetReserveMagazines)) {
            return Assessment.unavailable(NeedStatus.INVALID_THRESHOLDS);
        }
        if (loadout == null
                || loadout.state() == null
                || loadout.activeRole() == null
                || loadout.state() == SidearmState.RECOVERY_REQUIRED
                || loadout.activeRole()
                        == GuardWeaponAmmunition.ActiveRole.RECOVERY_REQUIRED) {
            return Assessment.unavailable(NeedStatus.RECOVERY_REQUIRED);
        }
        if ((loadout.state() == SidearmState.PRIMARY_ACTIVE
                        && loadout.activeRole() != GuardWeaponAmmunition.ActiveRole.PRIMARY)
                || (loadout.state() == SidearmState.SIDEARM_ACTIVE
                        && loadout.activeRole() != GuardWeaponAmmunition.ActiveRole.SIDEARM)) {
            return Assessment.unavailable(NeedStatus.RECOVERY_REQUIRED);
        }

        ArmoryResupplyWeaponRole role;
        GuardAmmoSummary.Snapshot weapon;
        if (loadout.activeRole() == GuardWeaponAmmunition.ActiveRole.SIDEARM) {
            role = ArmoryResupplyWeaponRole.SIDEARM;
            weapon = loadout.sidearm();
        } else {
            role = ArmoryResupplyWeaponRole.PRIMARY;
            weapon = loadout.primary();
        }
        if (weapon == null
                || !weapon.isReady()
                || weapon.gunId() == null
                || weapon.magazineCapacity() < 0
                || weapon.loadedAmmo() < 0
                || weapon.reserveUnits() < 0L) {
            return Assessment.unavailable(NeedStatus.WEAPON_DATA_UNAVAILABLE);
        }
        if (weapon.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL) {
            return Assessment.unavailable(NeedStatus.NON_PHYSICAL_RESERVE);
        }
        if (weapon.infiniteReserve()) {
            return Assessment.unavailable(NeedStatus.INFINITE_RESERVE);
        }
        if (!weapon.reserveScanComplete()) {
            return Assessment.unavailable(NeedStatus.RESERVE_SCAN_INCOMPLETE);
        }

        long magazineUnits = Math.max(1L, weapon.magazineCapacity());
        long triggerUnits = saturatingMultiply(magazineUnits, lowReserveMagazines);
        long targetUnits = saturatingMultiply(magazineUnits, targetReserveMagazines);
        long reserveUnits = weapon.reserveUnits();
        if (reserveUnits >= triggerUnits) {
            return Assessment.sufficient(
                    role,
                    weapon.gunId(),
                    weapon.magazineCapacity(),
                    weapon.displayedLoadedAmmo(),
                    reserveUnits,
                    targetUnits);
        }

        long deficit = targetUnits <= reserveUnits ? 0L : targetUnits - reserveUnits;
        long requestedUnits = Math.min(
                deficit,
                ArmoryResupplyLimits.HARD_MAX_REQUESTED_UNITS);
        if (requestedUnits <= 0L) {
            return Assessment.sufficient(
                    role,
                    weapon.gunId(),
                    weapon.magazineCapacity(),
                    weapon.displayedLoadedAmmo(),
                    reserveUnits,
                    targetUnits);
        }

        ArmoryResupplyUrgency urgency = weapon.displayedLoadedAmmo() <= 0
                        && reserveUnits == 0L
                ? ArmoryResupplyUrgency.OUT_OF_AMMO
                : ArmoryResupplyUrgency.LOW_RESERVE;
        return Assessment.required(
                role,
                weapon.gunId(),
                urgency,
                weapon.magazineCapacity(),
                weapon.displayedLoadedAmmo(),
                reserveUnits,
                targetUnits,
                requestedUnits);
    }

    private static boolean validThresholds(int low, int target) {
        return low >= 0
                && low <= ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES
                && target >= low
                && target <= ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES;
    }

    private static long saturatingMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier == 0) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE
                : value * multiplier;
    }

    public enum NeedStatus {
        REQUEST_REQUIRED(true),
        SUFFICIENT_RESERVE(false),
        INVALID_THRESHOLDS(false),
        RECOVERY_REQUIRED(false),
        WEAPON_DATA_UNAVAILABLE(false),
        NON_PHYSICAL_RESERVE(false),
        INFINITE_RESERVE(false),
        RESERVE_SCAN_INCOMPLETE(false);

        private final boolean requestRequired;

        NeedStatus(boolean requestRequired) {
            this.requestRequired = requestRequired;
        }

        public boolean requestRequired() {
            return requestRequired;
        }
    }

    public record Assessment(
            NeedStatus status,
            @Nullable ArmoryResupplyWeaponRole weaponRole,
            @Nullable ResourceLocation gunId,
            @Nullable ArmoryResupplyUrgency urgency,
            int magazineCapacity,
            int observedLoadedAmmo,
            long observedReserveUnits,
            long targetReserveUnits,
            long requestedUnits) {
        public Assessment {
            Objects.requireNonNull(status, "status");
            boolean identifiedWeapon = weaponRole != null && gunId != null;
            if (status == NeedStatus.REQUEST_REQUIRED) {
                long deficit = targetReserveUnits <= observedReserveUnits
                        ? 0L
                        : targetReserveUnits - observedReserveUnits;
                long boundedDeficit = Math.min(
                        deficit,
                        ArmoryResupplyLimits.HARD_MAX_REQUESTED_UNITS);
                ArmoryResupplyUrgency expectedUrgency = observedLoadedAmmo == 0
                                && observedReserveUnits == 0L
                        ? ArmoryResupplyUrgency.OUT_OF_AMMO
                        : ArmoryResupplyUrgency.LOW_RESERVE;
                if (!identifiedWeapon
                        || urgency == null
                        || magazineCapacity < 0
                        || observedLoadedAmmo < 0
                        || observedReserveUnits < 0L
                        || targetReserveUnits < 0L
                        || requestedUnits <= 0L
                        || requestedUnits > ArmoryResupplyLimits.HARD_MAX_REQUESTED_UNITS
                        || requestedUnits != boundedDeficit
                        || urgency != expectedUrgency) {
                    throw new IllegalArgumentException("Invalid required Armory ammunition need");
                }
            } else if (status == NeedStatus.SUFFICIENT_RESERVE) {
                if (!identifiedWeapon
                        || urgency != null
                        || magazineCapacity < 0
                        || observedLoadedAmmo < 0
                        || observedReserveUnits < 0L
                        || targetReserveUnits < 0L
                        || requestedUnits != 0L) {
                    throw new IllegalArgumentException("Invalid sufficient Armory ammunition need");
                }
            } else if (identifiedWeapon
                    || urgency != null
                    || magazineCapacity != -1
                    || observedLoadedAmmo != -1
                    || observedReserveUnits != 0L
                    || targetReserveUnits != 0L
                    || requestedUnits != 0L) {
                throw new IllegalArgumentException("Unavailable needs may not contain weapon data");
            }
        }

        public boolean requestRequired() {
            return status.requestRequired();
        }

        private static Assessment required(
                ArmoryResupplyWeaponRole role,
                ResourceLocation gunId,
                ArmoryResupplyUrgency urgency,
                int magazineCapacity,
                int loadedAmmo,
                long reserveUnits,
                long targetUnits,
                long requestedUnits) {
            return new Assessment(
                    NeedStatus.REQUEST_REQUIRED,
                    role,
                    gunId,
                    urgency,
                    magazineCapacity,
                    loadedAmmo,
                    reserveUnits,
                    targetUnits,
                    requestedUnits);
        }

        private static Assessment sufficient(
                ArmoryResupplyWeaponRole role,
                ResourceLocation gunId,
                int magazineCapacity,
                int loadedAmmo,
                long reserveUnits,
                long targetUnits) {
            return new Assessment(
                    NeedStatus.SUFFICIENT_RESERVE,
                    role,
                    gunId,
                    null,
                    magazineCapacity,
                    loadedAmmo,
                    reserveUnits,
                    targetUnits,
                    0L);
        }

        private static Assessment unavailable(NeedStatus status) {
            return new Assessment(status, null, null, null, -1, -1, 0L, 0L, 0L);
        }
    }
}
