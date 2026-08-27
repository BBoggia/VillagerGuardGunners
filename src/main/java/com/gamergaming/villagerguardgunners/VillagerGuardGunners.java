package com.gamergaming.villagerguardgunners;

import com.gamergaming.villagerguardgunners.command.GuardGunnerCommands;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModMenus;

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
        ModMenus.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(GuardGunnerCommands::register);
    }
}
