package com.gamergaming.villagerguardgunners.ammunition;

import tallestegg.guardvillagers.common.entities.Guard;
import com.gamergaming.villagerguardgunners.guns.FeedKind;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * On-demand, allocation-bounded description of a gun and its usable
 * reserves, read through the gun's provider. The combat tick does not call this service.
 */
public final class GuardAmmoSummary {
    private static final int MAX_SCANNED_SLOTS = 1_024;

    private GuardAmmoSummary() {
    }

    public static Snapshot capture(ItemStack gunStack, @Nullable IItemHandler reserveHandler) {
        if (gunStack == null || gunStack.isEmpty()) {
            return Snapshot.unavailable(Status.NO_GUN, null);
        }
        return GunProviders.find(gunStack)
                .map(provider -> provider.captureAmmo(gunStack, reserveHandler))
                .orElseGet(() -> Snapshot.unavailable(Status.NO_GUN, null));
    }

    public static Snapshot captureForGuard(Guard guard,
            ItemStack stack, @Nullable IItemHandler handler) {
        return withVirtualReserve(guard, capture(stack, handler));
    }

    /** Replaces a ready weapon's reserve with the configured unlimited guard reserve. */
    public static Snapshot withVirtualReserve(Guard guard, Snapshot value) {
        if (!GuardInfiniteAmmo.virtualReserve(guard) || !value.isReady()) return value;
        return Snapshot.ready(value.gunId(), value.feedType(), value.loadedAmmo(), value.magazineCapacity(),
                value.supportsChamber(), value.chambered(), 0L, true, ReserveSource.VIRTUAL,
                0, 0, 0, true);
    }

    /** Counts the reserve stacks a provider accepts for the gun. Bounded; never mutates the handler. */
    public static ReserveScan scanPhysicalReserve(
            ItemStack gunStack,
            @Nullable IItemHandler reserveHandler) {
        if (reserveHandler == null) {
            return ReserveScan.unavailable();
        }

        int slots;
        try {
            slots = reserveHandler.getSlots();
        } catch (RuntimeException exception) {
            return ReserveScan.unavailable();
        }
        if (slots < 0) {
            return ReserveScan.unavailable();
        }

        int scannedSlots = Math.min(slots, MAX_SCANNED_SLOTS);
        long reserveUnits = 0L;
        boolean infinite = false;
        int compatibleStacks = 0;
        int incompatibleAmmoStacks = 0;
        int unknownAmmoStacks = 0;
        boolean complete = slots <= MAX_SCANNED_SLOTS;

        for (int slot = 0; slot < scannedSlots; slot++) {
            ItemStack candidate;
            try {
                candidate = reserveHandler.getStackInSlot(slot);
            } catch (RuntimeException exception) {
                complete = false;
                continue;
            }
            if (candidate == null) {
                complete = false;
                continue;
            }

            AmmoCompatibility.Assessment assessment = AmmoCompatibility.assess(gunStack, candidate);
            if (!assessment.isAmmunition()) {
                continue;
            }
            switch (assessment.compatibility()) {
                case COMPATIBLE -> {
                    compatibleStacks = saturatingIncrement(compatibleStacks);
                    infinite |= assessment.infinite();
                    reserveUnits = saturatingAdd(reserveUnits, assessment.units());
                }
                case INCOMPATIBLE -> incompatibleAmmoStacks =
                        saturatingIncrement(incompatibleAmmoStacks);
                case UNKNOWN -> unknownAmmoStacks = saturatingIncrement(unknownAmmoStacks);
            }
        }

        return new ReserveScan(
                reserveUnits,
                infinite,
                compatibleStacks,
                incompatibleAmmoStacks,
                unknownAmmoStacks,
                complete);
    }

    static long saturatingAdd(long current, long addition) {
        if (current < 0L || addition < 0L || Long.MAX_VALUE - current < addition) {
            return Long.MAX_VALUE;
        }
        return current + addition;
    }

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    public enum Status {
        READY,
        NO_GUN,
        UNKNOWN_GUN_DATA,
        UNREADABLE_GUN
    }

    public enum ReserveSource {
        NONE,
        PHYSICAL,
        DUMMY,
        NATIVE_INFINITE,
        VIRTUAL
    }

    public record Snapshot(
            Status status,
            @Nullable ResourceLocation gunId,
            @Nullable FeedKind feedType,
            int loadedAmmo,
            int magazineCapacity,
            boolean supportsChamber,
            boolean chambered,
            long reserveUnits,
            boolean infiniteReserve,
            ReserveSource reserveSource,
            int compatibleStacks,
            int incompatibleAmmoStacks,
            int unknownAmmoStacks,
            boolean reserveScanComplete) {

        public boolean isReady() {
            return status == Status.READY;
        }

        public int displayedLoadedAmmo() {
            return displayedCount(loadedAmmo, chambered);
        }

        public int displayedCapacity() {
            return displayedCount(magazineCapacity, supportsChamber);
        }

        public boolean hasCompatibleReserve() {
            return infiniteReserve || reserveUnits > 0L;
        }

        private static int displayedCount(int count, boolean includeChamber) {
            if (count < 0 || !includeChamber || count == Integer.MAX_VALUE) {
                return count;
            }
            return count + 1;
        }

        public static Snapshot ready(
                @Nullable ResourceLocation gunId,
                @Nullable FeedKind feedType,
                int loadedAmmo,
                int magazineCapacity,
                boolean supportsChamber,
                boolean chambered,
                long reserveUnits,
                boolean infiniteReserve,
                ReserveSource reserveSource,
                int compatibleStacks,
                int incompatibleAmmoStacks,
                int unknownAmmoStacks,
                boolean reserveScanComplete) {
            return new Snapshot(
                    Status.READY,
                    gunId,
                    feedType,
                    loadedAmmo,
                    magazineCapacity,
                    supportsChamber,
                    chambered,
                    reserveUnits,
                    infiniteReserve,
                    reserveSource,
                    compatibleStacks,
                    incompatibleAmmoStacks,
                    unknownAmmoStacks,
                    reserveScanComplete);
        }

        public static Snapshot unavailable(Status status, @Nullable ResourceLocation gunId) {
            return new Snapshot(
                    status,
                    gunId,
                    null,
                    -1,
                    -1,
                    false,
                    false,
                    0L,
                    false,
                    ReserveSource.NONE,
                    0,
                    0,
                    0,
                    false);
        }
    }

    public record ReserveScan(
            long units,
            boolean infinite,
            int compatibleStacks,
            int incompatibleAmmoStacks,
            int unknownAmmoStacks,
            boolean complete) {
        private static ReserveScan unavailable() {
            return new ReserveScan(0L, false, 0, 0, 0, false);
        }
    }
}
