package com.gamergaming.villagerguardgunners.squad;

import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict item-NBT codec for a ledger's currently selected command mode. */
public final class SquadLedgerModeCodec {
    public static final String MODE_TAG =
            "villagerguardgunners:squad_command_mode";
    public static final int DATA_VERSION = 1;
    public static final SquadCommandType DEFAULT_MODE = SquadCommandType.RALLY;

    private static final String VERSION_TAG = "Version";
    private static final String MODE_VALUE_TAG = "Mode";
    private static final Set<String> RECORD_KEYS = Set.of(VERSION_TAG, MODE_VALUE_TAG);

    private SquadLedgerModeCodec() {
    }

    public static void writeTo(CompoundTag itemTag, SquadCommandType mode) {
        Objects.requireNonNull(itemTag, "itemTag");
        Objects.requireNonNull(mode, "mode");
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, DATA_VERSION);
        encoded.putString(MODE_VALUE_TAG, mode.name());
        itemTag.put(MODE_TAG, encoded);
    }

    public static DecodeResult readFrom(@Nullable CompoundTag itemTag) {
        if (itemTag == null || !itemTag.contains(MODE_TAG)) {
            return DecodeResult.blank();
        }
        if (!itemTag.contains(MODE_TAG, Tag.TAG_COMPOUND)) {
            return DecodeResult.corrupt();
        }
        CompoundTag encoded = itemTag.getCompound(MODE_TAG);
        if (!encoded.contains(VERSION_TAG, Tag.TAG_INT)) {
            return DecodeResult.corrupt();
        }
        int version = encoded.getInt(VERSION_TAG);
        if (version != DATA_VERSION) {
            return DecodeResult.unsupported(version);
        }
        if (!encoded.getAllKeys().equals(RECORD_KEYS)
                || !encoded.contains(MODE_VALUE_TAG, Tag.TAG_STRING)) {
            return DecodeResult.corrupt();
        }
        try {
            return DecodeResult.valid(SquadCommandType.valueOf(
                    encoded.getString(MODE_VALUE_TAG)));
        } catch (IllegalArgumentException exception) {
            return DecodeResult.corrupt();
        }
    }

    public enum DecodeStatus {
        BLANK,
        VALID,
        CORRUPT,
        UNSUPPORTED_VERSION
    }

    public record DecodeResult(
            DecodeStatus status,
            @Nullable SquadCommandType mode,
            int storedVersion) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            if ((status == DecodeStatus.VALID) != (mode != null)) {
                throw new IllegalArgumentException(
                        "Only a valid ledger mode may contain a command type");
            }
            if (status != DecodeStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported ledger mode may report a stored version");
            }
        }

        public SquadCommandType modeOrDefault() {
            return switch (status) {
                case BLANK -> DEFAULT_MODE;
                case VALID -> Objects.requireNonNull(mode, "mode");
                case CORRUPT, UNSUPPORTED_VERSION -> throw new IllegalStateException(
                        "A malformed or unsupported ledger mode has no usable value");
            };
        }

        private static DecodeResult blank() {
            return new DecodeResult(DecodeStatus.BLANK, null, -1);
        }

        private static DecodeResult valid(SquadCommandType mode) {
            return new DecodeResult(DecodeStatus.VALID, mode, -1);
        }

        private static DecodeResult corrupt() {
            return new DecodeResult(DecodeStatus.CORRUPT, null, -1);
        }

        private static DecodeResult unsupported(int storedVersion) {
            return new DecodeResult(DecodeStatus.UNSUPPORTED_VERSION, null, storedVersion);
        }
    }
}
