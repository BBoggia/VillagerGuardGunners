package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.UUID;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Persistent ownership checkpoint. Unknown data locks its owner rather than resetting identity. */
public final class ServiceReceipt {
    public static final String KEY = "villagerguardgunners:service_receipt";
    private UUID id = UUID.randomUUID();
    private long revision;
    private Tag preserved;
    private boolean quarantined;
    public ServiceReceipt() {}
    public ServiceReceipt(UUID identity) { id = identity; }
    public UUID id() { return id; }
    public long revision() { return revision; }
    public void revision(long value) { revision = value; }
    public boolean locked() { return preserved != null || quarantined; }
    public boolean known() { return preserved == null; }
    void reconciled(long checkpoint) { revision = checkpoint; quarantined = false; }
    public void quarantine() { quarantined = true; }
    public void save(CompoundTag root) {
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 1); tag.putUUID("Id", id);
        tag.putLong("Revision", revision); tag.putBoolean("Quarantined", quarantined); root.put(KEY, tag);
    }
    public void load(CompoundTag root) { load(root, id); }
    public void load(CompoundTag root, UUID identity) {
        id = identity; revision = 0; preserved = null; quarantined = false;
        if (!root.contains(KEY)) return;
        preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag tag) || !tag.getAllKeys().equals(Set.of("Version", "Id", "Revision", "Quarantined"))
                || !tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Id")
                || !identity.equals(tag.getUUID("Id"))
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0
                || !tag.contains("Quarantined", Tag.TAG_BYTE)) return;
        id = tag.getUUID("Id"); revision = tag.getLong("Revision"); quarantined = tag.getBoolean("Quarantined"); preserved = null;
    }
}
