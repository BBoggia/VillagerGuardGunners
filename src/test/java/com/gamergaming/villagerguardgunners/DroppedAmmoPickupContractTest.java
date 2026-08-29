package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DroppedAmmoPickupContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void pickupIsItemDrivenStaggeredBoundedAndTransactional() throws IOException {
        String pickup = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardDroppedAmmoPickup.java");
        String itemMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/ItemEntityMixin.java");
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String maintenance = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardAmmoMaintenance.java");
        String mixinConfig = read("src/main/resources/mixins.villagerguardgunners.json");

        assertAll(
                () -> assertTrue(pickup.contains("CHECK_INTERVAL_TICKS = 10")),
                () -> assertTrue(pickup.contains("pickupDroppedAmmo.get()")),
                () -> assertTrue(pickup.contains("GameRules.RULE_MOBGRIEFING")),
                () -> assertTrue(pickup.contains("droppedAmmoPickupRadius.get()")),
                () -> assertTrue(pickup.contains("getBoundingBox().inflate(radius)")),
                () -> assertTrue(pickup.contains("getEntitiesOfClass(")),
                () -> assertTrue(pickup.contains("Guard.class")),
                () -> assertTrue(pickup.contains("distanceToSqr(droppedAmmo)")),
                () -> assertTrue(pickup.contains("guard.isEffectiveAi()")),
                () -> assertTrue(pickup.contains("ReserveSource.PHYSICAL")),
                () -> assertTrue(pickup.contains("AmmoCompatibility.assess(gunStack, offered)")),
                () -> assertTrue(pickup.contains("GuardAmmoTransactions.planInsertion")),
                () -> assertTrue(pickup.contains("GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard)")),
                () -> assertTrue(pickup.contains("GuardAmmoTransactions.insert")),
                () -> assertTrue(pickup.contains("recipient.take(droppedAmmo, insertion.inserted())")),
                () -> assertTrue(pickup.contains("droppedAmmo.setItem(insertion.remainder().copy())")),
                () -> assertTrue(pickup.contains("droppedAmmo.discard()")),
                () -> assertFalse(pickup.contains("getNavigation()")),
                () -> assertFalse(pickup.contains("addGoal")),
                () -> assertFalse(pickup.contains("@SubscribeEvent")),
                () -> assertTrue(itemMixin.contains("@Mixin(ItemEntity.class)")),
                () -> assertTrue(itemMixin.contains("target != null")),
                () -> assertTrue(itemMixin.contains("itemEntity.hasPickUpDelay()")),
                () -> assertTrue(itemMixin.contains("GameRules.RULE_MOBGRIEFING")),
                () -> assertTrue(itemMixin.contains("Math.floorMod(")),
                () -> assertTrue(itemMixin.contains("GuardAmmoMaintenance.isPotentialSupply(stack)")),
                () -> assertTrue(itemMixin.contains("GuardDroppedAmmoPickup.tryPickup(itemEntity)")),
                () -> assertTrue(maintenance.contains("getAmmoCount(checkedBox) > 0")),
                () -> assertTrue(maintenance.contains("ejectEmptyFiniteBoxes(Guard guard)")),
                () -> assertTrue(maintenance.contains("guard.spawnAtLocation(extracted.copy())")),
                () -> assertFalse(maintenance.contains("TickEvent")),
                () -> assertFalse(guardMixin.contains("GuardDroppedAmmoPickup")),
                () -> assertTrue(mixinConfig.contains("ItemEntityMixin")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
