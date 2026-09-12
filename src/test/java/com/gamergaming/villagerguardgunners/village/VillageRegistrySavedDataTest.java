package com.gamergaming.villagerguardgunners.village;

import static org.junit.jupiter.api.Assertions.*;

import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSavedData;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class VillageRegistrySavedDataTest {
    static final ResourceLocation DIM = id("minecraft:overworld");
    static final ResourceLocation PLAINS = id("minecraft:village_plains");
    static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    static VillageIdentity identity(int x) { return new VillageIdentity(DIM, PLAINS, ChunkPos.asLong(x >> 4, 0)); }
    static VillageFootprint footprint(int x, int span) {
        return VillageFootprint.of(List.of(new VillageFootprint.Piece(new BlockPos(x, 64, 0), "NONE",
                id("minecraft:village/plains/houses/plains_small_house_1"),
                new VillageFootprint.Bounds(x, 60, 0, x + span, 80, 31))));
    }
    static VillageRegistrySavedData sample() {
        var data = new VillageRegistrySavedData(DIM);
        assertTrue(data.observe(identity(0), footprint(0, 31)));
        return data;
    }

    @Test void initialMembershipRejectsGroundBelowElevatedVillageWithoutChangingSavedMembers() {
        var data = new VillageRegistrySavedData(DIM);
        var elevated = VillageFootprint.of(List.of(new VillageFootprint.Piece(new BlockPos(0, 160, 0), "NONE",
                id("test:sky_house"), new VillageFootprint.Bounds(0, 158, 0, 31, 180, 31))));
        data.observe(identity(0), elevated);
        BlockPos ground = new BlockPos(5, 64, 5), deck = new BlockPos(5, 160, 5);
        assertEquals(identity(0).id(), data.at(ground).unambiguous());
        assertNull(data.membershipAt(ground).unambiguous());
        assertEquals(identity(0).id(), data.membershipAt(deck).unambiguous());
        UUID guard = UUID.randomUUID();
        data.affiliateGuard(guard, identity(0).id());
        data.setDirty(false);
        assertTrue(data.membershipOutsideObservedRegion(identity(0).id(), ground));
        assertEquals(identity(0).id(), data.guardVillage(guard));
        assertFalse(data.isDirty());
        data.observeHomes(SectionPos.asLong(ground), List.of(ground));
        assertTrue(data.get(identity(0).id()).homes().isEmpty());
    }

    @Test void initialMembershipUsesIndividualPiecesInsteadOfUnionElevation() {
        var low = new VillageFootprint.Piece(new BlockPos(0, 64, 0), "NONE", id("test:low"),
                new VillageFootprint.Bounds(0, 60, 0, 15, 75, 15));
        var high = new VillageFootprint.Piece(new BlockPos(16, 160, 0), "NONE", id("test:high"),
                new VillageFootprint.Bounds(16, 158, 0, 31, 180, 15));
        var footprint = VillageFootprint.of(List.of(low, high));
        assertFalse(footprint.supportsMembership(new BlockPos(20, 64, 5)));
        assertTrue(footprint.supportsMembership(new BlockPos(5, 64, 5)));
        assertTrue(footprint.supportsMembership(new BlockPos(20, 160, 5)));
    }

    @Test void identityIgnoresOrderAndFootprintChangesButIncludesStructureAndDimension() {
        var first = sample();
        first.observe(identity(64), footprint(64, 31));
        var reverse = new VillageRegistrySavedData(DIM);
        reverse.observe(identity(64), footprint(64, 31));
        reverse.observe(identity(0), footprint(0, 31));
        assertEquals(first.save(new CompoundTag()), reverse.save(new CompoundTag()));
        first.observe(identity(0), footprint(0, 40));
        assertEquals(2, first.get(identity(0).id()).revision());
        assertNotEquals(identity(0).id(), new VillageIdentity(id("minecraft:the_nether"), PLAINS, 0).id());
        assertNotEquals(identity(0).id(), new VillageIdentity(DIM, id("minecraft:village_desert"), 0).id());
        assertEquals(2, first.size());
    }
    @Test void unchangedObservationsAndReadsDoNotDirtyStorage() {
        var data = sample();
        data.setDirty(false);
        assertTrue(data.observe(identity(0), footprint(0, 31)));
        data.at(new BlockPos(5, 64, 5));
        data.page(null, 8);
        assertFalse(data.isDirty());
        assertEquals(1, data.get(identity(0).id()).revision());
    }
    @Test void overlappingVillagesRemainDistinctAndDoNotAutomaticallyChooseAnOwner() {
        var data = sample();
        data.observe(identity(16), footprint(16, 31));
        assertEquals(2, data.at(new BlockPos(20, 64, 4)).villages().size());
        assertNull(data.at(new BlockPos(20, 64, 4)).unambiguous());
        assertEquals(identity(0).id(), data.at(new BlockPos(1, 64, 4)).unambiguous());
    }
    @Test void membershipSurvivesSaveLoadAndCannotBeSilentlyReassigned() {
        var data = sample();
        data.observe(identity(64), footprint(64, 31));
        UUID guard = UUID.randomUUID(), building = UUID.randomUUID();
        assertTrue(data.affiliateGuard(guard, identity(0).id()));
        assertTrue(data.associateBuilding(building, identity(0).id()));
        var loaded = VillageRegistrySavedData.load(data.save(new CompoundTag()), DIM);
        assertEquals(identity(0).id(), loaded.guardVillage(guard));
        assertEquals(identity(0).id(), loaded.buildingVillage(building));
        loaded.setDirty(false);
        assertTrue(loaded.affiliateGuard(guard, identity(0).id()));
        assertFalse(loaded.affiliateGuard(guard, identity(64).id()));
        assertFalse(loaded.isDirty());
        assertEquals(1, loaded.guardCount(identity(0).id()));
    }
    @Test void homesUpdateOnlyTheKnownSectionAndPreserveOtherObservations() {
        var data = sample();
        BlockPos low = new BlockPos(3, 64, 3), high = new BlockPos(3, 80, 3);
        long lowSection = SectionPos.asLong(low), highSection = SectionPos.asLong(high);
        assertTrue(data.observeHomes(lowSection, List.of(low)));
        assertTrue(data.observeHomes(highSection, List.of(high)));
        data.setDirty(false);
        assertTrue(data.observeHomes(lowSection, List.of(low)));
        assertFalse(data.isDirty());
        assertTrue(data.observeHomes(lowSection, List.of()));
        assertEquals(Set.of(high), data.get(identity(0).id()).homes());
        assertFalse(data.observeHomes(lowSection, List.of(high)));
        var loaded = VillageRegistrySavedData.load(data.save(new CompoundTag()), DIM);
        assertEquals(Set.of(high), loaded.get(identity(0).id()).homes());
    }
    @Test void shrinkingFootprintRemovesOldIndexCellsAndOutlyingHomes() {
        var data = sample();
        data.observe(identity(0), footprint(0, 90));
        BlockPos home = new BlockPos(80, 64, 1);
        data.observeHomes(SectionPos.asLong(home), List.of(home));
        data.observe(identity(0), footprint(0, 31));
        assertTrue(data.at(home).villages().isEmpty());
        assertTrue(data.get(identity(0).id()).homes().isEmpty());
    }
    @Test void futureOrMalformedRootIsPreservedAndAllMutationsFailClosed() {
        var tag = sample().save(new CompoundTag());
        tag.putInt("Version", 200);
        tag.putString("FutureField", "must survive");
        var loaded = VillageRegistrySavedData.load(tag, DIM);
        assertFalse(loaded.enabled());
        assertFalse(loaded.observe(identity(64), footprint(64, 31)));
        assertFalse(loaded.affiliateGuard(UUID.randomUUID(), identity(0).id()));
        assertFalse(loaded.at(BlockPos.ZERO).complete());
        assertEquals(tag, loaded.save(new CompoundTag()));
        assertFalse(loaded.isDirty());
    }
    static Stream<Consumer<CompoundTag>> corruptions() {
        return Stream.of(
                tag -> tag.putString("Version", "1"),
                tag -> tag.putInt("Version", 0),
                tag -> tag.remove("Version"),
                tag -> tag.putString("Dimension", "minecraft:the_end"),
                tag -> tag.putString("Dimension", "overworld"),
                tag -> tag.getList("Records", 10).add(tag.getList("Records", 10).getCompound(0).copy()),
                tag -> tag.getList("Records", 10).getCompound(0).putUUID("Id", UUID.randomUUID()),
                tag -> tag.getList("Records", 10).getCompound(0).putLong("Revision", -1),
                tag -> tag.getList("Records", 10).getCompound(0).putString("Structure", "bad id"),
                tag -> tag.getList("Records", 10).getCompound(0).putString("FutureRecord", "x"),
                tag -> tag.getList("Records", 10).getCompound(0).getList("Pieces", 10).getCompound(0)
                        .putIntArray("Bounds", new int[]{8, 1, 0, 3, 2, 1}),
                tag -> tag.getList("Records", 10).getCompound(0).getList("Pieces", 10).getCompound(0)
                        .putIntArray("Position", new int[]{1, 2}),
                tag -> tag.getList("Records", 10).getCompound(0).getList("Pieces", 10).getCompound(0)
                        .putString("Rotation", "spin"),
                tag -> { ListTag values = new ListTag(); values.add(StringTag.valueOf("bad")); tag.put("Guards", values); },
                tag -> { CompoundTag member = new CompoundTag(); member.putUUID("Member", UUID.randomUUID());
                    member.putUUID("Village", UUID.randomUUID()); tag.getList("Buildings", 10).add(member); });
    }
    @ParameterizedTest @MethodSource("corruptions")
    void strictDecodingNeverPublishesAPartialRegistry(Consumer<CompoundTag> corrupt) {
        CompoundTag tag = sample().save(new CompoundTag());
        corrupt.accept(tag);
        var loaded = VillageRegistrySavedData.load(tag, DIM);
        assertFalse(loaded.enabled());
        assertEquals(0, loaded.size());
        assertEquals(tag, loaded.save(new CompoundTag()));
    }
    @Test void indexAndCursorPagingWorkWithTenThousandHistoricalVillages() {
        var data = new VillageRegistrySavedData(DIM);
        for (int i = 0; i < 10_000; i++) data.observe(identity(i * 128), footprint(i * 128, 31));
        assertEquals(identity(128).id(), data.at(new BlockPos(129, 64, 1)).unambiguous());
        assertTrue(data.at(new BlockPos(-200, 64, 1)).villages().isEmpty());
        var page = data.page(null, 8);
        var next = data.page(page.get(7).identity().id(), 8);
        assertEquals(8, next.size());
        assertTrue(page.stream().noneMatch(next::contains));
    }
    @Test void excessiveOverlapDefersInsteadOfSelectingAPartialWinner() {
        var data = new VillageRegistrySavedData(DIM);
        for (int i = 0; i < 65; i++) {
            var identity = new VillageIdentity(DIM, id("test:village_" + i), 0);
            data.observe(identity, footprint(0, 31));
        }
        assertFalse(data.at(new BlockPos(1, 64, 1)).complete());
        assertNull(data.at(new BlockPos(1, 64, 1)).unambiguous());
    }
    @Test void buildingChunkIndexRestoresCompletedAnchorsFromExistingSave() {
        var data = new VillageBuildingSavedData();
        var building = new VillageBuildingIdentity(1, UUID.randomUUID(), id("test:armory"),
                VillageBuildingType.ARMORY, DIM, new BlockPos(3, 64, 3));
        data.claimAnchor(building);
        assertEquals(List.of(building), data.identitiesInChunk(new ChunkPos(0, 0), 8));
        var loaded = VillageBuildingSavedData.load(data.save(new CompoundTag()));
        assertEquals(List.of(building), loaded.identitiesInChunk(new ChunkPos(0, 0), 8));
        assertTrue(loaded.identitiesInChunk(new ChunkPos(1, 0), 8).isEmpty());
    }
}
