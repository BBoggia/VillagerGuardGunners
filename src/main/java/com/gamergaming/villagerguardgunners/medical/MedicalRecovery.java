package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.init.ModItems;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;

/** Persistent, bounded retry outbox for canceled medical-pouch spawns. Never loads chunks. */
public final class MedicalRecovery extends SavedData {
    private static final String KEY = "villagerguardgunners_medical_recovery";
    private final LinkedHashMap<UUID, Pending> pending = new LinkedHashMap<>();
    private final ListTag quarantined = new ListTag();
    private Tag unsupported;
    private record Pending(BlockPos pos, ItemStack stack) {}

    public static MedicalRecovery get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MedicalRecovery::load, MedicalRecovery::new, KEY);
    }
    public int pendingCount() { return pending.size(); }
    public void enqueue(UUID id, BlockPos pos, ItemStack stack) {
        Pending candidate = new Pending(pos.immutable(), stack.copy());
        Pending existing = pending.putIfAbsent(id, candidate);
        if (existing != null && (!existing.pos.equals(candidate.pos)
                || !ItemStack.matches(existing.stack, candidate.stack))) {
            // Copied identities must not silently overwrite or discard somebody else's stock.
            quarantined.add(encode(id, candidate));
        }
        setDirty();
    }
    public static MedicalRecovery load(CompoundTag tag) {
        MedicalRecovery data = new MedicalRecovery();
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.contains("Pending", Tag.TAG_LIST)
                || !Set.of("Version", "Pending", "Quarantined", "Preserved").containsAll(tag.getAllKeys())
                || (tag.contains("Quarantined") && (!tag.contains("Quarantined", Tag.TAG_LIST)
                        || tag.getList("Quarantined", Tag.TAG_COMPOUND).size()
                                != ((ListTag) tag.get("Quarantined")).size()))
                || tag.getList("Pending", Tag.TAG_COMPOUND).size()
                        != ((ListTag) tag.get("Pending")).size()) {
            data.unsupported = tag.copy();
            return data;
        }
        if (tag.contains("Preserved")) data.unsupported = tag.get("Preserved").copy();
        ListTag entries = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (Tag entry : entries) {
            CompoundTag value = (CompoundTag) entry;
            ItemStack stack = ItemStack.of(value.getCompound("Stack"));
            if (!value.getAllKeys().equals(Set.of("Id", "Pos", "Stack"))
                    || !value.hasUUID("Id") || !value.contains("Pos", Tag.TAG_LONG) || stack.isEmpty()
                    || data.pending.containsKey(value.getUUID("Id"))
                    || !stack.is(ModItems.MEDICAL_POUCH.get())
                    || stack.getCount() != 1
                    || !stack.save(new CompoundTag()).equals(value.getCompound("Stack"))) {
                data.quarantined.add(value.copy());
                continue;
            }
            data.pending.put(value.getUUID("Id"), new Pending(BlockPos.of(value.getLong("Pos")), stack));
        }
        for (Tag entry : tag.getList("Quarantined", Tag.TAG_COMPOUND)) data.quarantined.add(entry.copy());
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        // Preserve an unsupported older outbox separately while allowing new removals to be saved.
        if (unsupported != null) tag.put("Preserved", unsupported.copy());
        tag.putInt("Version", 1);
        ListTag entries = new ListTag();
        pending.forEach((id, value) -> entries.add(encode(id, value)));
        tag.put("Pending", entries);
        tag.put("Quarantined", quarantined.copy());
        return tag;
    }
    private static CompoundTag encode(UUID id, Pending value) {
        CompoundTag item = new CompoundTag();
        item.putUUID("Id", id);
        item.putLong("Pos", value.pos.asLong());
        item.put("Stack", value.stack.save(new CompoundTag()));
        return item;
    }
    public void retry(ServerLevel level) {
        int budget = Math.min(2, pending.size());
        for (int i = 0; i < budget; i++) {
            var iterator = pending.entrySet().iterator();
            var entry = iterator.next();
            UUID id = entry.getKey();
            Pending value = entry.getValue();
            iterator.remove();
            boolean accepted = trySpawn(level, id, value);
            if (!accepted) pending.put(id, value);
            else setDirty();
        }
    }
    private boolean trySpawn(ServerLevel level, UUID id, Pending value) {
        if (!level.hasChunkAt(value.pos) || !level.isPositionEntityTicking(value.pos)) return false;
        if (level.getEntity(id) instanceof ItemEntity item) return ItemStack.matches(item.getItem(), value.stack);
        if (level.getEntity(id) != null) return false;
        ItemEntity item = new ItemEntity(level, value.pos.getX() + .5, value.pos.getY() + .5,
                value.pos.getZ() + .5, value.stack.copy());
        item.setUUID(id);
        item.setDefaultPickUpDelay();
        return level.addFreshEntity(item);
    }
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof tallestegg.guardvillagers.entities.Guard guard) {
            com.gamergaming.villagerguardgunners.api.GuardMedicalAccess.get(guard).release();
        }
    }
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) get(level).retry(level);
    }
}
