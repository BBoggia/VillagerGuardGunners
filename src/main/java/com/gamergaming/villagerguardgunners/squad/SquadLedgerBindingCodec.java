package com.gamergaming.villagerguardgunners.squad;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict item-NBT codec for a Squad Command Ledger's stable binding. */
public final class SquadLedgerBindingCodec {
    public static final String BINDING_TAG =
            "villagerguardgunners:squad_command_ledger";
    public static final int DATA_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String SQUAD_ID_TAG = "SquadId";
    private static final String DISPLAY_NAME_TAG = "DisplayName";
    private static final Set<String> RECORD_KEYS = Set.of(
            VERSION_TAG,
            SQUAD_ID_TAG,
            DISPLAY_NAME_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private SquadLedgerBindingCodec() {
    }

    /** Replaces only this add-on's binding payload. */
    public static void writeTo(CompoundTag itemTag, SquadLedgerBinding binding) {
        Objects.requireNonNull(itemTag, "itemTag");
        Objects.requireNonNull(binding, "binding");

        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, binding.schemaVersion());
        encoded.putUUID(SQUAD_ID_TAG, binding.squadId());
        encoded.putString(DISPLAY_NAME_TAG, binding.cachedDisplayName());
        itemTag.put(BINDING_TAG, encoded);
    }

    public static DecodeResult readFrom(@Nullable CompoundTag itemTag) {
        if (itemTag == null || !itemTag.contains(BINDING_TAG)) {
            return DecodeResult.blank();
        }
        if (!itemTag.contains(BINDING_TAG, Tag.TAG_COMPOUND)) {
            return DecodeResult.corrupt();
        }

        CompoundTag encoded = itemTag.getCompound(BINDING_TAG);
        if (!encoded.contains(VERSION_TAG, Tag.TAG_INT)) {
            return DecodeResult.corrupt();
        }
        int version = encoded.getInt(VERSION_TAG);
        if (version != DATA_VERSION) {
            return DecodeResult.unsupported(version);
        }
        if (!encoded.getAllKeys().equals(RECORD_KEYS)
                || !encoded.hasUUID(SQUAD_ID_TAG)
                || !encoded.contains(DISPLAY_NAME_TAG, Tag.TAG_STRING)) {
            return DecodeResult.corrupt();
        }

        try {
            UUID squadId = encoded.getUUID(SQUAD_ID_TAG);
            String displayName = encoded.getString(DISPLAY_NAME_TAG);
            if (NIL_UUID.equals(squadId)
                    || !SquadMetadata.isCanonicalDisplayName(displayName)) {
                return DecodeResult.corrupt();
            }
            return DecodeResult.valid(SquadLedgerBinding.bound(squadId, displayName));
        } catch (RuntimeException exception) {
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
            @Nullable SquadLedgerBinding binding,
            int storedVersion) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            if ((status == DecodeStatus.VALID) != (binding != null)) {
                throw new IllegalArgumentException(
                        "Only a valid ledger may contain a squad binding");
            }
            if (status != DecodeStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported ledger may report a stored version");
            }
        }

        public boolean isValid() {
            return status == DecodeStatus.VALID;
        }

        private static DecodeResult blank() {
            return new DecodeResult(DecodeStatus.BLANK, null, -1);
        }

        private static DecodeResult valid(SquadLedgerBinding binding) {
            return new DecodeResult(DecodeStatus.VALID, binding, -1);
        }

        private static DecodeResult corrupt() {
            return new DecodeResult(DecodeStatus.CORRUPT, null, -1);
        }

        private static DecodeResult unsupported(int storedVersion) {
            return new DecodeResult(
                    DecodeStatus.UNSUPPORTED_VERSION,
                    null,
                    storedVersion);
        }
    }
}
