package com.gamergaming.villagerguardgunners.defense;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict versioned NBT codec for a network's defense status. */
public final class VillageDefenseStatusCodec {
    private static final String VERSION_TAG = "Version";
    private static final String STATE_TAG = "State";
    private static final String STATE_SINCE_TAG = "StateSince";
    private static final String NEXT_REVIEW_TAG = "NextReview";
    private static final String REVISION_TAG = "Revision";
    private static final Set<String> KEYS = Set.of(
            VERSION_TAG,
            STATE_TAG,
            STATE_SINCE_TAG,
            NEXT_REVIEW_TAG,
            REVISION_TAG);

    public static CompoundTag encode(VillageDefenseStatus status) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, status.schemaVersion());
        encoded.putString(STATE_TAG, status.state().serializedName());
        encoded.putLong(STATE_SINCE_TAG, status.stateSinceGameTime());
        encoded.putLong(NEXT_REVIEW_TAG, status.nextReviewGameTime());
        encoded.putLong(REVISION_TAG, status.revision());
        return encoded;
    }

    public static @Nullable VillageDefenseStatus decode(CompoundTag encoded) {
        if (encoded == null
                || !encoded.getAllKeys().equals(KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG)
                        != VillageDefenseStatus.CURRENT_SCHEMA_VERSION
                || !encoded.contains(STATE_TAG, Tag.TAG_STRING)
                || !encoded.contains(STATE_SINCE_TAG, Tag.TAG_LONG)
                || !encoded.contains(NEXT_REVIEW_TAG, Tag.TAG_LONG)
                || !encoded.contains(REVISION_TAG, Tag.TAG_LONG)) {
            return null;
        }
        VillageDefenseState state = VillageDefenseState.byName(
                encoded.getString(STATE_TAG)).orElse(null);
        if (state == null) {
            return null;
        }
        try {
            return new VillageDefenseStatus(
                    VillageDefenseStatus.CURRENT_SCHEMA_VERSION,
                    state,
                    encoded.getLong(STATE_SINCE_TAG),
                    encoded.getLong(NEXT_REVIEW_TAG),
                    encoded.getLong(REVISION_TAG));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private VillageDefenseStatusCodec() {
    }
}
