package com.gamergaming.villagerguardgunners.ai.tactics;

import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Destination screening only. A plausible lane never authorizes a native discharge. */
public final class TacticalFiringLane {
    public static final int ENTITY_LIMIT = 64;
    public record Request(LivingEntity target, double minimumRange, double preferredRange,
                          double maximumRange, double clearance, double blastReach) { }
    private final Guard guard;
    private final Request request;
    private final Vec3 targetPoint;
    private final List<AABB> protectedBodies;

    private TacticalFiringLane(Guard guard, Request request, List<AABB> bodies) {
        this.guard = guard; this.request = request;
        targetPoint = request.target().getEyePosition();
        protectedBodies = List.copyOf(bodies);
    }

    public static TacticalFiringLane capture(Guard guard, Request request) {
        if (!(guard.level() instanceof ServerLevel level) || request == null || request.target() == null
                || !request.target().isAlive() || request.target().level() != level
                || !Double.isFinite(request.minimumRange()) || !Double.isFinite(request.maximumRange())
                || !Double.isFinite(request.clearance()) || !Double.isFinite(request.blastReach())
                || !Double.isFinite(request.preferredRange())
                || request.minimumRange() < 0 || request.maximumRange() < request.minimumRange()
                || request.clearance() < 0 || request.clearance() > 32 || request.blastReach() < 0 || request.blastReach() > 32
                || !loadedCorridor(level, guard.getEyePosition(), request.target().getEyePosition())
                || !guard.hasLineOfSight(request.target())) return null;
        var entities = new ArrayList<Entity>(ENTITY_LIMIT + 1);
        AABB bounds = guard.getBoundingBox().inflate(16).minmax(request.target().getBoundingBox())
                .inflate(request.clearance() + request.blastReach());
        level.getEntities(EntityTypeTest.forClass(Entity.class), bounds,
                entity -> entity != guard && entity.isAlive() && !entity.isSpectator()
                        && GuardAllyProtection.isProtected(guard, entity), entities, ENTITY_LIMIT + 1);
        if (entities.size() > ENTITY_LIMIT) return null;
        return new TacticalFiringLane(guard, request, entities.stream().map(Entity::getBoundingBox).toList());
    }

    public boolean clearFrom(Vec3 feet) {
        if (!plausibleFrom(feet)) return false;
        Vec3 from = feet.add(0, guard.getEyeHeight(), 0);
        return loadedCorridor((ServerLevel) guard.level(), from, targetPoint)
                && guard.level().clip(new ClipContext(from, targetPoint, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, guard)).getType() == HitResult.Type.MISS;
    }

    public boolean plausibleFrom(Vec3 feet) {
        double distance = feet.distanceTo(request.target().position());
        if (distance < request.minimumRange() || distance > request.maximumRange()) return false;
        Vec3 from = feet.add(0, guard.getEyeHeight(), 0);
        for (AABB body : protectedBodies) {
            if (intersects(body, from, targetPoint, request.clearance())) return false;
            // Explosive destinations need room around the anticipated impact as well.
            if (request.blastReach() > 0 && body.inflate(request.clearance() + request.blastReach()).contains(targetPoint)) return false;
        }
        return true;
    }

    public double rangeScore(Vec3 point) {
        return -Math.abs(point.distanceTo(request.target().position()) - request.preferredRange()) * .3;
    }

    static boolean intersects(AABB body, Vec3 from, Vec3 to, double clearance) {
        AABB expanded = body.inflate(clearance);
        return expanded.contains(from) || expanded.contains(to) || expanded.clip(from, to).isPresent();
    }

    static boolean loadedCorridor(ServerLevel level, Vec3 from, Vec3 to) {
        // Guard ranges are normally short; reject exotic requests before enumerating chunks.
        if (from.distanceToSqr(to) > 128 * 128) return false;
        int minX = (int) Math.floor(Math.min(from.x, to.x)) >> 4, maxX = (int) Math.floor(Math.max(from.x, to.x)) >> 4;
        int minZ = (int) Math.floor(Math.min(from.z, to.z)) >> 4, maxZ = (int) Math.floor(Math.max(from.z, to.z)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!level.hasChunk(x, z)) return false;
        return true;
    }
}
