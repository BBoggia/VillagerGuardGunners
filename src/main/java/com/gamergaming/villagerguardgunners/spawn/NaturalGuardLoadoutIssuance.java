package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoTransactions;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.combat.TaCZExplosionResolver;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Resolves and atomically issues one selected natural guard loadout.
 *
 * <p>All TaCZ and item-handler callbacks are treated as untrusted reloadable
 * boundaries. The candidate is resolved only at the moment of issuance, every
 * result is validated through TaCZ's public item APIs, and no equipment is
 * changed unless the complete physical ammunition stack can be accepted.</p>
 */
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
                || guard.isRemoved()) {
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
            return commit(
                    guard,
                    preparation.gunStack(),
                    preparation.ammoStack(),
                    preparation.provenance());
        } catch (RuntimeException exception) {
            // All mutations inside commit have their own snapshot boundary.
            // Reaching this catch means the failure occurred before that
            // boundary or after its rollback path had completed.
            return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
        }
    }

    private static Preparation prepare(
            Guard guard,
            NaturalGuardLoadoutCandidate candidate,
            boolean allowExplosiveWeapons) {
        GunData gunData;
        try {
            var resolvedGun = TimelessAPI.getCommonGunIndex(candidate.gunId());
            if (resolvedGun.isEmpty()) {
                return Preparation.failed(IssuanceStatus.GUN_DEFINITION_UNAVAILABLE);
            }
            gunData = resolvedGun.get().getGunData();
        } catch (RuntimeException exception) {
            return Preparation.failed(IssuanceStatus.GUN_DATA_UNREADABLE);
        }

        if (!hasRequiredGunData(gunData)) {
            return Preparation.failed(IssuanceStatus.GUN_DATA_UNREADABLE);
        }

        FireMode initialFireMode = gunData.getFireModeSet().get(0);
        ItemStack gunStack;
        try {
            gunStack = GunItemBuilder.create()
                    .setCount(1)
                    .setId(candidate.gunId())
                    .setFireMode(initialFireMode)
                    .setAmmoCount(gunData.getAmmoAmount())
                    .setAmmoInBarrel(true)
                    .setHeatData(gunData.hasHeatData())
                    .build();
        } catch (RuntimeException exception) {
            return Preparation.failed(IssuanceStatus.GUN_BUILD_FAILED);
        }

        IGun gun = validateGun(gunStack, candidate.gunId(), initialFireMode, gunData);
        if (gun == null) {
            return Preparation.failed(IssuanceStatus.GUN_BUILD_FAILED);
        }

        GuardAmmoSummary.Snapshot ammoUse = GuardAmmoSummary.capture(
                gunStack.copy(),
                gunData,
                null);
        if (!ammoUse.isReady()) {
            return Preparation.failed(IssuanceStatus.GUN_DATA_UNREADABLE);
        }
        if (ammoUse.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL) {
            return Preparation.failed(IssuanceStatus.PHYSICAL_AMMUNITION_UNSUPPORTED);
        }

        ResourceLocation ammoId = gunData.getAmmoId();
        CommonAmmoIndex ammoIndex;
        try {
            Optional<CommonAmmoIndex> resolvedAmmo = TimelessAPI.getCommonAmmoIndex(ammoId);
            if (resolvedAmmo.isEmpty()) {
                return Preparation.failed(IssuanceStatus.AMMO_DEFINITION_UNAVAILABLE);
            }
            ammoIndex = resolvedAmmo.get();
        } catch (RuntimeException exception) {
            return Preparation.failed(IssuanceStatus.AMMO_DEFINITION_UNAVAILABLE);
        }

        int fullStackSize;
        ItemStack ammoStack;
        try {
            fullStackSize = ammoIndex.getStackSize();
            if (fullStackSize <= 0) {
                return Preparation.failed(IssuanceStatus.AMMO_STACK_INVALID);
            }
            ammoStack = AmmoItemBuilder.create()
                    .setId(ammoId)
                    .setCount(fullStackSize)
                    .build();
        } catch (RuntimeException exception) {
            return Preparation.failed(IssuanceStatus.AMMO_BUILD_FAILED);
        }

        if (!isValidFullAmmoStack(ammoStack, ammoId, fullStackSize)) {
            return Preparation.failed(IssuanceStatus.AMMO_STACK_INVALID);
        }
        AmmoCompatibility.Assessment compatibility =
                AmmoCompatibility.assess(gunStack, ammoStack);
        if (compatibility.compatibility() == AmmoCompatibility.Compatibility.UNKNOWN) {
            return Preparation.failed(IssuanceStatus.AMMO_COMPATIBILITY_UNREADABLE);
        }
        if (!compatibility.isCompatible()
                || compatibility.kind() != AmmoCompatibility.AmmoKind.LOOSE
                || compatibility.infinite()
                || compatibility.units() != fullStackSize) {
            return Preparation.failed(IssuanceStatus.AMMO_INCOMPATIBLE);
        }

        if (!allowExplosiveWeapons) {
            final boolean explosive;
            try {
                explosive = isExplosive(guard, gunStack, gun, gunData);
            } catch (RuntimeException exception) {
                return Preparation.failed(IssuanceStatus.EXPLOSIVE_STATE_UNREADABLE);
            }
            if (explosive) {
                return Preparation.failed(IssuanceStatus.EXPLOSIVE_DISALLOWED);
            }
        }

        final NaturalGuardLoadoutProvenance provenance;
        try {
            provenance = NaturalGuardLoadoutProvenance.issued(
                    candidate.poolId(),
                    candidate.gunId(),
                    UUID.randomUUID(),
                    candidate.difficulty(),
                    candidate.spawnSource());
            if (!NaturalGuardLoadoutProvenanceCodec.stampGun(gunStack, provenance)) {
                return Preparation.failed(IssuanceStatus.PROVENANCE_WRITE_FAILED);
            }
        } catch (RuntimeException exception) {
            return Preparation.failed(IssuanceStatus.PROVENANCE_WRITE_FAILED);
        }

        return Preparation.ready(gunStack, ammoStack, provenance);
    }

    private static boolean hasRequiredGunData(@Nullable GunData gunData) {
        if (gunData == null
                || gunData.getAmmoId() == null
                || gunData.getAmmoAmount() < 1
                || gunData.getFireModeSet() == null
                || gunData.getFireModeSet().isEmpty()) {
            return false;
        }
        for (FireMode fireMode : gunData.getFireModeSet()) {
            if (fireMode == null || fireMode == FireMode.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable IGun validateGun(
            @Nullable ItemStack gunStack,
            ResourceLocation expectedGunId,
            FireMode expectedFireMode,
            GunData gunData) {
        if (gunStack == null
                || gunStack.isEmpty()
                || gunStack.getCount() != 1
                || !(gunStack.getItem() instanceof IGun gun)) {
            return null;
        }
        try {
            if (!expectedGunId.equals(gun.getGunId(gunStack))
                    || gun.getFireMode(gunStack) != expectedFireMode
                    || gun.getCurrentAmmoCount(gunStack) != gunData.getAmmoAmount()) {
                return null;
            }
            return gun;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isValidFullAmmoStack(
            @Nullable ItemStack ammoStack,
            ResourceLocation expectedAmmoId,
            int expectedCount) {
        if (ammoStack == null
                || ammoStack.isEmpty()
                || ammoStack.getCount() != expectedCount
                || expectedCount > ammoStack.getMaxStackSize()
                || !(ammoStack.getItem() instanceof IAmmo ammo)) {
            return false;
        }
        try {
            return expectedAmmoId.equals(ammo.getAmmoId(ammoStack));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Reproduces TaCZ's native attachment-cache and runtime-script resolution
     * without publishing the proposed gun to the guard first.
     */
    private static boolean isExplosive(
            Guard guard,
            ItemStack gunStack,
            IGun gun,
            GunData gunData) {
        ItemStack checkedGun = gunStack.copy();
        AttachmentCacheProperty cache = new AttachmentCacheProperty();
        cache.eval(checkedGun, gunData);
        return TaCZExplosionResolver.resolve(
                guard,
                checkedGun,
                gun,
                gunData,
                cache).enabled();
    }

    private static Result commit(
            Guard guard,
            ItemStack gunStack,
            ItemStack ammoStack,
            NaturalGuardLoadoutProvenance provenance) {
        final GuardAmmoHandler ammoHandler;
        final AmmoSnapshot ammoBefore;
        try {
            ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
            ammoBefore = AmmoSnapshot.capture(ammoHandler);
        } catch (RuntimeException exception) {
            return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
        }
        if (ammoBefore == null) {
            return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
        }

        GuardAmmoTransactions.InsertionPlan plan =
                GuardAmmoTransactions.planInsertion(ammoHandler, ammoStack.copy());
        if (!plan.valid()) {
            if (plan.status() == GuardAmmoTransactions.PlanStatus.INVALID_HANDLER) {
                return Result.failed(IssuanceStatus.TRANSACTION_FAILED);
            }
            // GuardAmmoTransactions already attempts this restoration. The
            // service owns the concrete three-slot handler and retains its own
            // snapshot as a second recovery layer if a simulation misbehaves.
            return Result.failed(ammoBefore.restoreSafely(ammoHandler)
                    ? IssuanceStatus.TRANSACTION_ROLLED_BACK
                    : IssuanceStatus.TRANSACTION_ROLLBACK_FAILED);
        }
        if (plan.accepted() != ammoStack.getCount() || !plan.remainder().isEmpty()) {
            return Result.failed(IssuanceStatus.AMMO_CAPACITY_UNAVAILABLE);
        }

        ItemStack mainHandBefore = guard.getMainHandItem().copy();
        GuardAmmoTransactions.InsertionResult insertion =
                GuardAmmoTransactions.insert(ammoHandler, ammoStack.copy());
        if (!insertion.committed()
                || insertion.inserted() != ammoStack.getCount()
                || !insertion.remainder().isEmpty()) {
            return Result.failed(ammoBefore.restoreSafely(ammoHandler)
                    ? IssuanceStatus.TRANSACTION_ROLLED_BACK
                    : IssuanceStatus.TRANSACTION_ROLLBACK_FAILED);
        }

        try {
            if (!stacksMatch(mainHandBefore, guard.getMainHandItem())) {
                return rollback(
                        guard,
                        mainHandBefore,
                        ammoHandler,
                        ammoBefore,
                        false);
            }
            guard.setItemSlot(EquipmentSlot.MAINHAND, gunStack.copy());
            if (!stacksMatch(gunStack, guard.getMainHandItem())) {
                return rollback(
                        guard,
                        mainHandBefore,
                        ammoHandler,
                        ammoBefore,
                        true);
            }
            return new Result(IssuanceStatus.ISSUED, ammoStack.getCount(), provenance);
        } catch (RuntimeException exception) {
            return rollback(
                    guard,
                    mainHandBefore,
                    ammoHandler,
                    ammoBefore,
                    true);
        }
    }

    private static Result rollback(
            Guard guard,
            ItemStack mainHandBefore,
            GuardAmmoHandler ammoHandler,
            AmmoSnapshot ammoBefore,
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
        boolean ammoRestored = ammoBefore.restoreSafely(ammoHandler);
        return Result.failed(mainHandRestored && ammoRestored
                ? IssuanceStatus.TRANSACTION_ROLLED_BACK
                : IssuanceStatus.TRANSACTION_ROLLBACK_FAILED);
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
        PROVENANCE_WRITE_FAILED,
        AMMO_CAPACITY_UNAVAILABLE,
        TRANSACTION_ROLLED_BACK,
        TRANSACTION_ROLLBACK_FAILED,
        TRANSACTION_FAILED
    }

    public record Result(
            IssuanceStatus status,
            int ammunitionItems,
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            if (ammunitionItems < 0
                    || (status == IssuanceStatus.ISSUED) != (ammunitionItems > 0)
                    || (status == IssuanceStatus.ISSUED) != (provenance != null)) {
                throw new IllegalArgumentException("Issuance result has inconsistent ammunition");
            }
        }

        public boolean issued() {
            return status == IssuanceStatus.ISSUED;
        }

        private static Result failed(IssuanceStatus status) {
            return new Result(status, 0, null);
        }
    }

    private record Preparation(
            @Nullable ItemStack gunStack,
            @Nullable ItemStack ammoStack,
            @Nullable NaturalGuardLoadoutProvenance provenance,
            @Nullable IssuanceStatus failure) {
        private boolean ready() {
            return gunStack != null
                    && ammoStack != null
                    && provenance != null
                    && failure == null;
        }

        private static Preparation ready(
                ItemStack gunStack,
                ItemStack ammoStack,
                NaturalGuardLoadoutProvenance provenance) {
            return new Preparation(
                    gunStack.copy(),
                    ammoStack.copy(),
                    Objects.requireNonNull(provenance, "provenance"),
                    null);
        }

        private static Preparation failed(IssuanceStatus status) {
            return new Preparation(
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

        private boolean restoreSafely(GuardAmmoHandler handler) {
            try {
                if (handler.getSlots() != stacks.size()) {
                    return false;
                }
                for (int slot = 0; slot < stacks.size(); slot++) {
                    handler.setStackInSlot(slot, stacks.get(slot).copy());
                }
                for (int slot = 0; slot < stacks.size(); slot++) {
                    if (!stacksMatch(stacks.get(slot), handler.getStackInSlot(slot))) {
                        return false;
                    }
                }
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }
}
