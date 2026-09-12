package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded server-owned manual revival channels. Supplies stay in their original slot until completion. */
public final class PlayerTreatmentRuntime {
    private static final Map<UUID, Channel> PLAYERS = new HashMap<>(), PATIENTS = new HashMap<>();
    private static Channel completing;
    static final class Channel {
        final Player player;
        final Guard patient;
        final Supplier<ItemStack> source;
        final Consumer<ItemStack> replace;
        final ItemStack supply;
        final TreatmentProfile profile;
        final CasualtyCarePolicy care;
        final UUID pouch;
        final long started, revision, damage;
        final net.minecraft.world.phys.Vec3 position;
        Channel(Player player, Guard patient, Supplier<ItemStack> source, Consumer<ItemStack> replace) {
            this.player = player; this.patient = patient; this.source = source; this.replace = replace;
            supply = source.get().copy(); profile = TreatmentProfiles.resolve(supply); care = CasualtyCarePolicy.forPatient(patient);
            pouch = GuardMedicalAccess.get(patient).dropId(); started = patient.level().getGameTime();
            revision = GuardLifeAccess.get(patient).revision(); damage = GuardMedicAccess.get(patient).damageRevision; position = patient.position();
        }
    }
    private PlayerTreatmentRuntime() { }
    public static boolean busy(Guard guard) { return PATIENTS.containsKey(guard.getUUID()); }
    public static boolean busy(Player player) { return PLAYERS.containsKey(player.getUUID()); }
    static CasualtyCarePolicy completingCare(Player player, Guard guard) {
        return completing != null && completing.player == player && completing.patient == guard ? completing.care : null;
    }
    private static boolean registered(Channel channel) {
        return PLAYERS.get(channel.player.getUUID()) == channel && PATIENTS.get(channel.patient.getUUID()) == channel;
    }
    static boolean completionValid() {
        return completing != null && registered(completing) && valid(completing)
                && completing.patient.level().getGameTime() - completing.started >= completing.care.channelTicks();
    }
    public static MedicalTreatment.Result begin(Player player, Guard patient, Supplier<ItemStack> source, Consumer<ItemStack> replace) {
        if (busy(player) || busy(patient) || PLAYERS.size() >= 64 || MedicRuntime.lease(patient) != null || TransportRuntime.busy(patient)) return MedicalTreatment.Result.BUSY;
        var channel = new Channel(player, patient, source, replace);
        if (channel.care.mode() == CasualtyCarePolicy.Mode.NORMAL || channel.profile == null || !valid(channel)) return MedicalTreatment.Result.UNAVAILABLE;
        PLAYERS.put(player.getUUID(), channel); PATIENTS.put(patient.getUUID(), channel); progress(channel);
        return MedicalTreatment.Result.STARTED;
    }
    private static boolean valid(Channel channel) {
        var patient = channel.patient; var player = channel.player;
        return !player.isRemoved() && !patient.isRemoved() && MedicalTreatment.gate(player, patient) == null
                && GuardLifeAccess.get(patient).stage() == GuardLifeState.Stage.STABILIZED
                && GuardLifeAccess.get(patient).revision() == channel.revision && GuardMedicAccess.get(patient).damageRevision == channel.damage
                && patient.position().distanceToSqr(channel.position) <= .04 && !patient.isPassenger()
                && channel.pouch.equals(GuardMedicalAccess.get(patient).dropId()) && (GuardMedicalAccess.get(patient).available() || completing == channel && GuardMedicalAccess.get(patient).readable() && !GuardMedicalAccess.get(patient).released())
                && patient.level().getGameTime() >= GuardMedicalAccess.get(patient).readyAt()
                && channel.care.validFor(patient) && ItemStack.matches(channel.supply, channel.source.get())
                && channel.profile.equals(TreatmentProfiles.resolve(channel.source.get()));
    }
    private static void remove(Channel channel) { PLAYERS.remove(channel.player.getUUID(), channel); PATIENTS.remove(channel.patient.getUUID(), channel); }
    public static void cancel(Guard guard) { var channel = PATIENTS.get(guard.getUUID()); if (channel != null) { remove(channel); MedicalTreatment.report(channel.player, MedicalTreatment.Result.CANCELED); } }
    private static void progress(Channel channel) {
        int seconds = Math.max(0, (int)(channel.care.channelTicks() - (channel.patient.level().getGameTime() - channel.started) + 19) / 20);
        channel.player.displayClientMessage(Component.translatable("message.villagerguardgunners.medical.progress",
                channel.patient.getDisplayName(), seconds), true);
    }
    public static int visual(Guard guard) {
        var channel = PATIENTS.get(guard.getUUID());
        return channel == null ? 0 : visual(channel.care, guard.level().getGameTime() - channel.started);
    }
    public static int visual(CasualtyCarePolicy care, long elapsed) {
        int kind = care.mode() == CasualtyCarePolicy.Mode.COT ? 1 : care.mode() == CasualtyCarePolicy.Mode.FIELD ? 2 : 3;
        return kind * 1000 + Math.max(0, Math.min(100, (int)(elapsed * 100 / care.channelTicks()))) / 5 * 5;
    }
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var channel : List.copyOf(PLAYERS.values())) {
            if (!registered(channel) || channel.patient.level().getServer() != event.getServer()) continue;
            if (!valid(channel)) { cancel(channel.patient); continue; }
            long elapsed = channel.patient.level().getGameTime() - channel.started;
            if (elapsed % 20 == 0) progress(channel);
            if (elapsed < channel.care.channelTicks()) continue;
            MedicalTreatment.Result result;
            completing = channel;
            try { result = MedicalTreatment.apply(channel.player, channel.patient, channel.source, channel.replace); }
            finally { completing = null; remove(channel); }
            MedicalTreatment.report(channel.player, result);
        }
    }
    public static void damaged(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        if (event.getEntity() instanceof Guard guard) cancel(guard);
        var channel = PLAYERS.get(event.getEntity().getUUID()); if (channel != null) cancel(channel.patient);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { var channel = PLAYERS.get(event.getEntity().getUUID()); if (channel != null) cancel(channel.patient); }
    public static void stopped(ServerStoppedEvent event) {
        for (var channel : List.copyOf(PLAYERS.values())) if (channel.patient.level().getServer() == event.getServer()) remove(channel);
    }
}
