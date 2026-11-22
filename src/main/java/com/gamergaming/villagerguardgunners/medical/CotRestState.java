package com.gamergaming.villagerguardgunners.medical;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** A location on the original patient; inventories and occupancy leases are never copied. */
public final class CotRestState {
    public static final String KEY = "villagerguardgunners:cot_rest";
    private BlockPos head;
    private Direction facing;
    private ResourceLocation dimension;
    private Tag preserved;
    public long nextExitAttempt;
    public boolean known() { return preserved == null; }
    public BlockPos head() { return head; }
    public Direction facing() { return facing; }
    public ResourceLocation dimension() { return dimension; }
    public void anchor(BlockPos pos, Direction direction, ResourceLocation level) {
        if (!known() || direction.getAxis().isVertical()) throw new IllegalStateException("Invalid cot anchor");
        head = pos.immutable(); facing = direction; dimension = level; nextExitAttempt = 0;
    }
    public void clear() { head = null; facing = null; dimension = null; nextExitAttempt = 0; }
    public void save(CompoundTag root) {
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        if (head == null) { root.remove(KEY); return; }
        var data = new CompoundTag(); data.putInt("Version", 1); data.putLong("Head", head.asLong());
        data.putString("Facing", facing.getName()); data.putString("Dimension", dimension.toString()); root.put(KEY, data);
    }
    public void load(CompoundTag root) {
        clear(); preserved = null; if (!root.contains(KEY)) return; preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag data) || !data.getAllKeys().equals(Set.of("Version", "Head", "Facing", "Dimension"))
                || !data.contains("Version", Tag.TAG_INT) || data.getInt("Version") != 1 || !data.contains("Head", Tag.TAG_LONG)
                || !data.contains("Facing", Tag.TAG_STRING) || !data.contains("Dimension", Tag.TAG_STRING)) return;
        var direction = Direction.byName(data.getString("Facing")); var level = ResourceLocation.tryParse(data.getString("Dimension"));
        var pos = BlockPos.of(data.getLong("Head"));
        if (direction == null || direction.getAxis().isVertical() || level == null || Math.abs(pos.getX()) > 30_000_000 || Math.abs(pos.getZ()) > 30_000_000) return;
        preserved = null; anchor(pos, direction, level);
    }
}
