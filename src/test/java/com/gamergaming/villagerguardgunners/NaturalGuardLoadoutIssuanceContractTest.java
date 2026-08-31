package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutIssuanceContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void issuanceIsOneShotNativeValidatedAndTransactional() throws IOException {
        String issuance = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardLoadoutIssuance.java");
        String analyzer = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardLoadoutCompatibilityAnalyzer.java");
        String integration = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardSpawnIntegration.java");
        String explosionResolver = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/"
                        + "TaCZExplosionResolver.java");

        assertAll(
                () -> assertTrue(analyzer.contains("TimelessAPI.getCommonGunIndex")),
                () -> assertTrue(analyzer.contains("GunItemBuilder.create()")),
                () -> assertTrue(analyzer.contains("TimelessAPI.getCommonAmmoIndex")),
                () -> assertTrue(analyzer.contains("AmmoItemBuilder.create()")),
                () -> assertTrue(analyzer.contains(
                        "GuardAmmoSummary.ReserveSource.PHYSICAL")),
                () -> assertTrue(analyzer.contains("AmmoCompatibility.assess")),
                () -> assertTrue(analyzer.contains("AttachmentCacheProperty")),
                () -> assertTrue(analyzer.contains("TaCZExplosionResolver.resolve(")),
                () -> assertTrue(explosionResolver.contains(
                        "GunProperties.RuntimeOnly.EXPLODE_ENABLED")),
                () -> assertTrue(explosionResolver.contains("DEFAULT_EXPLOSION_DATA")),
                () -> assertTrue(issuance.contains(
                        "NaturalGuardLoadoutCompatibilityAnalyzer.analyze(")),
                () -> assertTrue(issuance.contains("mapPrimaryFailure(")),
                () -> assertTrue(issuance.contains("planAllAmmunition(")),
                () -> assertTrue(issuance.contains("GuardAmmoTransactions.insert")),
                () -> assertTrue(issuance.contains("AmmoSnapshot")),
                () -> assertTrue(issuance.contains(
                        "NaturalGuardLoadoutProvenanceCodec.stampGun")),
                () -> assertTrue(integration.contains("NaturalGuardLoadoutIssuance.issue(")),
                () -> assertTrue(integration.contains("if (issuance.issued())")),
                () -> assertTrue(integration.contains(
                        "NaturalGuardLoadoutPools.reportUnavailableGun(candidate)")),
                () -> assertTrue(issuance.contains(
                        "villagerGuardGunners$setNaturalLoadoutProvenance(")),
                () -> assertFalse(integration.contains(
                        "issuance.provenance()")),
                () -> assertFalse(issuance.contains("TimelessAPI")),
                () -> assertFalse(issuance.contains("GunItemBuilder")),
                () -> assertFalse(issuance.contains("AmmoItemBuilder")),
                () -> assertFalse(analyzer.contains("GuardAmmoTransactions")),
                () -> assertFalse(analyzer.contains("NaturalGuardLoadoutProvenance")),
                () -> assertFalse(issuance.contains("TickEvent")),
                () -> assertFalse(issuance.contains("getEntitiesOfClass(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
