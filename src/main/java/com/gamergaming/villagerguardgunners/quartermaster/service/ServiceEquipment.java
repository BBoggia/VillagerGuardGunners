package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmEquipmentService;
import com.gamergaming.villagerguardgunners.sidearm.SidearmRuntimeAssessment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

public final class ServiceEquipment {
    public static final int SLOTS = 12;
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static ItemStack get(Guard guard, int slot) {
        if (slot == 1) return GuardSidearmAccess.getConfiguredSidearmInventory(guard).getStackInSlot(0).copy();
        if (slot < 6) return guard.getItemBySlot(EQUIPMENT[slot]).copy();
        if (slot < 9) return GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(slot - 6).copy();
        return GuardMedicalAccess.get(guard).inventory().getStackInSlot(slot - 9).copy();
    }
    public static void set(Guard guard, int slot, ItemStack stack) {
        if (slot == 1) GuardSidearmEquipmentService.replace(guard, stack);
        else if (slot < 6) guard.setItemSlot(EQUIPMENT[slot], stack.copy());
        else if (slot < 9) GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(slot - 6, stack.copy());
        else GuardMedicalAccess.get(guard).inventory().setStackInSlot(slot - 9, stack.copy());
    }
    public static List<ItemStack> snapshot(Guard guard) { return java.util.stream.IntStream.range(0, SLOTS).mapToObj(i -> get(guard, i)).toList(); }
    public static boolean same(Guard guard, List<ItemStack> expected) {
        for (int i = 0; i < SLOTS; i++) if (!ItemStack.matches(get(guard, i), expected.get(i))) return false; return true;
    }
    public static boolean valid(Guard guard, int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (slot == 0) return stack.getCount() == 1 && stack.getItem() instanceof IGun gun && TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).isPresent();
            if (slot == 1) return stack.getCount() == 1 && SidearmRuntimeAssessment.assessEligibility(guard, stack).eligible();
            if (slot < 6) return stack.getCount() == 1 && stack.canEquip(EQUIPMENT[slot], guard);
            if (slot < 9) return GuardAmmoAccess.getAmmoHandler(guard).isItemValid(slot - 6, stack);
            return GuardMedicalAccess.get(guard).inventory().isItemValid(slot - 9, stack);
        } catch (RuntimeException exception) { return false; }
    }
    public static int limit(Guard guard, int slot, ItemStack incoming) {
        if (slot < 6) return 1;
        return Math.min(incoming.getMaxStackSize(), slot < 9 ? GuardAmmoAccess.getAmmoHandler(guard).getSlotLimit(slot - 6) : 64);
    }
}
