package com.gamergaming.villagerguardgunners.village.compat;

import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/** Read-only template observations; independent of spawning and coverage switches. */
public final class SettlementObservations {
    public static List<BlockPos> worldPoints(VillageFootprint.Piece piece, List<PieceMeaning.Point> points) {
        if (piece.rotation().equals("UNKNOWN")) return List.of();
        Rotation rotation = Rotation.valueOf(piece.rotation());
        return points.stream().map(p -> new BlockPos(p.x(), p.y(), p.z()).rotate(rotation).offset(piece.position())).toList();
    }
    public static Summary inspect(SettlementCatalog.Decision decision, List<VillageFootprint.Piece> pieces, Set<String> nativeRequests) {
        Set<BlockPos> beds = new HashSet<>(), entrances = new HashSet<>();
        Set<String> sources = new TreeSet<>();
        int homes = 0, unsupported = 0;
        var intent = decision.intent();
        for (var piece : pieces) {
            var meaning = decision.meaning(piece.template().toString());
            if (meaning.supported()) {
                boolean newBeds = false;
                for (var bed : worldPoints(piece, meaning.beds())) newBeds |= beds.add(bed);
                if (newBeds) homes++;
                entrances.addAll(worldPoints(piece, meaning.entrances()));
            } else unsupported++;
            if (nativeRequests.contains(piece.template().toString())) sources.add(piece.template().toString());
        }
        return new Summary(intent, homes, beds.size(), entrances.size(), unsupported, Set.copyOf(sources));
    }
    public record Summary(SettlementProfile.Intent intent, int residences, int bedCapacity,
            int entranceCandidates, int unverifiedPieces, Set<String> nativeRequestTemplates) { }
    private SettlementObservations() { }
}
