package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Transient reservation; reload, unload or changed ownership invalidates it without moving items. */
public record ServicePlan(UUID operation, Guard guard, QuartermasterBlockEntity station, UUID placement,
        ServiceSource source, int sourceSlot, int targetSlot, int returnSlot, ItemStack stockBefore,
        ItemStack replacement, List<ItemStack> guardBefore, long profileRevision,
        GuardServiceState.Policy policy, ResourceLocation profile, boolean replaceExisting,
        long sourceRevision, long guardRevision, long expiresAt) {}
