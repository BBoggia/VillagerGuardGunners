package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict, fail-closed codec for a generated guard's post provenance. */
public final class GuardPostIdentityCodec {
    public static final String GUARD_POST_TAG = "villagerguardgunners:guard_post";

    private static final String VERSION_TAG = "Version";
    private static final String POST_ID_TAG = "PostId";
    private static final String GROUP_ID_TAG = "Group";
    private static final String VARIANT_TAG = "Variant";
    private static final String MEMBER_INDEX_TAG = "Member";
    private static final Set<String> RECORD_KEYS = Set.of(
            VERSION_TAG,
            POST_ID_TAG,
            GROUP_ID_TAG,
            VARIANT_TAG,
            MEMBER_INDEX_TAG);

    private GuardPostIdentityCodec() {
    }

    public static void writeGuardRecord(
            CompoundTag guardTag,
            @Nullable GuardPostIdentity identity) {
        if (identity == null) {
            guardTag.remove(GUARD_POST_TAG);
            return;
        }
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, identity.schemaVersion());
        encoded.putUUID(POST_ID_TAG, identity.postId());
        encoded.putString(GROUP_ID_TAG, identity.groupId().toString());
        encoded.putString(VARIANT_TAG, identity.variantName());
        encoded.putInt(MEMBER_INDEX_TAG, identity.memberIndex());
        guardTag.put(GUARD_POST_TAG, encoded);
    }

    /** Unknown, future, or malformed records recover as an unassociated guard. */
    public static @Nullable GuardPostIdentity readGuardRecord(CompoundTag guardTag) {
        if (guardTag == null || !guardTag.contains(GUARD_POST_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag encoded = guardTag.getCompound(GUARD_POST_TAG);
        if (!encoded.getAllKeys().equals(RECORD_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != GuardPostIdentity.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(POST_ID_TAG)
                || !encoded.contains(GROUP_ID_TAG, Tag.TAG_STRING)
                || !encoded.contains(VARIANT_TAG, Tag.TAG_STRING)
                || !encoded.contains(MEMBER_INDEX_TAG, Tag.TAG_INT)) {
            return null;
        }
        String encodedGroup = encoded.getString(GROUP_ID_TAG);
        ResourceLocation groupId = ResourceLocation.tryParse(encodedGroup);
        if (groupId == null || !groupId.toString().equals(encodedGroup)) {
            return null;
        }
        try {
            UUID postId = encoded.getUUID(POST_ID_TAG);
            return GuardPostIdentity.assigned(
                    postId,
                    groupId,
                    encoded.getString(VARIANT_TAG),
                    encoded.getInt(MEMBER_INDEX_TAG));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
