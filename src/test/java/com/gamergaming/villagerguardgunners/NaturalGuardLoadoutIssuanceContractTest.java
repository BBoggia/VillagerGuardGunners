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
        String integration = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardSpawnIntegration.java");
        String explosionResolver = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/"
                        + "TaCZExplosionResolver.java");

        assertAll(
                () -> assertTrue(issuance.contains("TimelessAPI.getCommonGunIndex")),
                () -> assertTrue(issuance.contains("GunItemBuilder.create()")),
                () -> assertTrue(issuance.contains("TimelessAPI.getCommonAmmoIndex")),
                () -> assertTrue(issuance.contains("AmmoItemBuilder.create()")),
                () -> assertTrue(issuance.contains("GuardAmmoSummary.ReserveSource.PHYSICAL")),
                () -> assertTrue(issuance.contains("AmmoCompatibility.assess")),
                () -> assertTrue(issuance.contains("AttachmentCacheProperty")),
                () -> assertTrue(issuance.contains("TaCZExplosionResolver.resolve(")),
                () -> assertTrue(explosionResolver.contains(
                        "GunProperties.RuntimeOnly.EXPLODE_ENABLED")),
                () -> assertTrue(explosionResolver.contains("DEFAULT_EXPLOSION_DATA")),
                () -> assertTrue(issuance.contains("GuardAmmoTransactions.planInsertion")),
                () -> assertTrue(issuance.contains("GuardAmmoTransactions.insert")),
                () -> assertTrue(issuance.contains("AmmoSnapshot")),
                () -> assertTrue(issuance.contains(
                        "NaturalGuardLoadoutProvenanceCodec.stampGun")),
                () -> assertTrue(integration.contains("NaturalGuardLoadoutIssuance.issue(")),
                () -> assertTrue(integration.contains("if (issuance.issued())")),
                () -> assertTrue(integration.contains(
                        "NaturalGuardLoadoutPools.reportUnavailableGun(candidate)")),
                () -> assertTrue(integration.contains(
                        "villagerGuardGunners$setNaturalLoadoutProvenance(")),
                () -> assertFalse(issuance.contains("TickEvent")),
                () -> assertFalse(issuance.contains("getEntitiesOfClass(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
