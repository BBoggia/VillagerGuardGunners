package com.gamergaming.villagerguardgunners.armory;

import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import java.util.Objects;
import java.util.UUID;

/** Immutable squad identity cached by an armory for display and authorization. */
public record ArmorySquadLock(int schemaVersion, UUID squadId, String displayName) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public ArmorySquadLock {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported armory lock schema version");
        }
        squadId = Objects.requireNonNull(squadId, "squadId");
        if (NIL_UUID.equals(squadId)) {
            throw new IllegalArgumentException("An armory lock may not use the nil UUID");
        }
        if (!SquadMetadata.isCanonicalDisplayName(displayName)) {
            throw new IllegalArgumentException("Armory lock display name is not canonical");
        }
    }

    public static ArmorySquadLock assigned(UUID squadId, String displayName) {
        return new ArmorySquadLock(CURRENT_SCHEMA_VERSION, squadId, displayName);
    }
}
