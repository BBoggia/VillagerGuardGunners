package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Deliberately test-only POI/profession; no station or profession enters the mod JAR. */
@Mod.EventBusSubscriber(modid = VillagerGuardGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuartermasterProbeRegistry {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            VillageIntegrationGameTests.NAMESPACE, "quartermaster_probe");

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.POI_TYPES, registry -> registry.register(ID,
                new PoiType(ImmutableSet.copyOf(Blocks.GOLD_BLOCK.getStateDefinition().getPossibleStates()), 1, 1)));
        event.register(ForgeRegistries.Keys.VILLAGER_PROFESSIONS, registry -> registry.register(ID,
                new VillagerProfession("quartermaster_probe",
                        holder -> holder.value() == ForgeRegistries.POI_TYPES.getValue(ID),
                        holder -> holder.value() == ForgeRegistries.POI_TYPES.getValue(ID),
                        ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_ARMORER)));
    }
}
