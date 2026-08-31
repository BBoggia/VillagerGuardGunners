package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Closed marker grammar; metadata can only select a guard-post group ID. */
public record GuardPostMarker(Action action, ResourceLocation groupId) {
    public static final String GUARD_POST_PREFIX = "guard_post:";
    public static final int MAXIMUM_METADATA_LENGTH = 128;

    public GuardPostMarker {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(groupId, "groupId");
    }

    public static Optional<GuardPostMarker> parse(String metadata) {
        if (metadata == null
                || metadata.length() > MAXIMUM_METADATA_LENGTH
                || !metadata.startsWith(GUARD_POST_PREFIX)) {
            return Optional.empty();
        }
        String encodedGroup = metadata.substring(GUARD_POST_PREFIX.length());
        ResourceLocation groupId = ResourceLocation.tryParse(encodedGroup);
        if (groupId == null || !groupId.toString().equals(encodedGroup)) {
            return Optional.empty();
        }
        return Optional.of(new GuardPostMarker(Action.GUARD_POST, groupId));
    }

    public enum Action {
        GUARD_POST(1);

        private final int mask;

        Action(int mask) {
            this.mask = mask;
        }

        int mask() {
            return mask;
        }

        static int supportedMask() {
            int supported = 0;
            for (Action action : values()) {
                supported |= action.mask;
            }
            return supported;
        }
    }
}
