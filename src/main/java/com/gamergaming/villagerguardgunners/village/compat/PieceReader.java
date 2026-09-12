package com.gamergaming.villagerguardgunners.village.compat;

import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/** Reads immutable native placement metadata; never retrieves a template or loads terrain. */
public interface PieceReader {
    Result read(StructurePiece piece);
    record Result(VillageFootprint.Piece geometry, String reader, boolean supported, String reason) { }
}
