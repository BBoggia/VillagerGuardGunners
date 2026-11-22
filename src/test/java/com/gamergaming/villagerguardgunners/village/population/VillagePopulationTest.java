package com.gamergaming.villagerguardgunners.village.population;

import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.gamergaming.villagerguardgunners.village.VillageIdentity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class VillagePopulationTest {
    static final ResourceLocation DIM = ResourceLocation.parse("minecraft:overworld");
    static final VillageIdentity ID = new VillageIdentity(DIM, ResourceLocation.parse("minecraft:village_plains"), 0);
    static VillagePopulationPlan.Source source(int x, boolean building, int capacity) {
        var piece = new VillageFootprint.Piece(new BlockPos(x, 64, 0), "NONE",
                ResourceLocation.parse(building ? "test:barracks" : "minecraft:village/plains/houses/plains_small_house_1"),
                new VillageFootprint.Bounds(x, 63, 0, x + 7, 69, 7));
        return new VillagePopulationPlan.Source(VillagePopulationPlan.sourceId(piece), piece, building, capacity, building ? 0 : 10);
    }
    static VillagePopulationPlan sample() {
        return VillagePopulationPlan.create(ID, List.of(source(0, false, 2), source(64, false, 2), source(96, true, 4)),
                VillagePopulationPlan.Settings.DEFAULT, 0);
    }
    static VillagePopulationPlan.Source nativeMarker(int x, int capacity) {
        var piece = new VillageFootprint.Piece(new BlockPos(x, 64, 0), "NONE",
                ResourceLocation.parse("minecraft:village/common/iron_golem"),
                new VillageFootprint.Bounds(x, 64, 0, x, 65, 0));
        return new VillagePopulationPlan.Source(VillagePopulationPlan.sourceId(piece), piece, false, false, capacity, 10);
    }
    @Test void expandedPlansRoundTripButCannotMasqueradeAsLegacyFormats() {
        var plan=VillagePopulationPlan.create(ID,List.of(nativeMarker(0,64)),
                new VillagePopulationPlan.Settings(64,64,3,2,2),0);
        assertEquals(64,plan.allowance());
        var encoded=VillagePopulationCodec.encode(plan);
        assertEquals(3,encoded.getInt("Version"));
        assertEquals(plan,VillagePopulationCodec.decode(encoded,DIM));
        for(int version: new int[]{1,2}) {
            var old=encoded.copy();old.putInt("Version",version);
            if(version==1)old.getList("Sources",10).getCompound(0).remove("Residence");
            assertThrows(IllegalArgumentException.class,()->VillagePopulationCodec.decode(old,DIM));
        }
        assertEquals(2,VillagePopulationCodec.encode(sample()).getInt("Version"));
    }

    @Test void expandedCreditsRemainSpentAcrossRestart() {
        var source=nativeMarker(0,64);
        var plan=VillagePopulationPlan.create(ID,List.of(source),new VillagePopulationPlan.Settings(64,64,3,2,2),0);
        var data=new VillagePopulationSavedData(DIM);assertTrue(data.freeze(plan));
        for(int i=0;i<64;i++)assertTrue(data.consume(ID.id(),source.id(),i,plan.slot(source.id(),i)));
        assertFalse(data.consume(ID.id(),source.id(),64,UUID.randomUUID()));
        var loaded=VillagePopulationSavedData.load(data.save(new CompoundTag()),DIM);
        assertEquals(64,loaded.spent(ID.id()));
        assertEquals(plan,loaded.plan(ID.id()));
        for(int i=0;i<64;i++)assertFalse(loaded.consume(ID.id(),source.id(),i,UUID.randomUUID()));
    }

    @Test void defaultNativeMarkerReceivesSixSlotsWithoutInflatingResidentialDemand() {
        var sources = new ArrayList<VillagePopulationPlan.Source>();
        for (int i = 0; i < 13; i++) sources.add(source(i * 32, false, 0));
        var marker = nativeMarker(512, 6);
        sources.add(marker);
        var plan = VillagePopulationPlan.create(ID, sources, VillagePopulationPlan.Settings.DEFAULT, 0);
        assertEquals(13, plan.residences()); assertEquals(13, plan.areas());
        assertEquals(6, plan.allowance()); assertEquals(6, plan.quotas().get(marker.id()));
        assertEquals(plan, VillagePopulationCodec.decode(VillagePopulationCodec.encode(plan), DIM));
        var capped = VillagePopulationPlan.create(ID, sources, new VillagePopulationPlan.Settings(2, 2, 3, 2, 2), 0);
        assertEquals(2, capped.allowance());
        assertThrows(IllegalArgumentException.class, () -> source(0, false, 6));
        assertThrows(IllegalArgumentException.class, () -> nativeMarker(0, 65));
    }
    @Test void nativeMarkersShareRemainingCreditsAfterReservedGarrisons() {
        var first = nativeMarker(0, 6); var second = nativeMarker(64, 6); var building = source(96, true, 4);
        var plan = VillagePopulationPlan.create(ID, List.of(first, second, building),
                new VillagePopulationPlan.Settings(16, 16, 3, 2, 2), 0);
        assertEquals(4, plan.quotas().get(building.id()));
        assertEquals(6, plan.quotas().get(first.id())); assertEquals(6, plan.quotas().get(second.id()));
        assertEquals(0, plan.residences()); assertEquals(1, plan.areas());
    }
    @Test void versionOneJournalAndSpentSlotsRemainSealedAcrossCodecUpgrade(@TempDir Path directory) throws Exception {
        var plan = sample(); var old = VillagePopulationCodec.encode(plan);
        old.putInt("Version", 1);
        old.getList("Sources", net.minecraft.nbt.Tag.TAG_COMPOUND).forEach(tag -> ((CompoundTag) tag).remove("Residence"));
        var file = directory.resolve("village_" + ID.id() + ".dat");
        net.minecraft.nbt.NbtIo.writeCompressed(old, file.toFile());
        byte[] original = java.nio.file.Files.readAllBytes(file);
        var migrated = VillagePopulationCodec.decode(old, DIM);
        assertEquals(plan, migrated);
        var journal = new VillagePopulationJournal(directory);
        var source = plan.sources().stream().filter(s -> plan.quotas().get(s.id()) > 0).findFirst().orElseThrow();
        var slot = plan.slot(source.id(), 0); var guard = UUID.randomUUID();
        assertTrue(journal.reserve(slot, guard)); assertTrue(journal.plan(migrated));
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
        assertEquals(slot, migrated.slot(source.id(), 0));
        assertFalse(new VillagePopulationJournal(directory).reserve(slot, UUID.randomUUID()));
        var malformed = old.copy(); malformed.putInt("Version", 2);
        assertThrows(IllegalArgumentException.class, () -> VillagePopulationCodec.decode(malformed, DIM));
    }
    static Stream<Arguments> targets() {
        return Stream.of(Arguments.of(4, 2, 0, 3), Arguments.of(12, 6, 1, 8), Arguments.of(24, 12, 3, 16),
                Arguments.of(0, 0, 0, 2), Arguments.of(128, 128, 128, 16));
    }
    @ParameterizedTest @MethodSource("targets")
    void formulaIsInspectableAndCapped(int homes, int areas, int buildings, int expected) {
        assertEquals(expected, VillagePopulationPlan.Settings.DEFAULT.target(homes, areas, buildings, 0));
    }
    @Test void chunkOrderCannotChangeGarrisonSharesOrGenericSlots() {
        var observations = new ArrayList<>(List.of(source(0, false, 2), source(64, false, 2), source(96, true, 4), source(160, true, 1)));
        var expected = VillagePopulationPlan.create(ID, observations, VillagePopulationPlan.Settings.DEFAULT, 0);
        for (int i = 0; i < 30; i++) {
            Collections.shuffle(observations, new java.util.Random(i));
            assertEquals(expected, VillagePopulationPlan.create(ID, observations, VillagePopulationPlan.Settings.DEFAULT, 0));
        }
        assertEquals(1, expected.quotas().get(source(160, true, 1).id()));
        assertEquals(4, expected.quotas().get(source(96, true, 4).id()));
        assertEquals(expected.target(), expected.allowance());
    }
    @Test void limitsAndDifficultyRemainIndependentOfGunChance() {
        assertEquals(16, VillagePopulationPlan.Settings.DEFAULT.target(24, 12, 3, 32));
        assertThrows(IllegalArgumentException.class, () -> VillagePopulationPlan.Settings.DEFAULT.target(1, 1, 0, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new VillagePopulationPlan.Settings(20, 16, 3, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new VillagePopulationPlan.Settings(0, 65, 3, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new VillagePopulationPlan.Settings(0, 16, 0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> VillagePopulationPlan.create(ID, Collections.nCopies(129, source(0, false, 2)),
                VillagePopulationPlan.Settings.DEFAULT, 0));
        assertEquals(2, VillagePopulationPlan.create(ID, List.of(source(0, false, 2)), VillagePopulationPlan.Settings.DEFAULT, 0).allowance());
    }
    @Test void deathAndRestartNeverRefundAnInitialCredit() {
        var plan = sample(); var data = new VillagePopulationSavedData(DIM); assertTrue(data.freeze(plan));
        var source = plan.sources().stream().filter(s -> plan.quotas().get(s.id()) > 0).findFirst().orElseThrow();
        UUID guard = UUID.randomUUID();
        assertTrue(data.observe(guard, ID.id())); assertTrue(data.observe(guard, ID.id()));
        assertEquals(1, data.living(ID.id()));
        assertTrue(data.consume(ID.id(), source.id(), 0, guard));
        assertTrue(data.consume(ID.id(), source.id(), 0, guard));
        assertFalse(data.consume(ID.id(), source.id(), 0, UUID.randomUUID()));
        data.confirmedDeath(guard); data.confirmedDeath(guard);
        var loaded = VillagePopulationSavedData.load(data.save(new CompoundTag()), DIM);
        assertTrue(loaded.enabled()); assertEquals(0, loaded.living(ID.id())); assertEquals(1, loaded.spent(ID.id()));
        assertFalse(loaded.observe(guard, ID.id()));
        assertEquals(guard, loaded.credit(plan.slot(source.id(), 0)).guard());
    }
    @Test void existingResidentsCreateNoPlanAndInflatedObservationsCannotReopenOne() {
        var data = new VillagePopulationSavedData(DIM); data.observe(UUID.randomUUID(), ID.id());
        assertNull(data.plan(ID.id())); assertEquals(0, data.spent(ID.id()));
        var plan = sample(); assertTrue(data.freeze(plan));
        var larger = VillagePopulationPlan.create(ID, List.of(source(0, false, 2), source(64, false, 2), source(128, false, 2)),
                new VillagePopulationPlan.Settings(16, 16, 1, 1, 2), 0);
        assertFalse(data.freeze(larger)); assertEquals(plan, data.plan(ID.id()));
    }
    @Test void codecRoundTripsAndPreservesUnrecognizedSavedData() {
        var plan = sample(); assertEquals(plan, VillagePopulationCodec.decode(VillagePopulationCodec.encode(plan), DIM));
        var data = new VillagePopulationSavedData(DIM); data.freeze(plan);
        CompoundTag tag = data.save(new CompoundTag()); tag.putInt("Version", 2);
        var future = VillagePopulationSavedData.load(tag, DIM);
        assertFalse(future.enabled()); assertEquals(tag, future.save(new CompoundTag())); assertFalse(future.freeze(plan));
        tag.putInt("Version", 1); tag.putString("Dimension", "minecraft:the_nether");
        assertFalse(VillagePopulationSavedData.load(tag, DIM).enabled());
        var bad = VillagePopulationCodec.encode(plan); bad.putInt("Target", 0);
        assertThrows(IllegalArgumentException.class, () -> VillagePopulationCodec.decode(bad, DIM));
    }
    @Test void durableIntentBlocksUnloadedOrUncertainReplacementAndCanceledAttemptCanRetry(@TempDir Path directory) throws Exception {
        var journal = new VillagePopulationJournal(directory); var plan = sample(); assertTrue(journal.plan(plan));
        UUID slot = UUID.randomUUID(), guard = UUID.randomUUID();
        assertTrue(journal.reserve(slot, guard));
        var restarted = new VillagePopulationJournal(directory);
        assertFalse(restarted.reserve(slot, guard)); assertFalse(restarted.reserve(slot, UUID.randomUUID()));
        assertTrue(restarted.matches(slot, guard));
        assertThrows(java.io.IOException.class, () -> restarted.canceled(slot, UUID.randomUUID()));
        restarted.canceled(slot, guard); assertTrue(restarted.reserve(slot, guard));
        assertEquals(VillagePopulationCodec.encode(plan), restarted.readPlan(ID.id()));
    }
    @Test void brokenJournalIsNeverOverwrittenWithFreshCredits(@TempDir Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("village_" + ID.id() + ".dat"), "interrupted");
        assertThrows(java.io.IOException.class, () -> new VillagePopulationJournal(directory).plan(sample()));
        assertEquals("interrupted", java.nio.file.Files.readString(directory.resolve("village_" + ID.id() + ".dat")));
    }
}
