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

final class NaturalGuardLoadoutProvenanceContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void provenanceIsVersionedAuthenticatedAndOutsideTheTickPath() throws IOException {
        String codec = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardLoadoutProvenanceCodec.java");
        String integration = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardSpawnIntegration.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String diagnostics = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardLoadoutDiagnostics.java");

        assertAll(
                () -> assertTrue(codec.contains("CURRENT_SCHEMA_VERSION")),
                () -> assertTrue(codec.contains("GUN_PROVENANCE_TAG")),
                () -> assertTrue(codec.contains("matchesIssuedGun(")),
                () -> assertTrue(codec.contains("provenance.equals(readGunRecord")),
                () -> assertTrue(mixin.contains(
                        "NaturalGuardLoadoutProvenanceCodec.readGuardRecord(compound)")),
                () -> assertTrue(mixin.contains(
                        "NaturalGuardLoadoutProvenanceCodec.writeGuardRecord(")),
                () -> assertTrue(mixin.contains(
                        "@Inject(method = \"readAdditionalSaveData\", at = @At(\"RETURN\"))")),
                () -> assertTrue(integration.contains("hasActiveNaturalLoadout(nearby)")),
                () -> assertTrue(diagnostics.contains("matchesIssuedGun(")),
                () -> assertTrue(diagnostics.contains("AuthenticationState.GUN_MISMATCH")),
                () -> assertFalse(codec.contains("TickEvent")),
                () -> assertFalse(codec.contains("getEntitiesOfClass(")),
                () -> assertFalse(codec.contains("spawnAtLocation(")),
                () -> assertFalse(diagnostics.contains("TickEvent")),
                () -> assertFalse(diagnostics.contains("getEntitiesOfClass(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
