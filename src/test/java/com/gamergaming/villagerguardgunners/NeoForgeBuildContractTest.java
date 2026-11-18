package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Active port gates, separate from the retained Forge performance/release reference. */
final class NeoForgeBuildContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(System.getProperty("villagerguardgunners.projectDir"), path));
    }

    @Test void ordinaryBuildAndCiRunAllTestsAndCurrentResourceValidation() throws Exception {
        String build = read("build.gradle"), workflow = read(".github/workflows/ci.yml");
        assertTrue(build.contains("tasks.named('check')"));
        assertTrue(build.contains("dependsOn tasks.named('checkNeoForgeFoundation'), tasks.named('verifyNeoForgeCompanionPacks')"));
        assertFalse(build.contains("tasks.named('test') { exclude"));
        assertTrue(workflow.contains("./gradlew --no-daemon build stageCoreQaMods stageMultiplayerQaMods"));
        assertTrue(workflow.contains("build/neoforge/reports/tests/test/**"));
        assertFalse(build.contains("apply from: 'gradle/legacy-forge.gradle'"));
        assertTrue(read("gradle/neoforge-distribution.gradle").contains("prepareNeoForgeRelease"));
    }

    @Test void packagedMetadataMixinsAndClassesAreCompleteAndDoNotContainTestMods() throws Exception {
        try (var jar = new ZipFile(System.getProperty("villagerguardgunners.artifact"))) {
            String metadata = new String(jar.getInputStream(jar.getEntry("META-INF/neoforge.mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(metadata.contains("${"));
            // Five required dependencies and three optional gun mods.
            assertEquals(8, metadata.split("\\[\\[dependencies\\.villagerguardgunners]]", -1).length - 1);
            assertTrue(metadata.contains("modId=\"pointblank\"\ntype=\"optional\""));
            // The Point Blank mixins sit in their own optional config and both builds' adapters are packaged.
            var pointBlankMixins = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("mixins.villagerguardgunners.pointblank.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(pointBlankMixins.get("required").getAsBoolean());
            assertEquals(0, pointBlankMixins.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            assertEquals(2, pointBlankMixins.getAsJsonArray("client").size());
            for (String side : new String[]{"mixins", "client"}) for (var mixin : pointBlankMixins.getAsJsonArray(side))
                assertNotNull(jar.getEntry("com/gamergaming/villagerguardgunners/compat/pointblank/mixin/" + mixin.getAsString() + ".class"), mixin.toString());
            for (String adapter : new String[]{"vpb/VpbAccess", "pbj/PbjAccess"})
                assertNotNull(jar.getEntry("com/gamergaming/villagerguardgunners/compat/pointblank/" + adapter + ".class"), adapter);
            // No gun mod is required: Guard Villagers, Fzzy Config, Kotlin for Forge, NeoForge and Minecraft are.
            assertEquals(5, metadata.split("type=\"required\"", -1).length - 1);
            assertTrue(metadata.contains("modId=\"tacz\"\ntype=\"optional\""));
            var taczMixins = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("mixins.villagerguardgunners.tacz.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(taczMixins.get("required").getAsBoolean());
            assertEquals("com.gamergaming.villagerguardgunners.guns.InstalledTargetMixinPlugin", taczMixins.get("plugin").getAsString());
            assertEquals(4, taczMixins.getAsJsonArray("mixins").size());
            for (var mixin : taczMixins.getAsJsonArray("mixins"))
                assertNotNull(jar.getEntry("com/gamergaming/villagerguardgunners/mixin/" + mixin.getAsString() + ".class"), mixin.toString());
            assertTrue(metadata.contains("modId=\"cgm\"\ntype=\"optional\""));
            assertNull(jar.getEntry("META-INF/mods.toml"));
            var mixins = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("mixins.villagerguardgunners.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(mixins.get("required").getAsBoolean());
            assertEquals(1, mixins.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            String root = mixins.get("package").getAsString().replace('.', '/') + "/";
            for (String side : new String[]{"mixins", "client"}) for (var mixin : mixins.getAsJsonArray(side))
                assertNotNull(jar.getEntry(root + mixin.getAsString().replace('.', '/') + ".class"), mixin.toString());
            for (String fix : new String[]{"GuardFollowPacketMixin", "GuardPatrolPacketMixin", "CasualtyDamageMixin"})
                assertTrue(mixins.getAsJsonArray("mixins").toString().contains('"' + fix + '"'));
            assertFalse(jar.stream().anyMatch(e -> e.getName().contains("/qa/") || e.getName().contains("/gametest/")
                    || e.getName().contains("/bootstrap/") || e.getName().contains("/integration/") || e.getName().contains("/perftest/")));
        }
    }

    @Test void installedRegressionRunnersBindEvidenceToUnchangedPackagedJars() throws Exception {
        String core = read("tools/run_neoforge_core_qa.py"), multiplayer = read("tools/run_neoforge_multiplayer_qa.py");
        assertTrue(core.contains("'jarHashesUnchanged': before == hashes()"));
        assertTrue(core.contains("'sha256': before"));
        assertTrue(multiplayer.contains("jarHashesUnchanged"));
        assertTrue(multiplayer.contains("server-result.json"));
        assertTrue(multiplayer.contains("127.0.0.1"));
        assertFalse(core.contains("gradlew runServer"));
        assertFalse(multiplayer.contains("gradlew runClient"));
    }
}
