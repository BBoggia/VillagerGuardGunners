package com.gamergaming.villagerguardgunners;

import com.gamergaming.villagerguardgunners.command.GuardGunnerCommands;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPools;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardPopulationIndex;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnIntegration;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VillagerGuardGunners.MODID)
public class VillagerGuardGunners {

    public static final String MODID = "villagerguardgunners";

    public VillagerGuardGunners(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModConfigs.init();
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(GuardGunnerCommands::register);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardLoadoutPools::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardSpawnIntegration::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardPopulationIndex::onEntityLeave);
    }
}
