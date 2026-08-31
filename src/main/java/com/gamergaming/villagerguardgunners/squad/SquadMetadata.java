package com.gamergaming.villagerguardgunners.squad;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/** Persistent player-facing metadata for a stable squad identity. */
public record SquadMetadata(
        int schemaVersion,
        UUID squadId,
        String displayName,
        @Nullable UUID createdBy,
        long createdGameTime,
        long updatedGameTime) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_DISPLAY_NAME_CODE_POINTS = 64;

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public SquadMetadata {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Squad metadata schema version must be positive");
        }
        squadId = requireNonNil(squadId, "squadId");
        if (createdBy != null) {
            createdBy = requireNonNil(createdBy, "createdBy");
        }
        displayName = requireCanonicalDisplayName(displayName);
        if (createdGameTime < 0L || updatedGameTime < createdGameTime) {
            throw new IllegalArgumentException("Squad metadata contains invalid game times");
        }
    }

    public static SquadMetadata create(
            UUID squadId,
            @Nullable String requestedDisplayName,
            @Nullable UUID createdBy,
            long gameTime) {
        return new SquadMetadata(
                CURRENT_SCHEMA_VERSION,
                squadId,
                normalizeDisplayName(requestedDisplayName, squadId),
                createdBy,
                gameTime,
                gameTime);
    }

    public SquadMetadata renamed(@Nullable String requestedDisplayName, long gameTime) {
        if (gameTime < createdGameTime) {
            throw new IllegalArgumentException("A squad cannot be renamed before it was created");
        }
        return new SquadMetadata(
                CURRENT_SCHEMA_VERSION,
                squadId,
                normalizeDisplayName(requestedDisplayName, squadId),
                createdBy,
                createdGameTime,
                gameTime);
    }

    static String normalizeDisplayName(@Nullable String requestedDisplayName, UUID squadId) {
        String candidate = requestedDisplayName == null ? "" : requestedDisplayName.trim();
        StringBuilder filtered = new StringBuilder(candidate.length());
        candidate.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(MAX_DISPLAY_NAME_CODE_POINTS)
                .forEach(filtered::appendCodePoint);
        String normalized = filtered.toString().trim();
        return normalized.isEmpty()
                ? "Squad " + squadId.toString().substring(0, 8)
                : normalized;
    }

    public static boolean isCanonicalDisplayName(String displayName) {
        if (displayName == null
                || displayName.isEmpty()
                || !displayName.equals(displayName.trim())
                || displayName.codePointCount(0, displayName.length())
                        > MAX_DISPLAY_NAME_CODE_POINTS) {
            return false;
        }
        return displayName.codePoints().noneMatch(Character::isISOControl);
    }

    private static String requireCanonicalDisplayName(String displayName) {
        if (!isCanonicalDisplayName(displayName)) {
            throw new IllegalArgumentException("Squad display name is not canonical");
        }
        return displayName;
    }

    private static UUID requireNonNil(UUID value, String field) {
        UUID normalized = Objects.requireNonNull(value, field);
        if (NIL_UUID.equals(normalized)) {
            throw new IllegalArgumentException(field + " may not be the nil UUID");
        }
        return normalized;
    }
}
