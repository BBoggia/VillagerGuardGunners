package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.TimelessAPI;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import tallestegg.guardvillagers.entities.Guard;

/** At most twelve destination slots and five explicitly linked physical sources. */
public final class ServicePlanner {
    private static final String[] ARMOR = {"head", "chest", "legs", "feet"};
    public static ServicePlan plan(Guard guard, QuartermasterBlockEntity station, List<ServiceSource> sources) {
        var state = GuardEquipmentServiceAccess.get(guard);
        EquipmentProfile profile = state.policy() == GuardServiceState.Policy.PROFILE ? EquipmentProfiles.snapshot().get(state.profile()) : null;
        if (state.policy() == GuardServiceState.Policy.MANUAL) { state.status = ServiceStatus.MANUAL; return null; }
        if (state.policy() == GuardServiceState.Policy.PROFILE && profile == null) { state.status = ServiceStatus.PROFILE_MISSING; return null; }
        state.status = ServiceStatus.SATISFIED;
        var before = ServiceEquipment.snapshot(guard);
        if (profile != null) for (int target = 0; target < 6; target++) {
            List<String> selectors = target == 0 ? profile.primary() : target == 1 ? profile.sidearm() : profile.armor().getOrDefault(ARMOR[target - 2], List.of());
            ItemStack old = before.get(target);
            if (selectors.isEmpty() || EquipmentProfile.matches(old, selectors, target < 2)) continue;
            if (!old.isEmpty() && (old.getCount() != 1 || !station.inventory().isItemValid(27, old) || !state.replaceExisting() || EnchantmentHelper.hasBindingCurse(old))) continue;
            int returnSlot = -1;
            if (!old.isEmpty()) {
                for (int slot = 27; slot < 36; slot++) if (station.inventory().getStackInSlot(slot).isEmpty() && !QuartermasterServiceRuntime.reserved(station, slot)) { returnSlot = slot; break; }
                if (returnSlot < 0) { state.status = ServiceStatus.RETURN_STORAGE_FULL; continue; }
            }
            final int destination = target;
            ServicePlan plan = find(guard, station, List.of(ServiceSource.of(station)), target, returnSlot, 1,
                    stack -> EquipmentProfile.matches(stack, selectors, destination < 2), before);
            if (plan != null) return plan;
            state.status = ServiceStatus.NO_MATCHING_STOCK;
        }
        for (int weaponSlot = 0; weaponSlot < 2; weaponSlot++) {
        ItemStack primary = before.get(weaponSlot);
        if (primary.getItem() instanceof IGun gun) {
            var definition = TimelessAPI.getCommonGunIndex(gun.getGunId(primary));
            if (definition.isPresent()) {
                var ammo = definition.get().getGunData().getAmmoId();
                Predicate<ItemStack> matches = stack -> stack.getItem() instanceof IAmmo item && ammo.equals(item.getAmmoId(stack));
                int count = 0; for (int i = 6; i < 9; i++) if (matches.test(before.get(i))) count += before.get(i).getCount();
                int needed = (profile == null ? 64 : profile.ammoReserve()) - count;
                if (needed > 0) { ServicePlan plan = topUp(guard, station, sources, 6, 9, needed, matches, before); if (plan != null) return plan; }
            }
        }
        }
        List<String> medicines = profile == null ? before.subList(9, 12).stream().filter(stack -> !stack.isEmpty())
                .map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).distinct().toList() : profile.medical();
        if (!medicines.isEmpty()) {
            Predicate<ItemStack> matches = stack -> EquipmentProfile.matches(stack, medicines, false)
                    && com.gamergaming.villagerguardgunners.medical.TreatmentProfiles.resolve(stack) != null;
            int count = 0; for (int i = 9; i < 12; i++) if (matches.test(before.get(i))) count += before.get(i).getCount();
            int needed = (profile == null ? 16 : profile.medicalReserve()) - count;
            if (needed > 0) { ServicePlan plan = topUp(guard, station, List.of(ServiceSource.of(station)), 9, 12, needed, matches, before); if (plan != null) return plan; }
        }
        return null;
    }
    private static ServicePlan topUp(Guard guard, QuartermasterBlockEntity station, List<ServiceSource> sources, int first, int end, int needed, Predicate<ItemStack> matches, List<ItemStack> before) {
        for (int target = first; target < end; target++) {
            ItemStack old = before.get(target);
            if (!old.isEmpty() && !matches.test(old)) continue;
            ServicePlan plan = find(guard, station, sources, target, -1, needed, matches, before);
            if (plan != null) return plan;
        }
        GuardEquipmentServiceAccess.get(guard).status = ServiceStatus.NO_MATCHING_STOCK; return null;
    }
    private static ServicePlan find(Guard guard, QuartermasterBlockEntity station, List<ServiceSource> sources, int target, int returned, int requested, Predicate<ItemStack> matches, List<ItemStack> before) {
        var state = GuardEquipmentServiceAccess.get(guard);
        for (ServiceSource source : sources) {
            if (!source.live(guard)) continue;
            for (int slot = 0; slot < source.stockSlots(); slot++) {
                ItemStack stock = source.inventory().getStackInSlot(slot);
                if (QuartermasterServiceRuntime.reserved(source.block(), slot) || !matches.test(stock)) continue;
                ItemStack old = before.get(target);
                if (target >= 6 && !old.isEmpty() && !ItemStack.isSameItemSameTags(old, stock)) continue;
                int amount = Math.min(requested, Math.min(stock.getCount(), ServiceEquipment.limit(guard, target, stock) - (target >= 6 ? old.getCount() : 0)));
                if (amount <= 0) continue;
                ItemStack next = stock.copy(); next.setCount(amount + (target >= 6 ? old.getCount() : 0));
                if (!ServiceEquipment.valid(guard, target, next)) continue;
                return new ServicePlan(UUID.randomUUID(), guard, station, station.dropId(), source, slot, target, returned,
                        stock.copy(), next, before, EquipmentProfiles.revision(), state.policy(), state.profile(), state.replaceExisting(),
                        source.receipt().revision(), state.receipt.revision(), guard.level().getGameTime() + 600);
            }
        }
        return null;
    }
}
