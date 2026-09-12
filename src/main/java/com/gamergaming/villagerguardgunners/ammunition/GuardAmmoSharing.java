package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Recipient-driven emergency sharing for loose physical TaCZ ammunition.
 * This service is invoked only after TaCZ proves that a reload has no
 * compatible reserve; it does not balance inventories or schedule work.
 */
public final class GuardAmmoSharing {
    private GuardAmmoSharing() {
    }

    public static SharingResult tryShare(Guard recipient) {
        if (recipient == null
                || recipient.level().isClientSide
                || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(recipient) || !recipient.isAlive()
                || !recipient.isEffectiveAi()) {
            return SharingResult.failed(SharingStatus.INVALID_RECIPIENT);
        }
        boolean serverSharingEnabled =
                ModConfigs.GUARD_GUNNERS.ammunition.shareAmmoBetweenGuards.get();
        if (!GuardRoleResolver.effectiveOrders(recipient)
                .permitsAmmoSharing(serverSharingEnabled)) {
            return SharingResult.failed(SharingStatus.DISABLED);
        }

        try {
            return tryShareSafely(recipient);
        } catch (RuntimeException exception) {
            return SharingResult.failed(SharingStatus.TRANSACTION_FAILED);
        }
    }

    private static SharingResult tryShareSafely(Guard recipient) {
        ItemStack recipientGun = recipient.getMainHandItem();
        if (recipientGun.isEmpty() || !(recipientGun.getItem() instanceof IGun)) {
            return SharingResult.failed(SharingStatus.INVALID_RECIPIENT);
        }

        GuardAmmoHandler destination = GuardAmmoAccess.getAmmoHandler(recipient);
        GuardAmmoMaintenance.ejectEmptyFiniteBoxes(recipient);
        GuardAmmoSummary.Snapshot recipientAmmo =
                GuardAmmoSummary.capture(recipientGun.copy(), destination);
        if (!recipientAmmo.isReady()
                || recipientAmmo.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL
                || !recipientAmmo.reserveScanComplete()) {
            return SharingResult.failed(SharingStatus.INVALID_RECIPIENT);
        }
        if (recipientAmmo.displayedLoadedAmmo() > 0 || recipientAmmo.hasCompatibleReserve()) {
            return SharingResult.failed(SharingStatus.RECIPIENT_NOT_DRY);
        }

        int requestedItems = requestedItems(recipientAmmo.magazineCapacity());
        if (requestedItems <= 0) {
            return SharingResult.failed(SharingStatus.INVALID_RECIPIENT);
        }

        double radius = ModConfigs.GUARD_GUNNERS.ammunition.ammoSharingRadius.get();
        double radiusSquared = radius * radius;
        AABB searchBounds = recipient.getBoundingBox().inflate(radius);
        List<Guard> nearbyGuards = recipient.level().getEntitiesOfClass(
                Guard.class,
                searchBounds,
                guard -> guard != recipient && !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) && guard.isAlive() && guard.isEffectiveAi());

        DonorCandidate selected = null;
        double selectedDistanceSquared = Double.POSITIVE_INFINITY;
        boolean requireMatchingOwner =
                ModConfigs.GUARD_GUNNERS.ammunition.requireMatchingOwnerForSharing.get();
        int retainedMagazines =
                ModConfigs.GUARD_GUNNERS.ammunition.donorReserveMagazines.get();

        for (Guard donor : nearbyGuards) {
            double distanceSquared = donor.distanceToSqr(recipient);
            if (distanceSquared > radiusSquared
                    || !GuardRoleResolver.effectiveOrders(donor).permitsAmmoSharing(true)
                    || !ownersMayShare(
                            recipient.getOwnerId(),
                            donor.getOwnerId(),
                            requireMatchingOwner)
                    || (selected != null
                    && (distanceSquared > selectedDistanceSquared
                    || (distanceSquared == selectedDistanceSquared
                    && donor.getId() >= selected.guard().getId())))) {
                continue;
            }

            DonorCandidate candidate = assessDonor(
                    donor,
                    recipientGun,
                    destination,
                    requestedItems,
                    retainedMagazines);
            if (candidate != null) {
                selected = candidate;
                selectedDistanceSquared = distanceSquared;
            }
        }

        if (selected == null) {
            return SharingResult.failed(SharingStatus.NO_ELIGIBLE_DONOR);
        }

        int transferred = transferFromDonor(
                selected,
                recipientGun,
                destination,
                requestedItems);
        if (transferred <= 0) {
            return SharingResult.failed(SharingStatus.TRANSACTION_FAILED);
        }

        recipient.playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
        return new SharingResult(
                SharingStatus.SHARED,
                transferred,
                selected.guard().getId());
    }

    private static DonorCandidate assessDonor(
            Guard donor,
            ItemStack recipientGun,
            GuardAmmoHandler destination,
            int requestedItems,
            int retainedMagazines) {
        GuardWeaponAmmunition.Loadout donorLoadout =
                GuardWeaponAmmunition.captureLoadout(donor);
        if (donorLoadout.recoveryRequired()) {
            return null;
        }

        GuardAmmoHandler source = GuardAmmoAccess.getAmmoHandler(donor);
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            GuardWeaponAmmunition.DonorReserve reserve =
                    GuardWeaponAmmunition.assessDonorReserve(
                            donorLoadout,
                            source,
                            stack,
                            retainedMagazines);
            if (!reserve.compatible() || reserve.shareableUnits() <= 0L) {
                continue;
            }
            int maximum = transferableLooseItems(
                    recipientGun,
                    stack,
                    requestedItems,
                    reserve.shareableUnits(),
                    stack);
            if (maximum <= 0) {
                continue;
            }

            ItemStack offered = stack.copy();
            offered.setCount(maximum);
            GuardAmmoTransactions.InsertionPlan plan =
                    GuardAmmoTransactions.planInsertion(destination, offered);
            if (plan.valid() && plan.accepted() > 0) {
                ItemStack ammunitionTemplate = stack.copy();
                ammunitionTemplate.setCount(1);
                return new DonorCandidate(
                        donor,
                        source,
                        reserve.shareableUnits(),
                        ammunitionTemplate);
            }
        }
        return null;
    }

    private static int transferFromDonor(
            DonorCandidate donor,
            ItemStack recipientGun,
            GuardAmmoHandler destination,
            int requestedItems) {
        int transferred = 0;
        long remainingShareableUnits = donor.shareableUnits();

        for (int slot = 0;
                slot < donor.source().getSlots()
                        && transferred < requestedItems
                        && remainingShareableUnits > 0L;
                slot++) {
            ItemStack stack = donor.source().getStackInSlot(slot);
            int maximum = transferableLooseItems(
                    recipientGun,
                    stack,
                    requestedItems - transferred,
                    remainingShareableUnits,
                    donor.ammunitionTemplate());
            if (maximum <= 0) {
                continue;
            }

            GuardAmmoTransactions.TransferResult transfer = GuardAmmoTransactions.transfer(
                    donor.source(),
                    slot,
                    destination,
                    maximum);
            if (transfer.committed() && transfer.transferred() > 0) {
                transferred += transfer.transferred();
                if (remainingShareableUnits != Long.MAX_VALUE) {
                    remainingShareableUnits -= transfer.transferred();
                }
                continue;
            }
            if (transfer.status() != GuardAmmoTransactions.TransactionStatus.NO_OP) {
                break;
            }
        }
        return transferred;
    }

    private static int transferableLooseItems(
            ItemStack recipientGun,
            ItemStack candidate,
            int requestedItems,
            long shareableUnits,
            ItemStack ammunitionTemplate) {
        if (candidate.isEmpty()
                || !(candidate.getItem() instanceof IAmmo)
                || candidate.getItem() instanceof IAmmoBox
                || !ItemStack.isSameItemSameTags(candidate, ammunitionTemplate)
                || !AmmoCompatibility.assess(recipientGun, candidate).isCompatible()) {
            return 0;
        }
        long bounded = Math.min(
                Math.min((long) candidate.getCount(), requestedItems),
                shareableUnits);
        return bounded <= 0L ? 0 : (int) bounded;
    }

    static int requestedItems(int magazineCapacity) {
        return magazineCapacity == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(1, magazineCapacity);
    }

    static long retainedReserveUnits(int magazineCapacity, int retainedMagazines) {
        return GuardWeaponAmmunition.retainedReserveUnits(
                magazineCapacity,
                retainedMagazines);
    }

    static boolean ownersMayShare(
            UUID recipientOwner,
            UUID donorOwner,
            boolean requireMatchingOwner) {
        return !requireMatchingOwner || Objects.equals(recipientOwner, donorOwner);
    }

    public enum SharingStatus {
        NOT_ATTEMPTED,
        SHARED,
        DISABLED,
        INVALID_RECIPIENT,
        RECIPIENT_NOT_DRY,
        NO_ELIGIBLE_DONOR,
        TRANSACTION_FAILED
    }

    public record SharingResult(SharingStatus status, int transferred, int donorEntityId) {
        public boolean shared() {
            return status == SharingStatus.SHARED;
        }

        private static SharingResult failed(SharingStatus status) {
            return new SharingResult(status, 0, -1);
        }
    }

    private record DonorCandidate(
            Guard guard,
            GuardAmmoHandler source,
            long shareableUnits,
            ItemStack ammunitionTemplate) {
        private DonorCandidate {
            ammunitionTemplate = ammunitionTemplate.copy();
        }

        @Override
        public ItemStack ammunitionTemplate() {
            return ammunitionTemplate.copy();
        }
    }
}
