package com.gamergaming.villagerguardgunners.spawn;

import java.util.Objects;

import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Event-driven bridge from Guard Villagers creation paths to the pure natural
 * loadout selector.
 *
 * <p>Spawn hooks only attach a source marker. The marker survives a worldgen
 * protochunk handoff. Already-equipped conversion guards can be selected from
 * the eventual live entity-join event; ordinary spawned guards wait until
 * Guard Villagers has completed its one-time native equipment pass. Structure
 * generation therefore never performs a live-entity query from a
 * world-generation worker, and the integration adds no recurring tick scan.</p>
 */
public final class NaturalGuardSpawnIntegration {
    private NaturalGuardSpawnIntegration() {
    }

    public static void markInitialSpawn(Guard guard, MobSpawnType spawnType) {
        markInitialSpawn(guard, mapSpawnType(spawnType));
    }

    public static void markInitialSpawn(Guard guard, NaturalGuardSpawnSource source) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(source, "source");
        if (guard.level().isClientSide) {
            return;
        }
        ((GuardNaturalLoadoutAccess) guard)
                .villagerGuardGunners$markNaturalLoadoutSpawnSource(source);
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Guard guard)) {
            return;
        }

        NaturalGuardPopulationIndex.synchronize(guard);

        // Guard Villagers' player conversion path copies its conversion weapon
        // before adding the new guard and never runs finalizeSpawn. Ordinary
        // spawned guards, however, are still empty-handed here and receive
        // their sword/crossbow during the first Guard#aiStep. Leave their marker
        // unclaimed until the precise post-equipment hook below.
        if (!guard.getMainHandItem().isEmpty()) {
            queueSelection(level, guard);
        }
    }

    /** Called once, immediately after Guard Villagers' native spawn equipment pass. */
    public static void onInitialEquipmentReady(Guard guard) {
        Objects.requireNonNull(guard, "guard");
        if (guard.level() instanceof ServerLevel level) {
            queueSelection(level, guard);
        }
    }

    private static void queueSelection(ServerLevel level, Guard guard) {
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        NaturalGuardSpawnSource source =
                access.villagerGuardGunners$claimNaturalLoadoutSpawnSource();
        if (source == null) {
            return;
        }

        // Queue explicitly: MinecraftServer#execute runs inline on the server
        // thread. At entity join that would be before Entity#onAddedToWorld;
        // after native equipment it would mutate equipment in the middle of
        // Guard#aiStep. The atomic source claim makes both readiness paths safe
        // if another mod creates an already-equipped finalized guard.
        level.getServer().tell(new TickTask(
                level.getServer().getTickCount(),
                () -> selectAfterJoin(level, guard, source)));
    }

    static NaturalGuardSpawnSource mapSpawnType(MobSpawnType spawnType) {
        Objects.requireNonNull(spawnType, "spawnType");
        if (spawnType == MobSpawnType.STRUCTURE) {
            return NaturalGuardSpawnSource.STRUCTURE;
        }
        if (spawnType == MobSpawnType.CONVERSION) {
            return NaturalGuardSpawnSource.CONVERSION;
        }
        if (spawnType == MobSpawnType.SPAWN_EGG) {
            return NaturalGuardSpawnSource.SPAWN_EGG;
        }
        if (spawnType == MobSpawnType.COMMAND) {
            return NaturalGuardSpawnSource.COMMAND;
        }
        return NaturalGuardSpawnSource.OTHER;
    }

    static NaturalGuardDifficulty mapDifficulty(Difficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        return switch (difficulty) {
            case PEACEFUL -> NaturalGuardDifficulty.PEACEFUL;
            case EASY -> NaturalGuardDifficulty.EASY;
            case NORMAL -> NaturalGuardDifficulty.NORMAL;
            case HARD -> NaturalGuardDifficulty.HARD;
        };
    }

    private static void selectAfterJoin(
            ServerLevel level,
            Guard guard,
            NaturalGuardSpawnSource source) {
        if (guard.isRemoved()
                || !guard.isAddedToWorld()
                || guard.level() != level) {
            return;
        }

        VillagerGuardGunnersConfig config = ModConfigs.GUARD_GUNNERS;
        NaturalGuardSpawnRules rules = config.naturalLoadouts.spawnRules();
        NaturalGuardDifficulty difficultyKey = mapDifficulty(level.getDifficulty());
        NaturalGuardDifficultySettings difficulty =
                config.naturalLoadouts.settingsFor(difficultyKey);
        NaturalGuardLoadoutPool pool = difficulty.poolId() == null
                ? null
                : NaturalGuardLoadoutPools.get(difficulty.poolId());
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        NaturalGuardLoadoutProvenance existingProvenance =
                access.villagerGuardGunners$getNaturalLoadoutProvenance();
        boolean alreadyIssued = NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                guard,
                existingProvenance);
        if (existingProvenance != null && !alreadyIssued) {
            access.villagerGuardGunners$setNaturalLoadoutProvenance(null);
            access.villagerGuardGunners$setNaturalLoadoutCandidate(null);
        }

        NaturalGuardLoadoutSelector.Decision decision = NaturalGuardLoadoutSelector.select(
                new NaturalGuardLoadoutSelector.Context(
                        true,
                        config.enabled.get() && config.naturalLoadouts.enabled.get(),
                        true,
                        source,
                        guard.getMainHandItem().is(Items.CROSSBOW),
                        alreadyIssued),
                rules,
                difficultyKey,
                difficulty,
                () -> NaturalGuardLoadoutContextCapture.capture(
                        level,
                        guard,
                        pool,
                        difficultyKey,
                        source),
                guard.getRandom()::nextDouble,
                () -> countNearbyCandidates(level, guard, rules.nearbyCapRadius()),
                pool,
                guard.getRandom()::nextInt);
        access.villagerGuardGunners$setNaturalLoadoutDecisionStatus(decision.status());
        if (decision.selected()) {
            NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                    difficulty.poolId(),
                    decision.entry().gunId(),
                    decision.entry().sidearmId(),
                    decision.entry().roleId(),
                    difficultyKey,
                    source);
            access.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
            NaturalGuardLoadoutIssuance.Result issuance =
                    NaturalGuardLoadoutIssuance.issue(
                            guard,
                            candidate,
                            rules.allowExplosiveWeapons());
            access.villagerGuardGunners$setNaturalLoadoutIssuanceStatus(issuance.status());
            if (issuance.issued()) {
                NaturalGuardPopulationIndex.synchronize(guard);
            } else {
                if (issuance.status()
                        == NaturalGuardLoadoutIssuance.IssuanceStatus.GUN_DEFINITION_UNAVAILABLE) {
                    NaturalGuardLoadoutPools.reportUnavailableGun(candidate);
                }
                if (issuance.status()
                        == NaturalGuardLoadoutIssuance.IssuanceStatus
                                .SIDEARM_DEFINITION_UNAVAILABLE) {
                    NaturalGuardLoadoutPools.reportUnavailableSidearm(candidate);
                }
                if (issuance.status()
                        == NaturalGuardLoadoutIssuance.IssuanceStatus
                                .ROLE_DEFINITION_UNAVAILABLE) {
                    NaturalGuardLoadoutPools.reportUnavailableRole(candidate);
                }
                // Only fully issued gunner guards reserve a nearby population
                // slot. A rejected or rolled-back candidate remains a
                // one-time attempt and is never retried.
                access.villagerGuardGunners$setNaturalLoadoutCandidate(null);
                access.villagerGuardGunners$setNaturalLoadoutProvenance(null);
            }
        }
    }

    private static int countNearbyCandidates(ServerLevel level, Guard origin, double radius) {
        double radiusSquared = radius * radius;
        AABB bounds = origin.getBoundingBox().inflate(radius);
        int loaded = level.getEntitiesOfClass(
                Guard.class,
                bounds,
                nearby -> nearby != origin
                        && nearby.isAlive()
                        && nearby.distanceToSqr(origin) <= radiusSquared
                        && hasActiveNaturalLoadout(nearby))
                .size();
        int unloaded = NaturalGuardPopulationIndex.get(level).countUnloadedWithin(
                level,
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                radius);
        return unloaded > Integer.MAX_VALUE - loaded
                ? Integer.MAX_VALUE
                : loaded + unloaded;
    }

    private static boolean hasActiveNaturalLoadout(Guard guard) {
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        return NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                guard,
                access.villagerGuardGunners$getNaturalLoadoutProvenance());
    }
}
