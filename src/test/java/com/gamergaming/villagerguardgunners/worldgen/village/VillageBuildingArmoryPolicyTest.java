package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class VillageBuildingArmoryPolicyTest {

    @Test
    void initialStockCannotOutlivePlacement() {
        assertDoesNotThrow(() -> new VillageBuildingArmoryPolicy(false, false));
        assertDoesNotThrow(() -> new VillageBuildingArmoryPolicy(true, false));
        assertDoesNotThrow(() -> new VillageBuildingArmoryPolicy(true, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VillageBuildingArmoryPolicy(false, true));
    }
}
