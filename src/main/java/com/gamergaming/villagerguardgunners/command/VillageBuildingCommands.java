package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig.VillageBuildingSettings;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitionReloadSnapshot;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitions;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingInstallationSnapshot;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolEntry;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolIntegration;
import com.gamergaming.villagerguardgunners.worldgen.village.VillagePoolInjectionReport;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Bounded, read-only operator diagnostics for Village Defense Buildings. */
public final class VillageBuildingCommands {
    private static final int ITEMS_PER_PAGE = 8;

    private VillageBuildingCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("villagebuildings")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(paged("entries", VillageBuildingCommands::entries))
                .then(paged("pools", VillageBuildingCommands::pools))
                .then(paged("errors", VillageBuildingCommands::errors));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> paged(
            String name,
            PageCommand command) {
        return Commands.literal(name)
                .executes(context -> command.run(context.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> command.run(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int status(CommandSourceStack source) {
        VillageBuildingSettings settings = ModConfigs.GUARD_GUNNERS.villageBuildings;
        VillageBuildingDefinitionReloadSnapshot definitions =
                VillageBuildingDefinitions.reloadSnapshot();
        VillageBuildingInstallationSnapshot installation =
                VillageBuildingPoolIntegration.installationSnapshot();
        VillagePoolInjectionReport report = installation.report();

        send(source, "[VGG] Village Defense Buildings", ChatFormatting.AQUA);
        send(source,
                "Policy: enabled=" + settings.enabled.get()
                        + ", barracks=" + settings.barracks.get()
                        + ", armories=" + settings.armories.get()
                        + ", sentryPosts=" + settings.sentryPosts.get()
                        + ", watchtowers=" + settings.integratedWatchtowers.get()
                        + ", trainingYards=" + settings.trainingYards.get(),
                settings.enabled.get() ? ChatFormatting.GRAY : ChatFormatting.YELLOW);
        send(source,
                "Functions: garrisons=" + settings.spawnGarrisons.get()
                        + ", maximumGuards=" + settings.maximumGuardsPerBuilding.get()
                        + ", gunChanceMultiplier="
                        + decimal(settings.guardGunChanceMultiplier.get())
                        + ", armoryBlocks=" + settings.placeArmoryBlocks.get()
                        + ", armoryStock=" + settings.stockArmories.get(),
                ChatFormatting.GRAY);
        send(source,
                "Definitions: generation=" + definitions.generation()
                        + ", accepted=" + definitions.lastReloadAccepted()
                        + ", published=" + definitions.definitions().size()
                        + ", rejected=" + definitions.lastErrors().size(),
                definitions.lastReloadAccepted() ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (!installation.attempted()) {
            send(source,
                    "Startup installation: not attempted in this server session.",
                    ChatFormatting.YELLOW);
        } else {
            send(source,
                    "Startup installation: enabled=" + report.enabled()
                            + ", requested=" + report.requested()
                            + ", injected=" + report.injected()
                            + ", alreadyPresent=" + report.alreadyPresent()
                            + ", rejected=" + report.rejectedEntries().size(),
                    report.hasWarnings() ? ChatFormatting.RED : ChatFormatting.GREEN);
            send(source,
                    "Startup checks: missingPools=" + report.missingPools()
                            + ", missingProcessors=" + report.missingProcessorLists()
                            + ", missingTemplates=" + report.missingTemplates()
                            + ", invalidTemplates=" + report.invalidTemplates(),
                    report.hasWarnings() ? ChatFormatting.RED : ChatFormatting.GRAY);
        }
        send(source,
                "Accepted reload changes are previews until the next server restart; use pools for live weights.",
                ChatFormatting.DARK_GRAY);
        return 1;
    }

    private static int entries(CommandSourceStack source, int requestedPage) {
        VillageBuildingDefinitionReloadSnapshot snapshot =
                VillageBuildingDefinitions.reloadSnapshot();
        VillageBuildingSettings settings = ModConfigs.GUARD_GUNNERS.villageBuildings;
        List<VillageBuildingPoolEntry> entries = snapshot.entries().stream()
                .sorted(Comparator.comparing(entry -> entry.entryId().toString()))
                .toList();
        Page page = Page.of(entries.size(), requestedPage);
        send(source,
                "[VGG] Accepted village-building definitions — page "
                        + page.number() + "/" + page.count()
                        + " (generation " + snapshot.generation() + ")",
                ChatFormatting.AQUA);
        if (entries.isEmpty()) {
            send(source, "No accepted village-building definitions are published.",
                    ChatFormatting.YELLOW);
            return 1;
        }
        for (VillageBuildingPoolEntry entry : entries.subList(page.from(), page.to())) {
            boolean configAllowsAtRestart = settings.enabled.get()
                    && settings.allows(entry.buildingType());
            send(source,
                    entry.entryId() + " — type=" + entry.buildingType().serializedName()
                            + ", pool=" + entry.targetPool()
                            + ", weight=" + entry.weight()
                            + ", selectionChance=" + entry.selectionChance()
                            + ", duties=" + entry.dutySlots()
                                    .map(slots -> "custom:" + slots.size())
                                    .orElse("built-in")
                            + ", configAllowsAtRestart=" + configAllowsAtRestart,
                    configAllowsAtRestart ? ChatFormatting.GRAY : ChatFormatting.YELLOW);
        }
        return page.to() - page.from();
    }

    private static int pools(CommandSourceStack source, int requestedPage) {
        VillageBuildingInstallationSnapshot installation =
                VillageBuildingPoolIntegration.installationSnapshot();
        Set<ResourceLocation> targetIds = new TreeSet<>(Comparator.comparing(
                ResourceLocation::toString));
        installation.requestedEntries().stream()
                .map(VillageBuildingPoolEntry::targetPool)
                .forEach(targetIds::add);
        VillageBuildingDefinitions.snapshot().values().stream()
                .map(VillageBuildingPoolEntry::targetPool)
                .forEach(targetIds::add);
        List<ResourceLocation> targets = List.copyOf(targetIds);
        Page page = Page.of(targets.size(), requestedPage);
        send(source,
                "[VGG] Live village-pool weights — page "
                        + page.number() + "/" + page.count(),
                ChatFormatting.AQUA);
        if (targets.isEmpty()) {
            send(source, "No startup or accepted target pools are available.",
                    ChatFormatting.YELLOW);
            return 1;
        }

        Registry<StructureTemplatePool> registry = source.getServer()
                .registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL);
        for (ResourceLocation targetId : targets.subList(page.from(), page.to())) {
            StructureTemplatePool pool = registry.get(targetId);
            if (pool == null) {
                send(source, targetId + " — missing", ChatFormatting.RED);
                continue;
            }
            PoolWeights weights = liveWeights(pool);
            send(source,
                    targetId + " — VGG entries=" + weights.vggEntries()
                            + ", VGG weight=" + weights.vggWeight()
                            + ", total weight=" + weights.totalWeight()
                            + ", live share=" + percent(
                                    weights.vggWeight(), weights.totalWeight()),
                    weights.vggWeight() > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        }
        return page.to() - page.from();
    }

    private static int errors(CommandSourceStack source, int requestedPage) {
        VillageBuildingDefinitionReloadSnapshot definitions =
                VillageBuildingDefinitions.reloadSnapshot();
        VillageBuildingInstallationSnapshot installation =
                VillageBuildingPoolIntegration.installationSnapshot();
        List<DiagnosticError> errors = new ArrayList<>();
        for (VillageBuildingDefinitionReloadSnapshot.RejectedDocument error
                : definitions.lastErrors()) {
            errors.add(new DiagnosticError(
                    "reload",
                    error.id() == null ? "registry" : error.id().toString(),
                    error.reason()));
        }
        for (ResourceLocation id : installation.report().rejectedEntries()) {
            errors.add(new DiagnosticError(
                    "startup",
                    id.toString(),
                    "entry failed one of the bounded startup checks; see status counters"));
        }
        Page page = Page.of(errors.size(), requestedPage);
        send(source,
                "[VGG] Village-building errors — page "
                        + page.number() + "/" + page.count(),
                ChatFormatting.AQUA);
        if (errors.isEmpty()) {
            send(source, "The latest reload and startup installation reported no errors.",
                    ChatFormatting.GREEN);
            return 1;
        }
        for (DiagnosticError error : errors.subList(page.from(), page.to())) {
            send(source,
                    error.stage() + ": " + error.id() + " — " + error.reason(),
                    ChatFormatting.RED);
        }
        return page.to() - page.from();
    }

    private static PoolWeights liveWeights(StructureTemplatePool pool) {
        List<Pair<StructurePoolElement, Integer>> raw =
                ((StructureTemplatePoolAccessor) pool)
                        .villagerGuardGunners$getRawTemplates();
        int vggEntries = 0;
        long vggWeight = 0L;
        long totalWeight = 0L;
        for (Pair<StructurePoolElement, Integer> pair : raw) {
            int weight = pair.getSecond();
            if (weight > 0) {
                totalWeight += weight;
                if (pair.getFirst() instanceof VillageBuildingPoolElement) {
                    vggEntries++;
                    vggWeight += weight;
                }
            }
        }
        return new PoolWeights(vggEntries, vggWeight, totalWeight);
    }

    private static String percent(long part, long total) {
        if (total <= 0L) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f%%", part * 100.0D / total);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void send(
            CommandSourceStack source,
            String text,
            ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    @FunctionalInterface
    private interface PageCommand {
        int run(CommandSourceStack source, int page);
    }

    private record Page(int number, int count, int from, int to) {
        private static Page of(int itemCount, int requestedPage) {
            int count = Math.max(1, (itemCount + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            int number = Math.min(requestedPage, count);
            int from = (number - 1) * ITEMS_PER_PAGE;
            return new Page(number, count, from, Math.min(from + ITEMS_PER_PAGE, itemCount));
        }
    }

    private record PoolWeights(int vggEntries, long vggWeight, long totalWeight) {
    }

    private record DiagnosticError(String stage, String id, String reason) {
    }
}
