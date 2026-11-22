package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Loaded occupancy, owned by the original resting patient. No block ticker or chunk tickets. */
public final class CotRestRuntime {
    private static final Map<ServerLevel, Map<BlockPos, Guard>> LEVELS = new WeakHashMap<>();
    private CotRestRuntime() { }
    public static boolean enabled() { return GuardLifeRuntime.enabled() && ModConfigs.GUARD_GUNNERS.medical.transport.get(); }
    public static boolean anchored(Guard guard) {
        return guard.level().isClientSide ? ((GuardLifeAccess) guard).villagerGuardGunners$getCotVisual().isPresent() : GuardLifeAccess.cot(guard).head() != null;
    }
    public static boolean occupied(ServerLevel level, BlockPos head, Guard patient) {
        var map = LEVELS.get(level); var other = map == null ? null : map.get(head);
        if (other != null && (other.isRemoved() || !other.isAlive() || !head.equals(GuardLifeAccess.cot(other).head()))) { map.remove(head, other); other = null; }
        return other != null && other != patient;
    }
    public static boolean attach(Guard patient, BlockPos head) {
        if (!(patient.level() instanceof ServerLevel level) || !enabled() || !GuardLifeAccess.cot(patient).known()
                || !GuardLifeRuntime.casualty(patient) || patient.isPassenger() || occupied(level, head, patient)) return false;
        var point = TransportRuntime.cotPosition(level, head);
        if (point == null || !TransportRuntime.safeStanding(patient, point) || patient.distanceToSqr(point) > 64) return false;
        var map = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
        if (map.size() >= TransportRuntime.MAX_COTS && !map.containsKey(head)) return false;
        var facing = level.getBlockState(head).getValue(RecoveryCotBlock.FACING);
        GuardLifeAccess.cot(patient).anchor(head, facing, level.dimension().location()); map.put(head.immutable(), patient);
        patient.setPos(point); align(patient, facing); synchronize(patient); return true;
    }
    public static boolean valid(Guard patient) {
        if (!(patient.level() instanceof ServerLevel level)) return false;
        var rest = GuardLifeAccess.cot(patient);
        if (!enabled() || rest.head() == null || !patient.isAlive() || patient.isRemoved() || patient.isPassenger()
                || !level.dimension().location().equals(rest.dimension()) || !GuardLifeAccess.get(patient).blocked()) return false;
        var point = TransportRuntime.cotPosition(level, rest.head());
        return point != null && level.getBlockState(rest.head()).getValue(RecoveryCotBlock.FACING) == rest.facing()
                && patient.distanceToSqr(point) < .0625 && TransportRuntime.safeStanding(patient, point);
    }
    public static Vec3 exitPosition(Guard patient, BlockPos head) {
        if (!(patient.level() instanceof ServerLevel level)) return null;
        var point = TransportRuntime.cotPosition(level, head); if (point == null) return null;
        var facing = level.getBlockState(head).getValue(RecoveryCotBlock.FACING);
        var foot = head.relative(facing.getOpposite());
        for (var part : List.of(head, foot)) for (var side : List.of(facing.getClockWise(), facing.getCounterClockWise(), facing, facing.getOpposite())) {
            var pos = part.relative(side); if (pos.equals(head) || pos.equals(foot)) continue;
            var exit = Vec3.atBottomCenterOf(pos);
            if (TransportRuntime.safeStanding(patient, exit) && level.noCollision(patient, patient.getBoundingBox().move(exit.subtract(patient.position())))) return exit;
        }
        return null;
    }
    public static void tick(Guard patient) {
        var rest = GuardLifeAccess.cot(patient); if (rest.head() == null || !(patient.level() instanceof ServerLevel level)) return;
        if (!valid(patient)) { release(patient, true); return; }
        var map = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
        var other = map.get(rest.head());
        if (other != null && other != patient && !other.isRemoved() && valid(other)) {
            // The lower UUID wins even when loaded entity tick order changes.
            if (patient.getUUID().compareTo(other.getUUID()) > 0) { release(patient, true); return; }
            release(other, true);
        }
        if (map.size() >= TransportRuntime.MAX_COTS && !map.containsKey(rest.head())) { release(patient, true); return; }
        map.put(rest.head(), patient); align(patient, rest.facing()); synchronize(patient);
        var life = GuardLifeAccess.get(patient);
        if (life.stage() == GuardLifeState.Stage.RECOVERING && life.remaining() == 0 && level.getGameTime() >= rest.nextExitAttempt) {
            rest.nextExitAttempt = level.getGameTime() + 20;
            var exit = exitPosition(patient, rest.head());
            if (exit != null) { release(patient, false); patient.setPos(exit); patient.setDeltaMovement(Vec3.ZERO); }
        }
    }
    private static void align(Guard patient, Direction facing) {
        patient.setDeltaMovement(Vec3.ZERO); patient.setYRot(facing.toYRot()); patient.setXRot(0);
        // Cot travel is suspended, so vanilla never decays its previous walking
        // animation. Reset both interpolation samples to prevent twitching legs.
        patient.walkAnimation.setSpeed(0); patient.walkAnimation.update(0, 1);
        patient.yBodyRot = patient.yBodyRotO = patient.yHeadRot = patient.yHeadRotO = facing.toYRot();
        patient.fallDistance = 0;
    }
    public static void synchronize(Guard patient) { ((GuardLifeAccess) patient).villagerGuardGunners$setCotVisual(Optional.ofNullable(GuardLifeAccess.cot(patient).head())); }
    /** The synchronized cot also fixes stale spawn/interpolation positions on the client. */
    public static void alignClient(Guard patient) {
        if (!patient.level().isClientSide || !patient.isAlive() || patient.isRemoved() || patient.isPassenger()) return;
        var head = ((GuardLifeAccess)patient).villagerGuardGunners$getCotVisual();
        if (head.isEmpty()) return;
        var point = TransportRuntime.cotPosition(patient.level(), head.get());
        if (point == null || patient.distanceToSqr(point) > 64) return;
        patient.setPos(point);
        patient.xOld = patient.xo = point.x; patient.yOld = patient.yo = point.y; patient.zOld = patient.zo = point.z;
        align(patient, patient.level().getBlockState(head.get()).getValue(RecoveryCotBlock.FACING));
    }
    public static void release(Guard patient, boolean field) {
        var rest = GuardLifeAccess.cot(patient); if (rest.head() == null) return;
        var map = LEVELS.get(patient.level()); if (map != null) map.remove(rest.head(), patient);
        var point = patient.level() instanceof ServerLevel level && level.dimension().location().equals(rest.dimension()) ? TransportRuntime.cotPosition(level, rest.head()) : null;
        var exit = point != null && patient.distanceToSqr(point) < 4 ? exitPosition(patient, rest.head()) : null;
        rest.clear(); synchronize(patient);
        if (field) {
            GuardLifeAccess.get(patient).fieldRecovery();
            MedicRuntime.cancel(patient, MedicState.Status.INTERRUPTED);
            if (exit == null) exit = TransportRuntime.findStanding(patient, patient.position());
            if (exit != null && patient.distanceToSqr(exit) <= 16) { patient.setPos(exit); patient.setDeltaMovement(Vec3.ZERO); }
        }
    }
    public static void removed(ServerLevel level, BlockPos head) {
        var map = LEVELS.get(level); var patient = map == null ? null : map.get(head);
        if (patient != null) release(patient, true);
    }
    public static void unloaded(ServerLevel level, BlockPos head) { var map = LEVELS.get(level); if (map != null) map.remove(head); }
    public static void left(Guard patient) {
        var map = LEVELS.get(patient.level()); if (map != null) map.remove(GuardLifeAccess.cot(patient).head(), patient);
    }
    public static void stopping(net.minecraft.server.MinecraftServer server) { LEVELS.keySet().removeIf(level -> level.getServer() == server); }
}
