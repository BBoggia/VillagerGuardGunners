package com.gamergaming.villagerguardgunners.village.population;

import com.gamergaming.villagerguardgunners.api.GuardPostAccess;
import com.gamergaming.villagerguardgunners.village.compat.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.gamergaming.villagerguardgunners.village.VillageIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import tallestegg.guardvillagers.entities.Guard;

/** Generation workers only create immutable plans and proto-chunk ticket entities. */
public final class VillagePopulationPlacement implements AutoCloseable {
    public static final String START_TAG = "villagerguardgunners:population_basis";
    public static final String TICKET_TAG = "villagerguardgunners:population_ticket";
    private static final ThreadLocal<VillagePopulationPlacement> CURRENT = new ThreadLocal<>();
    private final VillagePopulationPlacement previous;
    private final WorldGenLevel level;
    private final StructureStart start;
    private final VillagePopulationPlan plan;
    private final UUID source;
    private final boolean blocked;
    private int ordinal;
    public VillagePopulationPlacement(WorldGenLevel level, StructureStart start, StructurePiece piece, CompoundTag state) {
        previous = CURRENT.get(); this.level = level; this.start = start;
        VillagePopulationPlan decoded = null;
        boolean invalid = false;
        if (!(state.getAllKeys().equals(java.util.Set.of("Legacy")) && state.getBoolean("Legacy"))) {
            try { decoded = VillagePopulationCodec.decode(state, level.getLevel().dimension().location()); }
            catch (RuntimeException exception) { invalid = true; }
        }
        plan = decoded; blocked = invalid;
        VillageFootprint.Piece observation = decoded == null ? null : piece(piece);
        source = observation == null ? null : VillagePopulationPlan.sourceId(observation);
        CURRENT.set(this);
    }
    public static CompoundTag legacy() { CompoundTag tag = new CompoundTag(); tag.putBoolean("Legacy", true); return tag; }
    public static CompoundTag initialize(WorldGenLevel level, StructureStart start) {
        var config = ModConfigs.GUARD_GUNNERS;
        if (!config.enabled.get() || !config.villagePopulation.managedGeneration.get()) return legacy();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var key = registry.getResourceKey(start.getStructure());
        if (key.isEmpty()) return legacy();
        var classification = SettlementDefinitions.lookup(key.get().location());
        if (!classification.friendly()) return legacy();
        if (start.getPieces().size() > VillageFootprint.MAX_PIECES) return legacy();
        try {
            var settings = config.villagePopulation;
            var extraHomes = settings.residentialTemplates.get();
            if (extraHomes.size() > 128) return legacy();
            var nativeTemplates = tallestegg.guardvillagers.configuration.GuardConfig.COMMON.structuresThatSpawnGuards.get();
            if (nativeTemplates.size() > 2_048) return legacy();
            var requestedTemplates = new java.util.HashSet<String>(nativeTemplates);
            int nativeCapacity = Math.max(0, Math.min(VillagePopulationPlan.HARD_CAP, tallestegg.guardvillagers.configuration.GuardConfig.COMMON.guardSpawnInVillage.get()));
            var sources = new ArrayList<VillagePopulationPlan.Source>();
            var policy = config.villageBuildings.garrisonPolicy();
            var countedBeds = new java.util.HashSet<net.minecraft.core.BlockPos>();
            for (StructurePiece nativePiece : start.getPieces()) {
                var reading = PieceReaders.read(nativePiece, classification.reader());
                var piece = reading.supported() ? reading.geometry() : null;
                if (piece == null) continue;
                boolean home = classification.kind(piece.template().toString()) == SettlementProfile.Kind.RESIDENCE
                        || (!classification.templates().containsKey(piece.template().toString()) && extraHomes.contains(piece.template()));
                boolean residentialTemplate = home;
                var meaning = classification.meaning(piece.template().toString());
                if (meaning.supported() && !meaning.beds().isEmpty()) {
                    boolean newBeds = false;
                    for (var bed : SettlementObservations.worldPoints(piece, meaning.beds())) newBeds |= countedBeds.add(bed);
                    home = newBeds;
                }
                VillageBuildingType type = nativePiece instanceof PoolElementStructurePiece pool
                        && pool.getElement() instanceof VillageBuildingPoolElement building ? building.buildingType() : null;
                boolean nativeSource = requestedTemplates.contains(piece.template().toString());
                if (!home && type == null && !nativeSource) continue;
                int capacity = type == null ? (nativeSource ? (residentialTemplate ? Math.min(2, nativeCapacity) : nativeCapacity) : 0) : policy.enabled() && level.getLevel().getDifficulty() != Difficulty.PEACEFUL
                        ? policy.limitGuardCount(type, 4) : 0;
                int priority = type == null ? 10 : switch (type) {
                    case WATCHTOWER, SENTRY_POST -> 0;
                    case BARRACKS -> 1;
                    case TRAINING_YARD -> 2;
                    case ARMORY -> 3;
                };
                sources.add(new VillagePopulationPlan.Source(VillagePopulationPlan.sourceId(piece), piece, type != null, home && type == null, capacity, priority));
            }
            if (sources.isEmpty()) return legacy();
            var identity = new VillageIdentity(level.getLevel().dimension().location(), key.get().location(), start.getChunkPos().toLong());
            return VillagePopulationCodec.encode(VillagePopulationPlan.create(identity, sources, settings.settings(),
                    level.getLevel().getDifficulty() == Difficulty.HARD ? settings.hardDifficultyBonus.get() : 0));
        } catch (RuntimeException exception) { return legacy(); }
    }
    public static boolean isHouse(ResourceLocation id) {
        return SettlementProfile.nativeResidence(id.toString());
    }
    private static VillageFootprint.Piece piece(StructurePiece piece) {
        var result = PieceReaders.readFrozenSource(piece);
        return result.supported() ? result.geometry() : null;
    }
    public static void finalized(Guard guard) {
        var scope = CURRENT.get();
        if (scope == null || GuardPostAccess.getIdentity(guard) != null) return;
        if (scope.blocked) { guard.setSpawnCancelled(true); return; }
        if (scope.plan == null) return;
        int ordinal = scope.ordinal++;
        int quota = scope.source == null ? 0 : scope.plan.quotas().getOrDefault(scope.source, 0);
        // All native requests in an enrolled village share the plan, including zero-share pieces.
        guard.setSpawnCancelled(true);
        if (ordinal >= quota || scope.plan.source(scope.source).building()) return;
        var ticket = EntityType.MARKER.create(scope.level.getLevel());
        if (ticket == null) return;
        CompoundTag data = new CompoundTag();
        data.put("Plan", VillagePopulationCodec.encode(scope.plan)); data.putUUID("Source", scope.source); data.putInt("Ordinal", ordinal);
        ticket.getPersistentData().put(TICKET_TAG, data);
        ticket.moveTo(guard.getX(), guard.getY(), guard.getZ(), guard.getYRot(), 0);
        // A repeated chunk placement produces the same slot, never another allowance.
        ticket.setUUID(UUID.nameUUIDFromBytes((scope.plan.slot(scope.source, ordinal) + ":ticket")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        scope.level.addFreshEntity(ticket);
    }
    public static StructureStart currentStart() { var scope = CURRENT.get(); return scope == null ? null : scope.start; }
    public static boolean bindingRequired() {
        var scope = CURRENT.get();
        return scope != null && (scope.blocked || scope.plan != null);
    }
    public static Binding binding() {
        var scope = CURRENT.get();
        return scope == null || scope.plan == null || scope.source == null ? null : new Binding(scope.plan, scope.source);
    }
    public record Binding(VillagePopulationPlan plan, UUID source) { }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
