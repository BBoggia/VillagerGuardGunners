package com.gamergaming.villagerguardgunners.sidearm;
/** Only included by the isolated client QA launch configuration. */
public final class CombatQaSidearm {
 public static void equip(tallestegg.guardvillagers.entities.Guard guard, net.minecraft.world.item.ItemStack stack) {
  GuardSidearmStorageAccess.get(guard).setStackInSlot(0,stack);
 }
}
