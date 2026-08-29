package com.gamergaming.villagerguardgunners.orders;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.OrderField;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.ToggleOrder;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class GuardOrdersTest {

    @Test
    void defaultsPreserveServerBehaviorAndGlobalSafetyControlsRemainAuthoritative() {
        GuardOrders defaults = GuardOrders.DEFAULT;
        GuardOrders attemptedOverrides = defaults
                .withEngagement(EngagementOrder.GUARD_TARGETS)
                .withAmmoPickup(ToggleOrder.ENABLED)
                .withAmmoSharing(ToggleOrder.ENABLED);

        assertAll(
                () -> assertEquals(
                        EngagementMode.DEFENSIVE,
                        defaults.resolveEngagementMode(EngagementMode.DEFENSIVE)),
                () -> assertEquals(
                        EngagementMode.HOLD_FIRE,
                        attemptedOverrides.resolveEngagementMode(EngagementMode.HOLD_FIRE)),
                () -> assertFalse(attemptedOverrides.permitsAmmoPickup(false)),
                () -> assertFalse(attemptedOverrides.permitsAmmoSharing(false)),
                () -> assertTrue(defaults.permitsExplosiveWeapons()));
    }

    @Test
    void perGuardOrdersCanNarrowBehaviorWithoutWideningServerLimits() {
        GuardOrders orders = GuardOrders.DEFAULT
                .withEngagement(EngagementOrder.DEFENSIVE)
                .withTactics(TacticalOrder.MARKSMAN)
                .withExplosives(ToggleOrder.DISABLED)
                .withMaximumRange(RangeOrder.BLOCKS_24);

        assertAll(
                () -> assertEquals(
                        EngagementMode.DEFENSIVE,
                        orders.resolveEngagementMode(EngagementMode.GUARD_TARGETS)),
                () -> assertEquals(TacticalProfile.MARKSMAN, orders.tacticalProfileOverride()),
                () -> assertFalse(orders.permitsExplosiveWeapons()),
                () -> assertEquals(24.0D, orders.resolveMaximumRange(64.0D)),
                () -> assertEquals(16.0D, orders.resolveMaximumRange(16.0D)),
                () -> assertNull(GuardOrders.DEFAULT.tacticalProfileOverride()));
    }

    @Test
    void everyNetworkFieldRoundTripsAndRejectsUnknownValues() {
        GuardOrders orders = GuardOrders.DEFAULT;
        for (OrderField field : OrderField.values()) {
            GuardOrders cycled = orders.cycle(field);
            int networkValue = cycled.networkValue(field);
            assertEquals(cycled, orders.withNetworkValue(field, networkValue));
            assertEquals(orders, orders.withNetworkValue(field, Integer.MAX_VALUE));
        }
    }

    @Test
    void persistentDataRoundTripsAndMalformedFieldsFailBackIndividually() {
        GuardOrders expected = new GuardOrders(
                EngagementOrder.HOSTILE_MOBS_ONLY,
                TacticalOrder.SUPPORT,
                ToggleOrder.DISABLED,
                ToggleOrder.ENABLED,
                ToggleOrder.DISABLED,
                MovementOrder.PATROL_BOUND,
                RangeOrder.BLOCKS_32);
        CompoundTag saved = expected.save();

        assertEquals(expected, GuardOrders.load(saved));

        saved.putString("Engagement", "future_mode");
        saved.putString("Tactics", "  marksman  ");
        saved.putString("MaximumRange", "not_a_range");
        GuardOrders recovered = GuardOrders.load(saved);
        assertAll(
                () -> assertEquals(EngagementOrder.INHERIT, recovered.engagement()),
                () -> assertEquals(TacticalOrder.MARKSMAN, recovered.tactics()),
                () -> assertEquals(ToggleOrder.DISABLED, recovered.explosives()),
                () -> assertEquals(RangeOrder.INHERIT, recovered.maximumRange()),
                () -> assertEquals(GuardOrders.DEFAULT, GuardOrders.load(null)),
                () -> assertEquals(GuardOrders.DEFAULT, GuardOrders.load(new CompoundTag())));
    }

    @Test
    void movementOrdersPreserveUpstreamPatrolSemantics() {
        assertAll(
                () -> assertFalse(MovementOrder.INHERIT.treatAsPatrolling(false)),
                () -> assertTrue(MovementOrder.INHERIT.treatAsPatrolling(true)),
                () -> assertFalse(MovementOrder.TACTICAL.treatAsPatrolling(true)),
                () -> assertTrue(MovementOrder.PATROL_BOUND.treatAsPatrolling(false)),
                () -> assertTrue(MovementOrder.HOLD_POSITION.holdsPosition()),
                () -> assertFalse(MovementOrder.PATROL_BOUND.holdsPosition()));
    }
}
