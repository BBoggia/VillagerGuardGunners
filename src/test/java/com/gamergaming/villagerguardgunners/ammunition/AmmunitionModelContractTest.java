package com.gamergaming.villagerguardgunners.ammunition;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class AmmunitionModelContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void compatibilityRemainsTaCZNativeAndFailSoft() throws IOException {
        String compatibility = Files.readString(projectDirectory.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/compat/tacz/provider/TaczAmmoCompatibility.java"),
                StandardCharsets.UTF_8);
        String neutral = read("AmmoCompatibility.java");

        assertAll(
                () -> assertTrue(compatibility.contains("IAmmo looseAmmo")),
                () -> assertTrue(compatibility.contains("looseAmmo.isAmmoOfGun")),
                () -> assertTrue(compatibility.contains("IAmmoBox ammoBox")),
                () -> assertTrue(compatibility.contains("ammoBox.isAmmoBoxOfGun")),
                () -> assertTrue(compatibility.contains("ammoBox.getAmmoCount")),
                () -> assertTrue(compatibility.contains("ammoBox.isCreative")),
                () -> assertTrue(compatibility.contains("ammoBox.isAllTypeCreative")),
                () -> assertTrue(compatibility.contains("Assessment.unknown(")),
                () -> assertTrue(neutral.contains("Compatibility.UNKNOWN")),
                () -> assertTrue(compatibility.contains("gunStack.copy()")),
                () -> assertTrue(compatibility.contains("candidate.copy()")),
                () -> assertFalse(compatibility.contains("getRegistryName")),
                () -> assertTrue(neutral.contains("provider.assessAmmo(gunStack, candidate)")),
                () -> assertFalse(neutral.contains("com.tacz")));
    }

    @Test
    void summaryMirrorsTaCZCapacityChamberAndReserveRules() throws IOException {
        String summary = Files.readString(projectDirectory.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/compat/tacz/provider/TaczAmmoSummary.java"),
                StandardCharsets.UTF_8);
        String neutralSummary = read("GuardAmmoSummary.java");

        assertAll(
                () -> assertTrue(summary.contains("AttachmentDataUtils.getAmmoCountWithAttachment")),
                () -> assertTrue(summary.contains("boolean supportsChamber = bolt != Bolt.OPEN_BOLT")),
                () -> assertTrue(neutralSummary.contains("displayedCount(magazineCapacity, supportsChamber)")),
                () -> assertTrue(neutralSummary.contains("provider.captureAmmo(gunStack, reserveHandler)")),
                () -> assertFalse(neutralSummary.contains("com.tacz")),
                () -> assertTrue(summary.contains("reloadData.isInfinite()")),
                () -> assertTrue(summary.contains("gun.useDummyAmmo")),
                () -> assertTrue(summary.contains("ReserveSource.NATIVE_INFINITE")),
                () -> assertTrue(summary.contains("ReserveSource.DUMMY")),
                () -> assertTrue(summary.contains("ReserveSource.DUMMY,\n                        0,")),
                () -> assertTrue(neutralSummary.contains("MAX_SCANNED_SLOTS")),
                () -> assertEquals(
                        Long.MAX_VALUE,
                        GuardAmmoSummary.saturatingAdd(Long.MAX_VALUE - 2L, 3L)));
    }

    @Test
    void transactionsSimulateBeforeCommitAndRetainRollbackSnapshots() throws IOException {
        String transactions = read("GuardAmmoTransactions.java");

        assertAll(
                () -> assertTrue(transactions.contains("planInsertion(destination")),
                () -> assertTrue(transactions.contains("extractItem(sourceSlot, maximumItems, true)")),
                () -> assertTrue(transactions.contains("extractItem(sourceSlot, accepted, false)")),
                () -> assertTrue(transactions.contains("sourceSnapshot")),
                () -> assertTrue(transactions.contains("destinationSnapshot")),
                () -> assertTrue(transactions.contains("TransactionStatus.ROLLED_BACK")),
                () -> assertTrue(transactions.contains("PlanStatus.ROLLBACK_FAILED")),
                () -> assertTrue(transactions.contains("snapshot.stacks().size()")),
                () -> assertTrue(transactions.contains("ItemStack.isSameItemSameTags")));
    }

    @Test
    void depletedFiniteBoxesArePreservedWithoutAddingTickWork() throws IOException {
        String maintenance = read("GuardAmmoMaintenance.java");

        assertAll(
                () -> assertTrue(maintenance.contains("isPotentialSupply(ItemStack stack)")),
                () -> assertTrue(maintenance.contains("provider.isSpentAmmoContainer(stack)")),
                () -> assertTrue(maintenance.contains("isEmptyFiniteBox(original)")),
                () -> assertTrue(maintenance.contains("guard.spawnAtLocation(extracted.copy())")),
                () -> assertTrue(maintenance.contains("handler.setStackInSlot(slot, original)")),
                () -> assertFalse(maintenance.contains("TickEvent")),
                () -> assertFalse(maintenance.contains("getEntities")));
    }

    @Test
    void sidearmAmmoViewIsRoleStableBoundedAndFailClosed() throws IOException {
        String loadout = read("GuardWeaponAmmunition.java");

        assertAll(
                () -> assertTrue(loadout.contains("GuardSidearmAccess.getState(guard)")),
                () -> assertTrue(loadout.contains(
                        "GuardSidearmAccess.getEffectivePrimaryCopy(guard)")),
                () -> assertTrue(loadout.contains(
                        "GuardSidearmAccess.getConfiguredSidearmInventory(guard)")),
                () -> assertTrue(loadout.contains("state == SidearmState.SIDEARM_ACTIVE")),
                () -> assertTrue(loadout.contains("Loadout.recovery()")),
                () -> assertTrue(loadout.contains("assessPhysicalSupply(")),
                () -> assertTrue(loadout.contains("assessDonorReserve(")),
                () -> assertTrue(loadout.contains("compatibleWeapons++")),
                () -> assertTrue(loadout.contains("captureSummary(")),
                () -> assertFalse(loadout.contains("getEntities")),
                () -> assertFalse(loadout.contains("TickEvent")),
                () -> assertFalse(loadout.contains("getNavigation")),
                () -> assertEquals(
                        Long.MAX_VALUE,
                        GuardWeaponAmmunition.retainedReserveUnits(-1, 1)),
                () -> assertEquals(
                        34L,
                        GuardWeaponAmmunition.retainedReserveUnits(17, 2)));
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(
                projectDirectory.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/ammunition/" + fileName),
                StandardCharsets.UTF_8);
    }
}
