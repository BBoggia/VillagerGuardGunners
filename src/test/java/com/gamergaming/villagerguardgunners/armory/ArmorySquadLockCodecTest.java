package com.gamergaming.villagerguardgunners.armory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class ArmorySquadLockCodecTest {
    private static final UUID SQUAD_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");

    @Test
    void blankAndValidLocksRoundTripStrictly() {
        CompoundTag parent = new CompoundTag();
        assertEquals(
                ArmorySquadLockCodec.DecodeStatus.BLANK,
                ArmorySquadLockCodec.readFrom(parent).status());

        ArmorySquadLock expected = ArmorySquadLock.assigned(SQUAD_ID, "Alpha");
        ArmorySquadLockCodec.writeTo(parent, expected);
        ArmorySquadLockCodec.DecodeResult decoded =
                ArmorySquadLockCodec.readFrom(parent);
        assertEquals(ArmorySquadLockCodec.DecodeStatus.VALID, decoded.status());
        assertEquals(expected, decoded.lock());

        ArmorySquadLockCodec.writeTo(parent, null);
        assertEquals(
                ArmorySquadLockCodec.DecodeStatus.BLANK,
                ArmorySquadLockCodec.readFrom(parent).status());
    }

    @Test
    void malformedAndFutureLocksFailClosed() {
        CompoundTag malformedParent = new CompoundTag();
        malformedParent.putString(ArmorySquadLockCodec.LOCK_TAG, "not a record");
        ArmorySquadLockCodec.DecodeResult malformed =
                ArmorySquadLockCodec.readFrom(malformedParent);
        assertEquals(ArmorySquadLockCodec.DecodeStatus.CORRUPT, malformed.status());
        assertNull(malformed.lock());

        CompoundTag futureParent = new CompoundTag();
        CompoundTag future = new CompoundTag();
        future.putInt("Version", 99);
        futureParent.put(ArmorySquadLockCodec.LOCK_TAG, future);
        ArmorySquadLockCodec.DecodeResult unsupported =
                ArmorySquadLockCodec.readFrom(futureParent);
        assertEquals(
                ArmorySquadLockCodec.DecodeStatus.UNSUPPORTED_VERSION,
                unsupported.status());
        assertEquals(99, unsupported.storedVersion());
        assertNull(unsupported.lock());
    }
}
