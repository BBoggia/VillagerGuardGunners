package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict block-entity codec for completed generated Armory setup. */
public final class GuardPostArmoryIdentityCodec {
    public static final String IDENTITY_TAG = "villagerguardgunners:watchtower_armory";

    private static final String VERSION_TAG = "Version";
    private static final String POST_ID_TAG = "PostId";
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final Set<String> EMPTY_KEYS = Set.of(VERSION_TAG, POST_ID_TAG);
    private static final Set<String> LOOT_KEYS = Set.of(VERSION_TAG, POST_ID_TAG, LOOT_TABLE_TAG);

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
        if (identity.lootTableId() != null) {
            encoded.putString(LOOT_TABLE_TAG, identity.lootTableId().toString());
        }
        parent.put(IDENTITY_TAG, encoded);
    }

    public static @Nullable GuardPostArmoryIdentity read(@Nullable CompoundTag parent) {
        if (parent == null || !parent.contains(IDENTITY_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag encoded = parent.getCompound(IDENTITY_TAG);
        Set<String> keys = encoded.getAllKeys();
        if ((!keys.equals(EMPTY_KEYS) && !keys.equals(LOOT_KEYS))
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != GuardPostArmoryIdentity.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(POST_ID_TAG)
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
            return GuardPostArmoryIdentity.initialized(postId, lootTable);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
