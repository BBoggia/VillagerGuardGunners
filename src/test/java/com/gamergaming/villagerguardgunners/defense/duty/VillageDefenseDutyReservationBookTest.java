package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageDefenseDutyReservationBookTest {
    @Test
    void replacementIsBidirectionalAtomicAndNetworkScoped() {
        UUID firstNetwork = UUID.randomUUID();
        UUID secondNetwork = UUID.randomUUID();
        UUID firstGuard = UUID.randomUUID();
        UUID secondGuard = UUID.randomUUID();
        VillageDefenseDutyAssignment first = assignment(
                firstNetwork, UUID.randomUUID(), "first", firstGuard);
        VillageDefenseDutyAssignment second = assignment(
                secondNetwork, UUID.randomUUID(), "second", secondGuard);
        VillageDefenseDutyReservationBook book =
                new VillageDefenseDutyReservationBook();

        assertTrue(book.replaceNetwork(firstNetwork, List.of(first)));
        assertTrue(book.replaceNetwork(secondNetwork, List.of(second)));
        VillageDefenseDutyAssignment conflicting = assignment(
                firstNetwork, UUID.randomUUID(), "replacement", secondGuard);

        assertAll(
                () -> assertFalse(book.replaceNetwork(
                        firstNetwork, List.of(conflicting))),
                () -> assertSame(first, book.forGuard(firstGuard)),
                () -> assertSame(first, book.forSlot(first.slotId())),
                () -> assertSame(second, book.forGuard(secondGuard)),
                () -> assertEquals(2, book.size()));

        assertSame(first, book.releaseGuard(firstGuard));
        assertAll(
                () -> assertNull(book.forGuard(firstGuard)),
                () -> assertNull(book.forSlot(first.slotId())),
                () -> assertEquals(1, book.size()));
    }

    private static VillageDefenseDutyAssignment assignment(
            UUID networkId,
            UUID buildingId,
            String key,
            UUID guardId) {
        VillageDefenseDutySlot slot = new VillageDefenseDutySlot(
                VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                VillageDefenseDutyIds.slotId(buildingId, key),
                buildingId,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                key,
                BlockPos.ZERO,
                Direction.NORTH,
                VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        return new VillageDefenseDutyAssignment(networkId, slot, guardId, 0L, 100L);
    }
}
