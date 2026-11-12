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

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;


@Mod(VillagerGuardGunners.MODID)
public class VillagerGuardGunners {

    public static final String MODID = "villagerguardgunners";

    public VillagerGuardGunners(IEventBus modEventBus) {

        ModConfigs.init();
        // Gun mods register before any guard exists; lookups follow this order.
        com.gamergaming.villagerguardgunners.guns.GunProviders.register(
                new com.gamergaming.villagerguardgunners.compat.tacz.provider.TaczGunProvider());
        // Optional gun mods: each bootstrap class is touched only when its mod is installed.
        if (net.neoforged.fml.ModList.get().isLoaded(
                com.gamergaming.villagerguardgunners.guns.OptionalGunMods.CGM)) {
            com.gamergaming.villagerguardgunners.compat.cgm.CgmBootstrap.register();
        }
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) -> {
            if (event.getEntity() instanceof tallestegg.guardvillagers.common.entities.Guard guard)
                ((com.gamergaming.villagerguardgunners.api.GuardAmmoAccess) guard).villagerGuardGunners$invalidateInventory();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
            if (event.getEntity() instanceof tallestegg.guardvillagers.common.entities.Guard guard)
                ((com.gamergaming.villagerguardgunners.api.GuardAmmoAccess) guard).villagerGuardGunners$reviveInventory();
        });
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.trade.TradeDefinitions::onReload);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.trade.TradeDefinitions::started);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.trade.TradeDefinitions::stopped);
        modEventBus.addListener(com.gamergaming.villagerguardgunners.medical.MedicalCapabilities::register);
        modEventBus.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession::register);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::healed);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::reload);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime::melee);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::left);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::travel);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::mount);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TransportRuntime::stopping);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.TransportRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::joined);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::left);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::tick);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::tick);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::logout);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::stopped);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.PlayerTreatmentRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::stopped);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.gamergaming.villagerguardgunners.medical.MedicRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicRuntime::healed);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.EquipmentProfiles::onReload);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.EquipmentProfiles::stopped);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.ServiceJournal::stopped);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::joined);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::left);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::tick);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime::stopped);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.management.OverviewRuntime::tick);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.management.OverviewRuntime::stopped);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.quartermaster.service.ServiceHistory::stopped);
        modEventBus.addListener(TaCZGunPackSyncNetwork::register);
        modEventBus.addListener(com.gamergaming.villagerguardgunners.quartermaster.management.ManagementNetwork::register);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.gamergaming.villagerguardgunners.quartermaster.management.QuartermasterManagement::interact);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModStructures.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(GuardGunnerCommands::register);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TreatmentProfiles::onReload);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.TreatmentProfiles::onStopped);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.medical.MedicalRecovery::onServerTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, true, com.gamergaming.villagerguardgunners.medical.MedicalRecovery::onDrops);
        NeoForge.EVENT_BUS.addListener(QuartermasterRecovery::onServerTick);
        NeoForge.EVENT_BUS.addListener(QuartermasterBlock::onBreak);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onEntityLeave);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) -> com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.onBlockChange(event));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) -> com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.onBlockChange(event));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.FluidPlaceBlockEvent event) -> com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.onBlockChange(event));
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onDamage);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.onChunkChange(event));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload event) -> com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.onChunkChange(event));
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions::register);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions::tagsUpdated);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions::stopped);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(VillageDiscoveryRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(GuardRoles::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(NaturalGuardLoadoutPools::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(GuardPostGroups::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(VillageBuildingDefinitions::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(VillageBuildingDefinitions::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VillageBuildingPoolIntegration::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VillageBuildingActivationRecovery::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(VillageBuildingActivationRecovery::onServerTick);
        NeoForge.EVENT_BUS.addListener(VillageBuildingActivationRecovery::onServerStopping);
        NeoForge.EVENT_BUS.addListener(NaturalGuardSpawnIntegration::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(NaturalGuardPopulationIndex::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ArmoryResupplyServerServices::onServerStopping);
        NeoForge.EVENT_BUS.addListener(SquadLedgerService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(SquadLedgerService::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStateTicker::onServerStarted);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStateTicker::onServerTick);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStateTicker::onServerStopping);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(VillageDefenseStaffingRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                VillageDefenseEventSources::onGuardTargetChanged);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                VillageDefenseEventSources::onDefendedEntityDamaged);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                VillageDefenseEventSources::onDefendedEntityKilled);
        NeoForge.EVENT_BUS.addListener(VillageDefenseEventRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(VillageDefenseEventRuntime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(VillageDefenseEventRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(VillageBuildingPoolIntegration::onServerAboutToStart);
    }
}
