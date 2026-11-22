package com.gamergaming.villagerguardgunners.medical;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A recovery hint on the original passenger, never an entity or inventory snapshot. */
public final class TransportState {
    public static final String KEY = "villagerguardgunners:transport";
    private UUID carrier;
    private ResourceLocation dimension;
    private Vec3 safePosition;
    private Tag preserved;
    public long recoveryRetryAt;
    public boolean known() { return preserved == null; }
    public boolean present() { return carrier != null || preserved != null; }
    public UUID carrier() { return carrier; }
    public ResourceLocation dimension() { return dimension; }
    public Vec3 safePosition() { return safePosition; }
    public void mark(UUID carrier, ResourceLocation dimension, Vec3 safe) {
        if (!known() || carrier == null || dimension == null || !finite(safe)) throw new IllegalArgumentException("Invalid transport recovery hint");
        this.carrier = carrier; this.dimension = dimension; safePosition = safe;
    }
    public void clear() { carrier = null; dimension = null; safePosition = null; }
    private static boolean finite(Vec3 p) {
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 20_000_000;
    }
    public void save(CompoundTag root) {
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        if (carrier == null) { root.remove(KEY); return; }
        var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putUUID("Carrier", carrier);
        tag.putString("Dimension", dimension.toString()); tag.putDouble("X", safePosition.x);
        tag.putDouble("Y", safePosition.y); tag.putDouble("Z", safePosition.z); root.put(KEY, tag);
    }
    public void load(CompoundTag root) {
        clear(); preserved = null; recoveryRetryAt = 0; if (!root.contains(KEY)) return; preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag tag) || !tag.getAllKeys().equals(Set.of("Version", "Carrier", "Dimension", "X", "Y", "Z"))
                || !tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Carrier")
                || !tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("X", Tag.TAG_DOUBLE)
                || !tag.contains("Y", Tag.TAG_DOUBLE) || !tag.contains("Z", Tag.TAG_DOUBLE)) return;
        var dim = ResourceLocation.tryParse(tag.getString("Dimension"));
        var pos = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        if (dim == null || !finite(pos)) return;
        carrier = tag.getUUID("Carrier"); dimension = dim; safePosition = pos; preserved = null;
    }
}
