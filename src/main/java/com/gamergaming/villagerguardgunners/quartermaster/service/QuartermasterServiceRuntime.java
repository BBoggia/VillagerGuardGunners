package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Loaded-only indexes. Sixteen guards/operators and one durable transfer per dimension per second. */
public final class QuartermasterServiceRuntime {
    public static final int INSPECTIONS = 16, MAX_TRACKED = 4096, MAX_RESERVATIONS = 8;
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();
    private static final class State {
        final LinkedHashMap<UUID, Guard> guards = new LinkedHashMap<>();
        final LinkedHashMap<UUID, Villager> villagers = new LinkedHashMap<>();
        final Map<BlockPos, BlockEntity> blocks = new HashMap<>();
        final Map<Long, Set<BlockPos>> cells = new HashMap<>();
        final Map<UUID, ServicePlan> plans = new HashMap<>();
        final Set<UUID> travelling = new HashSet<>();
        boolean completeBlockIndex = true, completeGuardIndex = true;
        final Map<UUID, Set<BlockPos>> stationIdentities = new HashMap<>();
        final Map<BlockPos, UUID> identityAt = new HashMap<>();
    }
    private static State state(ServerLevel level) { return LEVELS.computeIfAbsent(level, ignored -> new State()); }
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = state(level);
        if (event.getEntity() instanceof Guard guard) {
            ServiceJournal.get(level.getServer()).verify(GuardEquipmentServiceAccess.get(guard).receipt);
            if (state.guards.size() < MAX_TRACKED) state.guards.put(guard.getUUID(), guard); else state.completeGuardIndex=false;
        } else if (event.getEntity() instanceof Villager villager && state.villagers.size() < MAX_TRACKED) state.villagers.put(villager.getUUID(), villager);
    }
    public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !LEVELS.containsKey(level)) return;
        var state = state(level); UUID id = event.getEntity().getUUID();
        if (event.getEntity() instanceof Guard guard) {
            cancel(guard, ServiceStatus.RETRY);
            if (guard.getHealth() <= 0 || guard.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED
                    || guard.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
                ServiceRoster.get(level).forget(guard.getUUID());
            else ServiceRoster.get(level).observe(guard);
        }
        state.guards.remove(id); state.villagers.remove(id);
    }
    public static void loaded(BlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level)) return;
        var state = state(level);
        if (!state.blocks.containsKey(block.getBlockPos()) && state.blocks.size() >= MAX_TRACKED) {
            state.completeBlockIndex = false; return;
        }
        ServiceReceipt receipt = block instanceof QuartermasterBlockEntity station ? station.serviceReceipt() : ((ArmoryBlockEntity) block).serviceReceipt();
        if (!ServiceJournal.get(level.getServer()).verify(receipt)) block.setChanged();
        state.blocks.put(block.getBlockPos(), block);
        refreshIdentity(block);
        state.cells.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(block.getBlockPos().getX() >> 4, block.getBlockPos().getZ() >> 4), ignored -> new TreeSet<>()).add(block.getBlockPos());
    }
    public static void removed(BlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level) || !LEVELS.containsKey(level)) return;
        var state = state(level);
        if (!state.blocks.remove(block.getBlockPos(), block)) return;
        unindexIdentity(state, block.getBlockPos());
        long cell = net.minecraft.world.level.ChunkPos.asLong(block.getBlockPos().getX() >> 4, block.getBlockPos().getZ() >> 4);
        var positions = state.cells.get(cell); if (positions != null) { positions.remove(block.getBlockPos()); if (positions.isEmpty()) state.cells.remove(cell); }
        for (var plan : List.copyOf(state.plans.values())) if (plan.station() == block || plan.source().block() == block) cancel(plan.guard(), ServiceStatus.RETRY);
    }
    private static List<BlockEntity> nearby(ServerLevel level, BlockPos origin, int radius) {
        var state = state(level); List<BlockEntity> found = new ArrayList<>(); int inspected = 0;
        search: for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
            for (BlockPos pos : state.cells.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), Set.of())) {
                if (++inspected > 32) break search;
                var block = state.blocks.get(pos);
                if (block != null && !block.isRemoved() && pos.distSqr(origin) <= radius * radius) found.add(block);
            }
        }
        found.sort(Comparator.<BlockEntity>comparingDouble(block -> block.getBlockPos().distSqr(origin)).thenComparing(BlockEntity::getBlockPos)); return found;
    }
    private static void unindexIdentity(State state, BlockPos pos) {
        UUID previous = state.identityAt.remove(pos);
        if (previous == null) return;
        var positions = state.stationIdentities.get(previous);
        if (positions != null) { positions.remove(pos); if (positions.isEmpty()) state.stationIdentities.remove(previous); }
    }
    /** Handles packed data being restored onto an already loaded block entity. */
    public static void refreshIdentity(BlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level) || !LEVELS.containsKey(level)) return;
        var state = state(level);
        if (state.blocks.get(block.getBlockPos()) != block) return;
        unindexIdentity(state, block.getBlockPos());
        if (block instanceof QuartermasterBlockEntity station) {
            state.identityAt.put(block.getBlockPos(), station.stationId());
            state.stationIdentities.computeIfAbsent(station.stationId(), ignored -> new HashSet<>()).add(block.getBlockPos());
        }
    }
    public record AssignedStation(QuartermasterBlockEntity station, ServiceStatus status) {}
    /** Identity lookup visits only matching loaded stations, never a saved position or missing chunk. */
    public static AssignedStation assignedStation(ServerLevel level, ServiceStationAssignment assignment, BlockPos origin) {
        if (!assignment.dimension().equals(level.dimension().location()))
            return new AssignedStation(null, ServiceStatus.ASSIGNED_STATION_UNAVAILABLE);
        State state = state(level);
        if (!state.completeBlockIndex) return new AssignedStation(null, ServiceStatus.ASSIGNED_STATION_AMBIGUOUS);
        QuartermasterBlockEntity found = null;
        for (BlockPos position : state.stationIdentities.getOrDefault(assignment.stationId(), Set.of())) {
            BlockEntity block = state.blocks.get(position);
            if (!(block instanceof QuartermasterBlockEntity station) || station.isRemoved()
                    || !station.stationId().equals(assignment.stationId()) || !level.hasChunkAt(station.getBlockPos())
                    || level.getBlockEntity(station.getBlockPos()) != station) continue;
            if (found != null) return new AssignedStation(null, ServiceStatus.ASSIGNED_STATION_AMBIGUOUS);
            found = station;
        }
        if (found == null) return new AssignedStation(null, ServiceStatus.ASSIGNED_STATION_UNAVAILABLE);
        if (origin.distSqr(found.getBlockPos()) > 48 * 48) return new AssignedStation(null, ServiceStatus.ASSIGNED_STATION_OUT_OF_RANGE);
        return new AssignedStation(found, ServiceStatus.READY);
    }
    public static boolean assignmentAllows(Guard guard, QuartermasterBlockEntity station) {
        var assignment = GuardEquipmentServiceAccess.get(guard).assignment();
        return assignment == null || guard.level() instanceof ServerLevel level
                && assignedStation(level, assignment, guard.blockPosition()).station() == station;
    }
    public static List<BlockPos> nearbyArmories(QuartermasterBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level)) return List.of();
        return nearby(level, station.getBlockPos(), 16).stream().filter(block -> block instanceof ArmoryBlockEntity).limit(4).map(BlockEntity::getBlockPos).toList();
    }
    public static List<ServiceSource> sources(QuartermasterBlockEntity station) {
        List<ServiceSource> result = new ArrayList<>(); result.add(ServiceSource.of(station));
        if (station.getLevel() instanceof ServerLevel level) for (BlockPos pos : station.linkedArmories()) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ArmoryBlockEntity armory && pos.distSqr(station.getBlockPos()) <= 256) result.add(ServiceSource.of(armory));
        }
        return result;
    }
    public static ServicePlan plan(Guard guard) { return guard.level() instanceof ServerLevel level ? state(level).plans.get(guard.getUUID()) : null; }
    public static boolean reserved(BlockEntity source, int slot) {
        if (!(source.getLevel() instanceof ServerLevel level)) return false;
        return state(level).plans.values().stream().anyMatch(plan -> plan.source().block() == source && plan.sourceSlot() == slot || plan.station() == source && plan.returnSlot() == slot);
    }
    public static boolean reserve(ServicePlan plan) {
        if (!(plan.guard().level() instanceof ServerLevel level) || !ServiceTransactions.valid(plan)) return false;
        State state = state(level);
        if (state.plans.size() >= MAX_RESERVATIONS || state.plans.containsKey(plan.guard().getUUID())
                || reserved(plan.source().block(), plan.sourceSlot()) || plan.returnSlot() >= 0 && reserved(plan.station(), plan.returnSlot())) return false;
        state.plans.put(plan.guard().getUUID(), plan); GuardEquipmentServiceAccess.get(plan.guard()).status = ServiceStatus.RESERVED; return true;
    }
    public static void travelling(Guard guard) { if (guard.level() instanceof ServerLevel level) { state(level).travelling.add(guard.getUUID()); GuardEquipmentServiceAccess.get(guard).status = ServiceStatus.TRAVELLING; } }
    public static void cancel(Guard guard, ServiceStatus reason) {
        if (!(guard.level() instanceof ServerLevel level)) return;
        State state = state(level); state.travelling.remove(guard.getUUID());
        if (state.plans.remove(guard.getUUID()) != null) {
            var service = GuardEquipmentServiceAccess.get(guard); service.status = reason; service.nextAttempt = level.getGameTime() + 100;
        }
    }
    public static List<Guard> loadedGuards(ServerLevel level) { return List.copyOf(state(level).guards.values()); }
    public static boolean completeGuardIndex(ServerLevel level) { return state(level).completeGuardIndex; }
    public static void stopped(ServerStoppedEvent event) { LEVELS.keySet().removeIf(level -> level.getServer() == event.getServer()); }
    private static <T> List<T> rotate(LinkedHashMap<UUID, T> map) {
        List<T> result = new ArrayList<>(); int count = Math.min(INSPECTIONS, map.size());
        for (int i = 0; i < count; i++) { var entry = map.entrySet().iterator().next(); UUID id = entry.getKey(); T value = entry.getValue(); map.remove(id); map.put(id, value); result.add(value); } return result;
    }
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!LEVELS.containsKey(level)) continue;
            State state = state(level);
            for (Villager villager : rotate(state.villagers)) {
                if (!villager.isAlive() || villager.isRemoved()) { state.villagers.remove(villager.getUUID()); continue; }
                if (villager.getVillagerData().getProfession() != net.minecraftforge.registries.ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)) continue;
                villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site -> site.dimension().equals(level.dimension())).ifPresent(site -> {
                    if (state.blocks.get(site.pos()) instanceof QuartermasterBlockEntity station) station.operator = villager.getUUID();
                });
            }
            boolean transferred = false;
            for (Guard guard : rotate(state.guards)) {
                if (!guard.isAlive() || guard.isRemoved()) {
                    cancel(guard, ServiceStatus.RETRY);
                    if (guard.getHealth() <= 0 || guard.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED || guard.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED) ServiceRoster.get(level).forget(guard.getUUID());
                    state.guards.remove(guard.getUUID()); continue;
                }
                ServiceRoster.get(level).observe(guard);
                var service = GuardEquipmentServiceAccess.get(guard);
                if (guard.isNoAi() || service.policy() == GuardServiceState.Policy.MANUAL) { cancel(guard, ServiceStatus.MANUAL); continue; }
                var current = state.plans.get(guard.getUUID());
                if (current != null) {
                    if (!ServiceTransactions.valid(current)) { cancel(guard, ServiceStatus.RETRY); continue; }
                    if (!transferred && state.travelling.contains(guard.getUUID())) {
                        ServiceStatus status = ServiceTransactions.transfer(current);
                        if (status != ServiceStatus.TRAVELLING) { cancel(guard, status); transferred = true; }
                    }
                    continue;
                }
                if (level.getGameTime() < service.nextAttempt) continue;
                service.nextAttempt = level.getGameTime() + 100; service.status = ServiceEligibility.guard(guard);
                if (service.status != ServiceStatus.READY) continue;
                service.status = ServiceStatus.NO_STATION;
                List<? extends BlockEntity> candidates;
                if (service.assignment() == null) candidates = nearby(level, guard.blockPosition(), 48);
                else {
                    var assigned = assignedStation(level, service.assignment(), guard.blockPosition());
                    if (assigned.station() == null) { service.status = assigned.status(); continue; }
                    candidates = List.of(assigned.station());
                }
                for (BlockEntity block : candidates) {
                    if (!(block instanceof QuartermasterBlockEntity station)) continue;
                    if (!station.mayService(guard)) { service.status = ServiceStatus.DENIED; continue; }
                    station.serviceStatus = ServiceEligibility.station(station); service.status = station.serviceStatus;
                    if (service.status != ServiceStatus.READY) continue;
                    try {
                        var proposed = ServicePlanner.plan(guard, station, sources(station));
                        if (proposed != null && reserve(proposed)) break;
                    } catch (RuntimeException incompatibleStack) { service.status = ServiceStatus.RETRY; }
                }
            }
        }
    }
}
