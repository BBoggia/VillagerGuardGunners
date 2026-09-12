package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventRuntime;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseRuntimePolicy;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseSignal;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseStatus;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseTimings;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySavedData;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetwork;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Explicit, read-only diagnostics for defense networks and event routing. */
public final class VillageDefenseCommands {
    private static final int ITEMS_PER_PAGE = 8;

    private VillageDefenseCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("defense")
                .then(VillageCoverageCommands.build())
                .then(VillagePopulationCommands.build())
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("nearest")
                        .executes(context -> nearest(context.getSource())))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("network", UuidArgument.uuid())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "network")))))
                .then(Commands.literal("networks")
                        .executes(context -> networks(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> networks(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(
                                                context, "page")))))
                .then(Commands.literal("events")
                        .executes(context -> events(context.getSource())));
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(level);
        VillageDefenseDutySavedData duties = VillageDefenseDutySavedData.get(level);
        EnumMap<VillageDefenseState, Integer> states =
                new EnumMap<>(VillageDefenseState.class);
        for (VillageDefenseNetwork network : data.snapshot()) {
            states.merge(network.defenseStatus().state(), 1, Integer::sum);
        }
        send(source, "[VGG] Village defense state — "
                + level.dimension().location(), ChatFormatting.AQUA);
        send(source,
                "Storage: enabled=" + data.enabled()
                        + ", networks=" + data.networkCount()
                        + ", buildings=" + data.buildingCount()
                        + ", aliases=" + data.aliasCount(),
                data.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED);
        send(source,
                "Duty slots: enabled=" + duties.enabled()
                        + ", buildings=" + duties.layoutCount()
                        + ", slots=" + duties.slotCount(),
                duties.enabled() ? ChatFormatting.GRAY : ChatFormatting.RED);
        send(source,
                "Readiness: peace=" + states.getOrDefault(VillageDefenseState.PEACE, 0)
                        + ", watch=" + states.getOrDefault(VillageDefenseState.WATCH, 0)
                        + ", alert=" + states.getOrDefault(VillageDefenseState.ALERT, 0)
                        + ", siege=" + states.getOrDefault(VillageDefenseState.SIEGE, 0)
                        + ", recovery="
                        + states.getOrDefault(VillageDefenseState.RECOVERY, 0),
                ChatFormatting.GRAY);
        send(source,
                "Next review: " + reviewTime(
                        data.nextReviewGameTime(), level.getGameTime()),
                ChatFormatting.GRAY);
        VillageDefenseTimings timings = VillageDefenseRuntimePolicy.timings();
        send(source,
                "Policy: automaticDuties="
                        + VillageDefenseRuntimePolicy.automaticDutiesEnabled()
                        + ", watchTicks=" + timings.watchTicks()
                        + ", alertTicks=" + timings.alertTicks()
                        + ", recoveryTicks=" + timings.recoveryTicks()
                        + ", dutySpeed="
                        + VillageDefenseRuntimePolicy.dutyMovementSpeed(),
                VillageDefenseRuntimePolicy.automaticDutiesEnabled()
                        ? ChatFormatting.GRAY
                        : ChatFormatting.YELLOW);
        VillageDefenseStaffingRuntime.Diagnostics staffing =
                VillageDefenseStaffingRuntime.diagnostics(level);
        send(source,
                "Staffing: loadedGuards=" + staffing.loadedGuards()
                        + ", assignments=" + staffing.reservations()
                        + ", pendingNetworks=" + staffing.pendingNetworks()
                        + ", reviews=" + staffing.completedReviews()
                        + ", limitRejections=" + staffing.guardLimitRejections(),
                staffing.guardLimitRejections() == 0L
                        ? ChatFormatting.GRAY
                        : ChatFormatting.YELLOW);
        VillageDefenseEventRuntime.DiagnosticsSnapshot runtime =
                VillageDefenseEventRuntime.diagnostics(level);
        send(source,
                "Runtime: routed=" + runtime.routedEvents()
                        + ", throttled=" + runtime.throttledEvents()
                        + ", unmatched=" + runtime.unmatchedEvents()
                        + ", rejected=" + runtime.rejectedEvents()
                        + ", activeRaids=" + runtime.activeRaids(),
                runtime.rejectedEvents() == 0L
                        ? ChatFormatting.GRAY
                        : ChatFormatting.YELLOW);
        return 1;
    }

    private static int nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPos.containing(source.getPosition());
        VillageDefenseNetwork network = VillageDefenseNetworkSavedData.get(level)
                .findNearest(
                        position,
                        VillageDefenseNetworkSavedData.ABSOLUTE_MAXIMUM_LINK_RADIUS);
        if (network == null) {
            source.sendFailure(Component.literal(
                    "No indexed village defense network is within 256 blocks."));
            return 0;
        }
        return describe(source, network, position, null);
    }

    private static int inspect(CommandSourceStack source, UUID requestedId) {
        VillageDefenseNetwork network = VillageDefenseNetworkSavedData.get(
                source.getLevel()).getNetwork(requestedId);
        if (network == null) {
            source.sendFailure(Component.literal(
                    "No village defense network or alias has ID " + requestedId + "."));
            return 0;
        }
        return describe(source, network, null, requestedId);
    }

    private static int networks(CommandSourceStack source, int requestedPage) {
        List<VillageDefenseNetwork> networks = VillageDefenseNetworkSavedData.get(
                source.getLevel()).snapshot();
        Page page = Page.of(networks.size(), requestedPage);
        send(source,
                "[VGG] Village defense networks — page "
                        + page.number() + "/" + page.count(),
                ChatFormatting.AQUA);
        if (networks.isEmpty()) {
            send(source, "No village defense networks are registered.",
                    ChatFormatting.YELLOW);
            return 1;
        }
        long gameTime = source.getLevel().getGameTime();
        for (VillageDefenseNetwork network
                : networks.subList(page.from(), page.to())) {
            VillageDefenseStatus status = network.defenseStatus();
            send(source,
                    network.networkId() + " — state="
                            + status.state().serializedName()
                            + ", buildings=" + network.buildings().size()
                            + ", review=" + reviewTime(
                                    status.nextReviewGameTime(), gameTime),
                    stateColor(status.state()));
        }
        return page.to() - page.from();
    }

    private static int events(CommandSourceStack source) {
        VillageDefenseEventRuntime.DiagnosticsSnapshot runtime =
                VillageDefenseEventRuntime.diagnostics(source.getLevel());
        send(source, "[VGG] Village defense event routing", ChatFormatting.AQUA);
        send(source,
                "Totals: routed=" + runtime.routedEvents()
                        + ", throttled=" + runtime.throttledEvents()
                        + ", unmatched=" + runtime.unmatchedEvents()
                        + ", rejected=" + runtime.rejectedEvents(),
                runtime.rejectedEvents() == 0L
                        ? ChatFormatting.GRAY
                        : ChatFormatting.YELLOW);
        send(source,
                "Signals: " + signalCounts(runtime.acceptedBySignal()),
                ChatFormatting.GRAY);
        send(source,
                "Raids: started=" + runtime.raidStarts()
                        + ", ended=" + runtime.raidEnds()
                        + ", active=" + runtime.activeRaids()
                        + ", observationOverflows="
                        + runtime.raidObservationOverflows()
                        + ", conservativeOverflow="
                        + runtime.raidTrackingOverflowed()
                        + ", restartReconciliations="
                        + runtime.restartReconciliations(),
                runtime.raidTrackingOverflowed()
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.GRAY);
        send(source,
                "Throttle state: trackedNetworks=" + runtime.trackedNetworks()
                        + ", boundedEvictions=" + runtime.evictions(),
                ChatFormatting.GRAY);
        VillageDefenseEventRuntime.LastEvent last = runtime.lastEvent();
        send(source,
                "Last event: " + (last == null
                        ? "none"
                        : "time=" + last.gameTime()
                                + ", source=" + last.source()
                                + ", signal=" + last.signal()
                                + ", network=" + (last.networkId() == null
                                        ? "none" : last.networkId())
                                + ", outcome=" + last.outcome()),
                ChatFormatting.GRAY);
        return 1;
    }

    private static int describe(
            CommandSourceStack source,
            VillageDefenseNetwork network,
            BlockPos origin,
            UUID requestedId) {
        VillageDefenseStatus status = network.defenseStatus();
        send(source, "[VGG] Defense network " + network.networkId(),
                ChatFormatting.AQUA);
        if (requestedId != null && !requestedId.equals(network.networkId())) {
            send(source, "Resolved alias: " + requestedId,
                    ChatFormatting.DARK_GRAY);
        }
        send(source,
                "State: " + status.state().serializedName()
                        + ", since=" + status.stateSinceGameTime()
                        + ", review=" + reviewTime(
                                status.nextReviewGameTime(),
                                source.getLevel().getGameTime())
                        + ", revision=" + status.revision(),
                stateColor(status.state()));
        send(source,
                "Membership: buildings=" + network.buildings().size()
                        + ", types=" + buildingCounts(network.buildings()),
                ChatFormatting.GRAY);
        VillageDefenseDutySavedData.DutyCoverage dutyCoverage =
                VillageDefenseDutySavedData.get(source.getLevel())
                        .coverageForBuildings(network.buildings().stream()
                                .map(VillageBuildingIdentity::buildingId)
                                .toList());
        send(source,
                "Authored duties: buildings=" + dutyCoverage.layouts()
                        + "/" + network.buildings().size()
                        + ", slots=" + dutyCoverage.slots(),
                dutyCoverage.layouts() == network.buildings().size()
                        ? ChatFormatting.GRAY
                        : ChatFormatting.YELLOW);
        VillageDefenseStaffingRuntime.NetworkDiagnostics staffing =
                VillageDefenseStaffingRuntime.networkDiagnostics(
                        source.getLevel(), network.networkId());
        send(source,
                "Current staffing: candidateGuards=" + staffing.candidateGuards()
                        + ", assignments=" + staffing.assignments(),
                staffing.assignments() < dutyCoverage.slots()
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.GRAY);
        if (origin != null) {
            double distance = network.buildings().stream()
                    .map(VillageBuildingIdentity::anchorPosition)
                    .mapToDouble(anchor -> Math.sqrt(anchor.distSqr(origin)))
                    .min()
                    .orElse(Double.NaN);
            send(source,
                    "Nearest indexed anchor: " + decimal(distance) + " blocks",
                    ChatFormatting.GRAY);
        }
        return 1;
    }

    private static String buildingCounts(List<VillageBuildingIdentity> buildings) {
        EnumMap<VillageBuildingType, Integer> counts =
                new EnumMap<>(VillageBuildingType.class);
        buildings.forEach(building -> counts.merge(
                building.buildingType(), 1, Integer::sum));
        StringBuilder output = new StringBuilder();
        for (VillageBuildingType type : VillageBuildingType.values()) {
            int count = counts.getOrDefault(type, 0);
            if (count == 0) {
                continue;
            }
            if (!output.isEmpty()) {
                output.append(", ");
            }
            output.append(type.serializedName()).append('=').append(count);
        }
        return output.isEmpty() ? "none" : output.toString();
    }

    private static String signalCounts(Map<VillageDefenseSignal, Long> counts) {
        StringBuilder output = new StringBuilder();
        for (VillageDefenseSignal signal : VillageDefenseSignal.values()) {
            if (!output.isEmpty()) {
                output.append(", ");
            }
            output.append(signal.name().toLowerCase(Locale.ROOT))
                    .append('=')
                    .append(counts.getOrDefault(signal, 0L));
        }
        return output.toString();
    }

    private static String reviewTime(long reviewTime, long gameTime) {
        if (reviewTime == VillageDefenseStatus.NO_REVIEW) {
            return "none";
        }
        return reviewTime + " (in " + Math.max(0L, reviewTime - gameTime) + " ticks)";
    }

    private static ChatFormatting stateColor(VillageDefenseState state) {
        return switch (state) {
            case PEACE -> ChatFormatting.GREEN;
            case RECOVERY -> ChatFormatting.GRAY;
            case WATCH -> ChatFormatting.YELLOW;
            case ALERT -> ChatFormatting.GOLD;
            case SIEGE -> ChatFormatting.RED;
        };
    }

    private static String decimal(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.1f", value)
                : "n/a";
    }

    private static void send(
            CommandSourceStack source,
            String text,
            ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    private record Page(int number, int count, int from, int to) {
        private static Page of(int itemCount, int requestedPage) {
            int count = Math.max(1, (itemCount + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            int number = Math.min(requestedPage, count);
            int from = (number - 1) * ITEMS_PER_PAGE;
            return new Page(number, count, from,
                    Math.min(from + ITEMS_PER_PAGE, itemCount));
        }
    }
}
