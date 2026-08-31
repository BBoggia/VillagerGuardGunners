package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict codec for one not-yet-consumed post member assignment. */
public final class GuardPostAssignmentCodec {
    public static final String ASSIGNMENT_TAG = "villagerguardgunners:guard_post_assignment";

    private static final String VERSION_TAG = "Version";
    private static final String LOADOUT_POOL_TAG = "LoadoutPool";
    private static final String ROLE_TAG = "Role";
    private static final Set<String> REQUIRED_KEYS = Set.of(VERSION_TAG, LOADOUT_POOL_TAG);
    private static final Set<String> ROLE_KEYS = Set.of(VERSION_TAG, LOADOUT_POOL_TAG, ROLE_TAG);

    private GuardPostAssignmentCodec() {
    }

    public static void writeGuardRecord(
            CompoundTag guardTag,
            @Nullable GuardPostAssignment assignment) {
        if (assignment == null) {
            guardTag.remove(ASSIGNMENT_TAG);
            return;
        }
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, assignment.schemaVersion());
        encoded.putString(LOADOUT_POOL_TAG, assignment.loadoutPool().toString());
        if (assignment.roleId() != null) {
            encoded.putString(ROLE_TAG, assignment.roleId().toString());
        }
        guardTag.put(ASSIGNMENT_TAG, encoded);
    }

    /** Unknown, future, or malformed work is discarded rather than reinterpreted. */
    public static @Nullable GuardPostAssignment readGuardRecord(@Nullable CompoundTag guardTag) {
        if (guardTag == null || !guardTag.contains(ASSIGNMENT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag encoded = guardTag.getCompound(ASSIGNMENT_TAG);
        Set<String> keys = encoded.getAllKeys();
        if ((!keys.equals(REQUIRED_KEYS) && !keys.equals(ROLE_KEYS))
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != GuardPostAssignment.CURRENT_SCHEMA_VERSION
                || !encoded.contains(LOADOUT_POOL_TAG, Tag.TAG_STRING)
                || (encoded.contains(ROLE_TAG) && !encoded.contains(ROLE_TAG, Tag.TAG_STRING))) {
            return null;
        }
        ResourceLocation pool = canonicalId(encoded.getString(LOADOUT_POOL_TAG));
        ResourceLocation role = encoded.contains(ROLE_TAG)
                ? canonicalId(encoded.getString(ROLE_TAG))
                : null;
        if (pool == null || (encoded.contains(ROLE_TAG) && role == null)) {
            return null;
        }
        try {
            return new GuardPostAssignment(
                    GuardPostAssignment.CURRENT_SCHEMA_VERSION,
                    pool,
                    role);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable ResourceLocation canonicalId(String encoded) {
        ResourceLocation parsed = ResourceLocation.tryParse(encoded);
        return parsed != null && parsed.toString().equals(encoded) ? parsed : null;
    }
}
