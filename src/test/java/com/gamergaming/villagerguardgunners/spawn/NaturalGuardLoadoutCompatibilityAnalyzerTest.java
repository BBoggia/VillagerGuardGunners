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
    void eachGunModOwnsItsCompatibilityPipeline() throws IOException {
        String tacz = readMain("compat/tacz/provider/TaczLoadoutAnalysis.java");
        String shared = readMain("spawn/ProviderLoadoutAnalysis.java");
        assertAll(
                () -> assertTrue(analyzer.contains("GunProviders.forGunId(gunId)")),
                () -> assertTrue(analyzer.contains("provider.get().analyzeLoadout(context, gunId, explosivePolicy)")),
                () -> assertFalse(analyzer.contains("com.tacz")),
                () -> assertFalse(shared.contains("com.tacz")),
                () -> assertTrue(tacz.contains("TimelessAPI.getCommonGunIndex")),
                () -> assertTrue(tacz.contains("GunItemBuilder.create()")),
                () -> assertTrue(tacz.contains("TimelessAPI.getCommonAmmoIndex")),
                () -> assertTrue(tacz.contains("AmmoItemBuilder.create()")),
                () -> assertTrue(tacz.contains(
                        "GuardAmmoSummary.ReserveSource.PHYSICAL")),
                () -> assertTrue(tacz.contains("AmmoCompatibility.assess")),
                () -> assertTrue(tacz.contains("AttachmentCacheProperty")),
                () -> assertTrue(tacz.contains("TaCZExplosionResolver.resolve(")),
                // Other gun mods go through the provider's own methods and are never issued unusable guns.
                () -> assertTrue(shared.contains("provider.createGun(gunId,")),
                () -> assertTrue(shared.contains("!descriptor.firingDataPresent()")),
                () -> assertTrue(shared.contains("AmmoCompatibility.assess(gun, ammo)")),
                () -> assertTrue(shared.contains("provider.isExplosive(")));
    }

    private static String readMain(String relative) throws IOException {
        return Files.readString(
                Path.of(System.getProperty("villagerguardgunners.projectDir"))
                        .resolve("src/main/java/com/gamergaming/villagerguardgunners/" + relative),
                StandardCharsets.UTF_8);
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
    void explosivePoliciesPreserveTheFrozenThreeModeContract() throws IOException {
        String tacz = readMain("compat/tacz/provider/TaczLoadoutAnalysis.java");
        String shared = readMain("spawn/ProviderLoadoutAnalysis.java");
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
                () -> assertTrue(tacz.contains(
                        "CompatibilityStatus.EXPLOSIVE_CONTEXT_REQUIRED")),
                () -> assertTrue(tacz.contains(
                        "CompatibilityStatus.EXPLOSIVE_DISALLOWED")),
                () -> assertTrue(shared.contains(
                        "CompatibilityStatus.EXPLOSIVE_DISALLOWED")));
    }
}
