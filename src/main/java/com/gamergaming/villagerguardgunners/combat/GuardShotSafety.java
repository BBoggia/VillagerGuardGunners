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
    public static final int MAX_BLOCK_VISITS = 65536;
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
        /** True only for a known, loaded fluid-free block. Unknown scenes keep the water envelope. */
        default boolean isDry(BlockPos pos) { return false; }
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
        Map<Integer, Candidate> unique = new java.util.LinkedHashMap<>();
        for (AABB query : envelope.queries()) {
            List<Candidate> found = scene.entities(query, MAX_ENTITIES + 1);
            if (found == null) return result(Status.INVALID_WORLD, -1, null, envelope);
            if (found.size() > MAX_ENTITIES) return result(Status.ENTITY_LIMIT, -1, null, envelope);
            for (Candidate entity : found) {
                if (entity == null || !valid(entity.bounds)) return result(Status.INVALID_WORLD, -1, null, envelope);
                Candidate previous = unique.get(entity.id);
                unique.put(entity.id, previous == null ? entity : new Candidate(entity.id,
                        previous.bounds.minmax(entity.bounds), previous.protectedEntity || entity.protectedEntity));
                if (unique.size() > MAX_ENTITIES) return result(Status.ENTITY_LIMIT, -1, null, envelope);
            }
        }
        List<Candidate> entities = List.copyOf(unique.values());
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
        Map<BlockPos, Boolean> dryBlocks = new HashMap<>();
        int visits = 0;
        for (Candidate vulnerable : nearBlast) {
            AABB danger = vulnerable.bounds.inflate(reach + envelope.clearance());
            GuardShotEnvelope impacts = dryPrefix(envelope, danger, scene, dryBlocks);
            if (impacts.fuseThreatens(vulnerable.bounds)) {
                return result(Status.UNSAFE_FUSE, vulnerable.id, null, envelope);
            }
            for (Candidate impact : entities) {
                if (danger.intersects(impact.bounds) && impacts.intersects(impact.bounds, 0)) {
                    return result(Status.EXPLOSIVE_ENTITY_IMPACT, vulnerable.id, null, envelope);
                }
            }
            // Only blocks near an otherwise unprotected muzzle-side blast case need scanning.
            // Never truncate travel at a block: TaCZ penetration and cancelable hit events remain native.
            if (!danger.intersects(impacts.bounds())) continue;
            AABB area = danger.intersect(impacts.bounds());
            long count = ((long) Math.floor(area.maxX) - (long) Math.floor(area.minX) + 3)
                    * ((long) Math.floor(area.maxY) - (long) Math.floor(area.minY) + 3)
                    * ((long) Math.floor(area.maxZ) - (long) Math.floor(area.minZ) + 3);
            if (count > MAX_BLOCK_VISITS - visits) return result(Status.BLOCK_LIMIT, vulnerable.id, null, envelope);
            // Include adjacent block origins for colliders such as 1.5-block-high fences.
            for (BlockPos pos : BlockPos.betweenClosed(
                    BlockPos.containing(area.minX - 1, area.minY - 1, area.minZ - 1),
                    BlockPos.containing(area.maxX + 1, area.maxY + 1, area.maxZ + 1))) {
                visits++;
                // A collider can extend one block beyond its owning block. Reject only
                // origins whose entire supported collider region misses the flight path.
                AABB possibleShape = new AABB(pos).inflate(1);
                if (!danger.intersects(possibleShape)
                        || !impacts.intersects(possibleShape.intersect(danger), 0)) continue;
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
                    if (danger.intersects(shape) && impacts.intersects(shape.intersect(danger), 0)) {
                        return result(Status.EXPLOSIVE_BLOCK_IMPACT, vulnerable.id, pos.immutable(), envelope);
                    }
                }
            }
        }
        return result(Status.CLEAR, -1, null, envelope);
    }

    private static GuardShotEnvelope dryPrefix(GuardShotEnvelope full, AABB danger, Scene scene,
                                               Map<BlockPos, Boolean> dryBlocks) {
        GuardShotEnvelope prefix = full.dryEscapePrefix(danger);
        if (prefix == null) return full;
        AABB area = prefix.bounds(); // Includes a one-block margin around possible projectile positions.
        long count = ((long)Math.floor(area.maxX) - (long)Math.floor(area.minX) + 1)
                * ((long)Math.floor(area.maxY) - (long)Math.floor(area.minY) + 1)
                * ((long)Math.floor(area.maxZ) - (long)Math.floor(area.minZ) + 1);
        if (count > MAX_BLOCKS) return full;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            Boolean dry = dryBlocks.get(pos);
            if (dry == null) {
                if (dryBlocks.size() >= MAX_BLOCKS) return full;
                dry = scene.isDry(pos);
                dryBlocks.put(pos.immutable(), dry);
            }
            if (!dry) return full;
        }
        return prefix;
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
