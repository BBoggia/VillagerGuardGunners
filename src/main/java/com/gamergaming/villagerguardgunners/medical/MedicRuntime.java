package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.GuardMedicAccess;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Loaded-only triage with exclusive, transient treatment leases. No chunk requests or item snapshots persist. */
public final class MedicRuntime {
    public static final int MAX_ACTORS = 4096, REPAIR_BUDGET = 32, CANDIDATE_BUDGET = 64, MAX_LEASES = 16;
    public static final double RETREAT_BELOW = .35, RECOVER_AT = .75;
    public static final class Lease {
        public final Guard medic, patient;
        public final long expires;
        public CasualtyCarePolicy care;
        public long preparedAt = -1, medicDamage, patientDamage;
        public Vec3 medicPosition, patientPosition;
        public int slot;
        public net.minecraft.world.item.ItemStack supply;
        public TreatmentProfile profile;
        public UUID sourceIdentity, patientIdentity;
        public Lease(Guard medic, Guard patient) { this.medic = medic; this.patient = patient; expires = medic.level().getGameTime() + 240 + CasualtyCarePolicy.forPatient(patient).channelTicks(); }
    }
    private record Retreat(Guard medic, long expires) {}
    private static final class State {
        final LinkedHashMap<UUID, Guard> guards = new LinkedHashMap<>();
        final Map<Long, NavigableSet<UUID>> cells = new HashMap<>();
        final Map<UUID, Long> positions = new HashMap<>();
        final Set<UUID> injured = new HashSet<>();
        final Map<UUID, Lease> patients = new HashMap<>(), medics = new HashMap<>();
        final Map<UUID, Retreat> retreats = new HashMap<>();
    }
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();
    private static State state(Guard guard) { return LEVELS.computeIfAbsent((ServerLevel)guard.level(), ignored -> new State()); }
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof Guard guard) {
            var state = state(guard); if (state.guards.size() < MAX_ACTORS) { state.guards.put(guard.getUUID(), guard); repair(guard); }
        }
    }
    public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Guard guard)) return;
        unregister(guard);
    }
    private static void unregister(Guard guard) {
        var state = state(guard); cancel(guard, MedicState.Status.INTERRUPTED); state.guards.remove(guard.getUUID()); state.injured.remove(guard.getUUID());
        Long old = state.positions.remove(guard.getUUID()); if (old != null) removeCell(state, old, guard.getUUID());
        state.retreats.remove(guard.getUUID()); state.retreats.values().removeIf(value -> value.medic() == guard);
    }
    private static void removeCell(State state, long cell, UUID id) { var ids = state.cells.get(cell); if (ids != null) { ids.remove(id); if (ids.isEmpty()) state.cells.remove(cell); } }
    public static void damaged(LivingDamageEvent event) {
        if (!event.isCanceled() && event.getAmount() > 0 && event.getEntity() instanceof Guard guard && guard.level() instanceof ServerLevel) {
            GuardMedicAccess.get(guard).damageRevision++; repair(guard); if (state(guard).guards.containsKey(guard.getUUID())) state(guard).injured.add(guard.getUUID()); cancel(guard, MedicState.Status.INTERRUPTED);
        }
    }
    public static void healed(LivingHealEvent event) {
        if (event.getEntity() instanceof Guard guard && guard.level() instanceof ServerLevel) repair(guard);
    }
    public static void stopped(ServerStoppedEvent event) { LEVELS.keySet().removeIf(level -> level.getServer() == event.getServer()); }
    private static void repair(Guard guard) {
        var state = state(guard); if (!state.guards.containsKey(guard.getUUID())) return;
        long cell = guard.chunkPosition().toLong(); Long old = state.positions.put(guard.getUUID(), cell);
        if (old != null && old != cell) removeCell(state, old, guard.getUUID());
        state.cells.computeIfAbsent(cell, ignored -> new TreeSet<>()).add(guard.getUUID());
        if (guard.isAlive() && MedicEligibility.fraction(guard) < 1) state.injured.add(guard.getUUID()); else state.injured.remove(guard.getUUID());
    }
    private static List<Guard> nearby(Guard guard, boolean injuredOnly) {
        var state = state(guard); var result = new ArrayList<Guard>(); int inspected = 0;
        var search = GuardMedicAccess.get(guard); var pos = guard.blockPosition();
        int minX = (pos.getX() - 32) >> 4, minZ = (pos.getZ() - 32) >> 4;
        int width = ((pos.getX() + 32) >> 4) - minX + 1;
        int depth = ((pos.getZ() + 32) >> 4) - minZ + 1;
        search.searchAfter.keySet().removeIf(cell -> {
            int x = net.minecraft.world.level.ChunkPos.getX(cell), z = net.minecraft.world.level.ChunkPos.getZ(cell);
            return x < minX || x >= minX + width || z < minZ || z >= minZ + depth;
        });
        int start = Math.floorMod(search.searchCell, width * depth);
        for (int visited = 0; visited < width * depth && inspected < CANDIDATE_BUDGET; visited++) {
            int index = (start + visited) % (width * depth);
            search.searchCell = (index + 1) % (width * depth);
            long cell = net.minecraft.world.level.ChunkPos.asLong(minX + index / depth, minZ + index % depth);
            var ids = state.cells.get(cell); if (ids == null || ids.isEmpty()) continue;
            // Each searcher resumes independently, including within a crowded cell. Tree lookup
            // avoids walking skipped actors, and neither other medics nor UUID removal reset progress.
            UUID after = search.searchAfter.get(cell);
            var iterator = (after == null ? ids : ids.tailSet(after, false)).iterator();
            for (int count = 0, size = ids.size(); count < size && inspected < CANDIDATE_BUDGET; count++) {
                if (!iterator.hasNext()) iterator = ids.iterator();
                UUID id = iterator.next(); search.searchAfter.put(cell, id); inspected++;
                Guard other = state.guards.get(id);
                if (other != null && (!injuredOnly || state.injured.contains(id)) && other.distanceToSqr(guard) <= 1024) result.add(other);
            }
        }
        result.sort(Comparator.comparingDouble(MedicEligibility::fraction).thenComparingDouble(guard::distanceToSqr).thenComparing(Guard::getUUID)); return result;
    }
    public static Lease lease(Guard guard) { if (!(guard.level() instanceof ServerLevel)) return null; var state = state(guard); var lease = state.medics.get(guard.getUUID()); return lease != null ? lease : state.patients.get(guard.getUUID()); }
    public static boolean busy(Guard guard) { return lease(guard) != null || retreating(guard) || TransportRuntime.busy(guard); }
    public static boolean retreating(Guard guard) { return guard.level() instanceof ServerLevel && state(guard).retreats.containsKey(guard.getUUID()); }
    public static boolean channeling(Guard guard) { var lease = lease(guard); return lease != null && lease.preparedAt >= 0; }
    public static void cancel(Guard guard, MedicState.Status reason) {
        var lease = lease(guard); if (lease == null) return;
        var state = state(guard); state.medics.remove(lease.medic.getUUID(), lease); state.patients.remove(lease.patient.getUUID(), lease);
        GuardMedicAccess.get(lease.medic).status = reason; GuardMedicAccess.get(lease.medic).nextAssessment = guard.level().getGameTime() + 40;
        GuardMedicAccess.get(lease.patient).status = reason;
    }
    private static boolean patientAvailable(Guard patient) {
        return GuardLifeRuntime.casualty(patient) ? GuardLifeRuntime.patientAvailable(patient) : MedicEligibility.available(patient, safeRetreat(patient));
    }
    public static boolean reserve(Guard medic, Guard patient) {
        if (TransportRuntime.busy(medic) || TransportRuntime.busy(patient) || PlayerTreatmentRuntime.busy(patient)) return false;
        var duty = GuardMedicAccess.get(medic);
        if (patient.getUUID().equals(duty.unreachablePatient) && medic.level().getGameTime() < duty.unreachableUntil) return false;
        if (!MedicEligibility.available(medic, false) || !patientAvailable(patient) || !MedicEligibility.allied(medic, patient)
                || !GuardMedicAccess.get(medic).medic() || MedicEligibility.supplySlot(medic) < 0 || MedicEligibility.fraction(patient) >= 1
                || medic.level().getGameTime() < GuardMedicalAccess.get(patient).readyAt()) return false;
        var state = state(medic);
        if (state.patients.size() >= MAX_LEASES || lease(medic) != null || lease(patient) != null || retreating(medic)) return false;
        var lease = new Lease(medic, patient); state.medics.put(medic.getUUID(), lease); state.patients.put(patient.getUUID(), lease);
        GuardMedicAccess.get(medic).status = MedicState.Status.APPROACH; return true;
    }
    public static boolean valid(Lease lease) {
        return lease != null && lease(lease.medic) == lease && lease.medic.level().getGameTime() < lease.expires
                && GuardMedicAccess.get(lease.medic).medic() && MedicEligibility.available(lease.medic, false)
                && !PlayerTreatmentRuntime.busy(lease.patient) && patientAvailable(lease.patient) && MedicEligibility.allied(lease.medic, lease.patient)
                && MedicEligibility.fraction(lease.patient) < 1 && lease.medic.distanceToSqr(lease.patient) <= 1600
                && lease.medic.level().getGameTime() >= GuardMedicalAccess.get(lease.patient).readyAt();
    }
    public static void treat(Lease lease) {
        if (!valid(lease)) { if (lease != null) cancel(lease.medic, MedicState.Status.INTERRUPTED); return; }
        var medic = lease.medic; var patient = lease.patient; long now = medic.level().getGameTime();
        if (medic.distanceToSqr(patient) > 9 || !medic.hasLineOfSight(patient)) { lease.preparedAt = -1; return; }
        if (lease.preparedAt < 0) {
            if (MedicEligibility.supplySlot(medic) < 0) { cancel(medic, MedicState.Status.NO_SUPPLY); return; }
            lease.slot = MedicEligibility.supplySlot(medic); lease.supply = GuardMedicalAccess.get(medic).inventory().getStackInSlot(lease.slot).copy();
            lease.profile = TreatmentProfiles.resolve(lease.supply); lease.sourceIdentity = GuardMedicalAccess.get(medic).dropId(); lease.patientIdentity = GuardMedicalAccess.get(patient).dropId();
            lease.care = CasualtyCarePolicy.forPatient(patient);
            lease.preparedAt = now; lease.medicPosition = medic.position(); lease.patientPosition = patient.position();
            lease.medicDamage = GuardMedicAccess.get(medic).damageRevision; lease.patientDamage = GuardMedicAccess.get(patient).damageRevision;
            GuardMedicAccess.get(medic).status = MedicState.Status.PREPARE; return;
        }
        if (!channelValid(lease)) { cancel(medic, MedicState.Status.INTERRUPTED); return; }
        if (now - lease.preparedAt < lease.care.channelTicks()) return;
        var result = MedicalTreatment.fromMedic(medic, patient, lease.slot, () -> channelValid(lease));
        cancel(medic, result == MedicalTreatment.Result.HEALED ? MedicState.Status.HEALED : MedicState.Status.INTERRUPTED);
        if (result == MedicalTreatment.Result.HEALED) TransportRuntime.tryStart(medic, patient);
    }
    public static boolean channelValid(Lease lease) {
        return valid(lease) && lease.preparedAt >= 0 && lease.care != null && lease.care.validFor(lease.patient) && lease.medic.hasLineOfSight(lease.patient) && lease.medic.distanceToSqr(lease.patient) <= 9
                && lease.medic.position().distanceToSqr(lease.medicPosition) <= .04 && lease.patient.position().distanceToSqr(lease.patientPosition) <= .04
                && net.minecraft.world.item.ItemStack.matches(lease.supply, GuardMedicalAccess.get(lease.medic).inventory().getStackInSlot(lease.slot))
                && lease.profile.equals(TreatmentProfiles.resolve(lease.supply))
                && lease.sourceIdentity.equals(GuardMedicalAccess.get(lease.medic).dropId()) && lease.patientIdentity.equals(GuardMedicalAccess.get(lease.patient).dropId())
                && GuardMedicAccess.get(lease.medic).damageRevision == lease.medicDamage && GuardMedicAccess.get(lease.patient).damageRevision == lease.patientDamage;
    }
    public static Guard destination(Guard guard) {
        if (!(guard.level() instanceof ServerLevel)) return null;
        var lease = lease(guard); if (lease != null) return lease.medic == guard ? lease.patient : (guard.distanceToSqr(lease.medic) <= 9 ? guard : null);
        var retreat = state(guard).retreats.get(guard.getUUID());
        if (retreat != null && (guard.level().getGameTime() >= retreat.expires() || !safeDestination(guard, retreat.medic()))) { stopRetreat(guard); return null; }
        return retreat == null ? null : retreat.medic();
    }
    public static boolean safeDestination(Guard guard, Guard medic) {
        var target = guard.getTarget();
        return MedicEligibility.allied(guard, medic) && (target == null || !target.isAlive()
                || (medic.distanceToSqr(target) > 144 && (guard.distanceToSqr(medic) <= 9
                || medic.distanceToSqr(target) > guard.distanceToSqr(target) + 16)));
    }
    public static boolean safeRetreat(Guard guard) {
        return retreating(guard) && (guard.getTarget() == null || !guard.getTarget().isAlive() || guard.distanceToSqr(guard.getTarget()) > 144);
    }
    public static void stopRetreat(Guard guard) {
        if (!(guard.level() instanceof ServerLevel)) return;
        if (state(guard).retreats.remove(guard.getUUID()) == null) return; GuardMedicAccess.get(guard).nextAssessment = guard.level().getGameTime() + 100;
        GuardMedicAccess.get(guard).status = MedicState.Status.NO_SAFE_DESTINATION;
    }
    public static void assessRetreat(Guard guard) {
        var state = state(guard); var medical = GuardMedicAccess.get(guard);
        if (!ModConfigs.GUARD_GUNNERS.medical.retreat.get() || !medical.retreat() || MedicEligibility.fraction(guard) >= RECOVER_AT) { medical.recovering = false; state.retreats.remove(guard.getUUID()); return; }
        if (MedicEligibility.fraction(guard) <= RETREAT_BELOW) medical.recovering = true;
        if (!medical.recovering || !MedicEligibility.request(guard, true)) { state.retreats.remove(guard.getUUID()); return; }
        Guard destination = nearby(guard, false).stream().filter(other -> GuardMedicAccess.get(other).medic() && MedicEligibility.available(other, false)
                && MedicEligibility.allied(other, guard) && MedicEligibility.supplySlot(other) >= 0
                && safeDestination(guard, other))
                .min(Comparator.comparingDouble(guard::distanceToSqr)).orElse(null);
        if (destination == null) { state.retreats.remove(guard.getUUID()); medical.status = MedicState.Status.NO_SAFE_DESTINATION; }
        else { state.retreats.putIfAbsent(guard.getUUID(), new Retreat(destination, guard.level().getGameTime() + 240)); medical.status = MedicState.Status.RETREAT; }
    }
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 10 != 0) return;
        for (var level : event.getServer().getAllLevels()) {
            var state = LEVELS.get(level); if (state == null) continue;
            for (var lease : List.copyOf(state.patients.values())) if (!valid(lease)) cancel(lease.medic, MedicState.Status.INTERRUPTED);
            for (int i = 0, count = Math.min(REPAIR_BUDGET, state.guards.size()); i < count; i++) {
                var entry = state.guards.entrySet().iterator().next(); var guard = entry.getValue(); state.guards.remove(entry.getKey()); state.guards.put(entry.getKey(), guard);
                if (!guard.isAlive() || guard.isRemoved() || guard.level() != level) { unregister(guard); continue; }
                repair(guard);
                var medical = GuardMedicAccess.get(guard);
                if (lease(guard) != null || TransportRuntime.busy(guard)) continue;
                if (level.getGameTime() < medical.nextAssessment) continue; medical.nextAssessment = level.getGameTime() + 40;
                if (!MedicEligibility.enabled()) { state.retreats.remove(guard.getUUID()); medical.recovering = false; medical.status = MedicState.Status.DISABLED; continue; }
                if (MedicEligibility.playerControl(guard)) { state.retreats.remove(guard.getUUID()); medical.status = MedicState.Status.PLAYER_CONTROL; continue; }
                assessRetreat(guard);
                if (!medical.medic() || !MedicEligibility.available(guard, false) || retreating(guard)) continue;
                if (MedicEligibility.supplySlot(guard) < 0) { medical.status = MedicState.Status.NO_SUPPLY; continue; }
                for (Guard patient : nearby(guard, true)) if (TransportRuntime.tryStart(guard, patient) || reserve(guard, patient)) break;
            }
        }
    }
}
