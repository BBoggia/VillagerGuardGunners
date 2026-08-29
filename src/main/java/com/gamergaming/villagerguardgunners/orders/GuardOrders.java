package com.gamergaming.villagerguardgunners.orders;

import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Immutable, persistent orders for one guard.
 *
 * <p>Every default is {@code INHERIT}, so adding this data to an existing world
 * cannot change behavior. Server safety and performance master controls remain
 * authoritative when an effective value is resolved.</p>
 */
public record GuardOrders(
        EngagementOrder engagement,
        TacticalOrder tactics,
        ToggleOrder explosives,
        ToggleOrder ammoPickup,
        ToggleOrder ammoSharing,
        MovementOrder movement,
        RangeOrder maximumRange) {
    public static final int DATA_VERSION = 1;
    public static final GuardOrders DEFAULT = new GuardOrders(
            EngagementOrder.INHERIT,
            TacticalOrder.INHERIT,
            ToggleOrder.INHERIT,
            ToggleOrder.INHERIT,
            ToggleOrder.INHERIT,
            MovementOrder.INHERIT,
            RangeOrder.INHERIT);

    private static final String VERSION_TAG = "Version";
    private static final String ENGAGEMENT_TAG = "Engagement";
    private static final String TACTICS_TAG = "Tactics";
    private static final String EXPLOSIVES_TAG = "Explosives";
    private static final String AMMO_PICKUP_TAG = "AmmoPickup";
    private static final String AMMO_SHARING_TAG = "AmmoSharing";
    private static final String MOVEMENT_TAG = "Movement";
    private static final String MAXIMUM_RANGE_TAG = "MaximumRange";

    public GuardOrders {
        engagement = Objects.requireNonNullElse(engagement, EngagementOrder.INHERIT);
        tactics = Objects.requireNonNullElse(tactics, TacticalOrder.INHERIT);
        explosives = Objects.requireNonNullElse(explosives, ToggleOrder.INHERIT);
        ammoPickup = Objects.requireNonNullElse(ammoPickup, ToggleOrder.INHERIT);
        ammoSharing = Objects.requireNonNullElse(ammoSharing, ToggleOrder.INHERIT);
        movement = Objects.requireNonNullElse(movement, MovementOrder.INHERIT);
        maximumRange = Objects.requireNonNullElse(maximumRange, RangeOrder.INHERIT);
    }

    public EngagementMode resolveEngagementMode(EngagementMode serverMode) {
        EngagementMode normalizedServer = Objects.requireNonNullElse(
                serverMode,
                EngagementMode.GUARD_TARGETS);
        // An operator-wide hold-fire order cannot be relaxed from a guard UI.
        return normalizedServer == EngagementMode.HOLD_FIRE
                ? EngagementMode.HOLD_FIRE
                : engagement.resolve(normalizedServer);
    }

    @Nullable
    public TacticalProfile tacticalProfileOverride() {
        return tactics.profile();
    }

    public boolean permitsExplosiveWeapons() {
        return explosives != ToggleOrder.DISABLED;
    }

    public boolean permitsAmmoPickup(boolean serverEnabled) {
        return serverEnabled && ammoPickup != ToggleOrder.DISABLED;
    }

    public boolean permitsAmmoSharing(boolean serverEnabled) {
        return serverEnabled && ammoSharing != ToggleOrder.DISABLED;
    }

    public double resolveMaximumRange(double serverMaximumRange) {
        return maximumRange.limit(serverMaximumRange);
    }

    public int networkValue(OrderField field) {
        return switch (field) {
            case ENGAGEMENT -> engagement.networkId();
            case TACTICS -> tactics.networkId();
            case EXPLOSIVES -> explosives.networkId();
            case AMMO_PICKUP -> ammoPickup.networkId();
            case AMMO_SHARING -> ammoSharing.networkId();
            case MOVEMENT -> movement.networkId();
            case MAXIMUM_RANGE -> maximumRange.networkId();
        };
    }

    public GuardOrders withNetworkValue(OrderField field, int value) {
        return switch (field) {
            case ENGAGEMENT -> withEngagement(EngagementOrder.fromNetworkId(value));
            case TACTICS -> withTactics(TacticalOrder.fromNetworkId(value));
            case EXPLOSIVES -> withExplosives(ToggleOrder.fromNetworkId(value));
            case AMMO_PICKUP -> withAmmoPickup(ToggleOrder.fromNetworkId(value));
            case AMMO_SHARING -> withAmmoSharing(ToggleOrder.fromNetworkId(value));
            case MOVEMENT -> withMovement(MovementOrder.fromNetworkId(value));
            case MAXIMUM_RANGE -> withMaximumRange(RangeOrder.fromNetworkId(value));
        };
    }

    public GuardOrders cycle(OrderField field) {
        return switch (field) {
            case ENGAGEMENT -> withEngagement(engagement.next());
            case TACTICS -> withTactics(tactics.next());
            case EXPLOSIVES -> withExplosives(explosives.next());
            case AMMO_PICKUP -> withAmmoPickup(ammoPickup.next());
            case AMMO_SHARING -> withAmmoSharing(ammoSharing.next());
            case MOVEMENT -> withMovement(movement.next());
            case MAXIMUM_RANGE -> withMaximumRange(maximumRange.next());
        };
    }

    public GuardOrders withEngagement(EngagementOrder value) {
        return new GuardOrders(value, tactics, explosives, ammoPickup, ammoSharing, movement, maximumRange);
    }

    public GuardOrders withTactics(TacticalOrder value) {
        return new GuardOrders(engagement, value, explosives, ammoPickup, ammoSharing, movement, maximumRange);
    }

    public GuardOrders withExplosives(ToggleOrder value) {
        return new GuardOrders(engagement, tactics, value, ammoPickup, ammoSharing, movement, maximumRange);
    }

    public GuardOrders withAmmoPickup(ToggleOrder value) {
        return new GuardOrders(engagement, tactics, explosives, value, ammoSharing, movement, maximumRange);
    }

    public GuardOrders withAmmoSharing(ToggleOrder value) {
        return new GuardOrders(engagement, tactics, explosives, ammoPickup, value, movement, maximumRange);
    }

    public GuardOrders withMovement(MovementOrder value) {
        return new GuardOrders(engagement, tactics, explosives, ammoPickup, ammoSharing, value, maximumRange);
    }

    public GuardOrders withMaximumRange(RangeOrder value) {
        return new GuardOrders(engagement, tactics, explosives, ammoPickup, ammoSharing, movement, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, DATA_VERSION);
        tag.putString(ENGAGEMENT_TAG, engagement.name());
        tag.putString(TACTICS_TAG, tactics.name());
        tag.putString(EXPLOSIVES_TAG, explosives.name());
        tag.putString(AMMO_PICKUP_TAG, ammoPickup.name());
        tag.putString(AMMO_SHARING_TAG, ammoSharing.name());
        tag.putString(MOVEMENT_TAG, movement.name());
        tag.putString(MAXIMUM_RANGE_TAG, maximumRange.name());
        return tag;
    }

    public static GuardOrders load(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DEFAULT;
        }
        return new GuardOrders(
                readEnum(tag, ENGAGEMENT_TAG, EngagementOrder.class, EngagementOrder.INHERIT),
                readEnum(tag, TACTICS_TAG, TacticalOrder.class, TacticalOrder.INHERIT),
                readEnum(tag, EXPLOSIVES_TAG, ToggleOrder.class, ToggleOrder.INHERIT),
                readEnum(tag, AMMO_PICKUP_TAG, ToggleOrder.class, ToggleOrder.INHERIT),
                readEnum(tag, AMMO_SHARING_TAG, ToggleOrder.class, ToggleOrder.INHERIT),
                readEnum(tag, MOVEMENT_TAG, MovementOrder.class, MovementOrder.INHERIT),
                readEnum(tag, MAXIMUM_RANGE_TAG, RangeOrder.class, RangeOrder.INHERIT));
    }

    private static <E extends Enum<E>> E readEnum(
            CompoundTag tag,
            String key,
            Class<E> type,
            E fallback) {
        String value = tag.getString(key);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static <E extends Enum<E> & NetworkValue<E>> E fromNetworkId(
            E[] values,
            int networkId,
            E fallback) {
        for (E value : values) {
            if (value.networkId() == networkId) {
                return value;
            }
        }
        return fallback;
    }

    private static <E extends Enum<E>> E next(E value, E[] values) {
        return values[(value.ordinal() + 1) % values.length];
    }

    private interface NetworkValue<E> {
        int networkId();
    }

    public enum OrderField {
        ENGAGEMENT(0),
        TACTICS(1),
        EXPLOSIVES(2),
        AMMO_PICKUP(3),
        AMMO_SHARING(4),
        MOVEMENT(5),
        MAXIMUM_RANGE(6);

        public static final int COUNT = 7;
        private final int buttonId;

        OrderField(int buttonId) {
            this.buttonId = buttonId;
        }

        public int buttonId() {
            return buttonId;
        }

        @Nullable
        public static OrderField fromButtonId(int buttonId) {
            for (OrderField field : values()) {
                if (field.buttonId == buttonId) {
                    return field;
                }
            }
            return null;
        }
    }

    public enum EngagementOrder implements NetworkValue<EngagementOrder> {
        INHERIT(0, null),
        GUARD_TARGETS(1, EngagementMode.GUARD_TARGETS),
        HOSTILE_MOBS_ONLY(2, EngagementMode.HOSTILE_MOBS_ONLY),
        DEFENSIVE(3, EngagementMode.DEFENSIVE),
        HOLD_FIRE(4, EngagementMode.HOLD_FIRE);

        private final int networkId;
        @Nullable
        private final EngagementMode mode;

        EngagementOrder(int networkId, @Nullable EngagementMode mode) {
            this.networkId = networkId;
            this.mode = mode;
        }

        @Override
        public int networkId() {
            return networkId;
        }

        public EngagementMode resolve(EngagementMode inherited) {
            return mode == null ? inherited : mode;
        }

        public EngagementOrder next() {
            return GuardOrders.next(this, values());
        }

        public static EngagementOrder fromNetworkId(int networkId) {
            return GuardOrders.fromNetworkId(values(), networkId, INHERIT);
        }
    }

    public enum TacticalOrder implements NetworkValue<TacticalOrder> {
        INHERIT(0, null),
        GENERALIST(1, TacticalProfile.GENERALIST),
        SKIRMISHER(2, TacticalProfile.SKIRMISHER),
        BREACHER(3, TacticalProfile.BREACHER),
        MARKSMAN(4, TacticalProfile.MARKSMAN),
        SUPPORT(5, TacticalProfile.SUPPORT),
        DEMOLITIONS(6, TacticalProfile.DEMOLITIONS);

        private final int networkId;
        @Nullable
        private final TacticalProfile profile;

        TacticalOrder(int networkId, @Nullable TacticalProfile profile) {
            this.networkId = networkId;
            this.profile = profile;
        }

        @Override
        public int networkId() {
            return networkId;
        }

        @Nullable
        public TacticalProfile profile() {
            return profile;
        }

        public TacticalOrder next() {
            return GuardOrders.next(this, values());
        }

        public static TacticalOrder fromNetworkId(int networkId) {
            return GuardOrders.fromNetworkId(values(), networkId, INHERIT);
        }
    }

    public enum ToggleOrder implements NetworkValue<ToggleOrder> {
        INHERIT(0),
        ENABLED(1),
        DISABLED(2);

        private final int networkId;

        ToggleOrder(int networkId) {
            this.networkId = networkId;
        }

        @Override
        public int networkId() {
            return networkId;
        }

        public ToggleOrder next() {
            return GuardOrders.next(this, values());
        }

        public static ToggleOrder fromNetworkId(int networkId) {
            return GuardOrders.fromNetworkId(values(), networkId, INHERIT);
        }
    }

    public enum MovementOrder implements NetworkValue<MovementOrder> {
        INHERIT(0),
        TACTICAL(1),
        HOLD_POSITION(2),
        PATROL_BOUND(3);

        private final int networkId;

        MovementOrder(int networkId) {
            this.networkId = networkId;
        }

        @Override
        public int networkId() {
            return networkId;
        }

        public boolean holdsPosition() {
            return this == HOLD_POSITION;
        }

        public boolean treatAsPatrolling(boolean guardIsPatrolling) {
            return switch (this) {
                case INHERIT -> guardIsPatrolling;
                case TACTICAL -> false;
                case HOLD_POSITION, PATROL_BOUND -> true;
            };
        }

        public MovementOrder next() {
            return GuardOrders.next(this, values());
        }

        public static MovementOrder fromNetworkId(int networkId) {
            return GuardOrders.fromNetworkId(values(), networkId, INHERIT);
        }
    }

    public enum RangeOrder implements NetworkValue<RangeOrder> {
        INHERIT(0, Double.POSITIVE_INFINITY),
        BLOCKS_8(1, 8.0D),
        BLOCKS_16(2, 16.0D),
        BLOCKS_24(3, 24.0D),
        BLOCKS_32(4, 32.0D),
        BLOCKS_48(5, 48.0D),
        BLOCKS_64(6, 64.0D);

        private final int networkId;
        private final double blocks;

        RangeOrder(int networkId, double blocks) {
            this.networkId = networkId;
            this.blocks = blocks;
        }

        @Override
        public int networkId() {
            return networkId;
        }

        public double blocks() {
            return blocks;
        }

        public double limit(double serverMaximumRange) {
            return this == INHERIT
                    ? serverMaximumRange
                    : Math.min(serverMaximumRange, blocks);
        }

        public RangeOrder next() {
            return GuardOrders.next(this, values());
        }

        public static RangeOrder fromNetworkId(int networkId) {
            return GuardOrders.fromNetworkId(values(), networkId, INHERIT);
        }
    }
}
