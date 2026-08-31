package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.role.GuardRoleDefinition;
import com.gamergaming.villagerguardgunners.role.GuardRoleReloadSnapshot;
import com.gamergaming.villagerguardgunners.role.GuardRoles;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded, operator-invoked role diagnostics and item-neutral assignment. */
final class GuardRoleCommands {
    private static final int PAGE_SIZE = 12;

    private GuardRoleCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("roles")
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("role", ResourceLocationArgument.id())
                                .suggests((context, builder) -> suggestRoles(builder))
                                .executes(context -> inspect(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "role")))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("guard", EntityArgument.entity())
                                .then(Commands.argument("role", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> suggestRoles(builder))
                                        .executes(context -> assign(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "guard"),
                                                ResourceLocationArgument.getId(context, "role"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("guard", EntityArgument.entity())
                                .executes(context -> clear(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "guard")))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestRoles(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                GuardRoles.snapshot().keySet().stream()
                        .map(ResourceLocation::toString)
                        .sorted(),
                builder);
    }

    private static int list(CommandSourceStack source, int requestedPage) {
        GuardRoleReloadSnapshot snapshot = GuardRoles.reloadSnapshot();
        List<ResourceLocation> ids = snapshot.roles().keySet().stream().sorted().toList();
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (requestedPage > pages) {
            return fail(source, "Role page " + requestedPage + " does not exist; maximum is " + pages);
        }
        source.sendSuccess(() -> Component.literal(
                        "[VGG Roles] generation=" + snapshot.generation()
                                + ", accepted=" + snapshot.lastReloadAccepted()
                                + ", roles=" + ids.size()
                                + ", page=" + requestedPage + "/" + pages)
                .withStyle(snapshot.lastReloadAccepted()
                        ? ChatFormatting.AQUA
                        : ChatFormatting.RED), false);
        int start = (requestedPage - 1) * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, ids.size()); index++) {
            ResourceLocation id = ids.get(index);
            GuardRoleDefinition definition = snapshot.roles().get(id);
            source.sendSuccess(() -> Component.literal(
                            id + " parents=" + definition.parents().size()
                                    + " defaults=" + summarize(definition.effectiveDefaults()))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        int displayedErrors = Math.min(PAGE_SIZE, snapshot.lastErrors().size());
        for (int index = 0; index < displayedErrors; index++) {
            String error = snapshot.lastErrors().get(index);
            source.sendSuccess(() -> Component.literal("Last reload error: " + error)
                    .withStyle(ChatFormatting.RED), false);
        }
        if (snapshot.lastErrors().size() > displayedErrors) {
            source.sendSuccess(() -> Component.literal(
                            (snapshot.lastErrors().size() - displayedErrors)
                                    + " additional reload error(s) omitted")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int inspect(CommandSourceStack source, ResourceLocation id) {
        GuardRoleDefinition role = GuardRoles.get(id);
        if (role == null) {
            return fail(source, "Unknown guard role: " + id);
        }
        source.sendSuccess(() -> Component.literal("[VGG Role] " + id)
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Parents: " + role.parents())
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                        "Declared: " + summarize(role.declaredDefaults()))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                        "Effective: " + summarize(role.effectiveDefaults()))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int assign(
            CommandSourceStack source,
            Entity entity,
            ResourceLocation roleId) {
        if (!(entity instanceof Guard guard)) {
            return fail(source, "Selected entity is not a Guard Villager");
        }
        if (GuardRoles.get(roleId) == null) {
            return fail(source, "Unknown guard role: " + roleId);
        }
        if (!GuardRoleAccess.setRoleId(guard, roleId)) {
            return fail(source, "Guard role assignment failed");
        }
        source.sendSuccess(() -> Component.literal(
                        "Assigned role " + roleId + " to " + guard.getDisplayName().getString())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clear(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Guard guard)) {
            return fail(source, "Selected entity is not a Guard Villager");
        }
        if (!GuardRoleAccess.setRoleId(guard, null)) {
            return fail(source, "Guard role assignment failed");
        }
        source.sendSuccess(() -> Component.literal(
                        "Cleared the role from " + guard.getDisplayName().getString())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String summarize(GuardOrders orders) {
        return "engagement=" + orders.engagement()
                + ", tactics=" + orders.tactics()
                + ", explosives=" + orders.explosives()
                + ", pickup=" + orders.ammoPickup()
                + ", sharing=" + orders.ammoSharing()
                + ", movement=" + orders.movement()
                + ", range=" + orders.maximumRange();
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
