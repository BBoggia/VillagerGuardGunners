package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

final class VillageDefenseDutyLayoutTest {
    private static final long WORLD_SEED = 42L;
    private static final ResourceLocation DIMENSION = id("minecraft", "overworld");
    private static final BlockPos ANCHOR = new BlockPos(20, 70, -12);

    @Test
    void everyBuildingAuthorsOnlyItsReviewedBoundedCells() {
        VillageDefenseDutyLayout barracks = layout(
                VillageBuildingType.BARRACKS, Rotation.NONE, ANCHOR);
        VillageDefenseDutyLayout armory = layout(
                VillageBuildingType.ARMORY, Rotation.NONE, ANCHOR.offset(32, 0, 0));
        VillageDefenseDutyLayout sentry = layout(
                VillageBuildingType.SENTRY_POST, Rotation.NONE, ANCHOR.offset(64, 0, 0));
        VillageDefenseDutyLayout watchtower = layout(
                VillageBuildingType.WATCHTOWER, Rotation.NONE, ANCHOR.offset(96, 0, 0));
        VillageDefenseDutyLayout training = layout(
                VillageBuildingType.TRAINING_YARD, Rotation.NONE, ANCHOR.offset(128, 0, 0));

        assertAll(
                () -> assertEquals(4, barracks.slots().size()),
                () -> assertEquals(1, armory.slots().size()),
                () -> assertEquals(1, sentry.slots().size()),
                () -> assertEquals(2, watchtower.slots().size()),
                () -> assertEquals(2, training.slots().size()),
                () -> assertEquals(ANCHOR,
                        barracks.slots().get(0).position()),
                () -> assertEquals(ANCHOR.west(),
                        barracks.slots().get(1).position()),
                () -> assertEquals(ANCHOR.east(),
                        barracks.slots().get(2).position()),
                () -> assertEquals(ANCHOR.north(),
                        barracks.slots().get(3).position()),
                () -> assertTrue(watchtower.slots().stream().allMatch(slot ->
                        slot.position().getY()
                                == watchtower.building().anchorPosition().getY() + 5
                                && slot.kind() == VillageDefenseDutySlotKind.OVERWATCH
                                && slot.weaponRequirement()
                                        == VillageDefenseDutyWeaponRequirement.RANGED)),
                () -> assertTrue(training.slots().stream().allMatch(slot ->
                        slot.facing() == Direction.SOUTH
                                && slot.kind() == VillageDefenseDutySlotKind.DRILL
                                && slot.weaponRequirement()
                                        == VillageDefenseDutyWeaponRequirement.RANGED)),
                () -> assertEquals(VillageDefenseDutySlotKind.SENTRY,
                        armory.slots().get(0).kind()),
                () -> assertEquals(VillageDefenseDutySlotKind.SENTRY,
                        sentry.slots().get(0).kind()));
    }

    @Test
    void positionsAndFacingsRotateTogetherWhileIdsRemainStable() {
        VillageBuildingIdentity identity = identity(
                VillageBuildingType.BARRACKS, ANCHOR);
        VillageDefenseDutyLayout none = VillageDefenseDutyLayouts.forBuilding(
                identity, Rotation.NONE);
        VillageDefenseDutyLayout clockwise = VillageDefenseDutyLayouts.forBuilding(
                identity, Rotation.CLOCKWISE_90);
        VillageDefenseDutyLayout opposite = VillageDefenseDutyLayouts.forBuilding(
                identity, Rotation.CLOCKWISE_180);
        VillageDefenseDutyLayout counterclockwise = VillageDefenseDutyLayouts.forBuilding(
                identity, Rotation.COUNTERCLOCKWISE_90);

        assertAll(
                () -> assertEquals(ANCHOR.north(), clockwise.slots().get(1).position()),
                () -> assertEquals(Direction.NORTH,
                        clockwise.slots().get(1).facing()),
                () -> assertEquals(ANCHOR.east(), opposite.slots().get(1).position()),
                () -> assertEquals(Direction.EAST,
                        opposite.slots().get(1).facing()),
                () -> assertEquals(ANCHOR.south(),
                        counterclockwise.slots().get(1).position()),
                () -> assertEquals(Direction.SOUTH,
                        counterclockwise.slots().get(1).facing()),
                () -> assertEquals(
                        none.slots().stream().map(VillageDefenseDutySlot::slotId).toList(),
                        clockwise.slots().stream()
                                .map(VillageDefenseDutySlot::slotId).toList()),
                () -> assertNotEquals(none, clockwise));
    }

    @Test
    void slotAndLayoutRejectMalformedOrConflictingState() {
        VillageBuildingIdentity identity = identity(
                VillageBuildingType.SENTRY_POST, ANCHOR);
        VillageDefenseDutySlot slot = VillageDefenseDutyLayouts.forBuilding(
                identity, Rotation.NONE).slots().get(0);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageDefenseDutySlot(
                                2,
                                slot.slotId(),
                                slot.buildingId(),
                                slot.dimensionId(),
                                slot.slotKey(),
                                slot.position(),
                                slot.facing(),
                                slot.kind(),
                                slot.weaponRequirement())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageDefenseDutySlot(
                                1,
                                UUID.randomUUID(),
                                slot.buildingId(),
                                slot.dimensionId(),
                                slot.slotKey(),
                                slot.position(),
                                slot.facing(),
                                slot.kind(),
                                slot.weaponRequirement())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageDefenseDutySlot(
                                1,
                                VillageDefenseDutyIds.slotId(slot.buildingId(), "Bad-Key"),
                                slot.buildingId(),
                                slot.dimensionId(),
                                "Bad-Key",
                                slot.position(),
                                slot.facing(),
                                slot.kind(),
                                slot.weaponRequirement())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageDefenseDutySlot(
                                1,
                                slot.slotId(),
                                slot.buildingId(),
                                slot.dimensionId(),
                                slot.slotKey(),
                                slot.position(),
                                Direction.UP,
                                slot.kind(),
                                slot.weaponRequirement())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageDefenseDutyLayout(
                                1, identity, Rotation.NONE, List.of(slot, slot))),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageDefenseDutyLayout(
                                1, identity, Rotation.NONE, List.of())));
    }

    @Test
    void savedDataRoundTripsAndUsesIndexedBoundedLookups() {
        VillageBuildingIdentity barracks = identity(
                VillageBuildingType.BARRACKS, ANCHOR);
        VillageBuildingIdentity watchtower = identity(
                VillageBuildingType.WATCHTOWER, ANCHOR.offset(40, 0, 0));
        VillageDefenseDutySavedData source = new VillageDefenseDutySavedData(DIMENSION);

        assertEquals(VillageDefenseDutySavedData.ClaimStatus.CLAIMED,
                source.claimBuilding(barracks, Rotation.CLOCKWISE_90));
        assertEquals(VillageDefenseDutySavedData.ClaimStatus.CLAIMED,
                source.claimBuilding(watchtower, Rotation.CLOCKWISE_180));
        assertEquals(VillageDefenseDutySavedData.ClaimStatus.ALREADY_CLAIMED,
                source.claimBuilding(barracks, Rotation.CLOCKWISE_90));

        CompoundTag encoded = source.save(new CompoundTag());
        VillageDefenseDutySavedData loaded = VillageDefenseDutySavedData.loadForLevel(
                encoded, DIMENSION, WORLD_SEED);
        List<VillageDefenseDutySlot> limited = loaded.slotsForBuildings(
                List.of(barracks.buildingId(), watchtower.buildingId()), 5);
        VillageDefenseDutySavedData.DutyCoverage coverage =
                loaded.coverageForBuildings(List.of(
                        barracks.buildingId(), barracks.buildingId(),
                        watchtower.buildingId()));

        assertAll(
                () -> assertTrue(loaded.enabled()),
                () -> assertEquals(2, loaded.layoutCount()),
                () -> assertEquals(6, loaded.slotCount()),
                () -> assertEquals(5, limited.size()),
                () -> assertEquals(2, coverage.layouts()),
                () -> assertEquals(6, coverage.slots()),
                () -> assertEquals(
                        VillageDefenseDutyLayouts.forBuilding(
                                barracks, Rotation.CLOCKWISE_90),
                        loaded.layout(barracks.buildingId())),
                () -> assertEquals(
                        limited.get(0), loaded.slot(limited.get(0).slotId())),
                () -> assertTrue(loaded.slotsForBuildings(List.of(), 5).isEmpty()),
                () -> assertTrue(loaded.slotsForBuildings(
                        List.of(barracks.buildingId()), 0).isEmpty()),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        loaded.slotsForBuildings(List.of(barracks.buildingId()), -1)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        loaded.coverageForBuildings(java.util.Collections.nCopies(
                                VillageDefenseDutySavedData.MAXIMUM_QUERY_BUILDINGS + 1,
                                barracks.buildingId()))));
    }

    @Test
    void savedDataClaimsAnExactPackAuthoredLayoutWithoutReauthoringIt() {
        VillageBuildingIdentity identity = identity(
                VillageBuildingType.SENTRY_POST, ANCHOR);
        VillageDefenseDutyLayout custom = VillageDefenseDutyLayouts.forBuilding(
                identity,
                Rotation.COUNTERCLOCKWISE_90,
                List.of(new VillageDefenseDutySlotDefinition(
                        "custom_roof",
                        new BlockPos(4, 6, -2),
                        Direction.SOUTH,
                        VillageDefenseDutySlotKind.OVERWATCH,
                        VillageDefenseDutyWeaponRequirement.RANGED)));
        VillageDefenseDutySavedData data = new VillageDefenseDutySavedData(DIMENSION);

        assertAll(
                () -> assertEquals(
                        VillageDefenseDutySavedData.ClaimStatus.CLAIMED,
                        data.claimLayout(custom)),
                () -> assertEquals(custom, data.layout(identity.buildingId())),
                () -> assertEquals(
                        VillageDefenseDutySavedData.ClaimStatus.ALREADY_CLAIMED,
                        data.claimLayout(custom)),
                () -> assertEquals(
                        VillageDefenseDutySavedData.ClaimStatus.CONFLICT,
                        data.claimBuilding(identity, Rotation.COUNTERCLOCKWISE_90)));
    }

    @Test
    void strictLoadRejectsFutureDuplicateAndNonDeterministicDataOpaque() {
        VillageDefenseDutySavedData source = new VillageDefenseDutySavedData(DIMENSION);
        VillageBuildingIdentity identity = identity(
                VillageBuildingType.SENTRY_POST, ANCHOR);
        source.claimBuilding(identity, Rotation.NONE);
        CompoundTag valid = source.save(new CompoundTag());

        CompoundTag future = valid.copy();
        future.putInt("Version", VillageDefenseDutySavedData.DATA_VERSION + 1);
        VillageDefenseDutySavedData futureData = VillageDefenseDutySavedData.loadForLevel(
                future, DIMENSION, WORLD_SEED);

        CompoundTag duplicate = valid.copy();
        ListTag duplicateLayouts = duplicate.getList("Layouts", 10);
        duplicateLayouts.add(duplicateLayouts.getCompound(0).copy());
        VillageDefenseDutySavedData duplicateData = VillageDefenseDutySavedData.loadForLevel(
                duplicate, DIMENSION, WORLD_SEED);

        CompoundTag wrongId = valid.copy();
        wrongId.getList("Layouts", 10).getCompound(0)
                .putUUID("BuildingId", UUID.randomUUID());
        VillageDefenseDutySavedData wrongIdData = VillageDefenseDutySavedData.loadForLevel(
                wrongId, DIMENSION, WORLD_SEED);

        assertAll(
                () -> assertFalse(futureData.enabled()),
                () -> assertEquals(future, futureData.save(new CompoundTag())),
                () -> assertFalse(duplicateData.enabled()),
                () -> assertFalse(wrongIdData.enabled()),
                () -> assertNull(futureData.layout(identity.buildingId())),
                () -> assertNotNull(valid.get("Layouts")));
    }

    @Test
    void claimsFailClosedAcrossDimensionsRotationsAndPhysicalConflicts() {
        VillageDefenseDutySavedData data = new VillageDefenseDutySavedData(DIMENSION);
        VillageBuildingIdentity first = identity(
                VillageBuildingType.SENTRY_POST, ANCHOR);
        VillageBuildingIdentity sameAnchor = identity(
                VillageBuildingType.ARMORY, ANCHOR);
        VillageBuildingIdentity nether = new VillageBuildingIdentity(
                VillageBuildingIdentity.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                id("example", "nether_sentry"),
                VillageBuildingType.SENTRY_POST,
                id("minecraft", "the_nether"),
                ANCHOR);

        assertAll(
                () -> assertEquals(VillageDefenseDutySavedData.ClaimStatus.CLAIMED,
                        data.claimBuilding(first, Rotation.NONE)),
                () -> assertEquals(VillageDefenseDutySavedData.ClaimStatus.CONFLICT,
                        data.claimBuilding(first, Rotation.CLOCKWISE_90)),
                () -> assertEquals(VillageDefenseDutySavedData.ClaimStatus.CONFLICT,
                        data.claimBuilding(sameAnchor, Rotation.NONE)),
                () -> assertEquals(VillageDefenseDutySavedData.ClaimStatus.WRONG_DIMENSION,
                        data.claimBuilding(nether, Rotation.NONE)),
                () -> assertEquals(1, data.layoutCount()),
                () -> assertEquals(1, data.slotCount()));
    }

    private static VillageDefenseDutyLayout layout(
            VillageBuildingType type,
            Rotation rotation,
            BlockPos anchor) {
        return VillageDefenseDutyLayouts.forBuilding(identity(type, anchor), rotation);
    }

    private static VillageBuildingIdentity identity(
            VillageBuildingType type,
            BlockPos anchor) {
        return VillageBuildingIdentity.anchored(
                WORLD_SEED,
                DIMENSION,
                id("example", type.serializedName() + "_" + anchor.getX()),
                type,
                anchor);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
