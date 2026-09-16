package com.gamergaming.villagerguardgunners.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import me.fzzyhmstrs.fzzy_config.annotations.Action;
import me.fzzyhmstrs.fzzy_config.annotations.RequiresAction;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.config.ConfigAction;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import org.junit.jupiter.api.Test;

final class SettingsPresentationTest {
    @Test void everySettingAndPreviewHasMatchingTextAtItsActualScope() throws Exception {
        JsonObject language = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json"))).getAsJsonObject();
        assertEquals(152, check(new VillagerGuardGunnersConfig(), "villagerguardgunners.guard_gunners", language));
    }

    private int check(Object section, String scope, JsonObject language) throws Exception {
        int count = 0;
        for (var field : section.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value = field.get(section);
            if (!(value instanceof ValidatedField<?> || value instanceof ConfigSection || value instanceof ConfigAction)) continue;
            String childScope = scope + "." + field.getName();
            // Fzzy 0.7.6 resolves annotations for each containing class, not its parent field.
            Translation translation = section.getClass().getAnnotation(Translation.class);
            if (translation == null) translation = field.getAnnotation(Translation.class);
            String key = translation == null || translation.negate() ? childScope : translation.prefix() + "." + field.getName();
            assertTrue(language.has(key), "Missing resolved name: " + key);
            assertTrue(language.has(key + ".desc"), "Missing resolved description: " + key);
            assertFalse(language.get(key + ".desc").getAsString().isBlank(), key);
            if (value instanceof ConfigSection) count += check(value, childScope, language);
            else if (value instanceof ValidatedField<?>) count++;
        }
        return count;
    }

    @Test void editorRejectsInvertedPairsAndSupportsBothAdjustmentDirections() {
        var config = new VillagerGuardGunnersConfig();
        var population = config.villagePopulation;
        population.minimumGuards.setAndUpdate(24);
        assertEquals(2, population.minimumGuards.get());
        population.maximumGuards.setAndUpdate(32);
        population.minimumGuards.setAndUpdate(24);
        assertEquals(24, population.minimumGuards.get());
        population.maximumGuards.setAndUpdate(0);
        assertEquals(32, population.maximumGuards.get());
        population.minimumGuards.setAndUpdate(0);
        population.maximumGuards.setAndUpdate(0);
        assertEquals(0, population.settings().maximum());
        var armory = config.armoryResupply;
        armory.lowReserveMagazines.setAndUpdate(6);
        assertEquals(1, armory.lowReserveMagazines.get());
        armory.targetReserveMagazines.setAndUpdate(8);
        armory.lowReserveMagazines.setAndUpdate(6);
        assertTrue(armory.snapshot(true).validReserveThresholds());
        armory.targetReserveMagazines.setAndUpdate(0);
        assertEquals(8, armory.targetReserveMagazines.get());
        armory.lowReserveMagazines.setAndUpdate(0);
        armory.targetReserveMagazines.setAndUpdate(0);
        assertTrue(armory.snapshot(true).validReserveThresholds());
    }

    @Test void loadingAndCopyingKeepIndependentBoundsAndLivePeerValidation() {
        var config = new VillagerGuardGunnersConfig();
        var population = config.villagePopulation;
        // A valid file may contain minimum first, beyond the previous/default maximum.
        assertTrue(population.minimumGuards.validateAndSet(24).isValid());
        assertTrue(population.maximumGuards.validateAndSet(32).isValid());
        assertEquals(24, population.settings().minimum());
        var copy = population.minimumGuards.instanceEntry();
        assertTrue(copy.validateEntry(30, ValidationType.STRONG).isValid());
        population.maximumGuards.validateAndSet(25);
        assertFalse(copy.validateEntry(30, ValidationType.STRONG).isValid());
        assertTrue(copy.validateEntry(30, ValidationType.WEAK).isValid());
        assertFalse(copy.validateEntry(33, ValidationType.WEAK).isValid());
    }

    @Test void restartNoticesApplyOnlyToLayoutFilters() throws Exception {
        var type = VillagerGuardGunnersConfig.VillageBuildingSettings.class;
        for (String name : new String[]{"enabled", "barracks", "armories", "sentryPosts", "integratedWatchtowers", "trainingYards"})
            assertEquals(Action.RESTART, type.getField(name).getAnnotation(RequiresAction.class).action());
        for (String name : new String[]{"spawnGarrisons", "maximumGuardsPerBuilding", "placeArmoryBlocks", "stockArmories"})
            assertNull(type.getField(name).getAnnotation(RequiresAction.class));
    }
}
