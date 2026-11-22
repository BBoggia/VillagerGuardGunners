package com.gamergaming.villagerguardgunners.medical;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Only player choices persist; leases and channels must be acquired again after loading. */
public final class MedicState {
    public static final String KEY = "villagerguardgunners:medic_duty";
    public enum Status { IDLE, DISABLED, NO_SUPPLY, APPROACH, PREPARE, HEALED, INTERRUPTED, RETREAT, RECOVER, NO_SAFE_DESTINATION, PLAYER_CONTROL, UNREADABLE }
    private boolean medic, retreat;
    private Tag preserved;
    public boolean recovering;
    public int searchCell;
    public final java.util.Map<Long, java.util.UUID> searchAfter = new java.util.HashMap<>();
    public long nextTransportAttempt;
    public java.util.UUID transportPatient;
    public final java.util.Map<net.minecraft.core.BlockPos, Long> failedCots = new java.util.LinkedHashMap<>();
    public String transportReason = "none";
    public long nextAssessment, damageRevision, unreachableUntil;
    public java.util.UUID unreachablePatient;
    public Status status = Status.IDLE;
    public boolean known() { return preserved == null; }
    public boolean medic() { return known() && medic; }
    public boolean retreat() { return known() && retreat; }
    public void medic(boolean value) { if (known()) medic = value; }
    public void retreat(boolean value) { if (known()) retreat = value; }
    public void save(CompoundTag root) {
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        var data = new CompoundTag(); data.putInt("Version", 1); data.putBoolean("Medic", medic); data.putBoolean("Retreat", retreat); root.put(KEY, data);
    }
    public void load(CompoundTag root) {
        nextTransportAttempt = 0; transportPatient = null; failedCots.clear(); transportReason = "none";
        searchCell = 0; searchAfter.clear();
        medic = retreat = recovering = false; nextAssessment = damageRevision = unreachableUntil = 0; unreachablePatient = null; preserved = null; status = Status.IDLE;
        if (!root.contains(KEY)) return;
        preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag data) || !data.getAllKeys().equals(Set.of("Version", "Medic", "Retreat"))
                || !data.contains("Version", Tag.TAG_INT) || data.getInt("Version") != 1
                || !data.contains("Medic", Tag.TAG_BYTE) || !data.contains("Retreat", Tag.TAG_BYTE)) { status = Status.UNREADABLE; return; }
        medic = data.getBoolean("Medic"); retreat = data.getBoolean("Retreat"); preserved = null;
    }
}
