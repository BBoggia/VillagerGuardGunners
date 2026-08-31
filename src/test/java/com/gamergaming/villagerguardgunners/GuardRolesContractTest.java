package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuardRolesContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void roleReloadPersistenceAndAssignmentHaveNoRecurringOrItemMutationPath()
            throws IOException {
        String registry = read("src/main/java/com/gamergaming/villagerguardgunners/role/GuardRoles.java");
        String mixin = read("src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String commands = read("src/main/java/com/gamergaming/villagerguardgunners/command/GuardRoleCommands.java");
        String main = read("src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");

        assertAll(
                () -> assertTrue(main.contains("GuardRoles::registerReloadListener")),
                () -> assertTrue(registry.contains("MAXIMUM_ROLES = 256")),
                () -> assertTrue(registry.contains("MAXIMUM_PARENTS = 8")),
                () -> assertTrue(registry.contains("MAXIMUM_INHERITANCE_DEPTH = 16")),
                () -> assertTrue(registry.contains("previous.roles()")),
                () -> assertTrue(registry.contains("volatile GuardRoleReloadSnapshot")),
                () -> assertFalse(registry.contains("getEntitiesOfClass(")),
                () -> assertFalse(registry.contains("getChunk(")),
                () -> assertFalse(registry.contains("tick(")),
                () -> assertTrue(mixin.contains("villagerguardgunners:guard_role")),
                () -> assertTrue(mixin.contains("putString(VILLAGER_GUARD_GUNNERS_ROLE_TAG")),
                () -> assertTrue(mixin.contains("villagerGuardGunners$readRoleId")),
                () -> assertTrue(mixin.contains("cachedEffectiveOrders")),
                () -> assertFalse(commands.contains("setItemSlot(")),
                () -> assertFalse(commands.contains("setItemInHand(")),
                () -> assertFalse(commands.contains("shrink(")),
                () -> assertFalse(commands.contains("grow(")),
                () -> assertFalse(commands.contains("getEntitiesOfClass(")),
                () -> assertFalse(commands.contains("tick(")));
    }

    @Test
    void everyLiveBehaviorConsumerUsesTheEffectiveRoleOrderLayer() throws IOException {
        List<String> consumers = List.of(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java",
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardAmmoSharing.java",
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardDroppedAmmoPickup.java",
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryResupplyLiveGate.java",
                "src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java",
                "src/main/java/com/gamergaming/villagerguardgunners/combat/engagement/GuardRulesOfEngagement.java",
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/GuardSidearmRuntimeController.java");

        assertAll(consumers.stream().map(path -> () -> {
            String source = read(path);
            assertTrue(source.contains("GuardRoleResolver.effectiveOrders("),
                    () -> path + " bypasses role-aware order precedence");
            assertFalse(source.contains("GuardOrdersAccess.getOrders("),
                    () -> path + " still reads raw orders in a live behavior path");
        }));
    }

    @Test
    void reservedCompatibilitySurfacesAreNowAdditiveAndBackwardReadable()
            throws IOException {
        String format = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/NaturalGuardLoadoutFormat.java");
        String parser = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/NaturalGuardLoadoutParser.java");
        String issuance = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/NaturalGuardLoadoutIssuance.java");
        String ledger = read(
                "src/main/java/com/gamergaming/villagerguardgunners/orders/GuardOrderTemplate.java");

        assertAll(
                () -> assertTrue(format.contains("ROLE_VERSION = 4")),
                () -> assertTrue(parser.contains("VERSION_4_ENTRY_FIELDS")),
                () -> assertTrue(issuance.contains("ROLE_DEFINITION_UNAVAILABLE")),
                () -> assertTrue(issuance.contains("roleBefore")),
                () -> assertTrue(ledger.contains("LEGACY_DATA_VERSION = 1")),
                () -> assertTrue(ledger.contains("DATA_VERSION = 2")),
                () -> assertTrue(ledger.contains("includesRoleAssignment")));
    }

    private static String read(String path) throws IOException {
        return Files.readString(PROJECT.resolve(path), StandardCharsets.UTF_8);
    }
}
