package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoTransactions;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmTransactions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Resolves and atomically issues one selected natural guard loadout. */
public final class NaturalGuardLoadoutIssuance {
    private NaturalGuardLoadoutIssuance() {
    }

    public static Result issue(
            @Nullable Guard guard,
            @Nullable NaturalGuardLoadoutCandidate candidate,
            boolean allowExplosiveWeapons) {
        if (guard == null
                || candidate == null
                || guard.level().isClientSide
                || !guard.isAlive()
                || guard.isRemoved()
                || guard.getServer() == null
                || !guard.getServer().isSameThread()) {
            return Result.failed(IssuanceStatus.INVALID_REQUEST);
        }

        final Preparation preparation;
        try {
            preparation = prepare(guard, candidate, allowExplosiveWeapons);
        } catch (RuntimeException exception) {
            return Result.failed(IssuanceStatus.GUN_DATA_UNREADABLE);
        }
        if (!preparation.ready()) {
            return Result.failed(preparation.failure());
        }
        try {
            return commit(guard, preparation);
        } catch (RuntimeException exception) {
            return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
        }
    }

    private static Preparation prepare(
            Guard guard,
            NaturalGuardLoadoutCandidate candidate,
            boolean allowExplosiveWeapons) {
        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy primaryExplosivePolicy =
                allowExplosiveWeapons
                        ? NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.IGNORE
                        : NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REJECT;
        NaturalGuardLoadoutCompatibilityAnalyzer.Analysis primary =
                NaturalGuardLoadoutCompatibilityAnalyzer.analyze(
                        guard,
                        candidate.gunId(),
                        primaryExplosivePolicy);
        if (!primary.compatible()) {
            return Preparation.failed(mapPrimaryFailure(primary.status()));
        }

        ItemStack primaryGun = primary.preparedGun();
        ItemStack primaryAmmo = primary.preparedAmmunition();
        ItemStack sidearmGun = ItemStack.EMPTY;
        ItemStack sidearmAmmo = ItemStack.EMPTY;
        if (candidate.sidearmId() != null) {
            NaturalGuardSidearmCompatibility.Analysis sidearm =
                    NaturalGuardSidearmCompatibility.analyze(
                            guard,
                            candidate.sidearmId(),
                            NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REJECT);
            if (!sidearm.weapon().compatible()) {
                return Preparation.failed(mapSidearmFailure(sidearm.weapon().status()));
            }
            if (!sidearm.eligible()) {
                return Preparation.failed(IssuanceStatus.SIDEARM_INELIGIBLE);
            }
            sidearmGun = sidearm.weapon().preparedGun();
            sidearmAmmo = sidearm.weapon().preparedAmmunition();
        }

        final NaturalGuardLoadoutProvenance provenance;
        try {
            provenance = NaturalGuardLoadoutProvenance.issued(
                    candidate.poolId(),
                    candidate.gunId(),
                    UUID.randomUUID(),
                    candidate.difficulty(),
                    candidate.spawnSource());
            if (!NaturalGuardLoadoutProvenanceCodec.stampGun(primaryGun, provenance)) {
                return Preparation.failed(IssuanceStatus.PROVENANCE_WRITE_FAILED);
            }
        } catch (RuntimeException exception) {
            return Preparation.failed(IssuanceStatus.PROVENANCE_WRITE_FAILED);
        }

        return Preparation.ready(
                primaryGun,
                primaryAmmo,
                sidearmGun,
                sidearmAmmo,
                provenance);
    }

    private static IssuanceStatus mapPrimaryFailure(
            NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus status) {
        return switch (status) {
            case INVALID_REQUEST -> IssuanceStatus.INVALID_REQUEST;
            case GUN_DEFINITION_UNAVAILABLE -> IssuanceStatus.GUN_DEFINITION_UNAVAILABLE;
            case GUN_DATA_UNREADABLE -> IssuanceStatus.GUN_DATA_UNREADABLE;
            case GUN_BUILD_FAILED -> IssuanceStatus.GUN_BUILD_FAILED;
            case PHYSICAL_AMMUNITION_UNSUPPORTED ->
                    IssuanceStatus.PHYSICAL_AMMUNITION_UNSUPPORTED;
            case AMMO_DEFINITION_UNAVAILABLE -> IssuanceStatus.AMMO_DEFINITION_UNAVAILABLE;
            case AMMO_BUILD_FAILED -> IssuanceStatus.AMMO_BUILD_FAILED;
            case AMMO_STACK_INVALID -> IssuanceStatus.AMMO_STACK_INVALID;
            case AMMO_COMPATIBILITY_UNREADABLE ->
                    IssuanceStatus.AMMO_COMPATIBILITY_UNREADABLE;
            case AMMO_INCOMPATIBLE -> IssuanceStatus.AMMO_INCOMPATIBLE;
            case EXPLOSIVE_CONTEXT_REQUIRED, EXPLOSIVE_STATE_UNREADABLE ->
                    IssuanceStatus.EXPLOSIVE_STATE_UNREADABLE;
            case EXPLOSIVE_DISALLOWED -> IssuanceStatus.EXPLOSIVE_DISALLOWED;
            case COMPATIBLE -> throw new IllegalArgumentException(
                    "A compatible loadout cannot be mapped to an issuance failure");
        };
    }

    private static IssuanceStatus mapSidearmFailure(
            NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus status) {
        return switch (status) {
            case GUN_DEFINITION_UNAVAILABLE ->
                    IssuanceStatus.SIDEARM_DEFINITION_UNAVAILABLE;
            case EXPLOSIVE_CONTEXT_REQUIRED, EXPLOSIVE_STATE_UNREADABLE ->
                    IssuanceStatus.SIDEARM_EXPLOSIVE_STATE_UNREADABLE;
            case EXPLOSIVE_DISALLOWED -> IssuanceStatus.SIDEARM_EXPLOSIVE_DISALLOWED;
            case COMPATIBLE -> throw new IllegalArgumentException(
                    "A compatible sidearm cannot be mapped to an issuance failure");
            default -> IssuanceStatus.SIDEARM_COMPATIBILITY_FAILED;
        };
    }

    private static Result commit(Guard guard, Preparation preparation) {
        final GuardAmmoHandler ammoHandler;
        final AmmoSnapshot ammoBefore;
        final GuardNaturalLoadoutAccess naturalAccess;
        final NaturalGuardLoadoutProvenance provenanceBefore;
        final NaturalGuardLoadoutCandidate candidateBefore;
        try {
            ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
            ammoBefore = AmmoSnapshot.capture(ammoHandler);
            naturalAccess = (GuardNaturalLoadoutAccess) guard;
            provenanceBefore = naturalAccess.villagerGuardGunners$getNaturalLoadoutProvenance();
            candidateBefore = naturalAccess.villagerGuardGunners$getNaturalLoadoutCandidate();
        } catch (RuntimeException exception) {
            return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
        }
        if (ammoBefore == null) {
            return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
        }

        List<ItemStack> ammunition = preparation.ammunitionStacks();
        IssuanceStatus planningFailure = planAllAmmunition(ammoBefore, ammunition);
        if (planningFailure != null) {
            return Result.failed(planningFailure);
        }

        ItemStack mainHandBefore = guard.getMainHandItem().copy();
        boolean sidearmConfigured = false;
        boolean mainHandMutationAttempted = false;
        ItemStack sidearmGun = preparation.sidearmGun();
        try {
            for (ItemStack ammoStack : ammunition) {
                GuardAmmoTransactions.InsertionResult insertion =
                        GuardAmmoTransactions.insert(ammoHandler, ammoStack.copy());
                if (!insertion.committed()
                        || insertion.inserted() != ammoStack.getCount()
                        || !insertion.remainder().isEmpty()) {
                    return rollback(
                            guard, mainHandBefore, ammoHandler, ammoBefore,
                            false, sidearmGun, naturalAccess,
                            provenanceBefore, candidateBefore, false);
                }
            }

            if (!sidearmGun.isEmpty()) {
                GuardSidearmTransactions.NaturalSidearmResult sidearmResult =
                        GuardSidearmTransactions.configureNaturalSidearm(
                                guard,
                                sidearmGun.copy());
                if (!sidearmResult.configured()) {
                    boolean ammoRestored = ammoBefore.restoreSafely(ammoHandler);
                    IssuanceStatus failure = switch (sidearmResult.status()) {
                        case SLOT_UNAVAILABLE -> IssuanceStatus.SIDEARM_SLOT_UNAVAILABLE;
                        case RECOVERY_REQUIRED -> IssuanceStatus.SIDEARM_STORAGE_UNREADABLE;
                        case ROLLBACK_FAILED -> IssuanceStatus.TRANSACTION_ROLLBACK_FAILED;
                        default -> ammoRestored
                                ? IssuanceStatus.TRANSACTION_ROLLED_BACK
                                : IssuanceStatus.TRANSACTION_ROLLBACK_FAILED;
                    };
                    if (!ammoRestored) {
                        failure = IssuanceStatus.TRANSACTION_ROLLBACK_FAILED;
                    }
                    return Result.failed(failure);
                }
                sidearmConfigured = true;
            }

            if (!stacksMatch(mainHandBefore, guard.getMainHandItem())) {
                return rollback(
                        guard, mainHandBefore, ammoHandler, ammoBefore,
                        sidearmConfigured, sidearmGun, naturalAccess,
                        provenanceBefore, candidateBefore, false);
            }
            mainHandMutationAttempted = true;
            guard.setItemSlot(EquipmentSlot.MAINHAND, preparation.primaryGun());
            if (!stacksMatch(preparation.primaryGun(), guard.getMainHandItem())) {
                return rollback(
                        guard, mainHandBefore, ammoHandler, ammoBefore,
                        sidearmConfigured, sidearmGun, naturalAccess,
                        provenanceBefore, candidateBefore, true);
            }
            naturalAccess.villagerGuardGunners$setNaturalLoadoutProvenance(
                    preparation.provenance());
            if (!preparation.provenance().equals(
                            naturalAccess.villagerGuardGunners$getNaturalLoadoutProvenance())
                    || !NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                            guard,
                            preparation.provenance())) {
                return rollback(
                        guard, mainHandBefore, ammoHandler, ammoBefore,
                        sidearmConfigured, sidearmGun, naturalAccess,
                        provenanceBefore, candidateBefore, true);
            }
            return new Result(
                    IssuanceStatus.ISSUED,
                    preparation.primaryAmmo().getCount(),
                    preparation.sidearmAmmo().getCount(),
                    preparation.provenance());
        } catch (RuntimeException exception) {
            return rollback(
                    guard, mainHandBefore, ammoHandler, ammoBefore,
                    sidearmConfigured, sidearmGun, naturalAccess,
                    provenanceBefore, candidateBefore, mainHandMutationAttempted);
        }
    }

    private static @Nullable IssuanceStatus planAllAmmunition(
            AmmoSnapshot ammoBefore,
            List<ItemStack> ammunition) {
        GuardAmmoHandler planningHandler = ammoBefore.planningCopy();
        if (planningHandler == null) {
            return IssuanceStatus.TRANSACTION_FAILED;
        }
        for (ItemStack ammoStack : ammunition) {
            GuardAmmoTransactions.InsertionResult insertion =
                    GuardAmmoTransactions.insert(planningHandler, ammoStack.copy());
            if (!insertion.committed()
                    || insertion.inserted() != ammoStack.getCount()
                    || !insertion.remainder().isEmpty()) {
                return IssuanceStatus.AMMO_CAPACITY_UNAVAILABLE;
            }
        }
        return null;
    }

    private static Result rollback(
            Guard guard,
            ItemStack mainHandBefore,
            GuardAmmoHandler ammoHandler,
            AmmoSnapshot ammoBefore,
            boolean sidearmConfigured,
            ItemStack sidearmGun,
            GuardNaturalLoadoutAccess naturalAccess,
            @Nullable NaturalGuardLoadoutProvenance provenanceBefore,
            @Nullable NaturalGuardLoadoutCandidate candidateBefore,
            boolean restoreMainHand) {
        boolean mainHandRestored = !restoreMainHand;
        if (restoreMainHand) {
            try {
                guard.setItemSlot(EquipmentSlot.MAINHAND, mainHandBefore.copy());
                mainHandRestored = stacksMatch(mainHandBefore, guard.getMainHandItem());
            } catch (RuntimeException exception) {
                mainHandRestored = false;
            }
        }
        boolean sidearmRestored = !sidearmConfigured
                || GuardSidearmTransactions.rollbackNaturalSidearm(guard, sidearmGun);
        boolean ammoRestored = ammoBefore.restoreSafely(ammoHandler);
        boolean provenanceRestored = restoreNaturalState(
                naturalAccess,
                provenanceBefore,
                candidateBefore);
        return Result.failed(mainHandRestored
                        && sidearmRestored
                        && ammoRestored
                        && provenanceRestored
                ? IssuanceStatus.TRANSACTION_ROLLED_BACK
                : IssuanceStatus.TRANSACTION_ROLLBACK_FAILED);
    }

    private static boolean restoreNaturalState(
            GuardNaturalLoadoutAccess naturalAccess,
            @Nullable NaturalGuardLoadoutProvenance provenance,
            @Nullable NaturalGuardLoadoutCandidate candidate) {
        try {
            naturalAccess.villagerGuardGunners$setNaturalLoadoutProvenance(provenance);
            naturalAccess.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
            return Objects.equals(
                            provenance,
                            naturalAccess.villagerGuardGunners$getNaturalLoadoutProvenance())
                    && Objects.equals(
                            candidate,
                            naturalAccess.villagerGuardGunners$getNaturalLoadoutCandidate());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean stacksMatch(@Nullable ItemStack first, @Nullable ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    public enum IssuanceStatus {
        ISSUED,
        INVALID_REQUEST,
        GUN_DEFINITION_UNAVAILABLE,
        GUN_DATA_UNREADABLE,
        GUN_BUILD_FAILED,
        PHYSICAL_AMMUNITION_UNSUPPORTED,
        AMMO_DEFINITION_UNAVAILABLE,
        AMMO_BUILD_FAILED,
        AMMO_STACK_INVALID,
        AMMO_COMPATIBILITY_UNREADABLE,
        AMMO_INCOMPATIBLE,
        EXPLOSIVE_STATE_UNREADABLE,
        EXPLOSIVE_DISALLOWED,
        SIDEARM_DEFINITION_UNAVAILABLE,
        SIDEARM_COMPATIBILITY_FAILED,
        SIDEARM_EXPLOSIVE_STATE_UNREADABLE,
        SIDEARM_EXPLOSIVE_DISALLOWED,
        SIDEARM_INELIGIBLE,
        SIDEARM_SLOT_UNAVAILABLE,
        SIDEARM_STORAGE_UNREADABLE,
        PROVENANCE_WRITE_FAILED,
        AMMO_CAPACITY_UNAVAILABLE,
        TRANSACTION_ROLLED_BACK,
        TRANSACTION_ROLLBACK_FAILED,
        TRANSACTION_FAILED
    }

    public record Result(
            IssuanceStatus status,
            int primaryAmmunitionItems,
            int sidearmAmmunitionItems,
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            if (primaryAmmunitionItems < 0
                    || sidearmAmmunitionItems < 0
                    || (status == IssuanceStatus.ISSUED)
                            != (primaryAmmunitionItems > 0)
                    || (status == IssuanceStatus.ISSUED) != (provenance != null)) {
                throw new IllegalArgumentException(
                        "Issuance result has inconsistent ammunition");
            }
        }

        public int ammunitionItems() {
            return primaryAmmunitionItems + sidearmAmmunitionItems;
        }

        public boolean issued() {
            return status == IssuanceStatus.ISSUED;
        }

        public boolean issuedSidearm() {
            return issued() && sidearmAmmunitionItems > 0;
        }

        private static Result failed(IssuanceStatus status) {
            return new Result(status, 0, 0, null);
        }
    }

    private record Preparation(
            @Nullable ItemStack primaryGun,
            @Nullable ItemStack primaryAmmo,
            @Nullable ItemStack sidearmGun,
            @Nullable ItemStack sidearmAmmo,
            @Nullable NaturalGuardLoadoutProvenance provenance,
            @Nullable IssuanceStatus failure) {
        private boolean ready() {
            return primaryGun != null
                    && !primaryGun.isEmpty()
                    && primaryAmmo != null
                    && !primaryAmmo.isEmpty()
                    && sidearmGun != null
                    && sidearmAmmo != null
                    && (sidearmGun.isEmpty() == sidearmAmmo.isEmpty())
                    && provenance != null
                    && failure == null;
        }

        private List<ItemStack> ammunitionStacks() {
            List<ItemStack> ammunition = new ArrayList<>(2);
            ammunition.add(primaryAmmo());
            if (!sidearmAmmo().isEmpty()) {
                ammunition.add(sidearmAmmo());
            }
            return List.copyOf(ammunition);
        }

        @Override
        public ItemStack primaryGun() {
            return primaryGun == null ? ItemStack.EMPTY : primaryGun.copy();
        }

        @Override
        public ItemStack primaryAmmo() {
            return primaryAmmo == null ? ItemStack.EMPTY : primaryAmmo.copy();
        }

        @Override
        public ItemStack sidearmGun() {
            return sidearmGun == null ? ItemStack.EMPTY : sidearmGun.copy();
        }

        @Override
        public ItemStack sidearmAmmo() {
            return sidearmAmmo == null ? ItemStack.EMPTY : sidearmAmmo.copy();
        }

        private static Preparation ready(
                ItemStack primaryGun,
                ItemStack primaryAmmo,
                ItemStack sidearmGun,
                ItemStack sidearmAmmo,
                NaturalGuardLoadoutProvenance provenance) {
            return new Preparation(
                    primaryGun.copy(),
                    primaryAmmo.copy(),
                    sidearmGun.copy(),
                    sidearmAmmo.copy(),
                    Objects.requireNonNull(provenance, "provenance"),
                    null);
        }

        private static Preparation failed(IssuanceStatus status) {
            return new Preparation(
                    null,
                    null,
                    null,
                    null,
                    null,
                    Objects.requireNonNull(status, "status"));
        }
    }

    private record AmmoSnapshot(List<ItemStack> stacks) {
        private static @Nullable AmmoSnapshot capture(GuardAmmoHandler handler) {
            try {
                if (handler.getSlots() != GuardAmmoHandler.SLOT_COUNT) {
                    return null;
                }
                List<ItemStack> stacks = new ArrayList<>(handler.getSlots());
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (stack == null) {
                        return null;
                    }
                    stacks.add(stack.copy());
                }
                return new AmmoSnapshot(List.copyOf(stacks));
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private @Nullable GuardAmmoHandler planningCopy() {
            try {
                GuardAmmoHandler copy = new GuardAmmoHandler();
                for (int slot = 0; slot < stacks.size(); slot++) {
                    copy.setStackInSlot(slot, stacks.get(slot).copy());
                }
                return matches(copy) ? copy : null;
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private boolean matches(GuardAmmoHandler handler) {
            if (handler.getSlots() != stacks.size()) {
                return false;
            }
            for (int slot = 0; slot < stacks.size(); slot++) {
                if (!stacksMatch(stacks.get(slot), handler.getStackInSlot(slot))) {
                    return false;
                }
            }
            return true;
        }

        private boolean restoreSafely(GuardAmmoHandler handler) {
            try {
                if (handler.getSlots() != stacks.size()) {
                    return false;
                }
                for (int slot = 0; slot < stacks.size(); slot++) {
                    handler.setStackInSlot(slot, stacks.get(slot).copy());
                }
                return matches(handler);
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }
}
