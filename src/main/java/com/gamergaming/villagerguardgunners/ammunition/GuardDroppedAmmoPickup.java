package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded, item-driven pickup for physical TaCZ ammunition. Eligible dropped
 * ammunition invokes this service at a staggered low frequency; guards never
 * scan for items or pathfind toward them.
 */
public final class GuardDroppedAmmoPickup {
    public static final int CHECK_INTERVAL_TICKS = 10;

    private GuardDroppedAmmoPickup() {
    }

    public static PickupResult tryPickup(ItemEntity droppedAmmo) {
        if (droppedAmmo == null
                || droppedAmmo.level().isClientSide
                || !droppedAmmo.isAlive()) {
            return PickupResult.failed(PickupStatus.INVALID_ITEM_ENTITY, ItemStack.EMPTY);
        }

        ItemStack offered = droppedAmmo.getItem();
        if (!GuardAmmoMaintenance.isPotentialSupply(offered)) {
            return PickupResult.failed(PickupStatus.NOT_AMMUNITION, offered);
        }
        if (!ModConfigs.GUARD_GUNNERS.ammunition.pickupDroppedAmmo.get()) {
            return PickupResult.failed(PickupStatus.DISABLED, offered);
        }
        if (!droppedAmmo.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return PickupResult.failed(PickupStatus.MOB_GRIEFING_DISABLED, offered);
        }
        if (droppedAmmo.hasPickUpDelay()) {
            return PickupResult.failed(PickupStatus.PICKUP_DELAY, offered);
        }

        double radius = ModConfigs.GUARD_GUNNERS.ammunition.droppedAmmoPickupRadius.get();
        double radiusSquared = radius * radius;
        AABB searchBounds = droppedAmmo.getBoundingBox().inflate(radius);
        List<Guard> nearbyGuards = droppedAmmo.level().getEntitiesOfClass(
                Guard.class,
                searchBounds,
                guard -> guard.isAlive() && guard.isEffectiveAi());

        Guard recipient = null;
        GuardAmmoHandler destination = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        boolean transactionFailed = false;

        for (Guard guard : nearbyGuards) {
            double distanceSquared = guard.distanceToSqr(droppedAmmo);
            if (distanceSquared > radiusSquared
                    || !GuardOrdersAccess.getOrders(guard).permitsAmmoPickup(true)
                    || (recipient != null
                    && (distanceSquared > nearestDistanceSquared
                    || (distanceSquared == nearestDistanceSquared
                    && guard.getId() >= recipient.getId())))) {
                continue;
            }

            if (!GuardWeaponAmmunition
                    .assessPhysicalSupply(guard, offered)
                    .compatible()) {
                continue;
            }

            GuardAmmoHandler candidateDestination = GuardAmmoAccess.getAmmoHandler(guard);
            GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard);
            GuardAmmoTransactions.InsertionPlan plan =
                    GuardAmmoTransactions.planInsertion(candidateDestination, offered);
            if (!plan.valid()) {
                transactionFailed = true;
                continue;
            }
            if (plan.accepted() <= 0) {
                continue;
            }

            recipient = guard;
            destination = candidateDestination;
            nearestDistanceSquared = distanceSquared;
        }

        if (recipient == null || destination == null) {
            return PickupResult.failed(
                    transactionFailed
                            ? PickupStatus.TRANSACTION_FAILED
                            : PickupStatus.NO_ELIGIBLE_GUARD,
                    offered);
        }

        GuardAmmoTransactions.InsertionResult insertion =
                GuardAmmoTransactions.insert(destination, offered.copy());
        if (!insertion.committed() || insertion.inserted() <= 0) {
            return PickupResult.failed(PickupStatus.TRANSACTION_FAILED, offered);
        }

        recipient.take(droppedAmmo, insertion.inserted());
        if (insertion.remainder().isEmpty()) {
            droppedAmmo.discard();
        } else {
            droppedAmmo.setItem(insertion.remainder().copy());
        }
        recipient.playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
        return new PickupResult(
                PickupStatus.PICKED_UP,
                insertion.inserted(),
                insertion.remainder().copy());
    }

    public enum PickupStatus {
        PICKED_UP,
        DISABLED,
        MOB_GRIEFING_DISABLED,
        PICKUP_DELAY,
        NOT_AMMUNITION,
        NO_ELIGIBLE_GUARD,
        TRANSACTION_FAILED,
        INVALID_ITEM_ENTITY
    }

    public record PickupResult(PickupStatus status, int inserted, ItemStack remainder) {
        public boolean pickedUp() {
            return status == PickupStatus.PICKED_UP;
        }

        private static PickupResult failed(PickupStatus status, ItemStack remainder) {
            return new PickupResult(
                    status,
                    0,
                    remainder == null ? ItemStack.EMPTY : remainder.copy());
        }
    }
}
