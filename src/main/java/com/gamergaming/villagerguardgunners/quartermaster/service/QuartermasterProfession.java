package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class QuartermasterProfession {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, "quartermaster");
    public static void register(RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE, registry -> registry.register(ID,
                new PoiType(ImmutableSet.copyOf(ModBlocks.QUARTERMASTER.get().getStateDefinition().getPossibleStates()), 1, 1)));
        event.register(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION, registry -> registry.register(ID,
                new VillagerProfession("quartermaster", holder -> holder.value() == BuiltInRegistries.POINT_OF_INTEREST_TYPE.get(ID),
                        holder -> holder.value() == BuiltInRegistries.POINT_OF_INTEREST_TYPE.get(ID),
                        ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_ARMORER)));
    }
}
