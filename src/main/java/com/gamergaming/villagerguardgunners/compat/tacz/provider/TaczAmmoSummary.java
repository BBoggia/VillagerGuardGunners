package com.gamergaming.villagerguardgunners.compat.tacz.provider;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.ReserveSource;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.Snapshot;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.Status;
import com.gamergaming.villagerguardgunners.guns.FeedKind;
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
import tallestegg.guardvillagers.entities.Guard;

/** Reads a TaCZ gun's magazine, chamber, feed, and reserve rules. The combat tick does not call this. */
public final class TaczAmmoSummary {
    private TaczAmmoSummary() {
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

    public static Snapshot captureForGuard(Guard guard,
            ItemStack stack, GunData data, @Nullable IItemHandler handler) {
        return GuardAmmoSummary.withVirtualReserve(guard, capture(stack, data, handler));
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
            FeedType nativeFeed = reloadData == null ? null : reloadData.getType();
            if (bolt == null || nativeFeed == null) {
                return Snapshot.unavailable(Status.UNREADABLE_GUN, gunId);
            }
            FeedKind feedType = feedKind(nativeFeed);
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

            GuardAmmoSummary.ReserveScan reserve = GuardAmmoSummary.scanPhysicalReserve(gunStack, reserveHandler);
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

    private static FeedKind feedKind(FeedType feedType) {
        return switch (feedType) {
            case MAGAZINE -> FeedKind.MAGAZINE;
            case MANUAL -> FeedKind.MANUAL;
            case FUEL -> FeedKind.FUEL;
            case INVENTORY -> FeedKind.INVENTORY;
        };
    }
}
