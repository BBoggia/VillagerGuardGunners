package com.gamergaming.villagerguardgunners.spawn;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;
import com.gamergaming.villagerguardgunners.api.GuardPostAccess;
import com.gamergaming.villagerguardgunners.api.GuardPostAssignmentAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.role.GuardRoles;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostAssignment;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIdentity;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIds;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSquads;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingGarrisons;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageWatchtowerGuardPlacement;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
        GuardPostIdentity postIdentity = GuardPostAccess.getIdentity(guard);
        if (GuardPostAssignmentAccess.hasUnreadableAssignment(guard)) {
            // An orphaned or future assignment is still pending work, even if
            // its companion identity is missing or unreadable. Preserve it and
            // the source marker rather than issuing an unrelated loadout.
            return;
        }
        if (postIdentity == null && GuardPostAccess.hasUnreadableIdentity(guard)) {
            // Preserve future/corrupt post data and its unclaimed spawn source.
            // This version must neither erase it nor reinterpret the entity as
            // an ordinary structure-spawned guard.
            return;
        }

        // Queue explicitly: MinecraftServer#execute runs inline on the server
        // thread. At entity join that would be before Entity#onAddedToWorld;
        // after native equipment it would mutate equipment in the middle of
        // Guard#aiStep. Persistent work is deliberately claimed inside the
        // queued task, after liveness is rechecked. An unload, dimension move,
        // cancellation, or shutdown before then therefore leaves the handoff
        // retryable when the guard next joins a live server level. Duplicate
        // readiness callbacks are harmless because the first live task claims
        // the source and assignment atomically and later tasks observe no work.
        level.getServer().tell(new TickTask(
                level.getServer().getTickCount(),
                () -> selectQueuedAfterJoin(level, guard)));
    }

    private static void selectQueuedAfterJoin(ServerLevel level, Guard guard) {
        if (!isLiveIn(level, guard)
                || GuardPostAssignmentAccess.hasUnreadableAssignment(guard)) {
            return;
        }
        GuardPostIdentity postIdentity = GuardPostAccess.getIdentity(guard);
        if (postIdentity == null && GuardPostAccess.hasUnreadableIdentity(guard)) {
            return;
        }

        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        if (postIdentity != null) {
            GuardPostAssignment assignment = GuardPostAssignmentAccess.claim(guard);
            NaturalGuardSpawnSource source =
                    access.villagerGuardGunners$claimNaturalLoadoutSpawnSource();
            if (assignment != null && source == NaturalGuardSpawnSource.STRUCTURE) {
                selectPostAfterJoin(level, guard, postIdentity, assignment);
            }
            // Generated-post guards never fall through to the generic
            // difficulty pool. Missing or malformed assignment data therefore
            // leaves Guard Villagers' normal equipment unchanged.
            return;
        }

        NaturalGuardSpawnSource source =
                access.villagerGuardGunners$claimNaturalLoadoutSpawnSource();
        if (source != null) {
            selectAfterJoin(level, guard, source);
        }
    }

    private static void selectPostAfterJoin(
            ServerLevel level,
            Guard guard,
            GuardPostIdentity identity,
            GuardPostAssignment assignment) {
        if (!isLiveIn(level, guard)) {
            return;
        }

        try {
            completePostAfterJoin(level, guard, identity, assignment);
        } finally {
            VillageWatchtowerGuardPlacement.finalizePosition(level, guard, identity);
        }
    }

    private static void completePostAfterJoin(
            ServerLevel level,
            Guard guard,
            GuardPostIdentity identity,
            GuardPostAssignment assignment) {

        UUID squadId = GuardPostSquads.squadId(identity.postId());
        GuardPostSquads.ensureRegistered(
                        level.getServer(),
                        identity.postId(),
                        identity.groupId(),
                        level.getGameTime())
                .ifPresent(ignored -> GuardSquadAccess.setMembership(
                        guard,
                        SquadMembership.assigned(squadId)));

        if (assignment.roleId() != null) {
            if (GuardRoles.get(assignment.roleId()) == null
                    || !GuardRoleAccess.setRoleId(guard, assignment.roleId())) {
                ((GuardNaturalLoadoutAccess) guard)
                        .villagerGuardGunners$setNaturalLoadoutIssuanceStatus(
                                NaturalGuardLoadoutIssuance.IssuanceStatus
                                        .ROLE_DEFINITION_UNAVAILABLE);
                return;
            }
        }

        VillagerGuardGunnersConfig config = ModConfigs.GUARD_GUNNERS;
        NaturalGuardSpawnRules rules = config.naturalLoadouts.spawnRules();
        NaturalGuardDifficulty difficultyKey = assignment.generationDifficulty();
        NaturalGuardDifficultySettings configured =
                config.naturalLoadouts.settingsFor(difficultyKey);
        boolean villageGarrison = VillageBuildingGarrisons.isVillageGroup(identity.groupId());
        double effectiveUpgradeChance = villageGarrison
                ? config.villageBuildings.guardGunUpgradeChance(configured.upgradeChance())
                : config.watchtowers.guardGunUpgradeChance(configured.upgradeChance());
        NaturalGuardDifficultySettings effective = new NaturalGuardDifficultySettings(
                effectiveUpgradeChance,
                configured.maximumNearby(),
                assignment.loadoutPool());
        NaturalGuardLoadoutPool pool = NaturalGuardLoadoutPools.get(assignment.loadoutPool());
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        NaturalGuardLoadoutProvenance existingProvenance =
                access.villagerGuardGunners$getNaturalLoadoutProvenance();
        boolean alreadyIssued = NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                guard,
                existingProvenance);

        RandomSource deterministic = RandomSource.create(
                GuardPostIds.memberLoadoutSeed(identity.postId(), identity.memberIndex()));
        NaturalGuardLoadoutSelector.Decision decision = NaturalGuardLoadoutSelector.select(
                new NaturalGuardLoadoutSelector.Context(
                        true,
                        config.enabled.get() && config.naturalLoadouts.enabled.get(),
                        true,
                        NaturalGuardSpawnSource.STRUCTURE,
                        villageGarrison || guard.getMainHandItem().is(Items.CROSSBOW),
                        alreadyIssued),
                rules,
                difficultyKey,
                effective,
                () -> NaturalGuardLoadoutContextCapture.capture(
                        level,
                        guard,
                        pool,
                        difficultyKey,
                        NaturalGuardSpawnSource.STRUCTURE),
                deterministic::nextDouble,
                () -> countNearbyCandidates(
                        level,
                        guard,
                        rules.nearbyCapRadius(),
                        effective.maximumNearby()),
                pool,
                deterministic::nextInt);
        access.villagerGuardGunners$setNaturalLoadoutDecisionStatus(decision.status());
        if (!decision.selected()) {
            return;
        }

        NaturalGuardLoadoutCandidate candidate = new NaturalGuardLoadoutCandidate(
                assignment.loadoutPool(),
                decision.entry().gunId(),
                decision.entry().sidearmId(),
                assignment.roleId() == null ? decision.entry().roleId() : null,
                difficultyKey,
                NaturalGuardSpawnSource.STRUCTURE);
        issueSelected(guard, access, candidate, rules.allowExplosiveWeapons());
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
        if (!isLiveIn(level, guard)) {
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
                () -> countNearbyCandidates(
                        level,
                        guard,
                        rules.nearbyCapRadius(),
                        difficulty.maximumNearby()),
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
            issueSelected(guard, access, candidate, rules.allowExplosiveWeapons());
        }
    }

    private static boolean isLiveIn(ServerLevel level, Guard guard) {
        return !guard.isRemoved()
                && guard.isAddedToWorld()
                && guard.level() == level;
    }

    private static void issueSelected(
            Guard guard,
            GuardNaturalLoadoutAccess access,
            NaturalGuardLoadoutCandidate candidate,
            boolean allowExplosiveWeapons) {
        access.villagerGuardGunners$setNaturalLoadoutCandidate(candidate);
        NaturalGuardLoadoutIssuance.Result issuance =
                NaturalGuardLoadoutIssuance.issue(
                        guard,
                        candidate,
                        allowExplosiveWeapons);
        access.villagerGuardGunners$setNaturalLoadoutIssuanceStatus(issuance.status());
        if (issuance.issued()) {
            NaturalGuardPopulationIndex.synchronize(guard);
            return;
        }
        if (issuance.status()
                == NaturalGuardLoadoutIssuance.IssuanceStatus.GUN_DEFINITION_UNAVAILABLE) {
            NaturalGuardLoadoutPools.reportUnavailableGun(candidate);
        }
        if (issuance.status()
                == NaturalGuardLoadoutIssuance.IssuanceStatus.SIDEARM_DEFINITION_UNAVAILABLE) {
            NaturalGuardLoadoutPools.reportUnavailableSidearm(candidate);
        }
        if (issuance.status()
                == NaturalGuardLoadoutIssuance.IssuanceStatus.ROLE_DEFINITION_UNAVAILABLE) {
            NaturalGuardLoadoutPools.reportUnavailableRole(candidate);
        }
        // A rejected or rolled-back candidate remains a one-time attempt.
        access.villagerGuardGunners$setNaturalLoadoutCandidate(null);
        access.villagerGuardGunners$setNaturalLoadoutProvenance(null);
    }

    private static int countNearbyCandidates(
            ServerLevel level,
            Guard origin,
            double radius,
            int maximumCount) {
        if (maximumCount <= 0) {
            return 0;
        }
        double radiusSquared = radius * radius;
        AABB bounds = origin.getBoundingBox().inflate(radius);
        ArrayList<Guard> loadedGuards = new ArrayList<>(maximumCount);
        level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(Guard.class),
                bounds,
                nearby -> nearby != origin
                        && nearby.isAlive()
                        && nearby.distanceToSqr(origin) <= radiusSquared
                        && hasActiveNaturalLoadout(nearby),
                loadedGuards,
                maximumCount);
        int loaded = loadedGuards.size();
        if (loaded >= maximumCount) {
            return maximumCount;
        }
        int unloaded = NaturalGuardPopulationIndex.get(level).countUnloadedWithin(
                level,
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                radius,
                maximumCount - loaded);
        return loaded + unloaded;
    }

    private static boolean hasActiveNaturalLoadout(Guard guard) {
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        return NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                guard,
                access.villagerGuardGunners$getNaturalLoadoutProvenance());
    }
}
