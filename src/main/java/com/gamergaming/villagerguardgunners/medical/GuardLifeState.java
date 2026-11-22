package com.gamergaming.villagerguardgunners.medical;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

/** Loaded simulation time only. Equipment and entity identity never move into this record. */
public final class GuardLifeState {
    public static final String KEY = "villagerguardgunners:life";
    public static final int BLEED_TICKS = 1200, RECOVERY_TICKS = 100, COOLDOWN_TICKS = 2400;
    public enum Stage { ACTIVE, DOWNED, STABILIZED, RECOVERING }
    private Stage stage = Stage.ACTIVE;
    private int remaining, cooldown;
    private int recoveryTotal, fieldRecoveryTotal, recoveryElapsed;
    private boolean cotRecovery;
    public static final int MAX_RECOVERY_TICKS = 2400;
    private Tag preserved;
    private ResourceLocation damageType = ResourceLocation.withDefaultNamespace("generic");
    private UUID attacker;
    public transient DamageSource source;
    public transient boolean dying;
    public transient long lastDamageTick = Long.MIN_VALUE;
    private long revision;
    public Stage stage() { return stage; }
    public int remaining() { return remaining; }
    public int cooldown() { return cooldown; }
    public long revision() { return revision; }
    public boolean known() { return preserved == null; }
    public boolean casualty() { return stage == Stage.DOWNED || stage == Stage.STABILIZED; }
    public boolean blocked() { return stage != Stage.ACTIVE; }
    public ResourceLocation damageType() { return damageType; }
    public UUID attacker() { return attacker; }
    public boolean down(DamageSource damage, long now) {
        if (!known() || blocked() || cooldown > 0 || dying) return false;
        stage = Stage.DOWNED; remaining = BLEED_TICKS; source = damage; lastDamageTick = now;
        damageType = damage.typeHolder().unwrapKey().map(key -> key.location()).orElse(ResourceLocation.withDefaultNamespace("generic"));
        attacker = damage.getEntity() == null ? null : damage.getEntity().getUUID(); revision++; return true;
    }
    public void finishDamage(float amount, long now) {
        // Native TaCZ split components and a queued burst can share a simulation tick.
        if (!casualty() || now == lastDamageTick || !(amount > 0)) return;
        lastDamageTick = now; remaining = Math.max(0, remaining - Math.min(200, Math.max(1, (int)Math.ceil(amount * 20)))); revision++;
    }
    public void treated() { treated(0); }
    public void treated(int treatmentCooldown) {
        if (stage == Stage.DOWNED) { stage = Stage.STABILIZED; remaining = BLEED_TICKS + Math.max(0, Math.min(1200, treatmentCooldown)); revision++; }
        else if (stage == Stage.STABILIZED) recover();
    }
    public void recover() { recover(RECOVERY_TICKS, 0, false); }
    public void recover(int ticks, int fieldTicks, boolean cot) {
        if (ticks < 1 || ticks > MAX_RECOVERY_TICKS || fieldTicks < 0 || fieldTicks > MAX_RECOVERY_TICKS || cot && fieldTicks < ticks)
            throw new IllegalArgumentException("Invalid recovery duration");
        stage = Stage.RECOVERING; recoveryElapsed = 0; remaining = recoveryTotal = ticks; fieldRecoveryTotal = fieldTicks; cotRecovery = cot;
        cooldown = COOLDOWN_TICKS; dying = false; source = null; revision++;
    }
    public void fieldRecovery() {
        if (stage != Stage.RECOVERING || !cotRecovery) return;
        int elapsed = recoveryElapsed;
        remaining = Math.max(0, fieldRecoveryTotal - elapsed); recoveryTotal = fieldRecoveryTotal; cotRecovery = false; revision++;
    }
    public void tick() { tick(false); }
    public void tick(boolean restingOnCot) {
        if (cooldown > 0) cooldown--;
        if (stage == Stage.RECOVERING) recoveryElapsed = Math.min(MAX_RECOVERY_TICKS, recoveryElapsed + 1);
        if (blocked() && remaining > 0) remaining--;
        if (stage == Stage.RECOVERING && remaining == 0 && !restingOnCot) { stage = Stage.ACTIVE; recoveryTotal = fieldRecoveryTotal = recoveryElapsed = 0; cotRecovery = false; revision++; }
    }
    public void save(CompoundTag root) {
        if (preserved != null) { root.put(KEY, preserved.copy()); return; }
        var data = new CompoundTag(); data.putInt("Version", 2); data.putString("Stage", stage.name());
        data.putInt("RecoveryElapsed", recoveryElapsed); data.putInt("RecoveryTotal", recoveryTotal); data.putInt("FieldRecovery", fieldRecoveryTotal); data.putBoolean("CotRecovery", cotRecovery);
        data.putInt("Remaining", remaining); data.putInt("Cooldown", cooldown); data.putString("DamageType", damageType.toString());
        if (attacker != null) data.putUUID("Attacker", attacker); root.put(KEY, data);
    }
    public void load(CompoundTag root) {
        stage = Stage.ACTIVE; remaining = cooldown = recoveryTotal = fieldRecoveryTotal = recoveryElapsed = 0; cotRecovery = false; preserved = null; source = null; dying = false;
        attacker = null; damageType = ResourceLocation.withDefaultNamespace("generic"); lastDamageTick = Long.MIN_VALUE; revision++;
        if (!root.contains(KEY)) return;
        preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag data)) return;
        int version = data.getInt("Version");
        var required = version == 2 ? Set.of("Version", "Stage", "Remaining", "Cooldown", "DamageType", "RecoveryTotal", "FieldRecovery", "CotRecovery", "RecoveryElapsed")
                : Set.of("Version", "Stage", "Remaining", "Cooldown", "DamageType");
        if (!data.getAllKeys().containsAll(required) || data.getAllKeys().stream().anyMatch(key -> !required.contains(key) && !key.equals("Attacker"))
                || !data.contains("Version", Tag.TAG_INT) || (version != 1 && version != 2)
                || !data.contains("Stage", Tag.TAG_STRING) || !data.contains("Remaining", Tag.TAG_INT)
                || !data.contains("Cooldown", Tag.TAG_INT) || !data.contains("DamageType", Tag.TAG_STRING)
                || data.contains("Attacker") && !data.hasUUID("Attacker")) return;
        try {
            Stage next = Stage.valueOf(data.getString("Stage")); int timer = data.getInt("Remaining"), retry = data.getInt("Cooldown");
            int total = version == 1 ? (next == Stage.RECOVERING ? RECOVERY_TICKS : 0) : data.getInt("RecoveryTotal");
            int elapsed = version == 1 ? (next == Stage.RECOVERING ? RECOVERY_TICKS - timer : 0) : data.getInt("RecoveryElapsed");
            int field = version == 1 ? 0 : data.getInt("FieldRecovery"); boolean cot = version == 2 && data.getBoolean("CotRecovery");
            if (version == 2 && (!data.contains("RecoveryElapsed", Tag.TAG_INT) || !data.contains("RecoveryTotal", Tag.TAG_INT) || !data.contains("FieldRecovery", Tag.TAG_INT) || !data.contains("CotRecovery", Tag.TAG_BYTE))) return;
            if (elapsed < 0 || elapsed > MAX_RECOVERY_TICKS || next != Stage.RECOVERING && elapsed != 0
                    || next == Stage.RECOVERING && timer != Math.max(0, total - elapsed) || total < 0 || total > MAX_RECOVERY_TICKS || field < 0 || field > MAX_RECOVERY_TICKS || cot && field < total
                    || next != Stage.RECOVERING && (total != 0 || field != 0 || cot) || next == Stage.RECOVERING && total == 0) return;
            ResourceLocation type = ResourceLocation.tryParse(data.getString("DamageType"));
            if (timer < 0 || timer > (next == Stage.RECOVERING ? total : next == Stage.STABILIZED ? BLEED_TICKS + 1200 : BLEED_TICKS)
                    || next == Stage.ACTIVE && timer != 0 || retry < 0 || retry > COOLDOWN_TICKS || type == null) return;
            stage = next; remaining = timer; cooldown = retry; damageType = type; recoveryTotal = total; recoveryElapsed = elapsed; fieldRecoveryTotal = field; cotRecovery = cot;
            attacker = data.hasUUID("Attacker") ? data.getUUID("Attacker") : null; preserved = null;
        } catch (IllegalArgumentException ignored) { /* Preserve future and malformed records without immobilizing the guard. */ }
    }
}
