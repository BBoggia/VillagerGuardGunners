package com.gamergaming.villagerguardgunners.squad;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict, fail-closed codec for the squad identity stored on a guard. */
public final class SquadMembershipCodec {
    public static final String GUARD_MEMBERSHIP_TAG =
            "villagerguardgunners:squad_membership";

    private static final String VERSION_TAG = "Version";
    private static final String SQUAD_ID_TAG = "SquadId";
    private static final Set<String> RECORD_KEYS = Set.of(VERSION_TAG, SQUAD_ID_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private SquadMembershipCodec() {
    }

    /** Writes only this add-on's identity record and removes it when unassigned. */
    public static void writeGuardRecord(
            CompoundTag guardTag,
            @Nullable SquadMembership membership) {
        if (membership == null) {
            guardTag.remove(GUARD_MEMBERSHIP_TAG);
            return;
        }

        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, membership.schemaVersion());
        encoded.putUUID(SQUAD_ID_TAG, membership.squadId());
        guardTag.put(GUARD_MEMBERSHIP_TAG, encoded);
    }

    /** Unknown, future, or malformed records recover as an unassigned guard. */
    @Nullable
    public static SquadMembership readGuardRecord(CompoundTag guardTag) {
        if (guardTag == null
                || !guardTag.contains(GUARD_MEMBERSHIP_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag encoded = guardTag.getCompound(GUARD_MEMBERSHIP_TAG);
        if (!encoded.getAllKeys().equals(RECORD_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != SquadMembership.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(SQUAD_ID_TAG)) {
            return null;
        }

        try {
            UUID squadId = encoded.getUUID(SQUAD_ID_TAG);
            return NIL_UUID.equals(squadId) ? null : SquadMembership.assigned(squadId);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
