package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.entities.Guard;

/** Equipment policy is explicit and independent of behavior role assignment. */
public final class GuardServiceState {
    public static final String KEY = "villagerguardgunners:equipment_service";
    public enum Policy { MANUAL, RESTOCK, PROFILE }
    private final UUID identity;
    private final Guard guard;
    public final ServiceReceipt receipt;
    public GuardServiceState(Guard guard) { this(guard.getUUID(), guard); }
    GuardServiceState(UUID identity) { this(identity, null); }
    private GuardServiceState(UUID identity, Guard guard) {
        this.identity = identity; this.guard = guard; receipt = new ServiceReceipt(identity);
    }
    private ServiceSettings settings = new ServiceSettings(Policy.MANUAL, null, false, null);
    private long settingsRevision;
    private Tag preserved;
    public long nextAttempt;
    public ServiceStatus status = ServiceStatus.MANUAL;
    public Policy policy() { return settings.policy(); }
    public ResourceLocation profile() { return settings.profile(); }
    public boolean replaceExisting() { return settings.replaceExisting(); }
    public ServiceStationAssignment assignment() { return settings.assignment(); }
    public ServiceSettings settings() { return settings; }
    public long settingsRevision() { return settingsRevision; }
    public boolean known() { return preserved == null; }
    public boolean readable() { return preserved == null && !receipt.locked(); }
    public void policy(Policy value) { update(new ServiceSettings(value, profile(), replaceExisting(), assignment())); }
    public void profile(ResourceLocation value) { update(new ServiceSettings(policy(), value, replaceExisting(), assignment())); }
    public void replaceExisting(boolean value) { update(new ServiceSettings(policy(), profile(), value, assignment())); }
    public void assignment(ServiceStationAssignment value) { update(new ServiceSettings(policy(), profile(), replaceExisting(), value)); }
    /** Authorization belongs to ServiceControls; internal callers still invalidate old reservations. */
    public boolean update(ServiceSettings value) {
        return update(value, true);
    }
    boolean update(ServiceSettings value, boolean observe) {
        java.util.Objects.requireNonNull(value);
        if (!readable()) return false;
        if (settings.equals(value)) return true;
        settings = value; settingsRevision++;
        if (guard != null) QuartermasterServiceRuntime.cancel(guard, ServiceStatus.RETRY);
        nextAttempt = 0; status = policy() == Policy.MANUAL ? ServiceStatus.MANUAL : ServiceStatus.READY;
        if (observe && guard != null && guard.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getEntity(guard.getUUID()) == guard) ServiceRoster.get(level).observe(guard);
        return true;
    }
    public void save(CompoundTag root) {
        receipt.save(root);
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        CompoundTag data = new CompoundTag(); data.putInt("Version", 2); data.putString("Policy", policy().name());
        if (profile() != null) data.putString("Profile", profile().toString());
        data.putBoolean("ReplaceExisting", replaceExisting());
        if (assignment() != null) data.put("Assignment", assignment().save());
        root.put(KEY, data);
    }
    public void load(CompoundTag root) {
        receipt.load(root, guard == null ? identity : guard.getUUID());
        settings = new ServiceSettings(Policy.MANUAL, null, false, null);
        preserved = null; nextAttempt = 0; settingsRevision++; status = ServiceStatus.MANUAL;
        if (!root.contains(KEY)) return;
        preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag data) || !data.contains("Version", Tag.TAG_INT)) return;
        int version = data.getInt("Version");
        Set<String> allowed = version == 1 ? Set.of("Version", "Policy", "Profile", "ReplaceExisting")
                : Set.of("Version", "Policy", "Profile", "ReplaceExisting", "Assignment");
        if ((version != 1 && version != 2) || !allowed.containsAll(data.getAllKeys())
                || !data.contains("Policy", Tag.TAG_STRING) || !data.contains("ReplaceExisting", Tag.TAG_BYTE)
                || data.getByte("ReplaceExisting") < 0 || data.getByte("ReplaceExisting") > 1) return;
        try {
            Policy policy = Policy.valueOf(data.getString("Policy"));
            ResourceLocation profile = null;
            if (data.contains("Profile")) {
                if (!data.contains("Profile", Tag.TAG_STRING)) return;
                String raw = data.getString("Profile"); profile = ResourceLocation.tryParse(raw);
                if (profile == null || !profile.toString().equals(raw)) return;
            }
            ServiceStationAssignment assignment = null;
            if (data.contains("Assignment")) {
                if (!data.contains("Assignment", Tag.TAG_COMPOUND)) return;
                assignment = ServiceStationAssignment.load(data.getCompound("Assignment"));
            }
            settings = new ServiceSettings(policy, profile, data.getBoolean("ReplaceExisting"), assignment);
            preserved = null; status = policy == Policy.MANUAL ? ServiceStatus.MANUAL : ServiceStatus.READY;
        } catch (IllegalArgumentException ignored) { /* Preserve unknown or malformed settings. */ }
    }
}
