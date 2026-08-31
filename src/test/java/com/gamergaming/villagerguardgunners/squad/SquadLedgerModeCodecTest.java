package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SquadLedgerModeCodecTest {

    @Test
    void everyModeRoundTripsAndCyclesInThePublishedOrder() {
        for (SquadCommandType mode : SquadCommandType.values()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("OtherMod", "preserved");
            SquadLedgerModeCodec.writeTo(itemTag, mode);
            SquadLedgerModeCodec.DecodeResult decoded =
                    SquadLedgerModeCodec.readFrom(itemTag);

            assertAll(
                    () -> assertEquals(
                            SquadLedgerModeCodec.DecodeStatus.VALID,
                            decoded.status()),
                    () -> assertEquals(mode, decoded.mode()),
                    () -> assertEquals("preserved", itemTag.getString("OtherMod")));
        }

        assertEquals(
                List.of(
                        SquadCommandType.RALLY,
                        SquadCommandType.DEFEND,
                        SquadCommandType.HOLD,
                        SquadCommandType.FOCUS_TARGET,
                        SquadCommandType.FALL_BACK,
                        SquadCommandType.RESUME_ORDERS,
                        SquadCommandType.RALLY),
                List.of(
                        SquadCommandType.RALLY,
                        SquadCommandType.RALLY.next(),
                        SquadCommandType.DEFEND.next(),
                        SquadCommandType.HOLD.next(),
                        SquadCommandType.FOCUS_TARGET.next(),
                        SquadCommandType.FALL_BACK.next(),
                        SquadCommandType.RESUME_ORDERS.next()));
    }

    @Test
    void blankMalformedAndFutureModeDataRemainDistinguishable() {
        CompoundTag valid = new CompoundTag();
        SquadLedgerModeCodec.writeTo(valid, SquadCommandType.HOLD);
        CompoundTag future = valid.copy();
        modeTag(future).putInt("Version", SquadLedgerModeCodec.DATA_VERSION + 1);
        CompoundTag unknown = valid.copy();
        modeTag(unknown).putString("Mode", "CHARGE");
        CompoundTag extended = valid.copy();
        modeTag(extended).putBoolean("Permanent", true);
        CompoundTag wrongOuter = new CompoundTag();
        wrongOuter.putString(SquadLedgerModeCodec.MODE_TAG, "HOLD");

        SquadLedgerModeCodec.DecodeResult blank =
                SquadLedgerModeCodec.readFrom(new CompoundTag());
        SquadLedgerModeCodec.DecodeResult unsupported =
                SquadLedgerModeCodec.readFrom(future);
        SquadLedgerModeCodec.DecodeResult corrupt =
                SquadLedgerModeCodec.readFrom(unknown);
        assertAll(
                () -> assertEquals(
                        SquadLedgerModeCodec.DecodeStatus.BLANK,
                        blank.status()),
                () -> assertEquals(
                        SquadLedgerModeCodec.DEFAULT_MODE,
                        blank.modeOrDefault()),
                () -> assertEquals(
                        SquadLedgerModeCodec.DecodeStatus.UNSUPPORTED_VERSION,
                        unsupported.status()),
                () -> assertEquals(
                        SquadLedgerModeCodec.DATA_VERSION + 1,
                        unsupported.storedVersion()),
                () -> assertNull(unsupported.mode()),
                () -> assertThrows(
                        IllegalStateException.class,
                        unsupported::modeOrDefault),
                () -> assertEquals(
                        SquadLedgerModeCodec.DecodeStatus.CORRUPT,
                        corrupt.status()),
                () -> assertThrows(
                        IllegalStateException.class,
                        corrupt::modeOrDefault),
                () -> assertEquals(
                        SquadLedgerModeCodec.DecodeStatus.CORRUPT,
                        SquadLedgerModeCodec.readFrom(extended).status()),
                () -> assertEquals(
                        SquadLedgerModeCodec.DecodeStatus.CORRUPT,
                        SquadLedgerModeCodec.readFrom(wrongOuter).status()));
    }

    private static CompoundTag modeTag(CompoundTag itemTag) {
        return itemTag.getCompound(SquadLedgerModeCodec.MODE_TAG);
    }
}
