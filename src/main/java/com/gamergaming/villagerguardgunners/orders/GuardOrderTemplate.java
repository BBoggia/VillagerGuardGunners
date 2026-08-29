package com.gamergaming.villagerguardgunners.orders;

import com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.ToggleOrder;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Versioned item-NBT representation of a complete set of raw per-guard orders.
 *
 * <p>This codec deliberately differs from {@link GuardOrders#load(CompoundTag)}:
 * world persistence recovers unknown individual values as {@code INHERIT}, while
 * a player-applied template must be complete and valid before it can change a
 * guard. This prevents a damaged or future template from silently resetting
 * only part of a guard's configuration.</p>
 */
public final class GuardOrderTemplate {
    public static final String TEMPLATE_TAG = "villagerguardgunners:order_template";
    public static final int DATA_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String ORDERS_TAG = "Orders";
    private static final String ENGAGEMENT_TAG = "Engagement";
    private static final String TACTICS_TAG = "Tactics";
    private static final String EXPLOSIVES_TAG = "Explosives";
    private static final String AMMO_PICKUP_TAG = "AmmoPickup";
    private static final String AMMO_SHARING_TAG = "AmmoSharing";
    private static final String MOVEMENT_TAG = "Movement";
    private static final String MAXIMUM_RANGE_TAG = "MaximumRange";

    private GuardOrderTemplate() {
    }

    /**
     * Replaces only this add-on's template payload, preserving display names
     * and every unrelated tag already present on the future ledger stack.
     */
    public static void writeTo(CompoundTag itemTag, GuardOrders orders) {
        Objects.requireNonNull(itemTag, "itemTag");
        Objects.requireNonNull(orders, "orders");

        CompoundTag templateTag = new CompoundTag();
        templateTag.putInt(VERSION_TAG, DATA_VERSION);
        templateTag.put(ORDERS_TAG, orders.save());
        itemTag.put(TEMPLATE_TAG, templateTag);
    }

    /**
     * Reads a template from an item's root tag without modifying that tag.
     */
    public static DecodeResult readFrom(@Nullable CompoundTag itemTag) {
        if (itemTag == null || !itemTag.contains(TEMPLATE_TAG)) {
            return DecodeResult.blank();
        }
        if (!itemTag.contains(TEMPLATE_TAG, Tag.TAG_COMPOUND)) {
            return DecodeResult.corrupt();
        }

        CompoundTag templateTag = itemTag.getCompound(TEMPLATE_TAG);
        if (!templateTag.contains(VERSION_TAG, Tag.TAG_INT)) {
            return DecodeResult.corrupt();
        }

        int templateVersion = templateTag.getInt(VERSION_TAG);
        if (templateVersion != DATA_VERSION) {
            return DecodeResult.unsupported(templateVersion);
        }
        if (!templateTag.contains(ORDERS_TAG, Tag.TAG_COMPOUND)) {
            return DecodeResult.corrupt();
        }

        CompoundTag ordersTag = templateTag.getCompound(ORDERS_TAG);
        if (!ordersTag.contains(VERSION_TAG, Tag.TAG_INT)) {
            return DecodeResult.corrupt();
        }

        int ordersVersion = ordersTag.getInt(VERSION_TAG);
        if (ordersVersion != GuardOrders.DATA_VERSION) {
            return DecodeResult.unsupported(ordersVersion);
        }

        EngagementOrder engagement = readRequiredEnum(
                ordersTag,
                ENGAGEMENT_TAG,
                EngagementOrder.class);
        TacticalOrder tactics = readRequiredEnum(
                ordersTag,
                TACTICS_TAG,
                TacticalOrder.class);
        ToggleOrder explosives = readRequiredEnum(
                ordersTag,
                EXPLOSIVES_TAG,
                ToggleOrder.class);
        ToggleOrder ammoPickup = readRequiredEnum(
                ordersTag,
                AMMO_PICKUP_TAG,
                ToggleOrder.class);
        ToggleOrder ammoSharing = readRequiredEnum(
                ordersTag,
                AMMO_SHARING_TAG,
                ToggleOrder.class);
        MovementOrder movement = readRequiredEnum(
                ordersTag,
                MOVEMENT_TAG,
                MovementOrder.class);
        RangeOrder maximumRange = readRequiredEnum(
                ordersTag,
                MAXIMUM_RANGE_TAG,
                RangeOrder.class);

        if (engagement == null
                || tactics == null
                || explosives == null
                || ammoPickup == null
                || ammoSharing == null
                || movement == null
                || maximumRange == null) {
            return DecodeResult.corrupt();
        }

        return DecodeResult.valid(new GuardOrders(
                engagement,
                tactics,
                explosives,
                ammoPickup,
                ammoSharing,
                movement,
                maximumRange));
    }

    @Nullable
    private static <E extends Enum<E>> E readRequiredEnum(
            CompoundTag tag,
            String key,
            Class<E> type) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return null;
        }

        String value = tag.getString(key).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public enum DecodeStatus {
        BLANK,
        VALID,
        CORRUPT,
        UNSUPPORTED_VERSION
    }

    /**
     * {@code storedVersion} is meaningful only for
     * {@link DecodeStatus#UNSUPPORTED_VERSION}; it is {@code -1} otherwise.
     */
    public record DecodeResult(
            DecodeStatus status,
            @Nullable GuardOrders orders,
            int storedVersion) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            if ((status == DecodeStatus.VALID) != (orders != null)) {
                throw new IllegalArgumentException(
                        "Only a valid template may contain decoded orders");
            }
            if (status != DecodeStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported template may report a stored version");
            }
        }

        public boolean isValid() {
            return status == DecodeStatus.VALID;
        }

        private static DecodeResult blank() {
            return new DecodeResult(DecodeStatus.BLANK, null, -1);
        }

        private static DecodeResult valid(GuardOrders orders) {
            return new DecodeResult(DecodeStatus.VALID, orders, -1);
        }

        private static DecodeResult corrupt() {
            return new DecodeResult(DecodeStatus.CORRUPT, null, -1);
        }

        private static DecodeResult unsupported(int storedVersion) {
            return new DecodeResult(
                    DecodeStatus.UNSUPPORTED_VERSION,
                    null,
                    storedVersion);
        }
    }
}
