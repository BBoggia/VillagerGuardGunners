package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class SquadLedgerBindingCodecTest {
    private static final UUID SQUAD_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Test
    void bindingRoundTripsWithoutChangingOtherItemData() {
        SquadLedgerBinding binding = SquadLedgerBinding.bound(SQUAD_ID, "Alpha Team");
        CompoundTag itemTag = new CompoundTag();
        itemTag.putString("display", "preserved");

        SquadLedgerBindingCodec.writeTo(itemTag, binding);
        SquadLedgerBindingCodec.DecodeResult decoded =
                SquadLedgerBindingCodec.readFrom(itemTag);

        assertAll(
                () -> assertEquals(
                        SquadLedgerBindingCodec.DecodeStatus.VALID,
                        decoded.status()),
                () -> assertTrue(decoded.isValid()),
                () -> assertEquals(binding, decoded.binding()),
                () -> assertEquals(-1, decoded.storedVersion()),
                () -> assertEquals("preserved", itemTag.getString("display")),
                () -> assertTrue(itemTag.contains(
                        SquadLedgerBindingCodec.BINDING_TAG,
                        Tag.TAG_COMPOUND)));
    }

    @Test
    void malformedExtendedAndWrongTypeBindingsFailClosed() {
        CompoundTag valid = validItemTag();
        CompoundTag missingName = valid.copy();
        record(missingName).remove("DisplayName");
        CompoundTag paddedName = valid.copy();
        record(paddedName).putString("DisplayName", " Alpha Team ");
        CompoundTag nilIdentity = valid.copy();
        record(nilIdentity).putUUID("SquadId", new UUID(0L, 0L));
        CompoundTag wrongIdentityType = valid.copy();
        record(wrongIdentityType).putString("SquadId", SQUAD_ID.toString());
        CompoundTag extended = valid.copy();
        record(extended).putBoolean("Member", true);
        CompoundTag wrongOuterType = new CompoundTag();
        wrongOuterType.putString(
                SquadLedgerBindingCodec.BINDING_TAG,
                "not-a-compound");

        assertAll(
                () -> assertEquals(
                        SquadLedgerBindingCodec.DecodeStatus.BLANK,
                        SquadLedgerBindingCodec.readFrom(null).status()),
                () -> assertEquals(
                        SquadLedgerBindingCodec.DecodeStatus.BLANK,
                        SquadLedgerBindingCodec.readFrom(new CompoundTag()).status()),
                () -> assertCorrupt(missingName),
                () -> assertCorrupt(paddedName),
                () -> assertCorrupt(nilIdentity),
                () -> assertCorrupt(wrongIdentityType),
                () -> assertCorrupt(extended),
                () -> assertCorrupt(wrongOuterType));
    }

    @Test
    void futureAndLegacyVersionsRemainDistinguishableWithoutDecoding() {
        CompoundTag future = validItemTag();
        record(future).putInt("Version", SquadLedgerBindingCodec.DATA_VERSION + 1);
        CompoundTag legacy = validItemTag();
        record(legacy).putInt("Version", 0);

        SquadLedgerBindingCodec.DecodeResult futureResult =
                SquadLedgerBindingCodec.readFrom(future);
        SquadLedgerBindingCodec.DecodeResult legacyResult =
                SquadLedgerBindingCodec.readFrom(legacy);

        assertAll(
                () -> assertEquals(
                        SquadLedgerBindingCodec.DecodeStatus.UNSUPPORTED_VERSION,
                        futureResult.status()),
                () -> assertEquals(
                        SquadLedgerBindingCodec.DATA_VERSION + 1,
                        futureResult.storedVersion()),
                () -> assertNull(futureResult.binding()),
                () -> assertEquals(
                        SquadLedgerBindingCodec.DecodeStatus.UNSUPPORTED_VERSION,
                        legacyResult.status()),
                () -> assertEquals(0, legacyResult.storedVersion()),
                () -> assertFalse(legacyResult.isValid()));
    }

    @Test
    void valueObjectsRejectInvalidState() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SquadLedgerBinding(0, SQUAD_ID, "Alpha Team")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadLedgerBinding.bound(new UUID(0L, 0L), "Alpha Team")),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> SquadLedgerBinding.bound(null, "Alpha Team")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadLedgerBinding.bound(SQUAD_ID, " Alpha Team ")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SquadLedgerBindingCodec.DecodeResult(
                                SquadLedgerBindingCodec.DecodeStatus.VALID,
                                null,
                                -1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SquadLedgerBindingCodec.DecodeResult(
                                SquadLedgerBindingCodec.DecodeStatus.CORRUPT,
                                null,
                                1)));
    }

    private static CompoundTag validItemTag() {
        CompoundTag itemTag = new CompoundTag();
        SquadLedgerBindingCodec.writeTo(
                itemTag,
                SquadLedgerBinding.bound(SQUAD_ID, "Alpha Team"));
        return itemTag;
    }

    private static CompoundTag record(CompoundTag itemTag) {
        return itemTag.getCompound(SquadLedgerBindingCodec.BINDING_TAG);
    }

    private static void assertCorrupt(CompoundTag itemTag) {
        SquadLedgerBindingCodec.DecodeResult result =
                SquadLedgerBindingCodec.readFrom(itemTag);
        assertEquals(SquadLedgerBindingCodec.DecodeStatus.CORRUPT, result.status());
        assertNull(result.binding());
        assertEquals(-1, result.storedVersion());
    }
}
