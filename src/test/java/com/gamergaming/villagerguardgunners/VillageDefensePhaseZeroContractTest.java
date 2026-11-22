package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmStorage;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutFormat;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenance;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBinding;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import org.junit.jupiter.api.Test;

final class VillageDefensePhaseZeroContractTest {
    @Test
    void currentSchemasRetainEveryShippedMigrationInput() {
        assertAll(
                () -> assertEquals(24, VillagerGuardGunnersConfig.CONFIG_VERSION),
                () -> assertEquals(1, NaturalGuardLoadoutFormat.LEGACY_VERSION),
                () -> assertEquals(2, NaturalGuardLoadoutFormat.CONDITIONAL_VERSION),
                () -> assertEquals(3, NaturalGuardLoadoutFormat.SIDEARM_VERSION),
                () -> assertEquals(4, NaturalGuardLoadoutFormat.ROLE_VERSION),
                () -> assertEquals(1, NaturalGuardLoadoutProvenance.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(3, GuardSidearmStorage.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(2, GuardOrderTemplate.DATA_VERSION),
                () -> assertEquals(1, SquadMembership.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(1, SquadLedgerBinding.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(1, SquadMetadata.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(1, SquadCommand.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(1, ArmorySquadLock.CURRENT_SCHEMA_VERSION));
    }

}
