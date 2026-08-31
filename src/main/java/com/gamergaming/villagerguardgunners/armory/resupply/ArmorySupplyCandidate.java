package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.tacz.guns.api.item.IGun;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bounded snapshot of one compatible Armory slot.
 *
 * <p>A candidate supports contention accounting only. It is never transfer
 * authorization; withdrawal re-reads the live block entity and stack.</p>
 */
public record ArmorySupplyCandidate(
        ArmoryReservationTarget target,
        ArmorySupplyFingerprint fingerprint,
        AmmoCompatibility.AmmoKind kind,
        int availableItems,
        int proposedItems,
        long proposedUnits) {
    public ArmorySupplyCandidate {
        target = Objects.requireNonNull(target, "target");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        kind = Objects.requireNonNull(kind, "kind");
        if (kind == AmmoCompatibility.AmmoKind.NONE
                || availableItems <= 0
                || proposedItems <= 0
                || proposedItems > availableItems
                || proposedUnits <= 0L
                || (kind == AmmoCompatibility.AmmoKind.LOOSE
                        && proposedUnits != proposedItems)
                || (kind == AmmoCompatibility.AmmoKind.BOX && proposedItems != 1)) {
            throw new IllegalArgumentException("Invalid Armory supply candidate quantities");
        }
    }

    public static Inspection inspect(
            ArmoryResupplyRequest request,
            ResourceLocation armoryDimension,
            ArmoryBlockEntity armory,
            ItemStack liveActiveGun,
            int sourceSlot,
            long gameTick) {
        if (request == null
                || armoryDimension == null
                || armory == null
                || liveActiveGun == null
                || gameTick < 0L) {
            return Inspection.failed(InspectionStatus.INVALID_INPUT);
        }
        if (request.isExpired(gameTick)) {
            return Inspection.failed(InspectionStatus.REQUEST_EXPIRED);
        }
        if (!request.dimension().equals(armoryDimension)) {
            return Inspection.failed(InspectionStatus.INVALID_TARGET);
        }
        Level armoryLevel = armory.getLevel();
        if (armoryLevel == null
                || armoryLevel.isClientSide
                || !armoryDimension.equals(armoryLevel.dimension().location())
                || armoryLevel.getGameTime() != gameTick
                || !armoryLevel.hasChunkAt(armory.getBlockPos())
                || armoryLevel.getBlockEntity(armory.getBlockPos()) != armory) {
            return Inspection.failed(InspectionStatus.INVALID_TARGET);
        }
        if (armory.isRemoved() || !armory.maySupply(request.squadId())) {
            return Inspection.failed(InspectionStatus.ACCESS_DENIED);
        }
        if (!matchesRequestedGun(request, liveActiveGun)) {
            return Inspection.failed(InspectionStatus.WEAPON_MISMATCH);
        }

        int slots;
        ItemStack source;
        try {
            slots = armory.getInventory().getSlots();
            if (slots < 0
                    || slots > ArmoryResupplyLimits.HARD_MAX_RESERVATION_SOURCE_SLOTS
                    || sourceSlot < 0
                    || sourceSlot >= slots) {
                return Inspection.failed(InspectionStatus.SOURCE_UNAVAILABLE);
            }
            source = armory.getInventory().getStackInSlot(sourceSlot);
            if (source == null || source.isEmpty()) {
                return Inspection.failed(InspectionStatus.SOURCE_UNAVAILABLE);
            }
            source = source.copy();
        } catch (RuntimeException exception) {
            return Inspection.failed(InspectionStatus.SOURCE_UNAVAILABLE);
        }

        AmmoCompatibility.Assessment assessment =
                AmmoCompatibility.assess(liveActiveGun, source);
        if (assessment.infinite()) {
            return Inspection.failed(InspectionStatus.INFINITE_AMMUNITION);
        }
        if (!assessment.isCompatible() || assessment.units() <= 0L) {
            return Inspection.failed(InspectionStatus.INCOMPATIBLE_AMMUNITION);
        }

        int proposedItems;
        long proposedUnits;
        if (assessment.kind() == AmmoCompatibility.AmmoKind.LOOSE) {
            proposedItems = (int) Math.min(
                    source.getCount(),
                    request.requestedUnits());
            proposedUnits = proposedItems;
        } else if (assessment.kind() == AmmoCompatibility.AmmoKind.BOX) {
            ItemStack singleBox = source.copy();
            singleBox.setCount(1);
            AmmoCompatibility.Assessment singleAssessment =
                    AmmoCompatibility.assess(liveActiveGun, singleBox);
            if (singleAssessment.infinite()) {
                return Inspection.failed(InspectionStatus.INFINITE_AMMUNITION);
            }
            if (!singleAssessment.isCompatible()
                    || singleAssessment.units() <= 0L) {
                return Inspection.failed(InspectionStatus.INCOMPATIBLE_AMMUNITION);
            }
            proposedItems = 1;
            proposedUnits = singleAssessment.units();
        } else {
            return Inspection.failed(InspectionStatus.INCOMPATIBLE_AMMUNITION);
        }
        if (proposedItems <= 0 || proposedUnits <= 0L) {
            return Inspection.failed(InspectionStatus.SOURCE_UNAVAILABLE);
        }

        try {
            return Inspection.ready(new ArmorySupplyCandidate(
                    new ArmoryReservationTarget(
                            armoryDimension,
                            armory.getBlockPos(),
                            sourceSlot),
                    ArmorySupplyFingerprint.capture(source),
                    assessment.kind(),
                    source.getCount(),
                    proposedItems,
                    proposedUnits));
        } catch (RuntimeException exception) {
            return Inspection.failed(InspectionStatus.SOURCE_UNAVAILABLE);
        }
    }

    private static boolean matchesRequestedGun(
            ArmoryResupplyRequest request,
            ItemStack liveActiveGun) {
        if (liveActiveGun.isEmpty() || !(liveActiveGun.getItem() instanceof IGun gun)) {
            return false;
        }
        try {
            return request.gunId().equals(gun.getGunId(liveActiveGun.copy()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public enum InspectionStatus {
        READY,
        INVALID_INPUT,
        REQUEST_EXPIRED,
        INVALID_TARGET,
        ACCESS_DENIED,
        WEAPON_MISMATCH,
        SOURCE_UNAVAILABLE,
        INCOMPATIBLE_AMMUNITION,
        INFINITE_AMMUNITION
    }

    public record Inspection(
            InspectionStatus status,
            @Nullable ArmorySupplyCandidate candidate) {
        public Inspection {
            Objects.requireNonNull(status, "status");
            if ((status == InspectionStatus.READY) != (candidate != null)) {
                throw new IllegalArgumentException("Candidate presence contradicts inspection status");
            }
        }

        public boolean ready() {
            return status == InspectionStatus.READY;
        }

        private static Inspection ready(ArmorySupplyCandidate candidate) {
            return new Inspection(InspectionStatus.READY, candidate);
        }

        private static Inspection failed(InspectionStatus status) {
            return new Inspection(status, null);
        }
    }
}
