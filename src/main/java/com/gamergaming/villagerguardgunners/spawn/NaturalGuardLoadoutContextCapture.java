package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import tallestegg.guardvillagers.entities.Guard;

/** Captures only the one-shot contextual data required by a selected pool. */
public final class NaturalGuardLoadoutContextCapture {
    private NaturalGuardLoadoutContextCapture() {
    }

    public static NaturalGuardLoadoutMatchContext capture(
            ServerLevel level,
            Guard guard,
            NaturalGuardLoadoutPool pool,
            NaturalGuardDifficulty difficulty,
            NaturalGuardSpawnSource spawnSource) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(spawnSource, "spawnSource");
        if (level.isClientSide || guard.level() != level) {
            throw new IllegalArgumentException(
                    "Natural loadout context requires the guard's live server level");
        }

        return capture(
                level,
                guard.blockPosition(),
                pool,
                difficulty,
                spawnSource,
                pool.requiresGuardTags() ? guard.getTags() : Set.of());
    }

    /** Captures a command-position context without requiring a spawned guard. */
    public static NaturalGuardLoadoutMatchContext capture(
            ServerLevel level,
            BlockPos position,
            NaturalGuardLoadoutPool pool,
            NaturalGuardDifficulty difficulty,
            NaturalGuardSpawnSource spawnSource,
            Set<String> guardTags) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(spawnSource, "spawnSource");
        Objects.requireNonNull(guardTags, "guardTags");
        if (level.isClientSide) {
            throw new IllegalArgumentException("Natural loadout context requires a server level");
        }

        Set<RegistrySelector> matchingBiomes = pool.requiresBiomeLookup()
                ? matchingBiomes(level.getBiome(position), pool.biomeSelectors())
                : Set.of();
        Set<RegistrySelector> matchingStructures = pool.requiresStructureLookup()
                ? matchingStructures(level.structureManager(), position, pool.structureSelectors())
                : Set.of();
        Raid raid = pool.requiresRaidLookup() ? level.getRaidAt(position) : null;
        boolean raidActive = raid != null && raid.isActive();
        Set<String> capturedGuardTags = pool.requiresGuardTags() ? guardTags : Set.of();

        return new NaturalGuardLoadoutMatchContext(
                level.dimension().location(),
                matchingBiomes,
                matchingStructures,
                difficulty,
                spawnSource,
                raidActive,
                capturedGuardTags);
    }

    private static Set<RegistrySelector> matchingBiomes(
            Holder<Biome> biome,
            Set<RegistrySelector> selectors) {
        if (selectors.isEmpty()) {
            return Set.of();
        }
        Set<RegistrySelector> matching = new HashSet<>();
        for (RegistrySelector selector : selectors) {
            boolean present = selector.tag()
                    ? biome.is(TagKey.create(Registries.BIOME, selector.id()))
                    : biome.is(ResourceKey.create(Registries.BIOME, selector.id()));
            if (present) {
                matching.add(selector);
            }
        }
        return matching;
    }

    private static Set<RegistrySelector> matchingStructures(
            StructureManager manager,
            BlockPos position,
            Set<RegistrySelector> selectors) {
        Set<RegistrySelector> matching = new HashSet<>();
        for (RegistrySelector selector : selectors) {
            StructureStart start = selector.tag()
                    ? manager.getStructureWithPieceAt(
                            position,
                            TagKey.create(Registries.STRUCTURE, selector.id()))
                    : manager.getStructureWithPieceAt(
                            position,
                            ResourceKey.create(Registries.STRUCTURE, selector.id()));
            if (start != null && start.isValid()) {
                matching.add(selector);
            }
        }
        return matching;
    }
}
