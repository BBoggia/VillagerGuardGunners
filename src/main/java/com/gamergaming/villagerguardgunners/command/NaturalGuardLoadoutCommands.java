package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.InspectedEntry;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.InspectionPage;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.PreviewEntry;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.PreviewReport;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.ValidationIssue;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.ValidationReport;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficultySettings;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutContextCapture;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDiagnosticSeverity;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutMatchContext;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPool;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPools;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutReloadSnapshot;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnRules;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import com.gamergaming.villagerguardgunners.role.GuardRoles;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.entities.Guard;

/** Explicit, bounded, read-only diagnostics for natural guard loadout pools. */
final class NaturalGuardLoadoutCommands {
    private static final Comparator<String> STRING_ORDER = Comparator.naturalOrder();

    private NaturalGuardLoadoutCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(NaturalGuardLoadoutCommandContract.ROOT_LITERAL)
                .requires(source -> source.hasPermission(
                        NaturalGuardLoadoutCommandContract.REQUIRED_PERMISSION_LEVEL))
                .then(Commands.literal(NaturalGuardLoadoutCommandContract.VALIDATE_LITERAL)
                        .executes(context -> validate(context.getSource(), null))
                        .then(Commands.argument("pool", ResourceLocationArgument.id())
                                .suggests((context, builder) -> suggestPools(builder))
                                .executes(context -> validate(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "pool")))))
                .then(Commands.literal(NaturalGuardLoadoutCommandContract.INSPECT_LITERAL)
                        .then(Commands.argument("pool", ResourceLocationArgument.id())
                                .suggests((context, builder) -> suggestPools(builder))
                                .executes(context -> inspect(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "pool"),
                                        1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> inspect(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(context, "pool"),
                                                IntegerArgumentType.getInteger(context, "page"))))))
                .then(Commands.literal(NaturalGuardLoadoutCommandContract.PREVIEW_LITERAL)
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        enumNames(NaturalGuardDifficulty.values()), builder))
                                .then(Commands.argument("spawn_source", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                enumNames(NaturalGuardSpawnSource.values()), builder))
                                        .executes(context -> preview(
                                                context.getSource(),
                                                StringArgumentType.getString(
                                                        context, "difficulty"),
                                                StringArgumentType.getString(
                                                        context, "spawn_source"),
                                                null))
                                        .then(Commands.argument(
                                                        "pool", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> suggestPools(builder))
                                                .executes(context -> preview(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context, "difficulty"),
                                                        StringArgumentType.getString(
                                                                context, "spawn_source"),
                                                        ResourceLocationArgument.getId(
                                                                context, "pool")))))));
    }

    private static int validate(
            CommandSourceStack source,
            @Nullable ResourceLocation requestedPool) {
        NaturalGuardLoadoutReloadSnapshot snapshot = NaturalGuardLoadoutPools.reloadSnapshot();
        final ValidationReport report;
        try {
            report = NaturalGuardLoadoutCommandService.validate(snapshot, requestedPool);
        } catch (IllegalArgumentException exception) {
            return fail(source, exception.getMessage());
        }
        List<String> missingRoles = missingRoles(snapshot, requestedPool);

        send(source, Component.literal("[VGG Loadouts] Validation generation ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(Long.toString(report.generation()))
                        .withStyle(ChatFormatting.WHITE)));
        send(source, line(
                "Summary",
                "pools=" + report.pools()
                        + ", entries=" + report.entries()
                        + ", compatible=" + report.compatible()
                        + ", contextDependent=" + report.contextDependent()
                        + ", warnings=" + report.warnings()
                        + ", errors=" + (report.errors() + missingRoles.size())
                        + ", rejectedDocuments=" + report.rejectedDocuments(),
                report.errors() > 0 || !missingRoles.isEmpty() ? ChatFormatting.RED
                        : report.warnings() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        for (ValidationIssue issue : report.displayedIssues()) {
            send(source, line(
                    issue.severity().name(),
                    issue.poolId() + " entry " + issue.entryNumber()
                            + " role=" + issue.role().name().toLowerCase(Locale.ROOT)
                            + " gun=" + issue.gunId()
                            + " status=" + issue.status()
                            + " explosion=" + issue.explosionState()
                            + (issue.sidearmEligibility() == null
                                    ? ""
                                    : " eligibility=" + issue.sidearmEligibility()),
                    severityColor(issue.severity())));
        }
        int displayedMissingRoles = Math.min(
                missingRoles.size(),
                NaturalGuardLoadoutCommandContract.MAX_VALIDATION_ISSUES_IN_SUMMARY);
        for (int index = 0; index < displayedMissingRoles; index++) {
            send(source, line("ERROR", missingRoles.get(index), ChatFormatting.RED));
        }
        if (missingRoles.size() > displayedMissingRoles) {
            send(source, line(
                    "More",
                    (missingRoles.size() - displayedMissingRoles)
                            + " additional missing-role issue(s) omitted",
                    ChatFormatting.YELLOW));
        }
        if (report.omittedIssues() > 0) {
            send(source, line(
                    "More",
                    report.omittedIssues()
                            + " additional issue(s) omitted; use /vgg loadouts inspect <pool> [page]",
                    ChatFormatting.YELLOW));
        } else if (report.issueCount() > 0) {
            send(source, line(
                    "Details",
                    "Use /vgg loadouts inspect <pool> [page] for entry conditions",
                    ChatFormatting.GRAY));
        }
        return 1;
    }

    private static List<String> missingRoles(
            NaturalGuardLoadoutReloadSnapshot snapshot,
            @Nullable ResourceLocation requestedPool) {
        Set<ResourceLocation> loadedRoles = GuardRoles.snapshot().keySet();
        Stream<NaturalGuardLoadoutPool> pools = requestedPool == null
                ? snapshot.pools().values().stream()
                : Stream.ofNullable(snapshot.get(requestedPool));
        return pools.sorted(Comparator.comparing(pool -> pool.id().toString()))
                .flatMap(pool -> java.util.stream.IntStream.range(0, pool.entries().size())
                        .mapToObj(index -> new Object[] {pool, index, pool.entries().get(index)}))
                .filter(parts -> ((NaturalGuardLoadoutPool.Entry) parts[2]).roleId() != null)
                .filter(parts -> !loadedRoles.contains(
                        ((NaturalGuardLoadoutPool.Entry) parts[2]).roleId()))
                .map(parts -> {
                    NaturalGuardLoadoutPool pool = (NaturalGuardLoadoutPool) parts[0];
                    int index = (Integer) parts[1];
                    NaturalGuardLoadoutPool.Entry entry =
                            (NaturalGuardLoadoutPool.Entry) parts[2];
                    return pool.id() + " entry " + (index + 1)
                            + " references missing guard role " + entry.roleId();
                })
                .toList();
    }

    private static int inspect(
            CommandSourceStack source,
            ResourceLocation poolId,
            int pageNumber) {
        NaturalGuardLoadoutReloadSnapshot snapshot = NaturalGuardLoadoutPools.reloadSnapshot();
        NaturalGuardLoadoutPool pool = snapshot.get(poolId);
        if (pool == null) {
            return fail(source, "Unknown natural loadout pool: " + poolId);
        }
        final InspectionPage page;
        try {
            page = NaturalGuardLoadoutCommandService.inspect(pool, pageNumber);
        } catch (IllegalArgumentException exception) {
            return fail(source, exception.getMessage());
        }

        send(source, Component.literal("[VGG Loadouts] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(pool.id().toString()).withStyle(ChatFormatting.WHITE)));
        send(source, line(
                "Pool",
                "format=" + pool.format()
                        + ", entries=" + pool.entries().size()
                        + ", totalWeight=" + pool.totalWeight()
                        + ", page=" + page.page() + "/" + page.pages()
                        + ", generation=" + snapshot.generation(),
                ChatFormatting.GRAY));
        for (InspectedEntry inspected : page.entries()) {
            send(source, line(
                    "Entry " + inspected.entryNumber(),
                    bounded("gun=" + inspected.entry().gunId()
                            + ", sidearm=" + value(inspected.entry().sidearmId())
                            + ", role=" + value(inspected.entry().roleId())
                            + ", weight=" + inspected.entry().weight()
                            + ", conditions=" + conditions(inspected.entry().conditions())
                            + ", compatibility=" + inspected.analysis().status()
                            + ", ammo=" + value(inspected.analysis().ammoId())
                            + ", ammoItems=" + inspected.analysis().ammunitionItems()
                            + ", explosion=" + inspected.analysis().explosionState()
                            + sidearmDetails(inspected.sidearmAnalysis())),
                    diagnosticColor(
                            inspected.analysis(),
                            inspected.sidearmAnalysis())));
        }
        return 1;
    }

    private static int preview(
            CommandSourceStack source,
            String difficultyName,
            String spawnSourceName,
            @Nullable ResourceLocation requestedPool) {
        NaturalGuardDifficulty difficulty = parseEnum(
                NaturalGuardDifficulty.class, difficultyName);
        NaturalGuardSpawnSource spawnSource = parseEnum(
                NaturalGuardSpawnSource.class, spawnSourceName);
        if (difficulty == null) {
            return fail(source, "Unknown difficulty: " + difficultyName);
        }
        if (spawnSource == null) {
            return fail(source, "Unknown spawn source: " + spawnSourceName);
        }

        VillagerGuardGunnersConfig config = ModConfigs.GUARD_GUNNERS;
        NaturalGuardDifficultySettings difficultySettings =
                config.naturalLoadouts.settingsFor(difficulty);
        ResourceLocation poolId = requestedPool == null
                ? difficultySettings.poolId()
                : requestedPool;
        if (poolId == null) {
            return fail(source, "The requested difficulty has no configured loadout pool");
        }
        NaturalGuardLoadoutReloadSnapshot snapshot = NaturalGuardLoadoutPools.reloadSnapshot();
        NaturalGuardLoadoutPool pool = snapshot.get(poolId);
        if (pool == null) {
            return fail(source, "Unknown natural loadout pool: " + poolId);
        }

        Guard guardContext = source.getEntity() instanceof Guard guard ? guard : null;
        Set<String> guardTags = guardContext == null ? Set.of() : guardContext.getTags();
        BlockPos position = BlockPos.containing(source.getPosition());
        final NaturalGuardLoadoutMatchContext matchContext;
        try {
            matchContext = NaturalGuardLoadoutContextCapture.capture(
                    source.getLevel(),
                    position,
                    pool,
                    difficulty,
                    spawnSource,
                    guardTags);
        } catch (RuntimeException exception) {
            return fail(source, "Could not capture the requested preview context");
        }
        PreviewReport report = NaturalGuardLoadoutCommandService.preview(
                pool,
                matchContext,
                guardContext != null,
                guardContext);

        NaturalGuardSpawnRules rules = config.naturalLoadouts.spawnRules();
        boolean configEnabledForSource = config.enabled.get()
                && config.naturalLoadouts.enabled.get()
                && difficultySettings.enabled()
                && rules.permits(spawnSource);
        send(source, Component.literal("[VGG Loadouts] Preview ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(pool.id().toString()).withStyle(ChatFormatting.WHITE)));
        send(source, line(
                "Context",
                "dimension=" + matchContext.dimensionId()
                        + ", pos=" + position.toShortString()
                        + ", difficulty=" + difficulty
                        + ", source=" + spawnSource
                        + ", raid=" + matchContext.raidActive()
                        + ", guardContext=" + (guardContext != null)
                        + ", configEnabledForSource=" + configEnabledForSource
                        + ", generation=" + snapshot.generation(),
                configEnabledForSource ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        send(source, line(
                "Matches",
                "entries=" + report.matchingEntries()
                        + ", weight=" + report.matchingWeight()
                        + ", guardContextRequired=" + report.contextRequiredEntries(),
                report.matchingEntries() > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        if (report.contextRequiredEntries() > 0) {
            send(source, line(
                    "WARNING",
                    "Guard-tag conditions require /execute as a Guard Villager guard for a definitive preview",
                    ChatFormatting.YELLOW));
        }
        for (PreviewEntry entry : report.displayedEntries()) {
            send(source, line(
                    "Entry " + entry.entryNumber(),
                    bounded("gun=" + entry.entry().gunId()
                            + ", sidearm=" + value(entry.entry().sidearmId())
                            + ", role=" + value(entry.entry().roleId())
                            + ", weight=" + entry.entry().weight()
                            + ", "
                            + (report.contextRequiredEntries() > 0
                                    ? "knownProbability=" : "probability=")
                            + percent(entry.probability())
                            + ", compatibility=" + entry.analysis().status()
                            + ", explosion=" + entry.analysis().explosionState()
                            + sidearmDetails(entry.sidearmAnalysis())),
                    diagnosticColor(entry.analysis(), entry.sidearmAnalysis())));
        }
        if (report.omittedMatchingEntries() > 0) {
            send(source, line(
                    "More",
                    report.omittedMatchingEntries() + " matching entry result(s) omitted",
                    ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPools(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                        NaturalGuardLoadoutPools.reloadSnapshot().pools().keySet().stream()
                                .map(ResourceLocation::toString),
                        builder);
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Stream.of(values)
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    private static <T extends Enum<T>> @Nullable T parseEnum(
            Class<T> type,
            String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String conditions(NaturalGuardLoadoutConditionSpec conditions) {
        if (conditions.isUnconditional()) {
            return "any";
        }
        List<String> fields = new ArrayList<>();
        addGroup(fields, "dimensions", conditions.dimensions(), ResourceLocation::toString);
        addGroup(fields, "biomes", conditions.biomes(),
                NaturalGuardLoadoutConditionSpec.RegistrySelector::asDatapackValue);
        addGroup(fields, "structures", conditions.structures(),
                NaturalGuardLoadoutConditionSpec.RegistrySelector::asDatapackValue);
        addSet(fields, "difficulties", conditions.difficulties().stream()
                .map(value -> value.name().toLowerCase(Locale.ROOT)));
        addSet(fields, "spawn_sources", conditions.spawnSources().stream()
                .map(value -> value.name().toLowerCase(Locale.ROOT)));
        if (conditions.raid() != NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY) {
            fields.add("raid=" + conditions.raid().name().toLowerCase(Locale.ROOT));
        }
        addSet(fields, "required_guard_tags", conditions.requiredGuardTags().stream());
        addSet(fields, "excluded_guard_tags", conditions.excludedGuardTags().stream());
        return String.join("; ", fields);
    }

    private static <T> void addGroup(
            List<String> fields,
            String name,
            NaturalGuardLoadoutConditionSpec.SelectorGroup<T> group,
            Function<T, String> formatter) {
        addSet(fields, name + ".include", group.include().stream().map(formatter));
        addSet(fields, name + ".exclude", group.exclude().stream().map(formatter));
    }

    private static void addSet(List<String> fields, String name, Stream<String> values) {
        List<String> ordered = values.sorted(STRING_ORDER).toList();
        if (!ordered.isEmpty()) {
            fields.add(name + "=[" + String.join(",", ordered) + "]");
        }
    }

    private static String bounded(String value) {
        int maximum = NaturalGuardLoadoutCommandContract.MAX_DETAIL_LENGTH;
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum - 3;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + "...";
    }

    private static String value(@Nullable Object value) {
        return value == null ? "none" : value.toString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0D);
    }

    private static Component line(String label, String value, ChatFormatting color) {
        return Component.literal(label + ": ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(value).withStyle(color));
    }

    private static ChatFormatting severityColor(NaturalGuardLoadoutDiagnosticSeverity severity) {
        return switch (severity) {
            case INFO -> ChatFormatting.GRAY;
            case WARNING -> ChatFormatting.YELLOW;
            case ERROR -> ChatFormatting.RED;
        };
    }

    private static ChatFormatting diagnosticColor(
            NaturalGuardLoadoutCommandService.CompatibilityDiagnostic diagnostic,
            @Nullable NaturalGuardLoadoutCommandService.SidearmDiagnostic sidearm) {
        if (!diagnostic.compatible()) {
            return ChatFormatting.RED;
        }
        if (sidearm != null && !sidearm.administrativelyCompatible()) {
            return ChatFormatting.RED;
        }
        if (sidearm != null && sidearm.contextRequired()) {
            return ChatFormatting.YELLOW;
        }
        return diagnostic.explosionState() == ExplosionState.CONTEXT_REQUIRED
                ? ChatFormatting.YELLOW
                : ChatFormatting.GRAY;
    }

    private static String sidearmDetails(
            @Nullable NaturalGuardLoadoutCommandService.SidearmDiagnostic sidearm) {
        if (sidearm == null) {
            return "";
        }
        return ", sidearmCompatibility=" + sidearm.weapon().status()
                + ", sidearmEligibility=" + sidearm.eligibility()
                + ", sidearmAmmo=" + value(sidearm.weapon().ammoId())
                + ", sidearmAmmoItems=" + sidearm.weapon().ammunitionItems()
                + ", sidearmExplosion=" + sidearm.weapon().explosionState();
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static int fail(CommandSourceStack source, @Nullable String message) {
        source.sendFailure(Component.literal(message == null ? "Command failed" : message));
        return 0;
    }
}
