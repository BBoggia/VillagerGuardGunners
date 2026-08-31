package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM boundary checks for the analyzer. Behavioral item-stack checks live
 * in GuardGunnerGameTests because Minecraft's ItemStack requires a bootstrapped
 * game registry.
 */
final class NaturalGuardLoadoutCompatibilityAnalyzerTest {
    private static String analyzer;

    @BeforeAll
    static void readAnalyzer() throws IOException {
        Path projectDirectory = Path.of(
                System.getProperty("villagerguardgunners.projectDir"));
        analyzer = Files.readString(
                projectDirectory.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                                + "NaturalGuardLoadoutCompatibilityAnalyzer.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    void analyzerOwnsTheNativeCompatibilityPipeline() {
        assertAll(
                () -> assertTrue(analyzer.contains("TimelessAPI.getCommonGunIndex")),
                () -> assertTrue(analyzer.contains("GunItemBuilder.create()")),
                () -> assertTrue(analyzer.contains("TimelessAPI.getCommonAmmoIndex")),
                () -> assertTrue(analyzer.contains("AmmoItemBuilder.create()")),
                () -> assertTrue(analyzer.contains(
                        "GuardAmmoSummary.ReserveSource.PHYSICAL")),
                () -> assertTrue(analyzer.contains("AmmoCompatibility.assess")),
                () -> assertTrue(analyzer.contains("AttachmentCacheProperty")),
                () -> assertTrue(analyzer.contains("TaCZExplosionResolver.resolve(")));
    }

    @Test
    void analyzerIsNonMutatingAndDefensivelyCopiesPreparedStacks() {
        assertAll(
                () -> assertTrue(analyzer.contains(
                        "public ItemStack preparedGun()")),
                () -> assertTrue(analyzer.contains(
                        "public ItemStack preparedAmmunition()")),
                () -> assertTrue(analyzer.contains("return preparedGun.copy();")),
                () -> assertTrue(analyzer.contains(
                        "return preparedAmmunition.copy();")),
                () -> assertFalse(analyzer.contains("GuardAmmoAccess")),
                () -> assertFalse(analyzer.contains("GuardAmmoTransactions")),
                () -> assertFalse(analyzer.contains("NaturalGuardLoadoutProvenance")),
                () -> assertFalse(analyzer.contains("UUID.randomUUID")),
                () -> assertFalse(analyzer.contains("setItemSlot(")),
                () -> assertFalse(analyzer.contains("TickEvent")),
                () -> assertFalse(analyzer.contains("getEntitiesOfClass(")));
    }

    @Test
    void explosivePoliciesPreserveTheFrozenThreeModeContract() {
        int enumStart = analyzer.indexOf("public enum ExplosivePolicy");
        int enumEnd = analyzer.indexOf("public enum ExplosionState", enumStart);
        String policies = analyzer.substring(enumStart, enumEnd);
        int ignore = policies.indexOf("IGNORE,");
        int report = policies.indexOf("REPORT,");
        int reject = policies.indexOf("REJECT");

        assertAll(
                () -> assertTrue(ignore >= 0),
                () -> assertTrue(report > ignore),
                () -> assertTrue(reject > report),
                () -> assertTrue(analyzer.contains(
                        "CompatibilityStatus.EXPLOSIVE_CONTEXT_REQUIRED")),
                () -> assertTrue(analyzer.contains(
                        "CompatibilityStatus.EXPLOSIVE_DISALLOWED")));
    }
}
