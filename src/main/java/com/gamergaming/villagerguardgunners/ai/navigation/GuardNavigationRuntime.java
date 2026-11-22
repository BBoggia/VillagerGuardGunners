package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Executes conditional transitions only for paths explicitly installed by an addon movement owner. */
public final class GuardNavigationRuntime {
    private static final Map<Guard, String> LAST_STATUS = new WeakHashMap<>();
    private static final Map<Guard, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerLevel, PassageReservations<Passage>> PASSAGES = new WeakHashMap<>();
    private static final Object DIRECT_OWNER = new Object();
    private static ServerLevel openingLevel;
    private GuardNavigationRuntime() { }
    public static void register(Guard guard, Path path) {
        register(guard, path, DIRECT_OWNER);
    }
    /** Owner must be an opaque token with no entity or controller references. */
    public static void register(Guard guard, Path path, Object owner) {
        if (path != null && path != guard.getNavigation().getPath()) return;
        Session current = SESSIONS.get(guard);
        if (path != null && current != null && current.path == path) {
            // Native moveTo can retain an equivalent installed Path. Keep its
            // queue position, sidestep and deadline even when its owner changes.
            current.owner = owner;
            return;
        }
        release(guard);
        LAST_STATUS.remove(guard);
        if (path != null && path == guard.getNavigation().getPath()) {
            if (guard.getNavigation() instanceof GuardNavigationClock clock) clock.villagerguardgunners$startRouteClock();
            SESSIONS.put(guard, new Session(path, owner));
        }
    }
    public static void release(Guard guard) {
        Session session = SESSIONS.remove(guard);
        if (session != null) clearOverride(guard, session);
        if (guard.level() instanceof ServerLevel level) {
            var book = PASSAGES.get(level);
            if (book != null) book.release(guard.getUUID());
        }
    }
    public static void release(Guard guard, Path path, Object owner) {
        if (owns(guard, path, owner)) release(guard);
    }
    public static boolean owns(Guard guard, Path path, Object owner) {
        Session session = SESSIONS.get(guard);
        return session != null && session.path == path && session.owner == owner;
    }
    public static boolean ownedByOther(Guard guard, Path path, Object owner) {
        Session session = SESSIONS.get(guard);
        return session != null && session.path == path && session.owner != owner;
    }
    public static boolean expectedDoorChange(Guard guard) {
        return guard.level() == openingLevel && owns(guard);
    }
    public static boolean owns(Guard guard) {
        Session s = SESSIONS.get(guard);
        return s != null && s.path == guard.getNavigation().getPath();
    }
    public static boolean waiting(Guard guard) { return status(guard).startsWith("WAITING_FOR_"); }
    public static String status(Guard guard) {
        Session s = SESSIONS.get(guard);
        return s == null || s.path != guard.getNavigation().getPath() ? LAST_STATUS.getOrDefault(guard, "IDLE") : s.status;
    }
    /** True pauses this native navigation tick without giving up its installed path. */
    public static boolean beforeTick(Guard guard) {
        Session s = SESSIONS.get(guard);
        if (s == null) return false;
        if (!(guard.level() instanceof ServerLevel level) || !guard.isAlive() || guard.isRemoved()
                || s.path != guard.getNavigation().getPath() || s.path.isDone()) { release(guard); return false; }
        long now = level.getGameTime();
        try {
            // The door is opened only within reach. Planning never alters world blocks.
            for (int i = s.path.getNextNodeIndex(); i < Math.min(s.path.getNodeCount(), s.path.getNextNodeIndex() + 3); i++) {
                BlockPos pos = s.path.getNodePos(i);
                if (guard.distanceToSqr(Vec3.atBottomCenterOf(pos)) > 4 || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
                var state = level.getBlockState(pos);
                if (DoorBlock.isWoodenDoor(state) && !state.getValue(DoorBlock.OPEN) && GuardGroundTraversal.canOpenDoors(guard)) {
                    openingLevel = level;
                    try { ((DoorBlock) state.getBlock()).setOpen(guard, level, state, pos, true); }
                    finally { openingLevel = null; }
                    if (!level.getBlockState(pos).getValue(DoorBlock.OPEN)) return stop(guard, s, "DOOR_BLOCKED");
                }
            }
            if (guard.onGround()) {
                for (int i = s.path.getNextNodeIndex(); i < Math.min(s.path.getNodeCount(), s.path.getNextNodeIndex() + 3); i++) {
                    Vec3 point = Vec3.atBottomCenterOf(s.path.getNodePos(i));
                    // Allow a screened escape to leave its hazardous starting cell.
                    if (guard.distanceToSqr(point) > 1 && !GuardGroundTraversal.safeStanding(guard, point, true))
                        return stop(guard, s, "TERRAIN_CHANGED");
                }
            }
            var book = PASSAGES.computeIfAbsent(level, unused -> new PassageReservations<>());
            BlockPos currentNode = GuardGroundTraversal.navigationNode(level, guard.position());
            if (s.passage != null && s.stage == null && !s.passage.contains(currentNode) && !ahead(s.path, s.passage)) {
                book.release(guard.getUUID()); s.passage = null;
            }
            if (s.passage == null && now >= s.nextProbe) {
                s.nextProbe = now + 5;
                for (int i = s.path.getNextNodeIndex(); i < Math.min(s.path.getNodeCount(), s.path.getNextNodeIndex() + 5); i++) {
                    BlockPos node = s.path.getNodePos(i);
                    if (guard.distanceToSqr(Vec3.atBottomCenterOf(node)) > 6.25) break;
                    Passage candidate = passage(level, node);
                    if (candidate != null && !candidate.contains(currentNode)) { s.passage = candidate; break; }
                }
            }
            if (s.stage != null && (!mayStage(guard) || !withinStageBounds(guard, s.stage)
                    || !withinStageBounds(guard, s.stageReturn))) return stop(guard, s, "STAGING_CANCELLED");
            if (s.waitingSince >= 0 && now - s.waitingSince >= 100) return stop(guard, s, "PASSAGE_TIMEOUT");
            boolean admitted = s.passage == null || book.request(s.passage, guard.getUUID(), now);
            boolean exitOccupied = s.stage != null && exitOccupied(guard, s);
            if (!admitted || exitOccupied) {
                if (s.waitingSince < 0) s.waitingSince = now;
                s.status = admitted ? "WAITING_FOR_CLEAR_EXIT" : "WAITING_FOR_PASSAGE";
                if (!admitted && s.stage == null) findStage(guard, level, s);
                if (s.stage != null && !safeStageSegment(guard, s.stage)) return stop(guard, s, "STAGING_CHANGED");
                Vec3 wait = s.stage == null ? guard.position() : s.stage;
                moveOverride(guard, s, wait, s.stage == null ? 0 : .6);
                return true;
            }
            if (s.stage != null) {
                // Rejoin at the verified wide end before following the original bridge nodes.
                if (!safeStageSegment(guard, s.stageReturn)) return stop(guard, s, "STAGING_CHANGED");
                if (guard.position().distanceToSqr(s.stageReturn) > .04) {
                    moveOverride(guard, s, s.stageReturn, .6);
                    s.status = "REJOINING_ROUTE"; return true;
                }
                s.stage = null; s.stageReturn = null;
            }
            clearOverride(guard, s);
            s.waitingSince = -1; s.status = s.passage == null ? "MOVING" : "CROSSING_PASSAGE";
            return false;
        } catch (RuntimeException exception) { return stop(guard, s, "TRANSITION_INVALID"); }
    }
    /** Noncombat waiters may use one safe lateral block; occupied posts and manual holds stay put. */
    private static void findStage(Guard guard, ServerLevel level, Session s) {
        if (s.passage == null || !mayStage(guard) || !withinStageBounds(guard, guard.position())) return;
        BlockPos node = GuardGroundTraversal.navigationNode(level, guard.position());
        if (node == null || s.passage.contains(node)) return;
        Direction axis = s.passage.axis;
        for (Direction side : new Direction[]{axis.getClockWise(), axis.getCounterClockWise()}) {
            BlockPos candidate = node.relative(side);
            Vec3 point = GuardGroundTraversal.surface(level, Vec3.atBottomCenterOf(candidate));
            if (point == null || !withinStageBounds(guard, point) || !GuardGroundTraversal.safeStep(level, node, candidate)
                    || !GuardGroundTraversal.safeStanding(guard, point)) continue;
            Vec3 delta = point.subtract(guard.position());
            if (occupied(guard, guard.getBoundingBox().expandTowards(delta).inflate(.1))) continue;
            if (safeStageSegment(guard, point)) { s.stageReturn = guard.position(); s.stage = point; return; }
        }
    }
    private static boolean mayStage(Guard guard) {
        return guard.getTarget() == null && !guard.isFollowing() && !guard.isPassenger()
                && (!(guard instanceof com.gamergaming.villagerguardgunners.api.GuardCoverageAccess access)
                    || !access.villagerGuardGunners$hasFollowOrder())
                && !com.gamergaming.villagerguardgunners.role.GuardRoleResolver.effectiveOrders(guard).movement().holdsPosition();
    }
    private static boolean withinStageBounds(Guard guard, Vec3 point) {
        if (!com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.bounds(guard).contains(point)) return false;
        var command = com.gamergaming.villagerguardgunners.squad.SquadCommandRuntime.activeCommand(guard);
        return command == null || command.type() != com.gamergaming.villagerguardgunners.squad.SquadCommandType.DEFEND
                || point.distanceToSqr(Vec3.atBottomCenterOf(command.position())) <= Math.pow(Math.max(.5, command.radius() - 1), 2);
    }
    private static boolean safeStageSegment(Guard guard, Vec3 point) {
        double distance = guard.position().distanceTo(point);
        // A displaced guard must replan; the lateral maneuver cannot turn into a new journey.
        if (distance > 2) return false;
        int samples = Math.max(1, (int) Math.ceil(distance * 4));
        for (int i = 0; i <= samples; i++)
            if (!GuardGroundTraversal.safeStanding(guard, guard.position().lerp(point, (double) i / samples))) return false;
        return true;
    }
    private static void moveOverride(Guard guard, Session s, Vec3 point, double speed) {
        s.override = point;
        s.overrideSpeed = speed;
        guard.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
    }
    private static void clearOverride(Guard guard, Session s) {
        var control = guard.getMoveControl();
        // Preserve a replacement goal's command if it has already taken over MoveControl.
        if (s.override != null && control.getWantedX() == s.override.x && control.getWantedY() == s.override.y
                && control.getWantedZ() == s.override.z && control.getSpeedModifier() == s.overrideSpeed)
            control.setWantedPosition(guard.getX(), guard.getY(), guard.getZ(), 0);
        s.override = null;
    }
    private static boolean exitOccupied(Guard guard, Session s) {
        // Let the departing guard pass the waiting position before moving back onto its centerline.
        Vec3 end = Vec3.atBottomCenterOf(s.passage.from).distanceToSqr(s.stageReturn)
                < Vec3.atBottomCenterOf(s.passage.to).distanceToSqr(s.stageReturn)
                ? Vec3.atBottomCenterOf(s.passage.from) : Vec3.atBottomCenterOf(s.passage.to);
        return occupied(guard, new net.minecraft.world.phys.AABB(s.stageReturn, end).inflate(.8, 1.8, .8));
    }
    private static boolean occupied(Guard guard, net.minecraft.world.phys.AABB box) {
        var entities = new ArrayList<net.minecraft.world.entity.LivingEntity>(1);
        guard.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.LivingEntity.class),
                box, entity -> entity != guard && entity.isAlive() && !entity.isSpectator(), entities, 1);
        return !entities.isEmpty();
    }
    private static boolean stop(Guard guard, Session s, String reason) {
        s.status = reason;
        LAST_STATUS.put(guard, reason);
        guard.getNavigation().stop();
        guard.getMoveControl().setWantedPosition(guard.getX(), guard.getY(), guard.getZ(), 0);
        release(guard);
        return true;
    }
    private static boolean ahead(Path path, Passage passage) {
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 5); i++)
            if (passage.contains(path.getNodePos(i))) return true;
        return false;
    }
    /** Straight, short passages with verified wide ends. Unknown terrain is never a staging area. */
    private static Passage passage(ServerLevel level, BlockPos node) {
        for (Direction axis : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            if (!narrow(level, node, axis)) continue;
            BlockPos a = node, b = node;
            for (int i = 0; i < 12 && narrow(level, a.relative(axis.getOpposite()), axis); i++) a = a.relative(axis.getOpposite());
            for (int i = 0; i < 12 && narrow(level, b.relative(axis), axis); i++) b = b.relative(axis);
            if (a.distManhattan(b) > 12 || !wide(level, a.relative(axis.getOpposite()), axis)
                    || !wide(level, b.relative(axis), axis)) continue;
            return new Passage(a.immutable(), b.immutable(), axis);
        }
        return null;
    }
    private static boolean narrow(ServerLevel level, BlockPos p, Direction axis) {
        return GuardGroundTraversal.planningStanding(level, p)
                && !GuardGroundTraversal.planningStanding(level, p.relative(axis.getClockWise()))
                && !GuardGroundTraversal.planningStanding(level, p.relative(axis.getCounterClockWise()));
    }
    private static boolean wide(ServerLevel level, BlockPos p, Direction axis) {
        return GuardGroundTraversal.safeStandingPosition(level, p)
                && (GuardGroundTraversal.safeStep(level, p, p.relative(axis.getClockWise()))
                    || GuardGroundTraversal.safeStep(level, p, p.relative(axis.getCounterClockWise())));
    }
    private record Passage(BlockPos from, BlockPos to, Direction axis) {
        boolean contains(BlockPos p) {
            return p != null && p.getY() == from.getY() && p.getX() >= from.getX() && p.getX() <= to.getX()
                    && p.getZ() >= from.getZ() && p.getZ() <= to.getZ();
        }
    }
    private static final class Session {
        final Path path;
        Object owner;
        Passage passage;
        Vec3 stage, stageReturn, override;
        double overrideSpeed;
        long nextProbe, waitingSince = -1;
        String status = "MOVING";
        Session(Path path, Object owner) { this.path = path; this.owner = owner; }
    }
}
