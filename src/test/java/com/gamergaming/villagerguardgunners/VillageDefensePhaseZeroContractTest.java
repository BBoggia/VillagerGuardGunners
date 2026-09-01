package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefensePhaseZeroContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void phaseZeroRecordsItsOriginalDevelopmentIdentity() throws IOException {
        assertTrue(read("docs/village-defense-logistics-phase-0.md")
                .contains("development artifacts use `1.3-dev`"));
    }

    @Test
    void currentSchemasRetainEveryShippedMigrationInput() {
        assertAll(
                () -> assertEquals(14, VillagerGuardGunnersConfig.CONFIG_VERSION),
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

    @Test
    void baselineScopeAndPerformanceBoundariesAreRecorded() throws IOException {
        String baseline = read("docs/village-defense-logistics-phase-0.md");
        String matrix = read("docs/village-defense-logistics-release-matrix.md");
        String readme = read("README.md");

        assertAll(
                () -> assertTrue(baseline.contains("`v1.2`")),
                () -> assertTrue(baseline.contains(
                        "ee4a63ee596992e880f50da8463761438739e0e7")),
                () -> assertTrue(baseline.contains(
                        "9809fdaf10a7d34369d108383a6f41a3eba5a2fb7e60861a67f26c0373936c6a")),
                () -> assertTrue(baseline.contains("Plain JUnit/source-contract tests | 317 passed")),
                () -> assertTrue(baseline.contains("Forge correctness GameTests | 73 passed")),
                () -> assertTrue(baseline.contains("Performance/JFR workloads | 17 defined")),
                () -> assertTrue(baseline.contains("no block-entity ticker")),
                () -> assertTrue(baseline.contains("never loads a chunk")),
                () -> assertTrue(baseline.contains("within a provisional `1.05x`")),
                () -> assertTrue(baseline.contains("guard-tower or outpost world generation")),
                () -> assertTrue(matrix.contains("ARMORY-INDEX-01")),
                () -> assertTrue(matrix.contains("ARMORY-RESERVE-01")),
                () -> assertTrue(matrix.contains("ARMORY-PATH-01")),
                () -> assertTrue(matrix.contains("SQUAD-UX-01")),
                () -> assertTrue(matrix.contains("SQUAD-TARGET-01")),
                () -> assertTrue(matrix.contains("ROLE-DATA-01")),
                () -> assertTrue(matrix.contains("VDEF-UPGRADE-01")),
                () -> assertTrue(matrix.contains("VDEF-PERF-01")),
                () -> assertTrue(readme.contains(
                        "docs/village-defense-logistics-phase-0.md")),
                () -> assertTrue(readme.contains(
                        "docs/village-defense-logistics-release-matrix.md")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
