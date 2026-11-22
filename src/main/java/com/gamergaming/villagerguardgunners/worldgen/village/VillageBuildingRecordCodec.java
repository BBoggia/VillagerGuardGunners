package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayout;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayoutCodec;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayouts;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** Strict schema-1 codec reusable by SavedData and future generated objects. */
public final class VillageBuildingRecordCodec {
    public static final String BUILDING_TAG = "VggVillageBuilding";

    private static final String VERSION_TAG = "Version";
    private static final String BUILDING_ID_TAG = "BuildingId";
    private static final String ENTRY_ID_TAG = "EntryId";
    private static final String BUILDING_TYPE_TAG = "BuildingType";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String ANCHOR_TAG = "Anchor";
    private static final String CLAIMED_ACTIONS_TAG = "ClaimedActions";
    private static final String PENDING_ACTIVATION_TAG = "PendingActivation";
    private static final String PENDING_VERSION_TAG = "Version";
    private static final String PENDING_ROTATION_TAG = "Rotation";
    private static final String PENDING_DIFFICULTY_TAG = "GenerationDifficulty";
    private static final String PENDING_ACTION_TAG = "RequiredAction";
    private static final String PENDING_DUTY_LAYOUT_TAG = "DutyLayout";
    private static final int LEGACY_PENDING_DATA_VERSION = 1;
    private static final int PENDING_DATA_VERSION = 2;
    private static final Set<String> BASE_RECORD_KEYS = Set.of(
            VERSION_TAG,
            BUILDING_ID_TAG,
            ENTRY_ID_TAG,
            BUILDING_TYPE_TAG,
            DIMENSION_TAG,
            ANCHOR_TAG,
            CLAIMED_ACTIONS_TAG);
    private static final Set<String> PENDING_RECORD_KEYS = Set.of(
            VERSION_TAG,
            BUILDING_ID_TAG,
            ENTRY_ID_TAG,
            BUILDING_TYPE_TAG,
            DIMENSION_TAG,
            ANCHOR_TAG,
            CLAIMED_ACTIONS_TAG,
            PENDING_ACTIVATION_TAG);
    private static final Set<String> LEGACY_PENDING_KEYS = Set.of(
            PENDING_VERSION_TAG,
            PENDING_ROTATION_TAG,
            PENDING_DIFFICULTY_TAG,
            PENDING_ACTION_TAG);
    private static final Set<String> PENDING_KEYS = Set.of(
            PENDING_VERSION_TAG,
            PENDING_ROTATION_TAG,
            PENDING_DIFFICULTY_TAG,
            PENDING_ACTION_TAG,
            PENDING_DUTY_LAYOUT_TAG);

    public static void writeRecord(
            CompoundTag owner,
            @Nullable VillageBuildingRecord record) {
        if (record == null) {
            owner.remove(BUILDING_TAG);
            return;
        }
        owner.put(BUILDING_TAG, encodePayload(record));
    }

    public static @Nullable VillageBuildingRecord readRecord(@Nullable CompoundTag owner) {
        if (owner == null || !owner.contains(BUILDING_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return decodePayload(owner.getCompound(BUILDING_TAG));
    }

    /** Copies an encoded record without interpreting or normalizing it. */
    public static @Nullable Tag copyEncodedRecord(@Nullable CompoundTag owner) {
        if (owner == null) {
            return null;
        }
        Tag encoded = owner.get(BUILDING_TAG);
        return encoded == null ? null : encoded.copy();
    }

    /** Writes recognized state or preserves an unreadable/future record exactly. */
    public static void writeRecordPreservingUnreadable(
            CompoundTag owner,
            @Nullable VillageBuildingRecord record,
            @Nullable Tag unreadableRecord) {
        if (record != null || unreadableRecord == null) {
            writeRecord(owner, record);
            return;
        }
        owner.put(BUILDING_TAG, unreadableRecord.copy());
    }

    static CompoundTag encodeWrapped(VillageBuildingRecord record) {
        CompoundTag wrapper = new CompoundTag();
        writeRecord(wrapper, record);
        return wrapper;
    }

    static @Nullable VillageBuildingRecord decodeWrapped(CompoundTag wrapper) {
        if (!wrapper.getAllKeys().equals(Set.of(BUILDING_TAG))) {
            return null;
        }
        return readRecord(wrapper);
    }

    private static CompoundTag encodePayload(VillageBuildingRecord record) {
        VillageBuildingIdentity identity = record.identity();
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, identity.schemaVersion());
        encoded.putUUID(BUILDING_ID_TAG, identity.buildingId());
        encoded.putString(ENTRY_ID_TAG, identity.entryId().toString());
        encoded.putString(BUILDING_TYPE_TAG, identity.buildingType().serializedName());
        encoded.putString(DIMENSION_TAG, identity.dimensionId().toString());
        encoded.putLong(ANCHOR_TAG, identity.anchorPosition().asLong());
        encoded.putInt(CLAIMED_ACTIONS_TAG, record.claimedActions());
        record.pendingActivation().ifPresent(pending ->
                encoded.put(PENDING_ACTIVATION_TAG, encodePending(pending)));
        return encoded;
    }

    private static @Nullable VillageBuildingRecord decodePayload(CompoundTag encoded) {
        Set<String> keys = encoded.getAllKeys();
        if ((!keys.equals(BASE_RECORD_KEYS) && !keys.equals(PENDING_RECORD_KEYS))
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != VillageBuildingIdentity.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(BUILDING_ID_TAG)
                || !encoded.contains(ENTRY_ID_TAG, Tag.TAG_STRING)
                || !encoded.contains(BUILDING_TYPE_TAG, Tag.TAG_STRING)
                || !encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                || !encoded.contains(ANCHOR_TAG, Tag.TAG_LONG)
                || !encoded.contains(CLAIMED_ACTIONS_TAG, Tag.TAG_INT)) {
            return null;
        }
        ResourceLocation entryId = canonicalId(encoded.getString(ENTRY_ID_TAG));
        ResourceLocation dimensionId = canonicalId(encoded.getString(DIMENSION_TAG));
        VillageBuildingType buildingType = VillageBuildingType.byName(
                encoded.getString(BUILDING_TYPE_TAG)).orElse(null);
        if (entryId == null || dimensionId == null || buildingType == null) {
            return null;
        }
        try {
            UUID buildingId = encoded.getUUID(BUILDING_ID_TAG);
            VillageBuildingIdentity identity = new VillageBuildingIdentity(
                    encoded.getInt(VERSION_TAG),
                    buildingId,
                    entryId,
                    buildingType,
                    dimensionId,
                    BlockPos.of(encoded.getLong(ANCHOR_TAG)));
            Optional<VillageBuildingPendingActivation> pending = Optional.empty();
            if (keys.equals(PENDING_RECORD_KEYS)) {
                if (!encoded.contains(PENDING_ACTIVATION_TAG, Tag.TAG_COMPOUND)) {
                    return null;
                }
                VillageBuildingPendingActivation decodedPending = decodePending(
                        encoded.getCompound(PENDING_ACTIVATION_TAG), identity);
                if (decodedPending == null) {
                    return null;
                }
                pending = Optional.of(decodedPending);
            }
            return new VillageBuildingRecord(
                    identity,
                    encoded.getInt(CLAIMED_ACTIONS_TAG),
                    pending);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static CompoundTag encodePending(
            VillageBuildingPendingActivation pending) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(PENDING_VERSION_TAG, PENDING_DATA_VERSION);
        encoded.putString(PENDING_ROTATION_TAG, pending.rotation().getSerializedName());
        encoded.putString(
                PENDING_DIFFICULTY_TAG,
                pending.generationDifficulty().name().toLowerCase(Locale.ROOT));
        encoded.putString(
                PENDING_ACTION_TAG,
                pending.requiredAction().name().toLowerCase(Locale.ROOT));
        encoded.put(
                PENDING_DUTY_LAYOUT_TAG,
                VillageDefenseDutyLayoutCodec.encode(pending.dutyLayout()));
        return encoded;
    }

    private static @Nullable VillageBuildingPendingActivation decodePending(
            CompoundTag encoded,
            VillageBuildingIdentity identity) {
        if (!encoded.contains(PENDING_VERSION_TAG, Tag.TAG_INT)) {
            return null;
        }
        int version = encoded.getInt(PENDING_VERSION_TAG);
        Set<String> expectedKeys = version == LEGACY_PENDING_DATA_VERSION
                ? LEGACY_PENDING_KEYS
                : version == PENDING_DATA_VERSION ? PENDING_KEYS : Set.of();
        if (expectedKeys.isEmpty()
                || !encoded.getAllKeys().equals(expectedKeys)
                || !encoded.contains(PENDING_ROTATION_TAG, Tag.TAG_STRING)
                || !encoded.contains(PENDING_DIFFICULTY_TAG, Tag.TAG_STRING)
                || !encoded.contains(PENDING_ACTION_TAG, Tag.TAG_STRING)) {
            return null;
        }
        Rotation rotation = Arrays.stream(Rotation.values())
                .filter(value -> value.getSerializedName().equals(
                        encoded.getString(PENDING_ROTATION_TAG)))
                .findFirst()
                .orElse(null);
        NaturalGuardDifficulty difficulty = enumByLowercaseName(
                NaturalGuardDifficulty.values(),
                encoded.getString(PENDING_DIFFICULTY_TAG));
        VillageBuildingAction action = enumByLowercaseName(
                VillageBuildingAction.values(),
                encoded.getString(PENDING_ACTION_TAG));
        if (rotation == null || difficulty == null || action == null) {
            return null;
        }
        try {
            VillageDefenseDutyLayout dutyLayout;
            if (version == LEGACY_PENDING_DATA_VERSION) {
                dutyLayout = VillageDefenseDutyLayouts.forBuilding(identity, rotation);
            } else {
                if (!encoded.contains(PENDING_DUTY_LAYOUT_TAG, Tag.TAG_COMPOUND)) {
                    return null;
                }
                dutyLayout = VillageDefenseDutyLayoutCodec.decode(
                        encoded.getCompound(PENDING_DUTY_LAYOUT_TAG));
                if (dutyLayout == null
                        || !identity.equals(dutyLayout.building())
                        || rotation != dutyLayout.rotation()) {
                    return null;
                }
            }
            VillageBuildingPendingActivation pending =
                    VillageBuildingPendingActivation.forBuilding(
                    identity, rotation, difficulty, dutyLayout);
            return pending.requiredAction() == action ? pending : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static <E extends Enum<E>> @Nullable E enumByLowercaseName(
            E[] values,
            String encoded) {
        return Arrays.stream(values)
                .filter(value -> value.name().toLowerCase(Locale.ROOT).equals(encoded))
                .findFirst()
                .orElse(null);
    }

    private static @Nullable ResourceLocation canonicalId(String encoded) {
        ResourceLocation id = ResourceLocation.tryParse(encoded);
        return id != null && id.toString().equals(encoded) ? id : null;
    }

    private VillageBuildingRecordCodec() {
    }
}
