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

final class SquadMembershipCodecTest {
    private static final UUID SQUAD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void membershipRoundTripsAndUnassignmentRemovesOnlyItsRecord() {
        SquadMembership membership = SquadMembership.assigned(SQUAD_ID);
        CompoundTag guardTag = new CompoundTag();
        guardTag.putString("OtherMod", "preserved");

        SquadMembershipCodec.writeGuardRecord(guardTag, membership);

        assertAll(
                () -> assertEquals(
                        membership,
                        SquadMembershipCodec.readGuardRecord(guardTag)),
                () -> assertTrue(guardTag.contains(
                        SquadMembershipCodec.GUARD_MEMBERSHIP_TAG,
                        Tag.TAG_COMPOUND)),
                () -> assertEquals("preserved", guardTag.getString("OtherMod")));

        SquadMembershipCodec.writeGuardRecord(guardTag, null);
        assertAll(
                () -> assertFalse(guardTag.contains(
                        SquadMembershipCodec.GUARD_MEMBERSHIP_TAG)),
                () -> assertEquals("preserved", guardTag.getString("OtherMod")));
    }

    @Test
    void missingMalformedFutureAndExtendedRecordsFailClosed() {
        CompoundTag valid = new CompoundTag();
        SquadMembershipCodec.writeGuardRecord(valid, SquadMembership.assigned(SQUAD_ID));

        CompoundTag futureVersion = valid.copy();
        record(futureVersion).putInt(
                "Version",
                SquadMembership.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag wrongUuidType = valid.copy();
        record(wrongUuidType).putString("SquadId", SQUAD_ID.toString());
        CompoundTag nilUuid = valid.copy();
        record(nilUuid).putUUID("SquadId", new UUID(0L, 0L));
        CompoundTag unexpectedField = valid.copy();
        record(unexpectedField).putBoolean("Member", true);
        CompoundTag wrongOuterType = new CompoundTag();
        wrongOuterType.putString(
                SquadMembershipCodec.GUARD_MEMBERSHIP_TAG,
                "not-a-compound");

        assertAll(
                () -> assertNull(SquadMembershipCodec.readGuardRecord(new CompoundTag())),
                () -> assertNull(SquadMembershipCodec.readGuardRecord(futureVersion)),
                () -> assertNull(SquadMembershipCodec.readGuardRecord(wrongUuidType)),
                () -> assertNull(SquadMembershipCodec.readGuardRecord(nilUuid)),
                () -> assertNull(SquadMembershipCodec.readGuardRecord(unexpectedField)),
                () -> assertNull(SquadMembershipCodec.readGuardRecord(wrongOuterType)));
    }

    @Test
    void membershipRejectsInvalidIdentity() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SquadMembership(0, SQUAD_ID)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> SquadMembership.assigned(null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadMembership.assigned(new UUID(0L, 0L))));
    }

    private static CompoundTag record(CompoundTag parent) {
        return parent.getCompound(SquadMembershipCodec.GUARD_MEMBERSHIP_TAG);
    }
}
