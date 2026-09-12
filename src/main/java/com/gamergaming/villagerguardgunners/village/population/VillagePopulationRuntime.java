package com.gamergaming.villagerguardgunners.village.population;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import com.gamergaming.villagerguardgunners.village.coverage.live.CoverageTerrain;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Loaded ticket queue. Initial credits, actual residents and weapon issuance remain independent. */
public final class VillagePopulationRuntime {
    public static final String RESIDENT_TAG = "villagerguardgunners:population_resident";
    public static final String CLAIM_TAG = "villagerguardgunners:population_claim";
    public static final int MAX_TICKETS = 1_024, TICKETS_PER_TICK = 2;
    private static final java.util.List<BlockPos> SPAWN_OFFSETS = BlockPos.betweenClosedStream(-4, -2, -4, 4, 3, 4)
            .map(BlockPos::immutable).sorted(java.util.Comparator.comparingInt((BlockPos p) -> p.distManhattan(BlockPos.ZERO))
                    .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static boolean generationEnabled() { return ModConfigs.GUARD_GUNNERS.enabled.get()
            && ModConfigs.GUARD_GUNNERS.villagePopulation.managedGeneration.get(); }
    public static boolean register(ServerLevel level, VillagePopulationPlan plan) throws IOException {
        if (!level.getServer().isSameThread() || !level.dimension().location().equals(plan.identity().dimension())) return false;
        var data = VillagePopulationSavedData.get(level);
        var registry = VillageRegistrySavedData.get(level);
        if (!data.canFreeze(plan) || !registry.enabled()) return false;
        State state = LEVELS.computeIfAbsent(level, k -> new State());
        var verified = state.verifiedPlans.get(plan.identity().id());
        if (verified != null && !verified.equals(plan)) return false;
        if (verified == null && !VillagePopulationJournal.get(level).plan(plan)) return false;
        if (!data.freeze(plan)) return false;
        if (state.verifiedPlans.size() < VillagePopulationSavedData.MAX_VILLAGES) state.verifiedPlans.put(plan.identity().id(), plan);
        if (registry.get(plan.identity().id()) == null) {
            return registry.observe(plan.identity(), VillageFootprint.of(plan.sources().stream().map(VillagePopulationPlan.Source::piece).toList()));
        }
        return true;
    }
    public static boolean bind(ServerLevel level, VillageBuildingIdentity building, VillagePopulationPlacement.Binding binding) {
        if (binding == null) return true;
        try {
            var source = binding.plan().source(binding.source());
            var registry = VillageRegistrySavedData.get(level);
            UUID previous = registry.buildingVillage(building.buildingId());
            if (source == null || !source.building() || (previous != null && !previous.equals(binding.plan().identity().id()))
                    || !register(level, binding.plan())) return false;
            VillagePopulationJournal.get(level).bind(building.buildingId(), binding);
            return registry.associateBuilding(building.buildingId(), binding.plan().identity().id());
        } catch (IOException | RuntimeException exception) { error(level, exception); return false; }
    }
    public static VillagePopulationPlacement.Binding binding(ServerLevel level, UUID building) throws IOException {
        CompoundTag tag = VillagePopulationJournal.get(level).binding(building);
        if (tag == null) {
            UUID village = VillageRegistrySavedData.get(level).buildingVillage(building);
            var data = VillagePopulationSavedData.get(level);
            if (village != null && (!data.enabled() || data.plan(village) != null || VillagePopulationJournal.get(level).hasPlan(village))) {
                throw new IOException("Missing managed building population binding");
            }
            return null;
        }
        if (!tag.hasUUID("Village") || !tag.hasUUID("Source") || tag.size() != 2) throw new IOException("Invalid building population binding");
        var plan = loadPlan(level, tag.getUUID("Village"));
        var source = plan.source(tag.getUUID("Source"));
        if (source == null || !source.building()) throw new IOException("Invalid building population source");
        return new VillagePopulationPlacement.Binding(plan, source.id());
    }
    private static VillagePopulationPlan loadPlan(ServerLevel level, UUID village) throws IOException {
        var plan = VillagePopulationSavedData.get(level).plan(village);
        if (plan == null) {
            plan = VillagePopulationCodec.decode(VillagePopulationJournal.get(level).readPlan(village), level.dimension().location());
        }
        // SavedData is another snapshot, not a bypass around the immutable journal.
        if (!plan.identity().id().equals(village) || !register(level, plan)) throw new IOException("Unresolved village population plan");
        return plan;
    }
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof Marker marker && marker.getPersistentData().contains(VillagePopulationPlacement.TICKET_TAG)) {
            Runnable queue = () -> {
                State state = LEVELS.computeIfAbsent(level, k -> new State());
                if (state.tickets.size() < MAX_TICKETS) state.tickets.putIfAbsent(marker.getUUID(), 0L);
                else state.rejected++;
            };
            if (level.getServer().isSameThread()) queue.run(); else level.getServer().execute(queue);
        }
        // Census joins are rechecked by the existing staggered guard assessment,
        // after all join listeners and native entity tracking have completed.
    }
    public static void synchronizeGuard(Guard guard) {
        if (!(guard.level() instanceof ServerLevel current) || !ModConfigs.GUARD_GUNNERS.enabled.get()
                || guard.isRemoved() || !guard.isAddedToWorld() || !current.getServer().isSameThread()) return;
        State state = LEVELS.computeIfAbsent(current, k -> new State());
        if (state.reconciled.contains(guard.getUUID())) return;
        CompoundTag persistent = guard.getPersistentData();
        if (persistent.contains(CLAIM_TAG, Tag.TAG_COMPOUND)) {
            if (state.claimRetryAt.getOrDefault(guard.getUUID(), 0L) > current.getGameTime()) return;
            CompoundTag claim = persistent.getCompound(CLAIM_TAG);
            try {
                VillagePopulationCodec.keys(claim, "Dimension", "Village", "Source", "Ordinal");
                VillagePopulationCodec.require(claim.contains("Dimension", Tag.TAG_STRING) && claim.hasUUID("Village") && claim.hasUUID("Source"));
                int ordinal = VillagePopulationCodec.integer(claim, "Ordinal");
                var home = home(current, claim);
                if (home == null) return;
                var plan = loadPlan(home, claim.getUUID("Village"));
                UUID source = claim.getUUID("Source");
                UUID slot = plan.slot(source, ordinal);
                if (plan.source(source).building()) {
                    var post = com.gamergaming.villagerguardgunners.api.GuardPostAccess.getIdentity(guard);
                    VillagePopulationCodec.require(post != null && post.memberIndex() == ordinal
                            && guard.getUUID().equals(com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIds.guardId(post.postId(), ordinal)));
                    var bound = binding(home, post.postId());
                    VillagePopulationCodec.require(bound != null && bound.plan().equals(plan) && bound.source().equals(source));
                } else VillagePopulationCodec.require(guard.getUUID().equals(slot));
                var journal = VillagePopulationJournal.get(home);
                journal.reserve(slot, guard.getUUID());
                if (!journal.matches(slot, guard.getUUID())) throw new IOException("Mismatched guard population intent");
                if (!VillagePopulationSavedData.get(home).consume(plan.identity().id(), source, ordinal, guard.getUUID())) {
                    throw new IOException("Guard population credit unavailable");
                }
                resident(guard, home, plan.identity().id());
                state.claimRetryAt.remove(guard.getUUID());
                if (state.reconciled.size() < VillagePopulationSavedData.MAX_RESIDENTS) state.reconciled.add(guard.getUUID());
            } catch (IOException | RuntimeException exception) {
                if (state.claimRetryAt.size() < VillagePopulationSavedData.MAX_RESIDENTS) state.claimRetryAt.put(guard.getUUID(), current.getGameTime() + 600);
                error(current, exception);
            }
            return;
        }
        if (persistent.contains(RESIDENT_TAG, Tag.TAG_COMPOUND)) {
            var tag = persistent.getCompound(RESIDENT_TAG);
            var home = home(current, tag);
            if (home != null && VillagePopulationSavedData.get(home).observe(guard.getUUID(), tag.getUUID("Village"))
                    && state.reconciled.size() < VillagePopulationSavedData.MAX_RESIDENTS) state.reconciled.add(guard.getUUID());
            return;
        }
        UUID village = VillageRegistrySavedData.get(current).guardVillage(guard.getUUID());
        if (village != null) {
            resident(guard, current, village);
            if (state.reconciled.size() < VillagePopulationSavedData.MAX_RESIDENTS) state.reconciled.add(guard.getUUID());
        }
    }
    private static ServerLevel home(ServerLevel current, CompoundTag tag) {
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        return dimension == null || !tag.hasUUID("Village") ? null
                : current.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
    private static void resident(Guard guard, ServerLevel home, UUID village) {
        var data = VillagePopulationSavedData.get(home);
        if (!data.observe(guard.getUUID(), village)) return;
        if (guard.getRemovalReason() != null && guard.getRemovalReason().shouldDestroy()) data.confirmedDeath(guard.getUUID());
        CompoundTag tag = new CompoundTag(); tag.putString("Dimension", home.dimension().location().toString()); tag.putUUID("Village", village);
        guard.getPersistentData().put(RESIDENT_TAG, tag);
        if (guard.level() == home) VillageRegistrySavedData.get(home).affiliateGuard(guard.getUUID(), village);
    }
    public static void mark(Guard guard, VillagePopulationPlan plan, UUID source, int ordinal) {
        plan.slot(source, ordinal);
        CompoundTag claim = new CompoundTag(); claim.putString("Dimension", plan.identity().dimension().toString());
        claim.putUUID("Village", plan.identity().id()); claim.putUUID("Source", source); claim.putInt("Ordinal", ordinal);
        guard.getPersistentData().put(CLAIM_TAG, claim);
    }
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = LEVELS.get(level);
        if (state != null) { state.tickets.remove(event.getEntity().getUUID()); state.reconciled.remove(event.getEntity().getUUID()); state.claimRetryAt.remove(event.getEntity().getUUID()); state.attempts.remove(event.getEntity().getUUID()); }
        if (event.getEntity() instanceof Guard guard && guard.getRemovalReason() != null && guard.getRemovalReason().shouldDestroy()) {
            var tag = guard.getPersistentData().getCompound(RESIDENT_TAG);
            ServerLevel home = home(level, tag);
            if (home != null) VillagePopulationSavedData.get(home).confirmedDeath(guard.getUUID());
            else {
                UUID village = VillageRegistrySavedData.get(level).guardVillage(guard.getUUID());
                if (village != null) {
                    var data = VillagePopulationSavedData.get(level); data.observe(guard.getUUID(), village); data.confirmedDeath(guard.getUUID());
                }
            }
        }
    }
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) for (ServerLevel level : event.getServer().getAllLevels()) tick(level);
    }
    public static void tick(ServerLevel level) {
        State state = LEVELS.get(level);
        if (state == null || !generationEnabled()) return;
        int count = Math.min(TICKETS_PER_TICK, state.tickets.size());
        for (int i = 0; i < count; i++) {
            var entry = state.tickets.entrySet().iterator().next(); UUID id = entry.getKey(); long due = entry.getValue();
            state.tickets.remove(id);
            if (due > level.getGameTime()) { state.tickets.put(id, due); continue; }
            var entity = level.getEntity(id);
            if (entity == null) {
                if (state.attempts.merge(id, 1, Integer::sum) < 10) state.tickets.put(id, level.getGameTime() + 20);
                else state.attempts.remove(id);
                continue;
            }
            if (!(entity instanceof Marker marker) || marker.isRemoved()) { state.attempts.remove(id); continue; }
            try { process(level, marker); }
            catch (IOException | RuntimeException exception) { error(level, exception); }
            if (!marker.isRemoved() && state.attempts.merge(id, 1, Integer::sum) < 10) state.tickets.put(id, level.getGameTime() + 100);
            else state.attempts.remove(id);
        }
    }
    private static void process(ServerLevel level, Marker marker) throws IOException {
        CompoundTag ticket = marker.getPersistentData().getCompound(VillagePopulationPlacement.TICKET_TAG);
        VillagePopulationCodec.keys(ticket, "Plan", "Source", "Ordinal");
        VillagePopulationCodec.require(ticket.hasUUID("Source") && ticket.contains("Plan", Tag.TAG_COMPOUND));
        var plan = VillagePopulationCodec.decode(ticket.getCompound("Plan"), level.dimension().location());
        UUID source = ticket.getUUID("Source"); int ordinal = VillagePopulationCodec.integer(ticket, "Ordinal");
        UUID slot = plan.slot(source, ordinal);
        if (plan.source(source).building() || !register(level, plan)) return;
        var data = VillagePopulationSavedData.get(level);
        if (data.credit(slot) != null) { marker.discard(); return; }
        Entity existing = level.getEntity(slot);
        if (existing instanceof Guard guard) { synchronizeGuard(guard); if (data.credit(slot) != null) marker.discard(); return; }
        if (existing != null) return;
        var journal = VillagePopulationJournal.get(level);
        if (journal.attempted(slot)) return;
        BlockPos origin = marker.blockPosition();
        BlockPos pos = null;
        for (BlockPos offset : SPAWN_OFFSETS) {
            BlockPos candidate = origin.offset(offset);
            if (safe(level, candidate)) { pos = candidate; break; }
        }
        if (pos == null) return;
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !level.isPositionEntityTicking(pos) || !level.getWorldBorder().isWithinBounds(pos)) return;
        if (!takeSpawnTurn(level)) return;
        Guard guard = GuardEntityType.GUARD.get().create(level);
        if (guard == null) return;
        guard.setUUID(slot); guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, marker.getYRot(), 0);
        guard.setPersistenceRequired(); mark(guard, plan, source, ordinal);
        guard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        if (guard.isSpawnCancelled()) { guard.discard(); return; }
        if (!journal.reserve(slot, slot)) { guard.discard(); return; }
        boolean inserted = level.addFreshEntity(guard);
        if (!inserted) { guard.discard(); journal.canceled(slot, slot); return; }
        data.consume(plan.identity().id(), source, ordinal, slot); resident(guard, level, plan.identity().id());
        // Keep the guard's claim through its first save; it reconciles an older saved-data snapshot on join.
        marker.discard();
    }
    private static boolean safe(ServerLevel level, BlockPos pos) {
        return CoverageTerrain.safeStandingPosition(level, pos);
    }
    /** Shared with managed garrisons: at most two guard creation attempts per dimension tick. */
    public static boolean takeSpawnTurn(ServerLevel level) {
        if (!level.getServer().isSameThread()) return false;
        State state = LEVELS.computeIfAbsent(level, k -> new State());
        if (state.spawnTick != level.getGameTime()) { state.spawnTick = level.getGameTime(); state.spawns = 0; }
        if (state.spawns >= 2) return false;
        state.spawns++; return true;
    }
    public static int pending(ServerLevel level) { State state = LEVELS.get(level); return state == null ? 0 : state.tickets.size(); }
    public static long errors(ServerLevel level) { State state = LEVELS.get(level); return state == null ? 0 : state.rejected; }
    private static void error(ServerLevel level, Exception exception) {
        State state = LEVELS.computeIfAbsent(level, k -> new State()); state.rejected++;
        if (state.rejected == 1) LOGGER.warn("Village population work deferred: {}", exception.toString());
    }
    public static void onLevelUnload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level); }
    public static void onServerStopping(ServerStoppingEvent event) { LEVELS.clear(); }
    private static final class State { final LinkedHashMap<UUID, Long> tickets = new LinkedHashMap<>(); final java.util.Set<UUID> reconciled = new java.util.HashSet<>(); final Map<UUID, VillagePopulationPlan> verifiedPlans = new java.util.HashMap<>(); final Map<UUID, Integer> attempts = new java.util.HashMap<>(); final Map<UUID, Long> claimRetryAt = new java.util.HashMap<>(); long rejected, spawnTick = Long.MIN_VALUE; int spawns; }
    private VillagePopulationRuntime() { }
}
