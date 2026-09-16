package com.gamergaming.villagerguardgunners.config;

import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimSettings;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import me.fzzyhmstrs.fzzy_config.impl.ConfigApiImpl;
import org.junit.jupiter.api.Test;

final class GuardAimingConfigTest {
    @Test void defaultsPreserveCurrentAimAndPlayerOverrideIsOptIn() {
        var config = new VillagerGuardGunnersConfig();
        var aiming = config.combat.aiming;
        assertEquals(GuardAimSettings.DEFAULT, aiming.resolve(false));
        assertEquals(GuardAimSettings.DEFAULT, aiming.resolve(true));
        assertFalse(aiming.overridePlayers.get());
        assertEquals(2, aiming.playerErrorDegrees.get());
        assertEquals(GuardAimPoint.CENTER_MASS, aiming.playerAimPoint.get());
        assertEquals(4, config.defaultPermLevel());
    }

    @Test void playerOverrideResolvesBothSettingsWithoutChangingMobsOrFacingTolerance() {
        var config = new VillagerGuardGunnersConfig();
        var aiming = config.combat.aiming;
        aiming.errorDegrees.accept(1.0); aiming.aimPoint.accept(GuardAimPoint.EYE_LEVEL);
        aiming.playerErrorDegrees.accept(3.0); aiming.playerAimPoint.accept(GuardAimPoint.CENTER_MASS);
        assertEquals(aiming.resolve(false), aiming.resolve(true));
        aiming.overridePlayers.accept(true);
        assertEquals(new GuardAimSettings(1, GuardAimPoint.EYE_LEVEL), aiming.resolve(false));
        assertEquals(new GuardAimSettings(3, GuardAimPoint.CENTER_MASS), aiming.resolve(true));
        assertEquals(10, config.combat.aimToleranceDegrees.get());
        aiming.overridePlayers.accept(false);
        assertEquals(aiming.resolve(false), aiming.resolve(true));
    }

    @Test void bothErrorControlsEnforceFiniteRangeThroughConfigValidation() {
        var aiming = new VillagerGuardGunnersConfig().combat.aiming;
        for (var field : new me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble[]{
                aiming.errorDegrees, aiming.playerErrorDegrees}) {
            assertEquals(0, field.correctEntry(-1.0, ValidationType.STRONG).get());
            assertEquals(10, field.correctEntry(11.0, ValidationType.STRONG).get());
            assertTrue(field.validateEntry(Double.NaN, ValidationType.STRONG).isError());
            assertTrue(field.validateEntry(Double.POSITIVE_INFINITY, ValidationType.STRONG).isError());
        }
    }

    @Test void migrationInitializesOnlyNewControlsAndRetainsCurrentSchemaChoices() {
        var config = new VillagerGuardGunnersConfig();
        config.combat.maximumEngagementRange.accept(48.0);
        config.combat.aimToleranceDegrees.accept(4.0);
        config.medical.enabled.accept(false);
        config.combat.aiming.overridePlayers.accept(true);
        config.combat.aiming.errorDegrees.accept(7.0);
        config.update(21);
        assertEquals(GuardAimSettings.DEFAULT, config.combat.aiming.resolve(true));
        assertEquals(48, config.combat.maximumEngagementRange.get());
        assertEquals(4, config.combat.aimToleranceDegrees.get());
        assertFalse(config.medical.enabled.get());
        config.combat.aiming.overridePlayers.accept(true);
        config.combat.aiming.playerErrorDegrees.accept(6.0);
        config.update(22);
        assertEquals(6, config.combat.aiming.resolve(true).errorDegrees());
    }

    @Test void oldTomlLoadsWithDefaultsAndNewControlsRoundTrip() {
        var config = new VillagerGuardGunnersConfig();
        // Actual Fzzy TOML deserialization, not a hand-written parser or merely update().
        ConfigApiImpl.INSTANCE.deserializeConfig$fzzy_config(config, """
                version = 21
                enabled = false
                [combat]
                requiredVisibleTicks = 9
                aimToleranceDegrees = 4.0
                maximumEngagementRange = 48.0
                [armoryResupply]
                enabled = false
                """, new ArrayList<String>(), (byte) 0, FileType.TOML);
        assertFalse(config.enabled.get());
        assertEquals(9, config.combat.requiredVisibleTicks.get());
        assertEquals(48, config.combat.maximumEngagementRange.get());
        assertFalse(config.armoryResupply.enabled.get());
        assertEquals(GuardAimSettings.DEFAULT, config.combat.aiming.resolve(true));
        config.combat.aiming.overridePlayers.accept(true);
        config.combat.aiming.playerErrorDegrees.accept(3.5);
        var encoded = ConfigApiImpl.INSTANCE.serializeConfig$fzzy_config(
                config, new ArrayList<String>(), (byte) 0, FileType.TOML);
        assertFalse(encoded.isError(), encoded.toString());
        String serialized = encoded.get();
        var reloaded = new VillagerGuardGunnersConfig();
        var decoded = ConfigApiImpl.INSTANCE.deserializeConfig$fzzy_config(reloaded, serialized,
                new ArrayList<String>(), (byte) 0, FileType.TOML);
        assertFalse(decoded.isError(), decoded.toString());
        assertEquals(new GuardAimSettings(3.5, GuardAimPoint.CENTER_MASS), reloaded.combat.aiming.resolve(true));
        assertFalse(reloaded.enabled.get());
        assertEquals(48, reloaded.combat.maximumEngagementRange.get());
    }

    @Test void activeControlsAndEnumsHaveAccurateTranslations() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json"))).getAsJsonObject();
        String base = "config.villagerguardgunners.guard_gunners.combat.aiming";
        for (String field : new String[]{"", ".errorDegrees", ".aimPoint", ".overridePlayers",
                ".playerErrorDegrees", ".playerAimPoint"}) {
            assertTrue(json.has(base + field)); assertTrue(json.has(base + field + ".desc"));
        }
        assertFalse(json.get(base + ".desc").getAsString().contains("do not affect firing yet"));
        assertEquals("Guard Accuracy", json.get(base).getAsString());
        for (var point : GuardAimPoint.values()) {
            assertTrue(json.has(point.translationKey())); assertTrue(json.has(point.descriptionKey()));
        }
    }
}
