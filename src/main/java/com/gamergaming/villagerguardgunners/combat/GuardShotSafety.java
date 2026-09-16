package com.gamergaming.villagerguardgunners.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Per-round world checks. Neither positive nor negative results are cached. */
public final class GuardShotSafety {
    public static final int MAX_ENTITIES = 256;
    public static final int MAX_BLOCKS = 8192;
    public static final int MAX_SHAPES_PER_BLOCK = 64;

    private GuardShotSafety() {}

    public enum Status {
        CLEAR, NOT_EVALUATED, UNSUPPORTED_TRAJECTORY, INVALID_AIM, INVALID_BALLISTICS, EXCESSIVE_ENVELOPE, STALE_AIM,
        TARGET_IS_PROTECTED, INVALID_WORLD, ENTITY_LIMIT, BLOCK_LIMIT, UNLOADED_IMPACT_AREA,
        PROTECTED_ENTITY_IN_PATH, EXPLOSIVE_ENTITY_IMPACT, EXPLOSIVE_BLOCK_IMPACT, UNSAFE_FUSE
    }
    public record Result(Status status, int entityId, BlockPos block, GuardShotEnvelope envelope) {
        public boolean clear() { return status == Status.CLEAR; }
    }
    public record Candidate(int id, AABB bounds, boolean protectedEntity) {}
    public interface Scene {
        /** Return at most limit entries; include hostile impact candidates, but exclude the shooter. */
        List<Candidate> entities(AABB bounds, int limit);
        /** World-coordinate collider boxes. Null means unavailable; never load a chunk here. */
        List<AABB> blockShapes(BlockPos pos);
    }

    public static Result check(GuardShotEnvelope envelope, AABB shooter, Scene scene) {
        try {
            return checkWorld(envelope, shooter, scene);
        } catch (RuntimeException exception) {
            // Broken third-party entity/shape providers must not convert a failed check to approval.
            return result(Status.INVALID_WORLD, -1, null, envelope);
        }
    }

    private static Result checkWorld(GuardShotEnvelope envelope, AABB shooter, Scene scene) {
        if (envelope == null || !valid(shooter) || scene == null) {
            return result(Status.INVALID_WORLD, -1, null, envelope);
        }
        // Exactly one bounded entity query, shared by projectile and explosive checks.
        List<Candidate> entities = scene.entities(envelope.bounds(), MAX_ENTITIES + 1);
        if (entities == null) return result(Status.INVALID_WORLD, -1, null, envelope);
        if (entities.size() > MAX_ENTITIES) return result(Status.ENTITY_LIMIT, -1, null, envelope);
        double reach = envelope.ballistics().blastReach();
        List<Candidate> nearBlast = new ArrayList<>();
        nearBlast.add(new Candidate(-1, shooter, true));
        for (Candidate entity : entities) {
            if (entity == null || !valid(entity.bounds)) return result(Status.INVALID_WORLD, -1, null, envelope);
            if (!entity.protectedEntity) continue;
            if (envelope.intersects(entity.bounds, envelope.clearance())
                    || reach > 0 && envelope.forwardBlastIntersects(entity.bounds)) {
                return result(Status.PROTECTED_ENTITY_IN_PATH, entity.id, null, envelope);
            }
            if (reach > 0 && envelope.intersects(entity.bounds, reach + envelope.clearance())) {
                nearBlast.add(entity);
            }
        }
        if (reach <= 0) return result(Status.CLEAR, -1, null, envelope);

        Map<BlockPos, List<AABB>> blocks = new HashMap<>();
        for (Candidate vulnerable : nearBlast) {
            if (envelope.fuseThreatens(vulnerable.bounds)) {
                return result(Status.UNSAFE_FUSE, vulnerable.id, null, envelope);
            }
            AABB danger = vulnerable.bounds.inflate(reach + envelope.clearance());
            for (Candidate impact : entities) {
                if (danger.intersects(impact.bounds) && envelope.intersects(impact.bounds, 0)) {
                    return result(Status.EXPLOSIVE_ENTITY_IMPACT, vulnerable.id, null, envelope);
                }
            }
            // Only blocks near an otherwise unprotected muzzle-side blast case need scanning.
            // Never truncate travel at a block: TaCZ penetration and cancelable hit events remain native.
            if (!danger.intersects(envelope.bounds())) continue;
            AABB area = danger.intersect(envelope.bounds());
            long count = ((long) Math.floor(area.maxX) - (long) Math.floor(area.minX) + 3)
                    * ((long) Math.floor(area.maxY) - (long) Math.floor(area.minY) + 3)
                    * ((long) Math.floor(area.maxZ) - (long) Math.floor(area.minZ) + 3);
            if (count > MAX_BLOCKS) return result(Status.BLOCK_LIMIT, vulnerable.id, null, envelope);
            // Include adjacent block origins for colliders such as 1.5-block-high fences.
            for (BlockPos pos : BlockPos.betweenClosed(
                    BlockPos.containing(area.minX - 1, area.minY - 1, area.minZ - 1),
                    BlockPos.containing(area.maxX + 1, area.maxY + 1, area.maxZ + 1))) {
                List<AABB> shapes = blocks.get(pos);
                if (shapes == null) {
                    if (blocks.size() >= MAX_BLOCKS) return result(Status.BLOCK_LIMIT, vulnerable.id, pos.immutable(), envelope);
                    shapes = scene.blockShapes(pos);
                    if (shapes == null) return result(Status.UNLOADED_IMPACT_AREA, vulnerable.id, pos.immutable(), envelope);
                    if (shapes.size() > MAX_SHAPES_PER_BLOCK) return result(Status.BLOCK_LIMIT, vulnerable.id, pos.immutable(), envelope);
                    blocks.put(pos.immutable(), shapes);
                }
                for (AABB shape : shapes) {
                    if (!valid(shape)) return result(Status.INVALID_WORLD, vulnerable.id, pos.immutable(), envelope);
                    if (danger.intersects(shape) && envelope.intersects(shape.intersect(danger), 0)) {
                        return result(Status.EXPLOSIVE_BLOCK_IMPACT, vulnerable.id, pos.immutable(), envelope);
                    }
                }
            }
        }
        return result(Status.CLEAR, -1, null, envelope);
    }

    private static boolean valid(AABB bounds) {
        return bounds != null && GuardShotEnvelope.finite(bounds.getCenter())
                && Double.isFinite(bounds.getXsize()) && bounds.getXsize() > 0
                && Double.isFinite(bounds.getYsize()) && bounds.getYsize() > 0
                && Double.isFinite(bounds.getZsize()) && bounds.getZsize() > 0;
    }

    static Result result(Status status, int id, BlockPos block, GuardShotEnvelope envelope) {
        return new Result(status, id, block, envelope);
    }
}
