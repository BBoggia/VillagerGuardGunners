package com.gamergaming.villagerguardgunners.squad;

import java.util.Objects;
import java.util.UUID;

/** Immutable, versioned identity assigning one guard to one squad. */
public record SquadMembership(int schemaVersion, UUID squadId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public SquadMembership {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Squad membership schema version must be positive");
        }
        squadId = Objects.requireNonNull(squadId, "squadId");
        if (NIL_UUID.equals(squadId)) {
            throw new IllegalArgumentException("A squad ID may not be the nil UUID");
        }
    }

    public static SquadMembership assigned(UUID squadId) {
        return new SquadMembership(CURRENT_SCHEMA_VERSION, squadId);
    }
}
