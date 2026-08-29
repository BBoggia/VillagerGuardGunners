package com.gamergaming.villagerguardgunners.orders;

import com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.OrderField;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.ToggleOrder;
import net.minecraft.network.chat.Component;

/** Shared localized presentation for order values in screens and item tooltips. */
public final class GuardOrderText {
    private GuardOrderText() {
    }

    public static String fieldKey(OrderField field) {
        return switch (field) {
            case ENGAGEMENT -> "engagement";
            case TACTICS -> "tactics";
            case EXPLOSIVES -> "explosives";
            case AMMO_PICKUP -> "ammo_pickup";
            case AMMO_SHARING -> "ammo_sharing";
            case MOVEMENT -> "movement";
            case MAXIMUM_RANGE -> "maximum_range";
        };
    }

    public static Component value(OrderField field, GuardOrders orders) {
        return switch (field) {
            case ENGAGEMENT -> engagement(orders.engagement());
            case TACTICS -> tactics(orders.tactics());
            case EXPLOSIVES -> toggle(orders.explosives());
            case AMMO_PICKUP -> toggle(orders.ammoPickup());
            case AMMO_SHARING -> toggle(orders.ammoSharing());
            case MOVEMENT -> movement(orders.movement());
            case MAXIMUM_RANGE -> maximumRange(orders.maximumRange());
        };
    }

    public static Component engagement(EngagementOrder order) {
        return Component.translatable(
                "container.villagerguardgunners.orders.value.engagement."
                        + switch (order) {
                            case INHERIT -> "inherit";
                            case GUARD_TARGETS -> "guard_targets";
                            case HOSTILE_MOBS_ONLY -> "hostile_mobs_only";
                            case DEFENSIVE -> "defensive";
                            case HOLD_FIRE -> "hold_fire";
                        });
    }

    public static Component tactics(TacticalOrder order) {
        return Component.translatable(
                "container.villagerguardgunners.orders.value.tactics."
                        + switch (order) {
                            case INHERIT -> "inherit";
                            case GENERALIST -> "generalist";
                            case SKIRMISHER -> "skirmisher";
                            case BREACHER -> "breacher";
                            case MARKSMAN -> "marksman";
                            case SUPPORT -> "support";
                            case DEMOLITIONS -> "demolitions";
                        });
    }

    public static Component toggle(ToggleOrder order) {
        return Component.translatable(
                "container.villagerguardgunners.orders.value.toggle."
                        + switch (order) {
                            case INHERIT -> "inherit";
                            case ENABLED -> "enabled";
                            case DISABLED -> "disabled";
                        });
    }

    public static Component movement(MovementOrder order) {
        return Component.translatable(
                "container.villagerguardgunners.orders.value.movement."
                        + switch (order) {
                            case INHERIT -> "inherit";
                            case TACTICAL -> "tactical";
                            case HOLD_POSITION -> "hold_position";
                            case PATROL_BOUND -> "patrol_bound";
                        });
    }

    public static Component maximumRange(RangeOrder order) {
        return order == RangeOrder.INHERIT
                ? Component.translatable(
                        "container.villagerguardgunners.orders.value.maximum_range.inherit")
                : Component.translatable(
                        "container.villagerguardgunners.orders.value.maximum_range.blocks",
                        (int) order.blocks());
    }
}
