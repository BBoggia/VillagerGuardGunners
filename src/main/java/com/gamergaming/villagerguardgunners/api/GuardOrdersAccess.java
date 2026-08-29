package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed persistent access to one guard's immutable orders. */
public interface GuardOrdersAccess {
    GuardOrders villagerGuardGunners$getOrders();

    void villagerGuardGunners$setOrders(GuardOrders orders);

    int villagerGuardGunners$getOrdersRevision();

    static GuardOrders getOrders(Guard guard) {
        return guard instanceof GuardOrdersAccess access
                ? access.villagerGuardGunners$getOrders()
                : GuardOrders.DEFAULT;
    }

    static boolean setOrders(Guard guard, GuardOrders orders) {
        if (!(guard instanceof GuardOrdersAccess access)) {
            return false;
        }
        access.villagerGuardGunners$setOrders(orders);
        return true;
    }

    static int getRevision(Guard guard) {
        return guard instanceof GuardOrdersAccess access
                ? access.villagerGuardGunners$getOrdersRevision()
                : 0;
    }
}
