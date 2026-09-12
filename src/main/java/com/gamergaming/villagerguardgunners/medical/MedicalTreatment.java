package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import tallestegg.guardvillagers.GuardVillagers;
import tallestegg.guardvillagers.entities.Guard;

/** Synchronous one-dose treatment with the same cancellable boundary used by LivingEntity.heal. */
public final class MedicalTreatment {
    private static final Set<UUID> activePatients = new HashSet<>();
    private static final Set<UUID> activePlayers = new HashSet<>();
    public enum Result { HEALED, FULL_HEALTH, CANCELED, NO_SUPPLY, COOLDOWN, UNAVAILABLE, DENIED, TOO_FAR, BUSY, STOCKED, FULL_POUCH, RESTORED, PACKED, FULL_INVENTORY, STARTED }
    public static boolean mayAccess(Player player, Guard guard) {
        return player != null && !player.isSpectator() && player.isAlive() && guard.isAlive() && !guard.isRemoved()
                && com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()
                && !com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(guard)
                && player.level() == guard.level() && guard.getTarget() != player && GuardVillagers.hotvChecker(player, guard);
    }
    static Result gate(Player player, Guard guard) {
        if (!mayAccess(player, guard)) return Result.DENIED;
        if (player.distanceToSqr(guard) > 16 || !player.hasLineOfSight(guard)) return Result.TOO_FAR;
        if (TransportRuntime.busy(guard)) return Result.BUSY;
        if (GuardLifeRuntime.casualty(guard) && !GuardLifeRuntime.enabled()) return Result.UNAVAILABLE;
        if (!ModConfigs.GUARD_GUNNERS.medical.enabled.get() || (!GuardMedicalAccess.get(guard).readable() || GuardMedicalAccess.get(guard).released())) return Result.UNAVAILABLE;
        if (!Float.isFinite(guard.getHealth()) || !Float.isFinite(guard.getMaxHealth()) || guard.getHealth() <= 0) return Result.UNAVAILABLE;
        return null;
    }
    public static Result fromPouch(Player player, Guard guard) {
        var storage = GuardMedicalAccess.get(guard);
        if (guard.level().isClientSide) return Result.UNAVAILABLE;
        for (int slot = 0; slot < MedicalStorage.SLOTS; slot++) {
            if (TreatmentProfiles.resolve(storage.inventory().getStackInSlot(slot)) != null) {
                final int index = slot;
                return apply(player, guard, () -> storage.inventory().getStackInSlot(index), value -> storage.inventory().setStackInSlot(index, value));
            }
        }
        return Result.NO_SUPPLY;
    }
    public static Result fromHand(Player player, Guard guard, InteractionHand hand) {
        return apply(player, guard, () -> player.getItemInHand(hand), value -> player.setItemInHand(hand, value));
    }
    static Result apply(Player player, Guard guard, Supplier<ItemStack> source, Consumer<ItemStack> replace) {
        if (guard.level().isClientSide) return Result.UNAVAILABLE;
        if (mayAccess(player, guard) && player.distanceToSqr(guard) <= 16 && player.hasLineOfSight(guard)) TransportRuntime.cancel(guard);
        Result denied = gate(player, guard); if (denied != null) return denied;
        var care = PlayerTreatmentRuntime.completingCare(player, guard);
        if (care == null && (PlayerTreatmentRuntime.busy(player) || PlayerTreatmentRuntime.busy(guard))) return Result.BUSY;
        if (care != null && !PlayerTreatmentRuntime.completionValid()) return Result.CANCELED;
        var storage = GuardMedicalAccess.get(guard);
        if (!storage.available() || activePatients.contains(guard.getUUID()) || activePlayers.contains(player.getUUID())) return Result.BUSY;
        if (!GuardLifeRuntime.casualty(guard) && guard.getHealth() >= guard.getMaxHealth()) return Result.FULL_HEALTH;
        if (guard.level().getGameTime() < storage.readyAt()) return Result.COOLDOWN;
        ItemStack original = source.get().copy();
        TreatmentProfile profile = TreatmentProfiles.resolve(original);
        if (profile == null) return Result.NO_SUPPLY;
        if (care == null && CasualtyCarePolicy.forPatient(guard).mode() != CasualtyCarePolicy.Mode.NORMAL)
            return PlayerTreatmentRuntime.begin(player, guard, source, replace);
        if (care == null) care = CasualtyCarePolicy.forPatient(guard);
        activePatients.add(guard.getUUID()); activePlayers.add(player.getUUID()); storage.busy(true);
        UUID identity = storage.dropId();
        try {
            // Invoke the vanilla Forge boundary once, then measure only our own post-event health gain.
            // A handler may heal independently, cancel, move items, reload profiles, or remove the guard.
            long lifeRevision = com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(guard).revision();
            float accepted = GuardLifeRuntime.treatmentEvent(guard, profile.healing());
            if (!Float.isFinite(accepted) || accepted <= 0) return Result.CANCELED;
            denied = gate(player, guard); if (denied != null) return denied;
            if (PlayerTreatmentRuntime.completingCare(player, guard) != null && !PlayerTreatmentRuntime.completionValid()) return Result.CANCELED;
            if (!care.validFor(guard) || lifeRevision != com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(guard).revision() || !identity.equals(storage.dropId()) || !ItemStack.matches(original, source.get())
                    || !profile.equals(TreatmentProfiles.resolve(source.get()))) return Result.CANCELED;
            float before = GuardLifeRuntime.casualty(guard) ? 1 : guard.getHealth();
            if (before >= guard.getMaxHealth()) return Result.FULL_HEALTH;
            float after = Math.min(guard.getMaxHealth(), before + accepted);
            if (!Float.isFinite(after) || after <= before) return Result.CANCELED;
            ItemStack replacement = original.copy(); replacement.shrink(1);
            if (profile.returnedItem() != null) replacement = new ItemStack(BuiltInRegistries.ITEM.get(profile.returnedItem()));
            // Accepted returned-container profiles consume their entire single-stack source slot.
            // No overflow item entity, inventory search, or item use callback is necessary.
            replace.accept(replacement);
            GuardLifeRuntime.completeTreatment(guard, after, profile.cooldownTicks(), care);
            storage.readyAt(guard.level().getGameTime() + profile.cooldownTicks());
            return Result.HEALED;
        } finally {
            storage.busy(false); activePatients.remove(guard.getUUID()); activePlayers.remove(player.getUUID());
        }
    }
    /** Medic doses share the player treatment re-entry boundary and never use item-spawn overflow. */
    public static Result fromMedic(Guard medic, Guard patient, int slot, java.util.function.BooleanSupplier stillValid) {
        var lease = MedicRuntime.lease(medic);
        if (medic.level().isClientSide || lease == null || lease.medic != medic || lease.patient != patient
                || slot != lease.slot || (lease.care == null || medic.level().getGameTime() - lease.preparedAt < lease.care.channelTicks()) || !MedicRuntime.channelValid(lease)
                || !stillValid.getAsBoolean()) return Result.UNAVAILABLE;
        var source = GuardMedicalAccess.get(medic); var target = GuardMedicalAccess.get(patient);
        if (!source.available() || slot < 0 || slot >= MedicalStorage.SLOTS || TreatmentProfiles.resolve(source.inventory().getStackInSlot(slot)) == null) return Result.NO_SUPPLY;
        if (!target.available() || activePatients.contains(medic.getUUID()) || activePatients.contains(patient.getUUID())) return Result.BUSY;
        if (!GuardLifeRuntime.casualty(patient) && patient.getHealth() >= patient.getMaxHealth()) return Result.FULL_HEALTH;
        ItemStack original = source.inventory().getStackInSlot(slot).copy(); var profile = TreatmentProfiles.resolve(original);
        UUID sourceId = source.dropId(), targetId = target.dropId();
        activePatients.add(medic.getUUID()); activePatients.add(patient.getUUID()); source.busy(true); target.busy(true);
        try {
            long lifeRevision = com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(patient).revision();
            float accepted = GuardLifeRuntime.treatmentEvent(patient, profile.healing());
            if (!Float.isFinite(accepted) || accepted <= 0) return Result.CANCELED;
            if (lifeRevision != com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(patient).revision() || !MedicRuntime.channelValid(lease) || !stillValid.getAsBoolean() || !sourceId.equals(source.dropId()) || !targetId.equals(target.dropId())
                    || !ItemStack.matches(original, source.inventory().getStackInSlot(slot))
                    || !profile.equals(TreatmentProfiles.resolve(original))) return Result.CANCELED;
            float before = GuardLifeRuntime.casualty(patient) ? 1 : patient.getHealth(), after = Math.min(patient.getMaxHealth(), before + accepted);
            if (!Float.isFinite(after) || after <= before) return Result.FULL_HEALTH;
            ItemStack replacement = original.copy(); replacement.shrink(1);
            if (profile.returnedItem() != null) replacement = new ItemStack(BuiltInRegistries.ITEM.get(profile.returnedItem()));
            source.inventory().setStackInSlot(slot, replacement); GuardLifeRuntime.completeTreatment(patient, after, profile.cooldownTicks(), lease.care);
            target.readyAt(patient.level().getGameTime() + profile.cooldownTicks()); return Result.HEALED;
        } finally { source.busy(false); target.busy(false); activePatients.remove(medic.getUUID()); activePatients.remove(patient.getUUID()); }
    }
    public static Result pack(Player player, Guard guard) {
        if (GuardLifeRuntime.blocked(guard) || guard.level().isClientSide || !mayAccess(player, guard) || player.distanceToSqr(guard) >= 64) return Result.DENIED;
        var storage = GuardMedicalAccess.get(guard);
        if (storage.busy() || storage.released()) return Result.BUSY;
        if (storage.empty()) return Result.NO_SUPPLY;
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) return Result.FULL_INVENTORY;
        ItemStack packed = storage.packed();
        storage.clearAfterPacking();
        player.getInventory().setItem(slot, packed);
        return Result.PACKED;
    }
    public static void report(Player player, Result result) {
        player.displayClientMessage(Component.translatable("message.villagerguardgunners.medical." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
    }
    private static boolean knownClientSupply(ItemStack stack) {
        if (stack.is(ModItems.BANDAGE.get())) return true;
        if (!stack.is(net.minecraft.world.item.Items.POTION)
                || (stack.hasTag() && stack.getTag().contains("CustomPotionEffects"))) return false;
        var potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack);
        return potion == net.minecraft.world.item.alchemy.Potions.HEALING
                || potion == net.minecraft.world.item.alchemy.Potions.STRONG_HEALING;
    }
    /** Null leaves the interaction to Guard Villagers. Sneak stocks; ordinary use treats. */
    public static InteractionResult interact(Player player, Guard guard, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean packed = held.is(ModItems.MEDICAL_POUCH.get());
        boolean supply = TreatmentProfiles.resolve(held) != null;
        if (!packed && !supply && !(guard.level().isClientSide && knownClientSupply(held))) return null;
        if (guard.level().isClientSide) return InteractionResult.SUCCESS;
        if (!mayAccess(player, guard)) { report(player, Result.DENIED); return InteractionResult.CONSUME; }
        if (player.distanceToSqr(guard) > 16 || !player.hasLineOfSight(guard)) { report(player, Result.TOO_FAR); return InteractionResult.CONSUME; }
        var storage = GuardMedicalAccess.get(guard);
        if (packed) {
            boolean restored = held.getCount() == 1 && storage.restore(held);
            if (restored) player.setItemInHand(hand, ItemStack.EMPTY);
            report(player, restored ? Result.RESTORED : Result.FULL_POUCH);
        } else if (player.isSecondaryUseActive()) {
            ItemStack remainder = held.copy();
            for (int i = 0; i < MedicalStorage.SLOTS; i++) remainder = storage.inventory().insertItem(i, remainder.copy(), false);
            boolean moved = remainder.getCount() < held.getCount();
            player.setItemInHand(hand, remainder);
            report(player, moved ? Result.STOCKED : Result.FULL_POUCH);
        } else report(player, fromHand(player, guard, hand));
        return InteractionResult.CONSUME;
    }
}
