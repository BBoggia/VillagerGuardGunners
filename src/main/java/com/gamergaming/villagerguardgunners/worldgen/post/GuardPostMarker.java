package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Closed marker grammar for one post group or one bounded Armory setup. */
public record GuardPostMarker(
        Action action,
        @Nullable ResourceLocation resourceId,
        @Nullable ArmoryAccess armoryAccess) {
    public static final String GUARD_POST_PREFIX = "guard_post:";
    public static final String ARMORY_PREFIX = "armory:";
    public static final int MAXIMUM_METADATA_LENGTH = 128;

    public GuardPostMarker {
        Objects.requireNonNull(action, "action");
        if (action == Action.GUARD_POST) {
            Objects.requireNonNull(resourceId, "guard-post group ID");
            if (armoryAccess != null) {
                throw new IllegalArgumentException("A guard-post marker cannot define Armory access");
            }
        } else if (armoryAccess == null) {
            throw new IllegalArgumentException("An Armory marker must define its access mode");
        }
    }

    public ResourceLocation groupId() {
        if (action != Action.GUARD_POST || resourceId == null) {
            throw new IllegalStateException("This marker does not reference a guard-post group");
        }
        return resourceId;
    }

    public @Nullable ResourceLocation lootTableId() {
        if (action != Action.ARMORY) {
            throw new IllegalStateException("This marker does not configure an Armory");
        }
        return resourceId;
    }

    public static Optional<GuardPostMarker> parse(String metadata) {
        if (metadata == null || metadata.length() > MAXIMUM_METADATA_LENGTH) {
            return Optional.empty();
        }
        if (metadata.startsWith(GUARD_POST_PREFIX)) {
            ResourceLocation group = canonicalId(
                    metadata.substring(GUARD_POST_PREFIX.length()));
            return group == null
                    ? Optional.empty()
                    : Optional.of(new GuardPostMarker(Action.GUARD_POST, group, null));
        }
        if (!metadata.startsWith(ARMORY_PREFIX)) {
            return Optional.empty();
        }

        String encoded = metadata.substring(ARMORY_PREFIX.length());
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator == encoded.length() - 1) {
            return Optional.empty();
        }
        ArmoryAccess access = ArmoryAccess.parse(encoded.substring(0, separator));
        if (access == null) {
            return Optional.empty();
        }
        String loot = encoded.substring(separator + 1);
        if ("empty".equals(loot)) {
            return Optional.of(new GuardPostMarker(Action.ARMORY, null, access));
        }
        ResourceLocation lootTable = canonicalId(loot);
        return lootTable == null
                ? Optional.empty()
                : Optional.of(new GuardPostMarker(Action.ARMORY, lootTable, access));
    }

    private static @Nullable ResourceLocation canonicalId(String encoded) {
        ResourceLocation parsed = ResourceLocation.tryParse(encoded);
        return parsed != null && parsed.toString().equals(encoded) ? parsed : null;
    }

    public enum ArmoryAccess {
        PUBLIC("public"),
        SQUAD_LOCKED("squad");

        private final String serializedName;

        ArmoryAccess(String serializedName) {
            this.serializedName = serializedName;
        }

        private static @Nullable ArmoryAccess parse(String encoded) {
            for (ArmoryAccess access : values()) {
                if (access.serializedName.equals(encoded)) {
                    return access;
                }
            }
            return null;
        }
    }

    public enum Action {
        GUARD_POST(1),
        ARMORY(2);

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
