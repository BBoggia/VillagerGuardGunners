package com.gamergaming.villagerguardgunners.ammunition;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.util.AttachmentDataUtils;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * On-demand, allocation-bounded description of a TaCZ gun and its usable
 * reserves. The combat tick does not call this service.
 */
public final class GuardAmmoSummary {
    private static final int MAX_SCANNED_SLOTS = 1_024;

    private GuardAmmoSummary() {
    }

    public static Snapshot capture(ItemStack gunStack, @Nullable IItemHandler reserveHandler) {
        if (gunStack == null
                || gunStack.isEmpty()
                || !(gunStack.getItem() instanceof IGun gun)) {
            return Snapshot.unavailable(Status.NO_GUN, null);
        }

        ResourceLocation gunId;
        try {
            gunId = gun.getGunId(gunStack);
            if (gunId == null) {
                return Snapshot.unavailable(Status.UNREADABLE_GUN, null);
            }
        } catch (RuntimeException exception) {
            return Snapshot.unavailable(Status.UNREADABLE_GUN, null);
        }

        try {
            return TimelessAPI.getCommonGunIndex(gunId)
                    .map(index -> captureResolved(gunStack, gun, gunId, index.getGunData(), reserveHandler))
                    .orElseGet(() -> Snapshot.unavailable(Status.UNKNOWN_GUN_DATA, gunId));
        } catch (RuntimeException exception) {
            return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
        }
    }

    /**
     * Resolved-data overload used by cached callers and deterministic tests.
     * The supplied data must correspond to the supplied gun stack.
     */
    public static Snapshot capture(
            ItemStack gunStack,
            GunData gunData,
            @Nullable IItemHandler reserveHandler) {
        if (gunStack == null
                || gunStack.isEmpty()
                || !(gunStack.getItem() instanceof IGun gun)) {
            return Snapshot.unavailable(Status.NO_GUN, null);
        }
        if (gunData == null) {
            return Snapshot.unavailable(Status.UNKNOWN_GUN_DATA, null);
        }

        ResourceLocation gunId = null;
        try {
            gunId = gun.getGunId(gunStack);
            if (gunId == null) {
                return Snapshot.unavailable(Status.UNREADABLE_GUN, null);
            }
            return captureResolved(gunStack, gun, gunId, gunData, reserveHandler);
        } catch (RuntimeException exception) {
            return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
        }
    }

    private static Snapshot captureResolved(
            ItemStack gunStack,
            IGun gun,
            @Nullable ResourceLocation gunId,
            GunData gunData,
            @Nullable IItemHandler reserveHandler) {
        try {
            int loadedAmmo = gun.getCurrentAmmoCount(gunStack);
            int magazineCapacity = AttachmentDataUtils.getAmmoCountWithAttachment(gunStack, gunData);
            if (loadedAmmo < 0 || magazineCapacity < 0) {
                return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
            }

            Bolt bolt = gunData.getBolt();
            GunReloadData reloadData = gunData.getReloadData();
            FeedType feedType = reloadData == null ? null : reloadData.getType();
            if (bolt == null || feedType == null) {
                return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
            }
            boolean supportsChamber = bolt != Bolt.OPEN_BOLT;
            boolean chambered = supportsChamber && gun.hasBulletInBarrel(gunStack);

            if (reloadData.isInfinite()) {
                return Snapshot.ready(
                        gunId,
                        feedType,
                        loadedAmmo,
                        magazineCapacity,
                        supportsChamber,
                        chambered,
                        0L,
                        true,
                        ReserveSource.NATIVE_INFINITE,
                        0,
                        0,
                        0,
                        true);
            }

            if (gun.useDummyAmmo(gunStack)) {
                int dummyAmmo = gun.getDummyAmmoAmount(gunStack);
                if (dummyAmmo < 0) {
                    return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
                }
                return Snapshot.ready(
                        gunId,
                        feedType,
                        loadedAmmo,
                        magazineCapacity,
                        supportsChamber,
                        chambered,
                        dummyAmmo,
                        false,
                        ReserveSource.DUMMY,
                        0,
                        0,
                        0,
                        true);
            }

            ReserveScan reserve = scanPhysicalReserve(gunStack, reserveHandler);
            return Snapshot.ready(
                    gunId,
                    feedType,
                    loadedAmmo,
                    magazineCapacity,
                    supportsChamber,
                    chambered,
                    reserve.units(),
                    reserve.infinite(),
                    ReserveSource.PHYSICAL,
                    reserve.compatibleStacks(),
                    reserve.incompatibleAmmoStacks(),
                    reserve.unknownAmmoStacks(),
                    reserve.complete());
        } catch (RuntimeException exception) {
            return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
        }
    }

    private static ReserveScan scanPhysicalReserve(
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
        NATIVE_INFINITE
    }

    public record Snapshot(
            Status status,
            @Nullable ResourceLocation gunId,
            @Nullable FeedType feedType,
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

        private static Snapshot ready(
                @Nullable ResourceLocation gunId,
                @Nullable FeedType feedType,
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

        private static Snapshot unavailable(Status status, @Nullable ResourceLocation gunId) {
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

    private record ReserveScan(
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
