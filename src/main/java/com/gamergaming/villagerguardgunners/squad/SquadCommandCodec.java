package com.gamergaming.villagerguardgunners.squad;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict, fail-closed guard-NBT codec for one temporary squad command. */
public final class SquadCommandCodec {
    public static final String GUARD_COMMAND_TAG =
            "villagerguardgunners:squad_command";

    private static final String VERSION_TAG = "Version";
    private static final String TYPE_TAG = "Type";
    private static final String ISSUED_BY_TAG = "IssuedBy";
    private static final String ISSUED_GAME_TIME_TAG = "IssuedGameTime";
    private static final String EXPIRES_GAME_TIME_TAG = "ExpiresGameTime";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String POSITION_TAG = "Position";
    private static final String RADIUS_TAG = "Radius";
    private static final String TARGET_TAG = "Target";
    private static final Set<String> POSITION_KEYS = Set.of(
            VERSION_TAG,
            TYPE_TAG,
            ISSUED_BY_TAG,
            ISSUED_GAME_TIME_TAG,
            EXPIRES_GAME_TIME_TAG,
            DIMENSION_TAG,
            POSITION_TAG);
    private static final Set<String> DEFEND_KEYS = Set.of(
            VERSION_TAG,
            TYPE_TAG,
            ISSUED_BY_TAG,
            ISSUED_GAME_TIME_TAG,
            EXPIRES_GAME_TIME_TAG,
            DIMENSION_TAG,
            POSITION_TAG,
            RADIUS_TAG);
    private static final Set<String> FOCUS_KEYS = Set.of(
            VERSION_TAG,
            TYPE_TAG,
            ISSUED_BY_TAG,
            ISSUED_GAME_TIME_TAG,
            EXPIRES_GAME_TIME_TAG,
            DIMENSION_TAG,
            TARGET_TAG);

    private SquadCommandCodec() {
    }

    public static void writeGuardRecord(
            CompoundTag guardTag,
            @Nullable SquadCommand command) {
        if (command == null) {
            guardTag.remove(GUARD_COMMAND_TAG);
            return;
        }

        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, command.schemaVersion());
        encoded.putString(TYPE_TAG, command.type().name());
        encoded.putUUID(ISSUED_BY_TAG, command.issuedBy());
        encoded.putLong(ISSUED_GAME_TIME_TAG, command.issuedGameTime());
        encoded.putLong(EXPIRES_GAME_TIME_TAG, command.expiresGameTime());
        encoded.putString(DIMENSION_TAG, command.dimension().toString());
        if (command.position() != null) {
            encoded.putLong(POSITION_TAG, command.position().asLong());
        }
        if (command.type() == SquadCommandType.DEFEND) {
            encoded.putDouble(RADIUS_TAG, command.radius());
        }
        if (command.targetId() != null) {
            encoded.putUUID(TARGET_TAG, command.targetId());
        }
        guardTag.put(GUARD_COMMAND_TAG, encoded);
    }

    /** Unknown, future, malformed, or internally inconsistent data is ignored. */
    @Nullable
    public static SquadCommand readGuardRecord(@Nullable CompoundTag guardTag) {
        if (guardTag == null
                || !guardTag.contains(GUARD_COMMAND_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag encoded = guardTag.getCompound(GUARD_COMMAND_TAG);
        if (!encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != SquadCommand.CURRENT_SCHEMA_VERSION
                || !encoded.contains(TYPE_TAG, Tag.TAG_STRING)
                || !encoded.hasUUID(ISSUED_BY_TAG)
                || !encoded.contains(ISSUED_GAME_TIME_TAG, Tag.TAG_LONG)
                || !encoded.contains(EXPIRES_GAME_TIME_TAG, Tag.TAG_LONG)
                || !encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)) {
            return null;
        }

        try {
            SquadCommandType type = SquadCommandType.valueOf(
                    encoded.getString(TYPE_TAG));
            if (!type.isStoredCommand()
                    || !encoded.getAllKeys().equals(keysFor(type))) {
                return null;
            }
            ResourceLocation dimension = ResourceLocation.tryParse(
                    encoded.getString(DIMENSION_TAG));
            if (dimension == null) {
                return null;
            }
            UUID issuedBy = encoded.getUUID(ISSUED_BY_TAG);
            long issuedGameTime = encoded.getLong(ISSUED_GAME_TIME_TAG);
            long expiresGameTime = encoded.getLong(EXPIRES_GAME_TIME_TAG);
            if (type == SquadCommandType.FOCUS_TARGET) {
                return encoded.hasUUID(TARGET_TAG)
                        ? SquadCommand.focusTarget(
                                issuedBy,
                                issuedGameTime,
                                expiresGameTime,
                                dimension,
                                encoded.getUUID(TARGET_TAG))
                        : null;
            }
            if (!encoded.contains(POSITION_TAG, Tag.TAG_LONG)) {
                return null;
            }
            if (type == SquadCommandType.DEFEND
                    && !encoded.contains(RADIUS_TAG, Tag.TAG_DOUBLE)) {
                return null;
            }
            double radius = type == SquadCommandType.DEFEND
                    ? encoded.getDouble(RADIUS_TAG)
                    : 0.0D;
            return SquadCommand.positional(
                    type,
                    issuedBy,
                    issuedGameTime,
                    expiresGameTime,
                    dimension,
                    BlockPos.of(encoded.getLong(POSITION_TAG)),
                    radius);
        } catch (IllegalArgumentException | ClassCastException exception) {
            return null;
        }
    }

    private static Set<String> keysFor(SquadCommandType type) {
        return switch (type) {
            case RALLY, HOLD, FALL_BACK -> POSITION_KEYS;
            case DEFEND -> DEFEND_KEYS;
            case FOCUS_TARGET -> FOCUS_KEYS;
            case RESUME_ORDERS -> Set.of();
        };
    }
}
