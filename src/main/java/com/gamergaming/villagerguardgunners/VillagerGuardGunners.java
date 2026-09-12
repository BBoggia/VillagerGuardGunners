package com.gamergaming.villagerguardgunners;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterRecovery;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlock;
import com.gamergaming.villagerguardgunners.command.GuardGunnerCommands;
import com.gamergaming.villagerguardgunners.village.VillageDiscoveryRuntime;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseStateTicker;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventRuntime;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventSources;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
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
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolIntegration;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitions;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingActivationRecovery;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VillagerGuardGunners.MODID)
public class VillagerGuardGunners {

    public static final String MODID = "villagerguardgunners";

    public VillagerGuardGunners(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModConfigs.init();
        modEventBus.addListener(com.gamergaming.villagerguardgunners.medical.MedicalCapabilities::register);
        modEventBus.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession::register);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::damaged);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::healed);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::reload);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::melee);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::left);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::travel);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::mount);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::stopping);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.TransportRuntime::damaged);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::joined);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::left);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::tick);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::tick);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::logout);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::stopped);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::damaged);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::stopped);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.MedicRuntime::damaged);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::healed);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.EquipmentProfiles::onReload);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.EquipmentProfiles::stopped);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.ServiceJournal::stopped);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::joined);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::left);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::tick);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::stopped);
        TaCZGunPackSyncNetwork.init();
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModStructures.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(GuardGunnerCommands::register);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TreatmentProfiles::onReload);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TreatmentProfiles::onStopped);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicalRecovery::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true, com.gamergaming.villagerguardgunners.medical.MedicalRecovery::onDrops);
        MinecraftForge.EVENT_BUS.addListener(QuartermasterRecovery::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(QuartermasterBlock::onBreak);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onBlockChange);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onDamage);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onChunkChange);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions::register);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions::tagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions::stopped);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(GuardRoles::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardLoadoutPools::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(GuardPostGroups::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingDefinitions::registerReloadListener);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingDefinitions::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingPoolIntegration::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingActivationRecovery::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingActivationRecovery::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingActivationRecovery::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardSpawnIntegration::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(NaturalGuardPopulationIndex::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(SquadLedgerService::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(SquadLedgerService::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStateTicker::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStateTicker::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStateTicker::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                VillageDefenseEventSources::onGuardTargetChanged);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                VillageDefenseEventSources::onDefendedEntityDamaged);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                VillageDefenseEventSources::onDefendedEntityKilled);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseEventRuntime::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseEventRuntime::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(VillageDefenseEventRuntime::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(VillageBuildingPoolIntegration::onServerAboutToStart);
    }
}
