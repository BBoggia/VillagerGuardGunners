package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
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
    private static final String DIFFICULTY_TAG = "Difficulty";
    private static final Set<String> REQUIRED_KEYS =
            Set.of(VERSION_TAG, LOADOUT_POOL_TAG, DIFFICULTY_TAG);
    private static final Set<String> ROLE_KEYS =
            Set.of(VERSION_TAG, LOADOUT_POOL_TAG, ROLE_TAG, DIFFICULTY_TAG);

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
        encoded.putString(DIFFICULTY_TAG, assignment.generationDifficulty().name());
        if (assignment.roleId() != null) {
            encoded.putString(ROLE_TAG, assignment.roleId().toString());
        }
        guardTag.put(ASSIGNMENT_TAG, encoded);
    }

    /** Copies any encoded record without interpreting or normalizing it. */
    public static @Nullable Tag copyEncodedRecord(@Nullable CompoundTag guardTag) {
        if (guardTag == null) {
            return null;
        }
        Tag encoded = guardTag.get(ASSIGNMENT_TAG);
        return encoded == null ? null : encoded.copy();
    }

    /** Writes recognized state, otherwise retaining an unreadable/future record exactly. */
    public static void writeGuardRecordPreservingUnreadable(
            CompoundTag guardTag,
            @Nullable GuardPostAssignment assignment,
            @Nullable Tag unreadableRecord) {
        if (assignment != null || unreadableRecord == null) {
            writeGuardRecord(guardTag, assignment);
            return;
        }
        guardTag.put(ASSIGNMENT_TAG, unreadableRecord.copy());
    }

    /** Returns only recognized current work; callers may preserve an unreadable tag opaquely. */
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
                || !encoded.contains(DIFFICULTY_TAG, Tag.TAG_STRING)
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
            NaturalGuardDifficulty difficulty = NaturalGuardDifficulty.valueOf(
                    encoded.getString(DIFFICULTY_TAG));
            return new GuardPostAssignment(
                    GuardPostAssignment.CURRENT_SCHEMA_VERSION,
                    pool,
                    role,
                    difficulty);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable ResourceLocation canonicalId(String encoded) {
        ResourceLocation parsed = ResourceLocation.tryParse(encoded);
        return parsed != null && parsed.toString().equals(encoded) ? parsed : null;
    }
}
