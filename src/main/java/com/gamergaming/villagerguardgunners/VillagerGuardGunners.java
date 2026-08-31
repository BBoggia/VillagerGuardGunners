package com.gamergaming.villagerguardgunners;

import com.gamergaming.villagerguardgunners.command.GuardGunnerCommands;
import com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSyncNetwork;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyServerServices;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModBlockEntities;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPools;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardPopulationIndex;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnIntegration;
import com.gamergaming.villagerguardgunners.role.GuardRoles;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerService;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroups;

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
        TaCZGunPackSyncNetwork.init();
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModStructures.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(GuardGunnerCommands::register);
        MinecraftForge.EVENT_BUS.addListener(GuardRoles::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardLoadoutPools::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(GuardPostGroups::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardSpawnIntegration::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardPopulationIndex::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(SquadLedgerService::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(SquadLedgerService::onPlayerLoggedOut);
    }
}
