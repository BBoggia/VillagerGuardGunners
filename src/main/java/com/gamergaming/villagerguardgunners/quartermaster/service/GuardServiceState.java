package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Equipment policy is explicit and independent of behavior role assignment. */
public final class GuardServiceState {
    public static final String KEY = "villagerguardgunners:equipment_service";
    public enum Policy { MANUAL, RESTOCK, PROFILE }
    private final tallestegg.guardvillagers.entities.Guard guard;
    public final ServiceReceipt receipt;
    public GuardServiceState(tallestegg.guardvillagers.entities.Guard guard) { this.guard = guard; receipt = new ServiceReceipt(guard.getUUID()); }
    private Policy policy = Policy.MANUAL;
    private ResourceLocation profile;
    private boolean replaceExisting;
    private Tag preserved;
    public long nextAttempt;
    public ServiceStatus status = ServiceStatus.MANUAL;
    public Policy policy() { return policy; }
    public ResourceLocation profile() { return profile; }
    public boolean replaceExisting() { return replaceExisting; }
    public boolean known() { return preserved == null; }
    public boolean readable() { return preserved == null && !receipt.locked(); }
    public void policy(Policy value) { if (readable()) { policy = value; nextAttempt = 0; } }
    public void profile(ResourceLocation value) { if (readable()) { profile = value; nextAttempt = 0; } }
    public void replaceExisting(boolean value) { if (readable()) replaceExisting = value; }
    public void save(CompoundTag root) {
        receipt.save(root);
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        CompoundTag data = new CompoundTag(); data.putInt("Version", 1); data.putString("Policy", policy.name());
        if (profile != null) data.putString("Profile", profile.toString());
        data.putBoolean("ReplaceExisting", replaceExisting); root.put(KEY, data);
    }
    public void load(CompoundTag root) {
        receipt.load(root, guard.getUUID()); policy = Policy.MANUAL; profile = null; replaceExisting = false; preserved = null; nextAttempt = 0;
        if (!root.contains(KEY)) return;
        preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag data) || !Set.of("Version", "Policy", "Profile", "ReplaceExisting").containsAll(data.getAllKeys())
                || !data.contains("Version", Tag.TAG_INT) || data.getInt("Version") != 1 || !data.contains("Policy", Tag.TAG_STRING)
                || !data.contains("ReplaceExisting", Tag.TAG_BYTE)) return;
        try {
            Policy decoded = Policy.valueOf(data.getString("Policy"));
            ResourceLocation selected = data.contains("Profile") ? ResourceLocation.tryParse(data.getString("Profile")) : null;
            if (data.contains("Profile") && (selected == null || !data.contains("Profile", Tag.TAG_STRING)
                    || !selected.toString().equals(data.getString("Profile")) || selected.toString().length() > 96)) return;
            policy = decoded; profile = selected; replaceExisting = data.getBoolean("ReplaceExisting"); preserved = null;
        } catch (IllegalArgumentException ignored) { /* Preserve unknown policy. */ }
    }
}
