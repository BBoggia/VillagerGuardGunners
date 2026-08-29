package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RulesOfEngagementContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void everyNativeFiringBoundaryUsesTheCentralPolicy() throws IOException {
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String policy = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/engagement/GuardRulesOfEngagement.java");

        assertAll(
                () -> assertTrue(goal.contains("GuardRulesOfEngagement.classify(guard, currentTarget)")),
                () -> assertTrue(goal.contains("boolean mayFire = mayEngage")),
                () -> assertTrue(goal.contains(
                        "!GuardRulesOfEngagement.mayEngage(guard, expectedTarget)")),
                () -> assertTrue(goal.contains(
                        "updateMovement(gameTime, distanceSquared, mayEngage)")),
                () -> assertTrue(goal.contains("engagementReason.releaseTarget()")),
                () -> assertTrue(goal.contains("EngagementReason.HOLD_FIRE")),
                () -> assertTrue(goal.contains("reason.releaseTarget()")),
                () -> assertTrue(goal.contains("guard.setTarget(null)")),
                () -> assertTrue(policy.contains("GuardAllyProtection.isProtected(shooter, target)")),
                () -> assertTrue(policy.contains("target.getLastHurtMob()")),
                () -> assertTrue(policy.contains("shooter.getLastHurtByMob() == target")));
    }

    @Test
    void hotPathContainsNoWorldScansAllocationsOrLogging() throws IOException {
        String policy = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/engagement/GuardRulesOfEngagement.java");

        assertAll(
                () -> assertFalse(policy.contains("getEntities")),
                () -> assertFalse(policy.contains("getEntitiesOfClass")),
                () -> assertFalse(policy.contains("getAllEntities")),
                () -> assertFalse(policy.contains("new ArrayList")),
                () -> assertFalse(policy.contains("new HashMap")),
                () -> assertFalse(policy.contains("Logger")),
                () -> assertFalse(policy.contains("Class.forName")));
    }

    @Test
    void datapackOverrideTagsArePackagedAsEntityTypeTags() throws IOException {
        String always = read(
                "src/main/resources/data/villagerguardgunners/tags/entity_types/always_engage.json");
        String never = read(
                "src/main/resources/data/villagerguardgunners/tags/entity_types/never_engage.json");

        assertAll(
                () -> assertTrue(always.contains("\"replace\": false")),
                () -> assertTrue(never.contains("\"replace\": false")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
