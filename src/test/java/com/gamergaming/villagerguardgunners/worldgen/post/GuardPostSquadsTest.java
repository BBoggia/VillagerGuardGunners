package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import org.junit.jupiter.api.Test;

final class GuardPostSquadsTest {
    @Test
    void automaticPostsLeaveAPlayerSquadCapacityReserve() {
        int ceiling = SquadRegistrySavedData.MAX_SQUADS - GuardPostSquads.PLAYER_SQUAD_RESERVE;

        assertTrue(GuardPostSquads.canAutomaticallyRegister(ceiling - 1));
        assertFalse(GuardPostSquads.canAutomaticallyRegister(ceiling));
        assertFalse(GuardPostSquads.canAutomaticallyRegister(SquadRegistrySavedData.MAX_SQUADS));
        assertFalse(GuardPostSquads.canAutomaticallyRegister(-1));
    }
}
