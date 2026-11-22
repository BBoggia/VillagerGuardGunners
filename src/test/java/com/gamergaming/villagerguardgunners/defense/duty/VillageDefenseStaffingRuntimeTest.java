package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageDefenseStaffingRuntimeTest {
    @Test
    void losingANetworkRestoresABoundedLookupDeadline() {
        long gameTime = 1_000L;

        assertAll(
                () -> assertEquals(
                        gameTime + VillageDefenseStaffingRuntime
                                .UNASSIGNED_NETWORK_RECHECK_TICKS,
                        VillageDefenseStaffingRuntime.updatedNetworkLookupDeadline(
                                Long.MAX_VALUE, false, true, null, gameTime)),
                () -> assertEquals(
                        Long.MAX_VALUE,
                        VillageDefenseStaffingRuntime.updatedNetworkLookupDeadline(
                                500L,
                                true,
                                true,
                                UUID.randomUUID(),
                                gameTime)),
                () -> assertEquals(
                        1_500L,
                        VillageDefenseStaffingRuntime.updatedNetworkLookupDeadline(
                                1_500L, false, false, null, gameTime)));
    }
}
