package com.gamergaming.villagerguardgunners.armory;

import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict, versioned codec for an armory's optional squad restriction. */
public final class ArmorySquadLockCodec {
    public static final String LOCK_TAG = "SquadLock";

    private static final String VERSION_TAG = "Version";
    private static final String SQUAD_ID_TAG = "SquadId";
    private static final String DISPLAY_NAME_TAG = "DisplayName";
    private static final Set<String> RECORD_KEYS = Set.of(
            VERSION_TAG,
            SQUAD_ID_TAG,
            DISPLAY_NAME_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private ArmorySquadLockCodec() {
    }

    public static void writeTo(CompoundTag parent, @Nullable ArmorySquadLock lock) {
        Objects.requireNonNull(parent, "parent");
        if (lock == null) {
            parent.remove(LOCK_TAG);
            return;
        }
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, lock.schemaVersion());
        encoded.putUUID(SQUAD_ID_TAG, lock.squadId());
        encoded.putString(DISPLAY_NAME_TAG, lock.displayName());
        parent.put(LOCK_TAG, encoded);
    }

    public static DecodeResult readFrom(@Nullable CompoundTag parent) {
        if (parent == null || !parent.contains(LOCK_TAG)) {
            return DecodeResult.blank();
        }
        if (!parent.contains(LOCK_TAG, Tag.TAG_COMPOUND)) {
            return DecodeResult.corrupt();
        }
        CompoundTag encoded = parent.getCompound(LOCK_TAG);
        if (!encoded.contains(VERSION_TAG, Tag.TAG_INT)) {
            return DecodeResult.corrupt();
        }
        int version = encoded.getInt(VERSION_TAG);
        if (version != ArmorySquadLock.CURRENT_SCHEMA_VERSION) {
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
            return DecodeResult.valid(ArmorySquadLock.assigned(squadId, displayName));
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
            @Nullable ArmorySquadLock lock,
            int storedVersion) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            if ((status == DecodeStatus.VALID) != (lock != null)) {
                throw new IllegalArgumentException("Only a valid decode may contain a lock");
            }
            if (status != DecodeStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported lock may report a stored version");
            }
        }

        private static DecodeResult blank() {
            return new DecodeResult(DecodeStatus.BLANK, null, -1);
        }

        private static DecodeResult valid(ArmorySquadLock lock) {
            return new DecodeResult(DecodeStatus.VALID, lock, -1);
        }

        private static DecodeResult corrupt() {
            return new DecodeResult(DecodeStatus.CORRUPT, null, -1);
        }

        private static DecodeResult unsupported(int version) {
            return new DecodeResult(DecodeStatus.UNSUPPORTED_VERSION, null, version);
        }
    }
}
