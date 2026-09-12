package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Transient exclusive carrier/patient/cot claims. Vanilla saves the original passenger once. */
public final class TransportRuntime {
    public static final int MAX_COTS = 256, MAX_CARRIERS = 16, RANGE = 32, LEASE_TICKS = 400;
    public static final class Lease {
        public final Guard carrier, patient;
        public final BlockPos cot;
        public final long expires;
        public boolean carried;
        private boolean mounting, releasing, delivery;
        private long nextReleaseAttempt;
        private Vec3 pendingDrop;
        private Lease(Guard carrier, Guard patient, BlockPos cot) {
            this.carrier = carrier; this.patient = patient; this.cot = cot.immutable(); expires = carrier.level().getGameTime() + LEASE_TICKS;
        }
    }
    private static final class State {
        final Set<BlockPos> cots = new LinkedHashSet<>();
        final Map<UUID, Lease> actors = new HashMap<>();
        final Map<BlockPos, Lease> destinations = new HashMap<>();
    }
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();
    private TransportRuntime() { }
    public static boolean enabled() { return GuardLifeRuntime.enabled() && MedicEligibility.enabled() && ModConfigs.GUARD_GUNNERS.medical.transport.get(); }
    public static Lease lease(Guard guard) { var state = LEVELS.get(guard.level()); return state == null ? null : state.actors.get(guard.getUUID()); }
    public static boolean busy(Guard guard) {
        if (guard.level().isClientSide) return guard.getPassengers().stream().anyMatch(e -> e instanceof Guard g && GuardLifeRuntime.blocked(g));
        return lease(guard) != null || guard.getPassengers().stream().anyMatch(e -> e instanceof Guard patient && GuardTransportAccess.get(patient).present());
    }
    public static boolean awaitingRelease(Guard guard) {
        var lease = lease(guard);
        return lease != null && lease.carrier == guard && lease.releasing || lease == null && busy(guard);
    }
    public static void cotLoaded(RecoveryCotBlockEntity cot) {
        if (!(cot.getLevel() instanceof ServerLevel level)) return;
        var state = LEVELS.computeIfAbsent(level, ignored -> new State());
        if (state.cots.size() < MAX_COTS) state.cots.add(cot.getBlockPos().immutable());
    }
    public static void cotRemoved(RecoveryCotBlockEntity cot) {
        var state = LEVELS.get(cot.getLevel()); if (state == null) return;
        state.cots.remove(cot.getBlockPos()); var lease = state.destinations.get(cot.getBlockPos());
        if (lease != null) cancel(lease.carrier);
        if (cot.getLevel() instanceof ServerLevel level) CotRestRuntime.removed(level, cot.getBlockPos());
    }
    public static void cotUnloaded(RecoveryCotBlockEntity cot) {
        var state = LEVELS.get(cot.getLevel()); if (state == null) return;
        state.cots.remove(cot.getBlockPos()); var lease = state.destinations.get(cot.getBlockPos());
        if (lease != null) cancel(lease.carrier);
        // The patient carries the saved anchor. Unloading furniture is not breaking it.
        if (cot.getLevel() instanceof ServerLevel level) CotRestRuntime.unloaded(level, cot.getBlockPos());
    }
    public static boolean tryStart(Guard carrier, Guard patient) {
        if (CotRestRuntime.anchored(patient) || !CasualtyCarePolicy.timeForEvacuation(patient) || PlayerTreatmentRuntime.busy(patient) || !enabled() || !(carrier.level() instanceof ServerLevel level) || level.getGameTime() < GuardMedicAccess.get(carrier).nextTransportAttempt
                || !MedicEligibility.available(carrier, false) || !carrierReady(carrier) || !patientReady(patient)
                || !MedicEligibility.allied(carrier, patient) || carrier.distanceToSqr(patient) > RANGE * RANGE
                || MedicRuntime.lease(carrier) != null || MedicRuntime.lease(patient) != null
                || busy(carrier) || busy(patient) || GuardTransportAccess.get(patient).present() || carrier.isVehicle() || patient.isPassenger()) return false;
        var state = LEVELS.get(level);
        if (state == null || state.destinations.size() >= MAX_CARRIERS || onCot(patient)) return false;
        var medical = GuardMedicAccess.get(carrier);
        if (!patient.getUUID().equals(medical.transportPatient)) { medical.failedCots.clear(); medical.transportPatient = patient.getUUID(); }
        medical.failedCots.values().removeIf(deadline -> deadline <= level.getGameTime());
        if (medical.failedCots.size() >= 3) return false;
        BlockPos selected = null; double distance = Double.POSITIVE_INFINITY;
        for (var cot : state.cots) {
            double next = patient.distanceToSqr(Vec3.atCenterOf(cot));
            if (!medical.failedCots.containsKey(cot) && next <= RANGE * RANGE && next < distance && !state.destinations.containsKey(cot) && cotAvailable(level, cot, carrier, patient)) { selected = cot; distance = next; }
        }
        if (selected == null) { medical.transportReason = medical.failedCots.isEmpty() ? "no_cot" : "route_blocked"; GuardMedicAccess.get(patient).transportReason = medical.transportReason; return false; }
        medical.transportReason = "evacuating"; GuardMedicAccess.get(patient).transportReason = "evacuating";
        var lease = new Lease(carrier, patient, selected);
        state.actors.put(carrier.getUUID(), lease); state.actors.put(patient.getUUID(), lease); state.destinations.put(selected, lease);
        MedicRuntime.stopRetreat(carrier); carrier.getNavigation().stop(); stopEquipment(carrier);
        return true;
    }
    private static boolean carrierReady(Guard carrier) {
        return MedicEligibility.request(carrier, false) && GuardMedicAccess.get(carrier).medic()
                && MedicEligibility.fraction(carrier) >= MedicRuntime.RECOVER_AT && MedicEligibility.supplySlot(carrier) >= 0;
    }
    private static boolean patientReady(Guard patient) {
        return GuardLifeAccess.get(patient).stage() == GuardLifeState.Stage.STABILIZED && patient.isAlive() && !patient.isRemoved()
                && !patient.isNoAi() && !patient.isSleeping() && !MedicEligibility.playerControl(patient)
                && GuardTransportAccess.get(patient).known() && GuardMedicalAccess.get(patient).available()
                && GuardLifeAccess.cot(patient).known()
                && GuardEquipmentServiceAccess.get(patient).readable();
    }
    public static boolean valid(Lease lease) {
        if (lease == null || lease.releasing || lease(lease.carrier) != lease || lease(lease.patient) != lease || !enabled()
                || !(lease.carrier.level() instanceof ServerLevel level) || lease.patient.level() != level
                || level.getGameTime() >= lease.expires || !carrierReady(lease.carrier) || !patientReady(lease.patient)
                || !MedicEligibility.allied(lease.carrier, lease.patient) || !cotAvailable(level, lease.cot, lease.carrier, lease.patient)) return false;
        return lease.carried ? lease.patient.getVehicle() == lease.carrier && lease.carrier.getPassengers().size() == 1
                : !lease.carrier.isVehicle() && !lease.patient.isPassenger() && lease.carrier.distanceToSqr(lease.patient) <= RANGE * RANGE;
    }
    public static boolean pickup(Lease lease) {
        if (!valid(lease) || lease.carried || lease.carrier.distanceToSqr(lease.patient) > 4 || !lease.carrier.hasLineOfSight(lease.patient)
                || !safeStanding(lease.patient, lease.patient.position()) || !clear((ServerLevel) lease.carrier.level(), carryBox(lease.carrier.position()))) return false;
        var hint = GuardTransportAccess.get(lease.patient);
        hint.mark(lease.carrier.getUUID(), lease.carrier.level().dimension().location(), lease.patient.position());
        lease.mounting = true;
        try {
            if (!lease.patient.startRiding(lease.carrier, true) || lease(lease.carrier) != lease) { hint.clear(); if (lease.patient.getVehicle() == lease.carrier) lease.patient.stopRiding(); return false; }
            lease.carried = true; return true;
        } finally { lease.mounting = false; }
    }
    public static Vec3 destination(Lease lease) { return cotPosition((ServerLevel) lease.carrier.level(), lease.cot); }
    public static List<BlockPos> approaches(Lease lease) {
        var level = (ServerLevel) lease.carrier.level();
        if (cotPosition(level, lease.cot) == null) return List.of();
        var facing = level.getBlockState(lease.cot).getValue(RecoveryCotBlock.FACING);
        List<BlockPos> result = new ArrayList<>(CotApproachRoutes.MAX_APPROACHES);
        for (var part : List.of(lease.cot, lease.cot.relative(facing.getOpposite())))
            for (var side : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
                var point = part.relative(side, 2);
                if (safeCarryStanding(lease.carrier, point)) result.add(point);
            }
        return List.copyOf(result);
    }
    /** Standing support and passenger envelope are separate from ordinary guard clearance. */
    static boolean safeCarryStanding(Guard carrier, BlockPos position) {
        var point = Vec3.atBottomCenterOf(position);
        return safeStanding(carrier, point) && clear((ServerLevel) carrier.level(), carryBox(point));
    }
    public static boolean deliver(Lease lease) {
        if (!valid(lease) || !lease.carried) return false;
        Vec3 destination = destination(lease);
        if (destination == null || lease.carrier.distanceToSqr(destination) > 9 || !safeStanding(lease.patient, destination)) return false;
        release(lease, destination); return CotRestRuntime.valid(lease.patient);
    }
    public static void failed(Guard actor) {
        var lease = lease(actor); if (lease == null) return;
        var medical = GuardMedicAccess.get(lease.carrier);
        if (medical.failedCots.size() < 3) medical.failedCots.put(lease.cot, actor.level().getGameTime() + 200);
        medical.transportReason = "route_blocked"; GuardMedicAccess.get(lease.patient).transportReason = "route_blocked";
        cancel(actor); medical.nextTransportAttempt = actor.level().getGameTime() + 10;
    }
    public static void cancel(Guard actor) {
        var lease = lease(actor); if (lease != null) release(lease, null);
    }
    private static void release(Lease lease, Vec3 destination) {
        long now = lease.carrier.level().getGameTime();
        lease.carrier.getNavigation().stop();
        if (now < lease.nextReleaseAttempt) return;
        if (!lease.releasing) { lease.delivery = destination != null; lease.pendingDrop = destination != null ? destination : lease.carried ? recoveryPosition(lease.patient) : null; }
        lease.releasing = true;
        if (lease.patient.getVehicle() == lease.carrier) lease.patient.stopRiding();
        if (lease.patient.getVehicle() == lease.carrier) {
            // Honor canceled dismounts without abandoning the relationship or saving an
            // unmarked passenger. Keep the carrier still and retry on loaded ticks.
            lease.nextReleaseAttempt = now + 20; return;
        }
        var state = LEVELS.get(lease.carrier.level());
        if (state != null) { state.actors.remove(lease.carrier.getUUID(), lease); state.actors.remove(lease.patient.getUUID(), lease); state.destinations.remove(lease.cot, lease); }
        Vec3 drop = lease.pendingDrop;
        if (drop != null && (!safeStanding(lease.patient, drop) || lease.patient.distanceToSqr(drop) > 64)) drop = recoveryPosition(lease.patient);
        GuardTransportAccess.get(lease.patient).clear();
        if (drop != null && !lease.patient.isRemoved()) {
            lease.patient.setPos(drop); lease.patient.setDeltaMovement(Vec3.ZERO);
            if (lease.delivery && lease.pendingDrop != null && lease.pendingDrop.equals(cotPosition((ServerLevel) lease.carrier.level(), lease.cot)) && drop.equals(lease.pendingDrop)) CotRestRuntime.attach(lease.patient, lease.cot);
        }
        GuardMedicAccess.get(lease.carrier).nextTransportAttempt = now + 200;
        GuardMedicAccess.get(lease.carrier).nextAssessment = now + 10;
    }
    public static void tickGuard(Guard guard) {
        if (!(guard.level() instanceof ServerLevel)) return;
        CotRestRuntime.tick(guard);
        var lease = lease(guard); var hint = GuardTransportAccess.get(guard);
        if (lease == null && hint.present() && (hint.known() || guard.isPassenger())) {
            // Loading nested passengers finishes before entity ticking. Resume safely in place,
            // never restore a stale destination/lease or request the carrier's old dimension.
            if (guard.level().getGameTime() < hint.recoveryRetryAt) return;
            var drop = recoveryPosition(guard);
            if (guard.isPassenger()) guard.stopRiding();
            if (guard.isPassenger()) { hint.recoveryRetryAt = guard.level().getGameTime() + 20; return; }
            hint.clear();
            if (drop != null) { guard.setPos(drop); guard.setDeltaMovement(Vec3.ZERO); }
            return;
        }
        if (lease == null) { if (awaitingRelease(guard)) { guard.getNavigation().stop(); guard.setDeltaMovement(0, guard.getDeltaMovement().y, 0); } return; }
        if (!valid(lease)) { cancel(guard); return; }
        if (lease.carrier != guard) return;
        if (lease.carried) {
            if (!clear((ServerLevel) guard.level(), carryBox(guard.position()))) { cancel(guard); return; }
            var point = findStanding(lease.patient, guard.position());
            if (point != null) GuardTransportAccess.get(lease.patient).mark(guard.getUUID(), guard.level().dimension().location(), point);
        }
        stopEquipment(guard);
    }
    private static void stopEquipment(Guard carrier) {
        carrier.stopUsingItem(); carrier.setSprinting(false);
        var operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(carrier);
        operator.cancelReload(); operator.aim(false);
    }
    public static boolean onCot(Guard patient) {
        return CotRestRuntime.valid(patient);
    }
    public static Vec3 cotPosition(net.minecraft.world.level.Level level, BlockPos head) {
        if (!level.hasChunkAt(head)) return null; var block = level.getBlockState(head);
        if (!block.is(ModBlocks.RECOVERY_COT.get()) || block.getValue(RecoveryCotBlock.PART) != BedPart.HEAD) return null;
        Direction facing = block.getValue(RecoveryCotBlock.FACING); BlockPos foot = head.relative(facing.getOpposite());
        if (!level.hasChunkAt(foot)) return null; var other = level.getBlockState(foot);
        if (!other.is(block.getBlock()) || other.getValue(RecoveryCotBlock.PART) != BedPart.FOOT || other.getValue(RecoveryCotBlock.FACING) != facing) return null;
        return Vec3.atBottomCenterOf(head).add(-facing.getStepX() * .5, .5, -facing.getStepZ() * .5);
    }
    private static boolean cotAvailable(ServerLevel level, BlockPos cot, Guard carrier, Guard patient) {
        if (CotRestRuntime.occupied(level, cot, patient)) return false;
        var point = cotPosition(level, cot); if (point == null || !safeStanding(patient, point)) return false;
        if (CotRestRuntime.exitPosition(patient, cot) == null) return false;
        var box = new AABB(point.x - .9, point.y, point.z - .9, point.x + .9, point.y + 2, point.z + .9);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != carrier && e != patient && e.isAlive()).isEmpty();
    }
    private static Vec3 recoveryPosition(Guard patient) {
        var hint = GuardTransportAccess.get(patient); var safe = hint.safePosition();
        if (safe != null && patient.level().dimension().location().equals(hint.dimension()) && patient.distanceToSqr(safe) <= 64 && safeStanding(patient, safe)) return safe;
        return findStanding(patient, patient.position());
    }
    static Vec3 findStanding(Guard patient, Vec3 near) {
        var base = BlockPos.containing(near);
        for (int radius = 0; radius <= 2; radius++) for (int y = 0; y >= -2; y--) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            Vec3 point = Vec3.atBottomCenterOf(base.offset(x, y, z)); if (safeStanding(patient, point)) return point;
        }
        return null;
    }
    static boolean safeStanding(Guard patient, Vec3 position) {
        if (!(patient.level() instanceof ServerLevel level)) return false;
        var box = patient.getBoundingBox().move(position.subtract(patient.position()));
        if (!clear(level, box)) return false;
        BlockPos floor = BlockPos.containing(position.add(0, -.05, 0));
        if (!level.hasChunkAt(floor)) return false; var block = level.getBlockState(floor);
        return block.getFluidState().isEmpty() && !block.is(Blocks.MAGMA_BLOCK) && !block.is(Blocks.CACTUS)
                && !block.is(Blocks.CAMPFIRE) && !block.is(Blocks.SOUL_CAMPFIRE)
                && (block.isFaceSturdy(level, floor, Direction.UP) || block.is(ModBlocks.RECOVERY_COT.get()));
    }
    static AABB carryBox(Vec3 point) { return new AABB(point.x - .7, point.y, point.z - .7, point.x + .7, point.y + 3.8, point.z + .7); }
    static boolean clear(ServerLevel level, AABB box) {
        if (!level.getWorldBorder().isWithinBounds(box) || box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()) return false;
        for (int x = net.minecraft.util.Mth.floor(box.minX) >> 4; x <= net.minecraft.util.Mth.floor(box.maxX) >> 4; x++)
            for (int z = net.minecraft.util.Mth.floor(box.minZ) >> 4; z <= net.minecraft.util.Mth.floor(box.maxZ) >> 4; z++) if (!level.getChunkSource().hasChunk(x, z)) return false;
        return !level.getBlockCollisions(null, box).iterator().hasNext() && !level.containsAnyLiquid(box);
    }
    public static void left(EntityLeaveLevelEvent event) { if (event.getEntity() instanceof Guard guard && !event.getLevel().isClientSide()) { cancel(guard); CotRestRuntime.left(guard); } }
    public static void damaged(LivingDamageEvent event) { if (!event.isCanceled() && event.getAmount() > 0 && event.getEntity() instanceof Guard guard) cancel(guard); }
    public static void travel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Guard guard && busy(guard)) { var lease = lease(guard); if (lease != null && lease.carried) event.setCanceled(true); cancel(guard); }
    }
    public static void mount(EntityMountEvent event) {
        if (event.getEntityMounting() instanceof Guard guard) {
            var lease = lease(guard);
            if (lease != null && !(lease.mounting && lease.patient == guard && event.getEntityBeingMounted() == lease.carrier && event.isMounting())) {
                if (event.isMounting()) event.setCanceled(true); else if (!lease.releasing) cancel(guard);
            }
        }
        if (event.isMounting() && event.getEntityBeingMounted() instanceof Guard carrier) {
            var lease = lease(carrier); if (lease != null && !(lease.mounting && event.getEntityMounting() == lease.patient)) event.setCanceled(true);
        }
    }
    public static void stopping(ServerStoppingEvent event) {
        for (var level : event.getServer().getAllLevels()) { var state = LEVELS.get(level); if (state != null) for (var lease : List.copyOf(state.destinations.values())) cancel(lease.carrier); LEVELS.remove(level); }
        CotRestRuntime.stopping(event.getServer());
    }
}
