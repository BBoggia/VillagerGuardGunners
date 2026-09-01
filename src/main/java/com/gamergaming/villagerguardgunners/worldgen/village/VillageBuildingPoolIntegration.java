package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/**
 * Performs one copy-on-write, additive mutation during server startup. It does
 * not scan chunks, locate villages, alter existing starts, or own tick work.
 */
public final class VillageBuildingPoolIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE_UNSET = Integer.MIN_VALUE;

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        boolean enabled = ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get();
        VillagePoolInjectionReport report = install(
                event.getServer().registryAccess(),
                enabled,
                VillageBuildingDefinitions.enabledEntries(
                        ModConfigs.GUARD_GUNNERS.villageBuildings));

        if (!report.enabled()) {
            LOGGER.info(
                    "Village Defense Building pool integration is disabled; no village pools were changed");
        } else if (report.hasWarnings()) {
            LOGGER.warn(
                    "Village Defense Building pool integration completed with missing dependencies: requested={}, injected={}, already_present={}, missing_pools={}, missing_processor_lists={}",
                    report.requested(),
                    report.injected(),
                    report.alreadyPresent(),
                    report.missingPools(),
                    report.missingProcessorLists());
        } else {
            LOGGER.info(
                    "Village Defense Building pool integration completed: requested={}, injected={}, already_present={}",
                    report.requested(),
                    report.injected(),
                    report.alreadyPresent());
        }
    }

    static VillagePoolInjectionReport install(
            RegistryAccess registryAccess,
            boolean enabled,
            List<VillageBuildingPoolEntry> requestedEntries) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        List<VillageBuildingPoolEntry> entries = List.copyOf(
                Objects.requireNonNull(requestedEntries, "requestedEntries"));
        validateBatch(entries);
        if (!enabled) {
            return VillagePoolInjectionReport.disabled(entries.size());
        }

        Registry<StructureTemplatePool> pools = registryAccess.registryOrThrow(
                Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = registryAccess.registryOrThrow(
                Registries.PROCESSOR_LIST);
        int injected = 0;
        int alreadyPresent = 0;
        int missingPools = 0;
        int missingProcessorLists = 0;

        for (VillageBuildingPoolEntry entry : entries) {
            StructureTemplatePool pool = pools.get(entry.targetPool());
            if (pool == null) {
                missingPools++;
                continue;
            }
            Holder.Reference<StructureProcessorList> processorList = processors.getHolder(
                    ResourceKey.create(Registries.PROCESSOR_LIST, entry.processors()))
                    .orElse(null);
            if (processorList == null) {
                missingProcessorLists++;
                continue;
            }
            if (containsEntry(pool, entry.entryId())) {
                alreadyPresent++;
                continue;
            }
            append(pool, entry, processorList);
            injected++;
        }

        return new VillagePoolInjectionReport(
                true,
                entries.size(),
                injected,
                alreadyPresent,
                missingPools,
                missingProcessorLists);
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

    private static void append(
            StructureTemplatePool pool,
            VillageBuildingPoolEntry entry,
            Holder<StructureProcessorList> processorList) {
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
        VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                entry.entryId(),
                entry.buildingType(),
                entry.template(),
                processorList,
                entry.projection());

        List<Pair<StructurePoolElement, Integer>> rawTemplates = new ArrayList<>(
                accessor.villagerGuardGunners$getRawTemplates());
        ObjectArrayList<StructurePoolElement> templates = new ObjectArrayList<>(
                accessor.villagerGuardGunners$getTemplates());
        rawTemplates.add(Pair.of(element, entry.weight()));
        for (int index = 0; index < entry.weight(); index++) {
            templates.add(element);
        }

        accessor.villagerGuardGunners$setRawTemplates(rawTemplates);
        accessor.villagerGuardGunners$setTemplates(templates);
        accessor.villagerGuardGunners$setMaxSize(SIZE_UNSET);
    }

    private VillageBuildingPoolIntegration() {
    }
}
