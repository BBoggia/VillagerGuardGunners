package com.gamergaming.villagerguardgunners;

import org.slf4j.Logger;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModEntities;
import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VillagerGuardGunners.MODID)
public class VillagerGuardGunners {

    public static final String MODID = "villagerguardgunners";

    public static final Logger LOGGER = LogUtils.getLogger();

    public VillagerGuardGunners(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModConfigs.init();
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
    }

    public static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
