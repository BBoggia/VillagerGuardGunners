package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class NaturalGuardPopulationIndexTest {
    private static final UUID FIRST_GUARD =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_GUARD =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_GUARD =
            UUID.fromString("10000000-0000-0000-0000-000000000003");

    @Test
    void spatialCountIncludesOnlyUnloadedRecordsInsideTheSphere() {
        NaturalGuardPopulationIndex index = new NaturalGuardPopulationIndex();
        index.put(FIRST_GUARD, issuance(1), 0.0D, 64.0D, 0.0D);
        index.put(SECOND_GUARD, issuance(2), 15.0D, 64.0D, 0.0D);
        index.put(THIRD_GUARD, issuance(3), 17.0D, 80.0D, 0.0D);

        assertAll(
                () -> assertEquals(3, index.countWithin(
                        0.0D, 64.0D, 0.0D, 24.0D, ignored -> false)),
                () -> assertEquals(2, index.countWithin(
                        0.0D,
                        64.0D,
                        0.0D,
                        24.0D,
                        Set.of(SECOND_GUARD)::contains)),
                () -> assertEquals(1, index.countWithin(
                        0.0D,
                        64.0D,
                        0.0D,
                        24.0D,
                        ignored -> false,
                        1)),
                () -> assertEquals(0, index.countWithin(
                        0.0D,
                        64.0D,
                        0.0D,
                        24.0D,
                        ignored -> false,
                        0)),
                () -> assertEquals(0, index.countWithin(
                        0.0D, 64.0D, 0.0D, Double.NaN, ignored -> false)));
    }

    @Test
    void movingAndRemovingARecordUpdatesItsChunkBucketWithoutDuplicates() {
        NaturalGuardPopulationIndex index = new NaturalGuardPopulationIndex();
        index.put(FIRST_GUARD, issuance(1), 1.0D, 64.0D, 1.0D);
        index.put(FIRST_GUARD, issuance(1), 80.0D, 64.0D, 1.0D);

        assertAll(
                () -> assertEquals(0, index.countWithin(
                        1.0D, 64.0D, 1.0D, 8.0D, ignored -> false)),
                () -> assertEquals(1, index.countWithin(
                        80.0D, 64.0D, 1.0D, 8.0D, ignored -> false)));

        index.remove(FIRST_GUARD);
        assertEquals(0, index.countWithin(
                80.0D, 64.0D, 1.0D, 8.0D, ignored -> false));
    }

    @Test
    void persistentRoundTripRetainsIdentityPositionAndCleanState() {
        NaturalGuardPopulationIndex original = new NaturalGuardPopulationIndex();
        original.put(FIRST_GUARD, issuance(1), -17.25D, 70.0D, 31.75D);
        CompoundTag saved = original.save(new CompoundTag());

        NaturalGuardPopulationIndex restored = NaturalGuardPopulationIndex.load(saved);

        assertAll(
                () -> assertEquals(1, restored.countWithin(
                        -17.25D, 70.0D, 31.75D, 0.0D, ignored -> false)),
                () -> assertFalse(restored.isDirty()));
    }

    private static UUID issuance(int suffix) {
        return UUID.fromString("20000000-0000-0000-0000-00000000000" + suffix);
    }
}
