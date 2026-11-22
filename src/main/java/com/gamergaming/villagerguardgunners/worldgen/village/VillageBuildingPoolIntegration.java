package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Performs one copy-on-write, additive mutation during server startup. It does
 * not scan chunks, locate villages, alter existing starts, or own tick work.
 */
public final class VillageBuildingPoolIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE_UNSET = Integer.MIN_VALUE;
    private static volatile VillageBuildingInstallationSnapshot installationSnapshot =
            VillageBuildingInstallationSnapshot.empty();
    private static volatile Map<ResourceLocation, VillageBuildingPoolEntry>
            startupDefinitions = Map.of();

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        startupDefinitions = Map.copyOf(VillageBuildingDefinitions.snapshot());
        boolean enabled = ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get();
        Set<String> poolNamespaces = event.getServer().registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL).keySet().stream()
                .map(ResourceLocation::getNamespace).collect(java.util.stream.Collectors.toSet());
        List<VillageBuildingPoolEntry> requestedEntries =
                VillageBuildingDefinitions.enabledEntries(
                        ModConfigs.GUARD_GUNNERS.villageBuildings).stream()
                        .filter(entry -> com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions.snapshot().allowsPool(entry.targetPool().toString()))
                        .toList();
        List<VillageBuildingPoolEntry> activeEntries = requestedEntries.stream()
                .filter(entry -> !VillageBuildingOptionalProviders.isAbsent(
                        entry.targetPool(), net.minecraftforge.fml.ModList.get()::isLoaded, poolNamespaces))
                .toList();
        int inactive = requestedEntries.size() - activeEntries.size();
        if (enabled && inactive > 0) {
            LOGGER.info("Skipped {} optional village-building entries because their village providers are absent", inactive);
        }
        requestedEntries = activeEntries;
        VillagePoolInjectionReport report = install(
                event.getServer().registryAccess(),
                event.getServer().getStructureManager(),
                enabled,
                requestedEntries);
        installationSnapshot = new VillageBuildingInstallationSnapshot(
                true, requestedEntries, report);

        if (!report.enabled()) {
            LOGGER.info(
                    "Village Defense Building pool integration is disabled; no village pools were changed");
        } else if (report.hasWarnings()) {
            LOGGER.warn(
                    "Village Defense Building pool integration completed with rejected entries: requested={}, injected={}, already_present={}, missing_pools={}, missing_processor_lists={}, missing_templates={}, invalid_templates={}, rejected_entries={}",
                    report.requested(),
                    report.injected(),
                    report.alreadyPresent(),
                    report.missingPools(),
                    report.missingProcessorLists(),
                    report.missingTemplates(),
                    report.invalidTemplates(),
                    report.rejectedEntries());
        } else {
            LOGGER.info(
                    "Village Defense Building pool integration completed: requested={}, injected={}, already_present={}",
                    report.requested(),
                    report.injected(),
                    report.alreadyPresent());
        }
    }

    public static VillageBuildingInstallationSnapshot installationSnapshot() {
        return installationSnapshot;
    }

    /** Exact accepted definition set paired with this session's live template pools. */
    public static @Nullable VillageBuildingPoolEntry startupDefinition(
            ResourceLocation entryId) {
        return startupDefinitions.get(Objects.requireNonNull(entryId, "entryId"));
    }

    /** Prevents one integrated-server session's diagnostics from leaking into another. */
    public static void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        installationSnapshot = VillageBuildingInstallationSnapshot.empty();
        startupDefinitions = Map.of();
    }

    static VillagePoolInjectionReport install(
            RegistryAccess registryAccess,
            @Nullable StructureTemplateManager templateManager,
            boolean enabled,
            List<VillageBuildingPoolEntry> requestedEntries) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        List<VillageBuildingPoolEntry> entries = List.copyOf(
                Objects.requireNonNull(requestedEntries, "requestedEntries"));
        validateBatch(entries);
        if (!enabled) {
            return VillagePoolInjectionReport.disabled(entries.size());
        }
        Objects.requireNonNull(templateManager, "templateManager");

        Registry<StructureTemplatePool> pools = registryAccess.registryOrThrow(
                Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = registryAccess.registryOrThrow(
                Registries.PROCESSOR_LIST);
        int injected = 0;
        int alreadyPresent = 0;
        int missingPools = 0;
        int missingProcessorLists = 0;
        int missingTemplates = 0;
        int invalidTemplates = 0;
        List<ResourceLocation> rejectedEntries = new ArrayList<>();
        List<ResolvedEntry> resolved = new ArrayList<>();

        for (VillageBuildingPoolEntry entry : entries) {
            StructureTemplatePool pool = pools.get(entry.targetPool());
            if (pool == null) {
                missingPools++;
                rejectedEntries.add(entry.entryId());
                continue;
            }
            Holder.Reference<StructureProcessorList> processorList = processors.getHolder(
                    ResourceKey.create(Registries.PROCESSOR_LIST, entry.processors()))
                    .orElse(null);
            if (processorList == null) {
                missingProcessorLists++;
                rejectedEntries.add(entry.entryId());
                continue;
            }
            if (containsEntry(pool, entry.entryId())) {
                alreadyPresent++;
                continue;
            }
            if (templateManager.get(entry.template()).isEmpty()) {
                missingTemplates++;
                rejectedEntries.add(entry.entryId());
                continue;
            }
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entry.entryId(),
                    entry.buildingType(),
                    entry.template(),
                    processorList,
                    entry.projection(),
                    entry.selectionChance(),
                    entry.terrainSafety());
            if (!hasValidAnchorContract(templateManager, element, entry.buildingType())) {
                invalidTemplates++;
                rejectedEntries.add(entry.entryId());
                continue;
            }
            resolved.add(new ResolvedEntry(pool, entry, element));
        }

        Map<StructureTemplatePool, PoolMutation> mutations = new LinkedHashMap<>();
        for (ResolvedEntry candidate : resolved) {
            PoolMutation mutation = mutations.computeIfAbsent(
                    candidate.pool(), PoolMutation::capture);
            mutation.append(candidate.entry(), candidate.element());
            injected++;
        }
        List<PoolMutation> committed = new ArrayList<>();
        try {
            for (PoolMutation mutation : mutations.values()) {
                committed.add(mutation);
                mutation.commit();
            }
        } catch (RuntimeException exception) {
            for (int index = committed.size() - 1; index >= 0; index--) {
                committed.get(index).rollback();
            }
            throw exception;
        }

        return new VillagePoolInjectionReport(
                true,
                entries.size(),
                injected,
                alreadyPresent,
                missingPools,
                missingProcessorLists,
                missingTemplates,
                invalidTemplates,
                rejectedEntries);
    }

    static void validateBatch(List<VillageBuildingPoolEntry> entries) {
        if (entries.size() > VillageBuildingPools.MAXIMUM_INJECTED_ENTRIES) {
            throw new IllegalArgumentException("Village pool integration exceeds its entry limit");
        }
        Set<?> targetPools = entries.stream()
                .map(VillageBuildingPoolEntry::targetPool)
                .collect(java.util.stream.Collectors.toSet());
        if (targetPools.size() > VillageBuildingPools.MAXIMUM_TARGET_POOLS) {
            throw new IllegalArgumentException("Village pool integration exceeds its pool limit");
        }
        Set<ResourceLocation> entryIds = new HashSet<>();
        for (VillageBuildingPoolEntry entry : entries) {
            if (!entryIds.add(entry.entryId())) {
                throw new IllegalArgumentException("Duplicate village entry ID: " + entry.entryId());
            }
        }
    }

    private static boolean containsEntry(
            StructureTemplatePool pool,
            ResourceLocation entryId) {
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
        return accessor.villagerGuardGunners$getRawTemplates().stream()
                .map(Pair::getFirst)
                .filter(VillageBuildingPoolElement.class::isInstance)
                .map(VillageBuildingPoolElement.class::cast)
                .anyMatch(element -> element.entryId().equals(entryId));
    }

    private static boolean hasValidAnchorContract(
            StructureTemplateManager templateManager,
            VillageBuildingPoolElement element,
            VillageBuildingType buildingType) {
        try {
            return VillageBuildingAnchorMarker.validateTemplateMarkers(
                    element.getDataMarkers(
                            templateManager,
                            BlockPos.ZERO,
                            Rotation.NONE,
                            false),
                    buildingType);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private record ResolvedEntry(
            StructureTemplatePool pool,
            VillageBuildingPoolEntry entry,
            VillageBuildingPoolElement element) {
    }

    private static final class PoolMutation {
        private final StructureTemplatePoolAccessor accessor;
        private final List<Pair<StructurePoolElement, Integer>> originalRaw;
        private final ObjectArrayList<StructurePoolElement> originalTemplates;
        private final int originalMaxSize;
        private final List<Pair<StructurePoolElement, Integer>> updatedRaw;
        private final ObjectArrayList<StructurePoolElement> updatedTemplates;

        private PoolMutation(StructureTemplatePool pool) {
            accessor = (StructureTemplatePoolAccessor) pool;
            originalRaw = accessor.villagerGuardGunners$getRawTemplates();
            originalTemplates = accessor.villagerGuardGunners$getTemplates();
            originalMaxSize = accessor.villagerGuardGunners$getMaxSize();
            updatedRaw = new ArrayList<>(originalRaw);
            updatedTemplates = new ObjectArrayList<>(originalTemplates);
        }

        static PoolMutation capture(StructureTemplatePool pool) {
            return new PoolMutation(pool);
        }

        void append(
                VillageBuildingPoolEntry entry,
                VillageBuildingPoolElement element) {
            updatedRaw.add(Pair.of(element, entry.weight()));
            for (int index = 0; index < entry.weight(); index++) {
                updatedTemplates.add(element);
            }
        }

        void commit() {
            accessor.villagerGuardGunners$setRawTemplates(updatedRaw);
            accessor.villagerGuardGunners$setTemplates(updatedTemplates);
            accessor.villagerGuardGunners$setMaxSize(SIZE_UNSET);
        }

        void rollback() {
            accessor.villagerGuardGunners$setRawTemplates(originalRaw);
            accessor.villagerGuardGunners$setTemplates(originalTemplates);
            accessor.villagerGuardGunners$setMaxSize(originalMaxSize);
        }
    }

    private VillageBuildingPoolIntegration() {
    }
}
