package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Write-ahead ownership watermarks. Never reconstructs an item from a journal snapshot.
 * An older independently saved owner is quarantined until an administrator reconciles its physical items.
 */
public final class ServiceJournal {
    private static final Map<MinecraftServer, ServiceJournal> SERVERS = new WeakHashMap<>();
    public static final int MAX_OWNERS = 32768;
    private final Path file;
    private Map<UUID, Long> revisions = new HashMap<>();
    private boolean healthy = true;
    private UUID lastOperation = new UUID(0, 0);
    public ServiceJournal(Path file) {
        this.file = file;
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file.toFile());
            if (!root.getAllKeys().equals(Set.of("Version", "LastOperation", "Owners")) || root.getInt("Version") != 1
                    || !root.contains("Version", Tag.TAG_INT) || !root.hasUUID("LastOperation") || !root.contains("Owners", Tag.TAG_LIST)) throw new IOException("Unsupported service journal");
            var list = root.getList("Owners", Tag.TAG_COMPOUND);
            if (list.size() > MAX_OWNERS || list.size() != ((ListTag) root.get("Owners")).size()) throw new IOException("Invalid owner list");
            for (Tag value : list) {
                var entry = (CompoundTag) value;
                if (!entry.getAllKeys().equals(Set.of("Id", "Revision")) || !entry.hasUUID("Id")
                        || !entry.contains("Revision", Tag.TAG_LONG) || entry.getLong("Revision") <= 0
                        || revisions.putIfAbsent(entry.getUUID("Id"), entry.getLong("Revision")) != null) throw new IOException("Invalid ownership checkpoint");
            }
            lastOperation = root.getUUID("LastOperation");
        } catch (Exception exception) { healthy = false; }
    }
    public static ServiceJournal get(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, s -> new ServiceJournal(s.getWorldPath(LevelResource.ROOT).resolve("data/villagerguardgunners_equipment_service.dat")));
    }
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
    public boolean healthy() { return healthy; }
    public boolean verify(ServiceReceipt receipt) {
        if (!healthy || receipt.locked() || revisions.getOrDefault(receipt.id(), 0L) != receipt.revision()) {
            receipt.quarantine(); return false;
        }
        return true;
    }
    /** Persist intent before changing either owner. A failed write performs no inventory mutation. */
    public boolean begin(ServiceReceipt source, ServiceReceipt guard) { return begin(source, guard, UUID.randomUUID()); }
    public boolean begin(ServiceReceipt source, ServiceReceipt guard, UUID operation) {
        if (!verify(source) || !verify(guard) || source.id().equals(guard.id())
                || source.revision() == Long.MAX_VALUE || guard.revision() == Long.MAX_VALUE) return false;
        Map<UUID, Long> next = new HashMap<>(revisions);
        next.put(source.id(), source.revision() + 1); next.put(guard.id(), guard.revision() + 1);
        if (next.size() > MAX_OWNERS) return false;
        try { persist(next, operation); revisions = next; lastOperation = operation; return true; }
        catch (IOException exception) { healthy = false; return false; }
    }
    /** Caller must verify that this loaded owner is physically empty before acknowledging its watermark. */
    public boolean recoverEmpty(ServiceReceipt receipt) {
        if (!healthy || !receipt.known()) return false;
        receipt.reconciled(revisions.getOrDefault(receipt.id(), 0L));
        return true;
    }
    public long expected(ServiceReceipt receipt) { return revisions.getOrDefault(receipt.id(), 0L); }
    private void persist(Map<UUID, Long> owners, UUID operation) throws IOException {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 1); root.putUUID("LastOperation", operation);
        ListTag list = new ListTag();
        owners.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag value = new CompoundTag(); value.putUUID("Id", entry.getKey()); value.putLong("Revision", entry.getValue()); list.add(value);
        });
        root.put("Owners", list); Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(root, temporary.toFile());
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
