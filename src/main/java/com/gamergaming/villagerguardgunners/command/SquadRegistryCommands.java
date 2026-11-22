package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRoute;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/** Explicit, permission-gated lifecycle tools for the bounded squad registry. */
public final class SquadRegistryCommands {
    private static final int ENTRIES_PER_PAGE = 10;

    private SquadRegistryCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("squads")
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument(
                                        "page",
                                        IntegerArgumentType.integer(1))
                                .executes(context -> list(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("routes")
                        .executes(context -> routes(context.getSource(), 1))
                        .then(Commands.argument(
                                        "page",
                                        IntegerArgumentType.integer(1))
                                .executes(context -> routes(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("unregister")
                        .then(Commands.argument("squad", UuidArgument.uuid())
                                .executes(context -> unregister(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "squad")))));
    }

    private static int list(CommandSourceStack source, int requestedPage) {
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(source.getServer());
        if (!registry.enabled()) {
            return disabled(source, registry);
        }
        List<SquadMetadata> squads = registry.snapshot();
        int pages = Math.max(1, (squads.size() + ENTRIES_PER_PAGE - 1)
                / ENTRIES_PER_PAGE);
        if (requestedPage > pages) {
            source.sendFailure(Component.translatable(
                    "commands.villagerguardgunners.squads.invalid_page",
                    pages));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                        "commands.villagerguardgunners.squads.list_header",
                        requestedPage,
                        pages,
                        squads.size()),
                false);
        int fromIndex = (requestedPage - 1) * ENTRIES_PER_PAGE;
        int toIndex = Math.min(squads.size(), fromIndex + ENTRIES_PER_PAGE);
        for (int index = fromIndex; index < toIndex; index++) {
            SquadMetadata metadata = squads.get(index);
            source.sendSuccess(() -> Component.translatable(
                            "commands.villagerguardgunners.squads.list_entry",
                            metadata.displayName(),
                            metadata.squadId().toString()),
                    false);
        }
        return squads.isEmpty() ? 0 : toIndex - fromIndex;
    }

    private static int routes(CommandSourceStack source, int requestedPage) {
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(source.getServer());
        if (!registry.enabled()) {
            return disabled(source, registry);
        }
        List<SquadPatrolRoute> routes = registry.patrolRouteSnapshot();
        int pages = Math.max(1, (routes.size() + ENTRIES_PER_PAGE - 1)
                / ENTRIES_PER_PAGE);
        if (requestedPage > pages) {
            source.sendFailure(Component.translatable(
                    "commands.villagerguardgunners.squads.invalid_page",
                    pages));
            return 0;
        }
        long executableRoutes = routes.stream().filter(SquadPatrolRoute::executable).count();
        source.sendSuccess(() -> Component.translatable(
                        "commands.villagerguardgunners.squads.routes_header",
                        requestedPage,
                        pages,
                        routes.size(),
                        executableRoutes),
                false);
        int fromIndex = (requestedPage - 1) * ENTRIES_PER_PAGE;
        int toIndex = Math.min(routes.size(), fromIndex + ENTRIES_PER_PAGE);
        for (int index = fromIndex; index < toIndex; index++) {
            SquadPatrolRoute route = routes.get(index);
            String name = registry.find(route.squadId())
                    .map(SquadMetadata::displayName)
                    .orElse(route.squadId().toString());
            source.sendSuccess(() -> Component.translatable(
                            "commands.villagerguardgunners.squads.routes_entry",
                            name,
                            route.dimensionId().toString(),
                            route.waypoints().size(),
                            route.revision(),
                            Component.translatable(route.executable()
                                    ? "commands.villagerguardgunners.squads.route_state.ready"
                                    : "commands.villagerguardgunners.squads.route_state.draft")),
                    false);
        }
        return routes.isEmpty() ? 0 : toIndex - fromIndex;
    }

    private static int disabled(
            CommandSourceStack source,
            SquadRegistrySavedData registry) {
        source.sendFailure(Component.translatable(
                "commands.villagerguardgunners.squads.disabled",
                registry.disabledReason() == null ? "unknown decoding failure"
                        : registry.disabledReason()));
        return 0;
    }

    private static int unregister(CommandSourceStack source, UUID squadId) {
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(source.getServer());
        if (!registry.enabled()) {
            return disabled(source, registry);
        }
        SquadMetadata metadata = registry.find(squadId).orElse(null);
        if (metadata == null) {
            source.sendFailure(Component.translatable(
                    "commands.villagerguardgunners.squads.not_found",
                    squadId.toString()));
            return 0;
        }
        boolean removedRoute = registry.findPatrolRoute(squadId).isPresent();
        if (!registry.remove(squadId)) {
            source.sendFailure(Component.translatable(
                    "commands.villagerguardgunners.squads.not_found",
                    squadId.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                        removedRoute
                                ? "commands.villagerguardgunners.squads.unregistered_with_route"
                                : "commands.villagerguardgunners.squads.unregistered",
                        metadata.displayName(),
                        squadId.toString()),
                true);
        return 1;
    }
}
