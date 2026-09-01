package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict block-entity codec for pending or completed generated Armory setup. */
public final class GuardPostArmoryIdentityCodec {
    public static final String IDENTITY_TAG = "villagerguardgunners:watchtower_armory";

    private static final String VERSION_TAG = "Version";
    private static final String POST_ID_TAG = "PostId";
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final String STATE_TAG = "State";
    private static final String LOOT_SEED_TAG = "LootSeed";
    private static final Set<String> LEGACY_EMPTY_KEYS = Set.of(VERSION_TAG, POST_ID_TAG);
    private static final Set<String> LEGACY_LOOT_KEYS =
            Set.of(VERSION_TAG, POST_ID_TAG, LOOT_TABLE_TAG);
    private static final Set<String> CURRENT_EMPTY_KEYS =
            Set.of(VERSION_TAG, POST_ID_TAG, STATE_TAG, LOOT_SEED_TAG);
    private static final Set<String> CURRENT_LOOT_KEYS =
            Set.of(VERSION_TAG, POST_ID_TAG, LOOT_TABLE_TAG, STATE_TAG, LOOT_SEED_TAG);

    private GuardPostArmoryIdentityCodec() {
    }

    public static void write(
            CompoundTag parent,
            @Nullable GuardPostArmoryIdentity identity) {
        if (identity == null) {
            parent.remove(IDENTITY_TAG);
            return;
        }
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, identity.schemaVersion());
        encoded.putUUID(POST_ID_TAG, identity.postId());
        encoded.putString(STATE_TAG, identity.state().name());
        encoded.putLong(LOOT_SEED_TAG, identity.lootSeed());
        if (identity.lootTableId() != null) {
            encoded.putString(LOOT_TABLE_TAG, identity.lootTableId().toString());
        }
        parent.put(IDENTITY_TAG, encoded);
    }

    /** Copies any encoded record without interpreting or normalizing it. */
    public static @Nullable Tag copyEncodedRecord(@Nullable CompoundTag parent) {
        if (parent == null) {
            return null;
        }
        Tag encoded = parent.get(IDENTITY_TAG);
        return encoded == null ? null : encoded.copy();
    }

    /** Writes recognized state, otherwise retaining an unreadable/future record exactly. */
    public static void writePreservingUnreadable(
            CompoundTag parent,
            @Nullable GuardPostArmoryIdentity identity,
            @Nullable Tag unreadableRecord) {
        if (identity != null || unreadableRecord == null) {
            write(parent, identity);
            return;
        }
        parent.put(IDENTITY_TAG, unreadableRecord.copy());
    }

    public static @Nullable GuardPostArmoryIdentity read(@Nullable CompoundTag parent) {
        if (parent == null || !parent.contains(IDENTITY_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag encoded = parent.getCompound(IDENTITY_TAG);
        Set<String> keys = encoded.getAllKeys();
        if (!encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || !encoded.hasUUID(POST_ID_TAG)) {
            return null;
        }
        int version = encoded.getInt(VERSION_TAG);
        if (version == 1) {
            return readLegacy(encoded, keys);
        }
        if (version != GuardPostArmoryIdentity.CURRENT_SCHEMA_VERSION
                || (!keys.equals(CURRENT_EMPTY_KEYS) && !keys.equals(CURRENT_LOOT_KEYS))
                || !encoded.contains(STATE_TAG, Tag.TAG_STRING)
                || !encoded.contains(LOOT_SEED_TAG, Tag.TAG_LONG)
                || (encoded.contains(LOOT_TABLE_TAG)
                        && !encoded.contains(LOOT_TABLE_TAG, Tag.TAG_STRING))) {
            return null;
        }
        ResourceLocation lootTable = null;
        if (encoded.contains(LOOT_TABLE_TAG)) {
            String raw = encoded.getString(LOOT_TABLE_TAG);
            lootTable = ResourceLocation.tryParse(raw);
            if (lootTable == null || !lootTable.toString().equals(raw)) {
                return null;
            }
        }
        try {
            UUID postId = encoded.getUUID(POST_ID_TAG);
            GuardPostArmoryIdentity.State state =
                    GuardPostArmoryIdentity.State.valueOf(encoded.getString(STATE_TAG));
            return new GuardPostArmoryIdentity(
                    GuardPostArmoryIdentity.CURRENT_SCHEMA_VERSION,
                    postId,
                    lootTable,
                    state,
                    encoded.getLong(LOOT_SEED_TAG));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable GuardPostArmoryIdentity readLegacy(
            CompoundTag encoded,
            Set<String> keys) {
        if ((!keys.equals(LEGACY_EMPTY_KEYS) && !keys.equals(LEGACY_LOOT_KEYS))
                || (encoded.contains(LOOT_TABLE_TAG)
                        && !encoded.contains(LOOT_TABLE_TAG, Tag.TAG_STRING))) {
            return null;
        }
        ResourceLocation lootTable = null;
        if (encoded.contains(LOOT_TABLE_TAG)) {
            String raw = encoded.getString(LOOT_TABLE_TAG);
            lootTable = ResourceLocation.tryParse(raw);
            if (lootTable == null || !lootTable.toString().equals(raw)) {
                return null;
            }
        }
        try {
            return GuardPostArmoryIdentity.initialized(
                    encoded.getUUID(POST_ID_TAG),
                    lootTable);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
