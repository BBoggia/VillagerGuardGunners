package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SquadCommandCodecTest {
    private static final UUID ISSUER =
            UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID TARGET =
            UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void everyStoredCommandTypeRoundTripsWithoutChangingUnrelatedGuardData() {
        List<SquadCommand> commands = List.of(
                positional(SquadCommandType.RALLY, 0.0D),
                positional(SquadCommandType.DEFEND, 12.0D),
                positional(SquadCommandType.HOLD, 0.0D),
                SquadCommand.focusTarget(ISSUER, 100L, 200L, DIMENSION, TARGET),
                positional(SquadCommandType.FALL_BACK, 0.0D));

        for (SquadCommand command : commands) {
            CompoundTag guardTag = new CompoundTag();
            guardTag.putString("OtherMod", "preserved");
            SquadCommandCodec.writeGuardRecord(guardTag, command);

            assertAll(
                    () -> assertEquals(
                            command,
                            SquadCommandCodec.readGuardRecord(guardTag)),
                    () -> assertTrue(guardTag.contains(
                            SquadCommandCodec.GUARD_COMMAND_TAG,
                            Tag.TAG_COMPOUND)),
                    () -> assertEquals("preserved", guardTag.getString("OtherMod")));

            SquadCommandCodec.writeGuardRecord(guardTag, null);
            assertAll(
                    () -> assertFalse(guardTag.contains(
                            SquadCommandCodec.GUARD_COMMAND_TAG)),
                    () -> assertEquals("preserved", guardTag.getString("OtherMod")));
        }
    }

    @Test
    void malformedFutureExtendedAndContradictoryRecordsFailClosed() {
        CompoundTag valid = encoded(positional(SquadCommandType.RALLY, 0.0D));
        CompoundTag future = valid.copy();
        commandTag(future).putInt("Version", SquadCommand.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag resume = valid.copy();
        commandTag(resume).putString("Type", SquadCommandType.RESUME_ORDERS.name());
        CompoundTag badDimension = valid.copy();
        commandTag(badDimension).putString("Dimension", "not a resource id");
        CompoundTag wrongPositionType = valid.copy();
        commandTag(wrongPositionType).putString("Position", "0,64,0");
        CompoundTag unexpectedRadius = valid.copy();
        commandTag(unexpectedRadius).putDouble("Radius", 2.0D);
        CompoundTag validDefend = encoded(positional(SquadCommandType.DEFEND, 12.0D));
        CompoundTag wrongDefendRadiusType = validDefend.copy();
        commandTag(wrongDefendRadiusType).putInt("Radius", 12);
        CompoundTag extended = valid.copy();
        commandTag(extended).putBoolean("Permanent", true);
        CompoundTag nilIssuer = valid.copy();
        commandTag(nilIssuer).putUUID("IssuedBy", new UUID(0L, 0L));
        CompoundTag wrongOuterType = new CompoundTag();
        wrongOuterType.putString(
                SquadCommandCodec.GUARD_COMMAND_TAG,
                "not-a-compound");

        assertAll(
                () -> assertNull(SquadCommandCodec.readGuardRecord(null)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(new CompoundTag())),
                () -> assertNull(SquadCommandCodec.readGuardRecord(future)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(resume)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(badDimension)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(wrongPositionType)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(unexpectedRadius)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(wrongDefendRadiusType)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(extended)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(nilIssuer)),
                () -> assertNull(SquadCommandCodec.readGuardRecord(wrongOuterType)));
    }

    @Test
    void commandRecordRejectsMismatchedPayloadsAndInvalidLifetimes() {
        BlockPos position = new BlockPos(1, 64, 2);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadCommand.positional(
                                SquadCommandType.FOCUS_TARGET,
                                ISSUER,
                                100L,
                                200L,
                                DIMENSION,
                                position,
                                0.0D)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadCommand.positional(
                                SquadCommandType.RESUME_ORDERS,
                                ISSUER,
                                100L,
                                200L,
                                DIMENSION,
                                position,
                                0.0D)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadCommand.positional(
                                SquadCommandType.DEFEND,
                                ISSUER,
                                100L,
                                200L,
                                DIMENSION,
                                position,
                                0.0D)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadCommand.positional(
                                SquadCommandType.RALLY,
                                ISSUER,
                                100L,
                                200L,
                                DIMENSION,
                                position,
                                1.0D)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadCommand.focusTarget(
                                ISSUER,
                                100L,
                                100L,
                                DIMENSION,
                                TARGET)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SquadCommand.focusTarget(
                                ISSUER,
                                100L,
                                200L,
                                DIMENSION,
                                new UUID(0L, 0L))));
    }

    @Test
    void expirationBoundaryIsInclusive() {
        SquadCommand command = positional(SquadCommandType.RALLY, 0.0D);

        assertAll(
                () -> assertFalse(command.isExpired(199L)),
                () -> assertTrue(command.isExpired(200L)));
    }

    private static SquadCommand positional(SquadCommandType type, double radius) {
        return SquadCommand.positional(
                type,
                ISSUER,
                100L,
                200L,
                DIMENSION,
                new BlockPos(1, 64, 2),
                radius);
    }

    private static CompoundTag encoded(SquadCommand command) {
        CompoundTag guardTag = new CompoundTag();
        SquadCommandCodec.writeGuardRecord(guardTag, command);
        return guardTag;
    }

    private static CompoundTag commandTag(CompoundTag guardTag) {
        return guardTag.getCompound(SquadCommandCodec.GUARD_COMMAND_TAG);
    }
}
