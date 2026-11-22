package com.gamergaming.villagerguardgunners.village.compat;

import com.gamergaming.villagerguardgunners.mixin.accessor.VillagePoolElementAccessor;
import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.mojang.datafixers.util.Either;
import java.lang.reflect.Field;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraftforge.fml.ModList;

/** Optional code is resolved only after an exact version gate. Vanilla includes legacy subclasses. */
public final class PieceReaders {
    private static final ResourceLocation UNKNOWN = ResourceLocation.parse("villagerguardgunners:unknown_piece");
    private static final PieceReader NATIVE = piece -> {
        if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
            var id = ((VillagePoolElementAccessor) single).villagerGuardGunners$template().left().orElse(null);
            return result(piece, id, SettlementProfile.NATIVE, id == null ? "runtime_template_without_id" : "native_single_or_legacy");
        }
        return result(piece, null, SettlementProfile.NATIVE, "unsupported_element_type");
    };
    private PieceReaders() { }
    public static PieceReader.Result read(StructurePiece piece, String reader) {
        var vanilla = NATIVE.read(piece);
        if (vanilla.supported() || !SettlementProfile.INTEGRATED.equals(reader)) return vanilla;
        var mod = ModList.get().getModContainerById("integrated_api");
        if (mod.isEmpty() || !mod.get().getModInfo().getVersion().toString().equals("1.8.0"))
            return result(piece, null, reader, "unverified_or_absent_integrated_api");
        return Integrated.INSTANCE.read(piece);
    }
    /** Already frozen plans retain their template-based source identity across profile reloads. */
    public static PieceReader.Result readFrozenSource(StructurePiece piece) { return read(piece, SettlementProfile.INTEGRATED); }
    static PieceReader.Result result(StructurePiece piece, ResourceLocation template, String reader, String reason) {
        var b = piece.getBoundingBox();
        var pool = piece instanceof PoolElementStructurePiece p ? p : null;
        var geometry = new VillageFootprint.Piece(pool == null ? b.getCenter() : pool.getPosition(),
                pool == null ? "UNKNOWN" : pool.getRotation().name(), template == null ? UNKNOWN : template,
                new VillageFootprint.Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
        return new PieceReader.Result(geometry, reader, template != null, reason);
    }
    private static final class Integrated implements PieceReader {
        private static final Integrated INSTANCE = new Integrated();
        private final Class<?> type;
        private final Field template;
        Integrated() {
            Class<?> loaded = null; Field field = null;
            try {
                loaded = Class.forName("com.craisinlord.integrated_api.world.structures.pieces.IASinglePoolElement", false, PieceReaders.class.getClassLoader());
                field = loaded.getField("template");
                if (field.getType() != Either.class) field = null;
            } catch (ReflectiveOperationException | LinkageError ignored) { /* Unsupported remains explicit. */ }
            type = loaded; template = field;
        }
        public Result read(StructurePiece piece) {
            if (!(piece instanceof PoolElementStructurePiece pool) || type == null || template == null || pool.getElement().getClass() != type)
                return result(piece, null, SettlementProfile.INTEGRATED, "unsupported_integrated_element");
            try {
                Object id = ((Either<?, ?>) template.get(pool.getElement())).left().orElse(null);
                return result(piece, id instanceof ResourceLocation location ? location : null,
                        SettlementProfile.INTEGRATED, id instanceof ResourceLocation ? "verified_integrated_single" : "runtime_template_without_id");
            } catch (ReflectiveOperationException | RuntimeException failure) {
                return result(piece, null, SettlementProfile.INTEGRATED, "integrated_metadata_unavailable");
            }
        }
    }
}
