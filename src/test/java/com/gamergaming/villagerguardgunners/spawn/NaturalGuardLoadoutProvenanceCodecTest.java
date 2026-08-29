package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutProvenanceCodecTest {

    @Test
    void guardRecordRoundTripsExactlyAndCanBeRemoved() {
        NaturalGuardLoadoutProvenance provenance = provenance();
        CompoundTag guardTag = new CompoundTag();

        NaturalGuardLoadoutProvenanceCodec.writeGuardRecord(guardTag, provenance);

        assertAll(
                () -> assertEquals(
                        provenance,
                        NaturalGuardLoadoutProvenanceCodec.readGuardRecord(guardTag)),
                () -> assertTrue(guardTag.contains(
                        NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG,
                        Tag.TAG_COMPOUND)));

        NaturalGuardLoadoutProvenanceCodec.writeGuardRecord(guardTag, null);
        assertFalse(guardTag.contains(
                NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG));
    }

    @Test
    void malformedUnsupportedAndExtendedRecordsFailClosed() {
        CompoundTag validParent = new CompoundTag();
        NaturalGuardLoadoutProvenanceCodec.writeGuardRecord(validParent, provenance());

        CompoundTag futureVersion = validParent.copy();
        record(futureVersion).putInt(
                "Version",
                NaturalGuardLoadoutProvenance.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag unexpectedField = validParent.copy();
        record(unexpectedField).putBoolean("Trusted", true);
        CompoundTag invalidId = validParent.copy();
        record(invalidId).putString("Gun", "TACZ:GLOCK_17");
        CompoundTag nilIdentity = validParent.copy();
        record(nilIdentity).putUUID("IssuanceId", new UUID(0L, 0L));
        CompoundTag wrongUuidType = validParent.copy();
        record(wrongUuidType).putString("IssuanceId", "not-a-uuid");
        CompoundTag unknownDifficulty = validParent.copy();
        record(unknownDifficulty).putString("Difficulty", "NIGHTMARE");

        assertAll(
                () -> assertNull(NaturalGuardLoadoutProvenanceCodec.readGuardRecord(
                        futureVersion)),
                () -> assertNull(NaturalGuardLoadoutProvenanceCodec.readGuardRecord(
                        unexpectedField)),
                () -> assertNull(NaturalGuardLoadoutProvenanceCodec.readGuardRecord(
                        invalidId)),
                () -> assertNull(NaturalGuardLoadoutProvenanceCodec.readGuardRecord(
                        nilIdentity)),
                () -> assertNull(NaturalGuardLoadoutProvenanceCodec.readGuardRecord(
                        wrongUuidType)),
                () -> assertNull(NaturalGuardLoadoutProvenanceCodec.readGuardRecord(
                        unknownDifficulty)));
    }

    @Test
    void diagnosticSnapshotsCannotMisrepresentWhetherARecordExists() {
        NaturalGuardLoadoutProvenance provenance = provenance();
        NaturalGuardLoadoutDiagnostics.Snapshot active =
                new NaturalGuardLoadoutDiagnostics.Snapshot(
                        NaturalGuardLoadoutDiagnostics.AuthenticationState.ACTIVE,
                        true,
                        NaturalGuardLoadoutSelector.DecisionStatus.SELECTED,
                        NaturalGuardLoadoutIssuance.IssuanceStatus.ISSUED,
                        provenance);

        assertAll(
                () -> assertTrue(active.active()),
                () -> assertFalse(new NaturalGuardLoadoutDiagnostics.Snapshot(
                                NaturalGuardLoadoutDiagnostics.AuthenticationState.NO_RECORD,
                                false,
                                null,
                                null,
                                null)
                        .active()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutDiagnostics.Snapshot(
                                NaturalGuardLoadoutDiagnostics.AuthenticationState.ACTIVE,
                                true,
                                null,
                                null,
                                null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutDiagnostics.Snapshot(
                                NaturalGuardLoadoutDiagnostics.AuthenticationState.NO_RECORD,
                                false,
                                null,
                                null,
                                provenance)));
    }

    private static CompoundTag record(CompoundTag parent) {
        return parent.getCompound(NaturalGuardLoadoutProvenanceCodec.GUARD_PROVENANCE_TAG);
    }

    private static NaturalGuardLoadoutProvenance provenance() {
        return NaturalGuardLoadoutProvenance.issued(
                ResourceLocation.fromNamespaceAndPath("test", "hard"),
                ResourceLocation.fromNamespaceAndPath("tacz", "glock_17"),
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE);
    }
}
