package com.gamergaming.villagerguardgunners.handlers.firing_modes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

public interface FireModeStrategy {
    void performAttack(Guard guard, ItemStack gunItem, LivingEntity target);
}