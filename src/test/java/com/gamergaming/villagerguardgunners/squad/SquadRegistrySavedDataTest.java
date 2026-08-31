package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class SquadRegistrySavedDataTest {
    private static final UUID FIRST_SQUAD =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_SQUAD =
            UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID CREATOR =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Test
    void registrationRenameRemovalAndDuplicateHandlingAreAtomic() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        SquadMetadata created = registry.register(
                        FIRST_SQUAD,
                        "  First\n Squad  ",
                        CREATOR,
                        100L)
                .orElseThrow();

        assertAll(
                () -> assertEquals("First Squad", created.displayName()),
                () -> assertEquals(CREATOR, created.createdBy()),
                () -> assertTrue(registry.isDirty()),
                () -> assertTrue(registry.register(
                                FIRST_SQUAD,
                                "Replacement",
                                CREATOR,
                                101L)
                        .isEmpty()),
                () -> assertEquals(1, registry.size()),
                () -> assertEquals(
                        "First Squad",
                        registry.find(FIRST_SQUAD).orElseThrow().displayName()));

        SquadMetadata renamed = registry.rename(FIRST_SQUAD, "Alpha", 120L).orElseThrow();
        assertAll(
                () -> assertEquals("Alpha", renamed.displayName()),
                () -> assertEquals(100L, renamed.createdGameTime()),
                () -> assertEquals(120L, renamed.updatedGameTime()),
                () -> assertTrue(registry.remove(FIRST_SQUAD)),
                () -> assertFalse(registry.remove(FIRST_SQUAD)),
                () -> assertTrue(registry.find(FIRST_SQUAD).isEmpty()));
    }

    @Test
    void saveLoadRoundTripPreservesMetadataAndReturnsDeterministicSnapshots() {
        SquadRegistrySavedData original = new SquadRegistrySavedData();
        SquadMetadata second = original.register(
                        SECOND_SQUAD,
                        null,
                        null,
                        80L)
                .orElseThrow();
        SquadMetadata first = original.register(
                        FIRST_SQUAD,
                        "Alpha",
                        CREATOR,
                        40L)
                .orElseThrow();
        CompoundTag saved = original.save(new CompoundTag());

        SquadRegistrySavedData restored = SquadRegistrySavedData.load(saved);

        assertAll(
                () -> assertEquals(2, restored.size()),
                () -> assertEquals(first, restored.find(FIRST_SQUAD).orElseThrow()),
                () -> assertEquals(second, restored.find(SECOND_SQUAD).orElseThrow()),
                () -> assertEquals(
                        java.util.List.of(FIRST_SQUAD, SECOND_SQUAD),
                        restored.snapshot().stream().map(SquadMetadata::squadId).toList()),
                () -> assertEquals(
                        "Squad 40000000",
                        restored.find(SECOND_SQUAD).orElseThrow().displayName()),
                () -> assertFalse(restored.isDirty()));
    }

    @Test
    void corruptEntriesAreSkippedAndUnsupportedRegistryRecoversEmpty() {
        SquadRegistrySavedData original = new SquadRegistrySavedData();
        original.register(FIRST_SQUAD, "Alpha", CREATOR, 10L).orElseThrow();
        CompoundTag saved = original.save(new CompoundTag());
        ListTag entries = saved.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND);

        CompoundTag futureEntry = entries.getCompound(0).copy();
        futureEntry.putUUID("SquadId", SECOND_SQUAD);
        futureEntry.putInt("Version", SquadMetadata.CURRENT_SCHEMA_VERSION + 1);
        entries.add(futureEntry);

        CompoundTag invalidName = entries.getCompound(0).copy();
        invalidName.putUUID(
                "SquadId",
                UUID.fromString("40000000-0000-0000-0000-000000000003"));
        invalidName.putString("DisplayName", " Bad ");
        entries.add(invalidName);

        CompoundTag duplicate = entries.getCompound(0).copy();
        duplicate.putString("DisplayName", "Replacement");
        entries.add(duplicate);

        SquadRegistrySavedData recovered = SquadRegistrySavedData.load(saved);
        CompoundTag futureRegistry = saved.copy();
        futureRegistry.putInt("Version", 2);
        SquadRegistrySavedData unsupported = SquadRegistrySavedData.load(futureRegistry);

        assertAll(
                () -> assertEquals(1, recovered.size()),
                () -> assertEquals(
                        "Alpha",
                        recovered.find(FIRST_SQUAD).orElseThrow().displayName()),
                () -> assertTrue(recovered.find(SECOND_SQUAD).isEmpty()),
                () -> assertFalse(recovered.isDirty()),
                () -> assertEquals(0, unsupported.size()),
                () -> assertFalse(unsupported.isDirty()));
    }

    @Test
    void displayNamesAreBoundedByCodePointWithoutSplittingUnicode() {
        String requested = "\uD83D\uDEE1".repeat(80);
        String normalized = SquadMetadata.normalizeDisplayName(requested, FIRST_SQUAD);

        assertAll(
                () -> assertEquals(
                        SquadMetadata.MAX_DISPLAY_NAME_CODE_POINTS,
                        normalized.codePointCount(0, normalized.length())),
                () -> assertTrue(SquadMetadata.isCanonicalDisplayName(normalized)));
    }
}
