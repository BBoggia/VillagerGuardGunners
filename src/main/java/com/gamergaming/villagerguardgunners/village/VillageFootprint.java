package com.gamergaming.villagerguardgunners.village;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Planned geometry only: neither a walkability claim nor guard-generation credit. */
public record VillageFootprint(List<Piece> pieces, Bounds bounds) {
    public static final int MAX_PIECES = 512;
    public static final int MAX_SPAN = 1024;

    public VillageFootprint {
        pieces = List.copyOf(pieces);
        if (pieces.isEmpty() || pieces.size() > MAX_PIECES) throw new IllegalArgumentException("Invalid piece count");
        Bounds actual = pieces.get(0).bounds();
        for (Piece piece : pieces) actual = actual.union(piece.bounds());
        if (!actual.equals(bounds) || bounds.maxX - (long) bounds.minX > MAX_SPAN
                || bounds.maxZ - (long) bounds.minZ > MAX_SPAN) {
            throw new IllegalArgumentException("Invalid village extent");
        }
    }

    public static VillageFootprint of(List<Piece> pieces) {
        if (pieces.isEmpty()) throw new IllegalArgumentException("Empty footprint");
        Bounds bounds = pieces.get(0).bounds();
        for (Piece piece : pieces) bounds = bounds.union(piece.bounds());
        return new VillageFootprint(pieces, bounds);
    }

    public enum Evidence { PRESENT, ABSENT, UNRESOLVED }
    /** Shared by all lookups in a section publication, including failed queries. */
    public static final class EvidenceBudget {
        private int remaining;
        public EvidenceBudget(int limit) { remaining = limit; }
        public boolean take() { if (remaining == 0) return false; remaining--; return true; }
        public int remaining() { return remaining; }
    }

    /** Planned geometry is evidence of ownership, never a claim of walkable access. */
    public Evidence membershipEvidence(BlockPos position, int horizontalMargin, EvidenceBudget budget) {
        boolean uncertain = false;
        for (Piece piece : pieces) {
            if (!budget.take()) return Evidence.UNRESOLVED;
            Bounds b = piece.bounds();
            if (position.getX() < (long)b.minX() - horizontalMargin || position.getX() > (long)b.maxX() + horizontalMargin
                    || position.getZ() < (long)b.minZ() - horizontalMargin || position.getZ() > (long)b.maxZ() + horizontalMargin
                    || position.getY() < b.minY() - 2 || position.getY() > b.maxY() + 2) continue;
            // Unreadable geometry and tall volumes need floor-band observations before admission.
            if (piece.template().toString().equals("villagerguardgunners:unknown_piece")
                    || piece.rotation().equals("UNKNOWN") || b.maxY() - b.minY() > 24) uncertain = true;
            else return Evidence.PRESENT;
        }
        return uncertain ? Evidence.UNRESOLVED : Evidence.ABSENT;
    }
    public boolean supportsMembership(BlockPos position) {
        return membershipEvidence(position, 0, new EvidenceBudget(MAX_PIECES)) == Evidence.PRESENT;
    }

    public record Piece(BlockPos position, String rotation, ResourceLocation template, Bounds bounds) {
        public Piece {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(template, "template");
            if (!List.of("NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90", "UNKNOWN")
                    .contains(rotation)) throw new IllegalArgumentException("Unknown rotation");
            if (Math.abs((long) position.getX()) > 30_000_000 || Math.abs((long) position.getZ()) > 30_000_000
                    || Math.abs((long) position.getY()) > 20_000) throw new IllegalArgumentException("Invalid piece position");
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ || minX < -30_000_000 || maxX > 30_000_000
                    || minZ < -30_000_000 || maxZ > 30_000_000 || minY < -20_000 || maxY > 20_000) {
                throw new IllegalArgumentException("Invalid bounds");
            }
        }
        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
        public Bounds union(Bounds b) {
            return new Bounds(Math.min(minX, b.minX), Math.min(minY, b.minY), Math.min(minZ, b.minZ),
                    Math.max(maxX, b.maxX), Math.max(maxY, b.maxY), Math.max(maxZ, b.maxZ));
        }
    }
}
