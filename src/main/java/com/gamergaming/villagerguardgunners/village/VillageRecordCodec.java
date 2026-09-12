package com.gamergaming.villagerguardgunners.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict current-format codec. Unknown or malformed data is preserved by the enclosing saved data. */
public final class VillageRecordCodec {
    public static CompoundTag encode(VillageRecord record) {
        CompoundTag out = new CompoundTag();
        out.putUUID("Id", record.identity().id());
        out.putString("Structure", record.identity().structure().toString());
        out.putLong("Start", record.identity().startChunk());
        out.putLong("Revision", record.revision());
        ListTag pieces = new ListTag();
        for (var piece : record.footprint().pieces()) {
            CompoundTag p = new CompoundTag();
            p.putIntArray("Position", new int[]{piece.position().getX(), piece.position().getY(), piece.position().getZ()});
            p.putString("Rotation", piece.rotation());
            p.putString("Template", piece.template().toString());
            var b = piece.bounds();
            p.putIntArray("Bounds", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
            pieces.add(p);
        }
        out.put("Pieces", pieces);
        ListTag homes = new ListTag();
        record.homes().stream().sorted(BlockPos::compareTo).forEach(p ->
                homes.add(new net.minecraft.nbt.IntArrayTag(new int[]{p.getX(), p.getY(), p.getZ()})));
        out.put("Homes", homes);
        return out;
    }

    public static VillageRecord decode(CompoundTag in, ResourceLocation dimension) {
        keys(in, Set.of("Id", "Structure", "Start", "Revision", "Pieces", "Homes"));
        require(in.hasUUID("Id") && in.contains("Structure", Tag.TAG_STRING)
                && in.contains("Start", Tag.TAG_LONG) && in.contains("Revision", Tag.TAG_LONG));
        var identity = new VillageIdentity(dimension, id(in.getString("Structure")), in.getLong("Start"));
        require(identity.id().equals(in.getUUID("Id")));
        var pieces = new ArrayList<VillageFootprint.Piece>();
        for (Tag raw : list(in, "Pieces", Tag.TAG_COMPOUND, VillageFootprint.MAX_PIECES)) {
            CompoundTag p = (CompoundTag) raw;
            keys(p, Set.of("Position", "Rotation", "Template", "Bounds"));
            require(p.contains("Position", Tag.TAG_INT_ARRAY) && p.contains("Bounds", Tag.TAG_INT_ARRAY)
                    && p.contains("Rotation", Tag.TAG_STRING) && p.contains("Template", Tag.TAG_STRING));
            int[] pos = p.getIntArray("Position"), b = p.getIntArray("Bounds");
            require(pos.length == 3 && b.length == 6);
            pieces.add(new VillageFootprint.Piece(new BlockPos(pos[0], pos[1], pos[2]), p.getString("Rotation"),
                    id(p.getString("Template")), new VillageFootprint.Bounds(b[0], b[1], b[2], b[3], b[4], b[5])));
        }
        Set<BlockPos> homes = new HashSet<>();
        for (Tag raw : list(in, "Homes", Tag.TAG_INT_ARRAY, VillageRecord.MAX_HOMES)) {
            int[] pos = ((net.minecraft.nbt.IntArrayTag) raw).getAsIntArray();
            require(pos.length == 3 && homes.add(new BlockPos(pos[0], pos[1], pos[2])));
        }
        return new VillageRecord(identity, VillageFootprint.of(pieces), in.getLong("Revision"), homes);
    }

    static ListTag list(CompoundTag in, String key, int type, int maximum) {
        require(in.get(key) instanceof ListTag);
        ListTag list = (ListTag) in.get(key);
        require(list.size() <= maximum && (list.isEmpty() || list.getElementType() == type));
        return list;
    }
    static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        require(id != null && id.toString().equals(value));
        return id;
    }
    static void keys(CompoundTag in, Set<String> keys) { require(in.getAllKeys().equals(keys)); }
    static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid village data"); }
    private VillageRecordCodec() {}
}
