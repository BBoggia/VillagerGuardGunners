package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingGarrisons;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/** Deterministic bridge from a generated post to the existing squad formats. */
public final class GuardPostSquads {
    /** Capacity kept available for player-created/recovered squads. */
    public static final int PLAYER_SQUAD_RESERVE = 256;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<MinecraftServer> CAPACITY_WARNED_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());

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
        return ensureRegistered(server, postId, null, gameTime);
    }

    /** Uses the building identity for village garrisons while retaining tower names. */
    public static Optional<SquadMetadata> ensureRegistered(
            MinecraftServer server,
            UUID postId,
            ResourceLocation groupId,
            long gameTime) {
        Objects.requireNonNull(server, "server");
        UUID squadId = squadId(postId);
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(server);
        Optional<SquadMetadata> existing = registry.find(squadId);
        if (existing.isPresent()) {
            return existing;
        }
        if (!canAutomaticallyRegister(registry.size())) {
            reportAutomaticCapacityOnce(server, registry.size());
            return Optional.empty();
        }
        String displayName = VillageBuildingGarrisons.isVillageGroup(groupId)
                ? VillageBuildingGarrisons.squadDisplayName(groupId, postId)
                : displayName(postId);
        return registry.register(squadId, displayName, null, gameTime);
    }

    static boolean canAutomaticallyRegister(int registrySize) {
        return registrySize >= 0
                && registrySize < SquadRegistrySavedData.MAX_SQUADS - PLAYER_SQUAD_RESERVE;
    }

    private static void reportAutomaticCapacityOnce(MinecraftServer server, int registrySize) {
        synchronized (CAPACITY_WARNED_SERVERS) {
            if (!CAPACITY_WARNED_SERVERS.add(server)) {
                return;
            }
        }
        LOGGER.warn(
                "Generated guard squad registration reached its automatic capacity at {} registered squads ({} total slots are reserved for player-created or recovered squads). Generated guards remain functional, but new generated posts will not receive squad metadata until capacity is available.",
                registrySize,
                PLAYER_SQUAD_RESERVE);
    }
}
