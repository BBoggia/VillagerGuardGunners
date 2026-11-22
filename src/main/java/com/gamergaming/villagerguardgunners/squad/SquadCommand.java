package com.gamergaming.villagerguardgunners.squad;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Immutable, versioned temporary command assigned to one squad guard. */
public record SquadCommand(
        int schemaVersion,
        SquadCommandType type,
        UUID issuedBy,
        long issuedGameTime,
        long expiresGameTime,
        ResourceLocation dimension,
        @Nullable BlockPos position,
        double radius,
        @Nullable UUID targetId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final double MAXIMUM_COMMAND_RADIUS = 64.0D;

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public SquadCommand {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Squad command schema version must be positive");
        }
        type = Objects.requireNonNull(type, "type");
        if (!type.isStoredCommand()) {
            throw new IllegalArgumentException(
                    "This ledger action is not stored command data");
        }
        issuedBy = requireNonNil(issuedBy, "issuedBy");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (issuedGameTime < 0L || expiresGameTime <= issuedGameTime) {
            throw new IllegalArgumentException("Squad command contains invalid game times");
        }
        position = position == null ? null : position.immutable();
        if (!Double.isFinite(radius)
                || radius < 0.0D
                || radius > MAXIMUM_COMMAND_RADIUS) {
            throw new IllegalArgumentException("Squad command radius is invalid");
        }
        if (targetId != null) {
            targetId = requireNonNil(targetId, "targetId");
        }

        if (type.requiresPosition() != (position != null)
                || type.requiresTarget() != (targetId != null)
                || (type == SquadCommandType.DEFEND) != (radius > 0.0D)
                || (type != SquadCommandType.DEFEND && radius != 0.0D)) {
            throw new IllegalArgumentException(
                    "Squad command payload does not match its command type");
        }
    }

    public static SquadCommand positional(
            SquadCommandType type,
            UUID issuedBy,
            long issuedGameTime,
            long expiresGameTime,
            ResourceLocation dimension,
            BlockPos position,
            double radius) {
        return new SquadCommand(
                CURRENT_SCHEMA_VERSION,
                type,
                issuedBy,
                issuedGameTime,
                expiresGameTime,
                dimension,
                position,
                radius,
                null);
    }

    public static SquadCommand focusTarget(
            UUID issuedBy,
            long issuedGameTime,
            long expiresGameTime,
            ResourceLocation dimension,
            UUID targetId) {
        return new SquadCommand(
                CURRENT_SCHEMA_VERSION,
                SquadCommandType.FOCUS_TARGET,
                issuedBy,
                issuedGameTime,
                expiresGameTime,
                dimension,
                null,
                0.0D,
                targetId);
    }

    public boolean isExpired(long currentGameTime) {
        return currentGameTime >= expiresGameTime;
    }

    private static UUID requireNonNil(UUID value, String field) {
        UUID normalized = Objects.requireNonNull(value, field);
        if (NIL_UUID.equals(normalized)) {
            throw new IllegalArgumentException(field + " may not be the nil UUID");
        }
        return normalized;
    }
}
