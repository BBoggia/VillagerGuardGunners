package com.gamergaming.villagerguardgunners.squad;

import java.util.Objects;
import java.util.UUID;

/** Versioned identity and display cache carried by a Squad Command Ledger. */
public record SquadLedgerBinding(
        int schemaVersion,
        UUID squadId,
        String cachedDisplayName) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public SquadLedgerBinding {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Squad ledger schema version must be positive");
        }
        squadId = Objects.requireNonNull(squadId, "squadId");
        if (NIL_UUID.equals(squadId)) {
            throw new IllegalArgumentException("A squad ledger may not use the nil UUID");
        }
        if (!SquadMetadata.isCanonicalDisplayName(cachedDisplayName)) {
            throw new IllegalArgumentException("Squad ledger display cache is not canonical");
        }
    }

    public static SquadLedgerBinding bound(UUID squadId, String displayName) {
        return new SquadLedgerBinding(
                CURRENT_SCHEMA_VERSION,
                squadId,
                displayName);
    }
}
