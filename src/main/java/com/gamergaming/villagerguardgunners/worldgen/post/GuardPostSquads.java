package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Deterministic bridge from a generated post to the existing squad formats. */
public final class GuardPostSquads {
    private GuardPostSquads() {
    }

    public static UUID squadId(UUID postId) {
        return GuardPostIds.squadId(postId);
    }

    public static String displayName(UUID postId) {
        Objects.requireNonNull(postId, "postId");
        return "Watchtower " + postId.toString().substring(0, 8);
    }

    public static ArmorySquadLock armoryLock(UUID postId) {
        return ArmorySquadLock.assigned(squadId(postId), displayName(postId));
    }

    /** Registers at most once and never replaces player-visible metadata. */
    public static Optional<SquadMetadata> ensureRegistered(
            MinecraftServer server,
            UUID postId,
            long gameTime) {
        Objects.requireNonNull(server, "server");
        UUID squadId = squadId(postId);
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(server);
        Optional<SquadMetadata> existing = registry.find(squadId);
        return existing.isPresent()
                ? existing
                : registry.register(squadId, displayName(postId), null, gameTime);
    }
}
