package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.api.GuardPostAccess;
import com.gamergaming.villagerguardgunners.api.GuardPostAssignmentAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostAssignment;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroupPool;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroupReloadSnapshot;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroups;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIdentity;
import com.gamergaming.villagerguardgunners.worldgen.structure.WatchtowerGenerationPolicy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded, read-only operator diagnostics for Village Watchtowers. */
public final class WatchtowerCommands {
    private static final int GROUPS_PER_PAGE = 8;

    private WatchtowerCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("watchtowers")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("groups")
                        .executes(context -> groups(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> groups(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("errors")
                        .executes(context -> errors(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> errors(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("guard", EntityArgument.entity())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "guard")))));
    }

    private static int status(CommandSourceStack source) {
        WatchtowerGenerationPolicy policy =
                ModConfigs.GUARD_GUNNERS.watchtowers.snapshot();
        GuardPostGroupReloadSnapshot groups = GuardPostGroups.reloadSnapshot();
        send(source, "[VGG] Village Watchtowers", ChatFormatting.AQUA);
        send(source,
                "Registration: structure=" + ModStructures.WATCHTOWER.isPresent()
                        + ", piece=" + ModStructures.WATCHTOWER_PIECE.isPresent(),
                registrationColor());
        send(source,
                "Policy: generation=" + policy.generationEnabled()
                        + ", guards=" + policy.guardSpawningEnabled()
                        + ", maximumGuards=" + policy.maximumGuardsPerPost()
                        + ", armory=" + policy.armoryPlacementEnabled()
                        + ", stock=" + policy.armoryStockEnabled(),
                policy.generationEnabled() ? ChatFormatting.GRAY : ChatFormatting.YELLOW);
        send(source,
                "Guard groups: generation=" + groups.generation()
                        + ", accepted=" + groups.lastReloadAccepted()
                        + ", published=" + groups.groups().size()
                        + ", rejected=" + groups.lastErrors().size(),
                groups.lastReloadAccepted() ? ChatFormatting.GREEN : ChatFormatting.RED);
        send(source,
                "Precedence: datapacks define placement, groups, and loot; config may only suppress new actions.",
                ChatFormatting.DARK_GRAY);
        return 1;
    }

    private static int groups(CommandSourceStack source, int requestedPage) {
        GuardPostGroupReloadSnapshot snapshot = GuardPostGroups.reloadSnapshot();
        List<GuardPostGroupPool> pools = snapshot.groups().values().stream()
                .sorted(Comparator.comparing(pool -> pool.id().toString()))
                .toList();
        int pageCount = Math.max(1, (pools.size() + GROUPS_PER_PAGE - 1) / GROUPS_PER_PAGE);
        int page = Math.min(requestedPage, pageCount);
        int from = (page - 1) * GROUPS_PER_PAGE;
        int to = Math.min(from + GROUPS_PER_PAGE, pools.size());

        send(source,
                "[VGG] Published watchtower groups — page " + page + "/" + pageCount
                        + " (generation " + snapshot.generation() + ")",
                ChatFormatting.AQUA);
        if (pools.isEmpty()) {
            send(source, "No accepted guard-post groups are published.", ChatFormatting.YELLOW);
            return 1;
        }
        for (GuardPostGroupPool pool : pools.subList(from, to)) {
            send(source,
                    pool.id() + " — variants=" + pool.variants().size()
                            + ", parents=" + pool.parents().size(),
                    ChatFormatting.GRAY);
        }
        return to - from;
    }

    private static int errors(CommandSourceStack source, int requestedPage) {
        GuardPostGroupReloadSnapshot snapshot = GuardPostGroups.reloadSnapshot();
        List<GuardPostGroupReloadSnapshot.RejectedDocument> errors = snapshot.lastErrors();
        int pageCount = Math.max(1, (errors.size() + GROUPS_PER_PAGE - 1) / GROUPS_PER_PAGE);
        int page = Math.min(requestedPage, pageCount);
        int from = (page - 1) * GROUPS_PER_PAGE;
        int to = Math.min(from + GROUPS_PER_PAGE, errors.size());
        send(source,
                "[VGG] Last watchtower group reload errors — page " + page + "/" + pageCount,
                ChatFormatting.AQUA);
        if (errors.isEmpty()) {
            send(source, "The last guard-group reload reported no errors.", ChatFormatting.GREEN);
            return 1;
        }
        for (GuardPostGroupReloadSnapshot.RejectedDocument error : errors.subList(from, to)) {
            send(source,
                    (error.id() == null ? "registry" : error.id().toString())
                            + " — " + error.reason(),
                    ChatFormatting.RED);
        }
        return to - from;
    }

    private static int inspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Guard guard)) {
            source.sendFailure(Component.literal("The selected entity is not a Guard Villager."));
            return 0;
        }
        GuardPostIdentity identity = GuardPostAccess.getIdentity(guard);
        if (identity == null) {
            if (GuardPostAccess.hasUnreadableIdentity(guard)) {
                source.sendFailure(Component.literal(
                        "This guard has an unreadable or future watchtower identity; it is preserved and inactive."));
                return 0;
            }
            source.sendFailure(Component.literal("This guard has no generated watchtower identity."));
            return 0;
        }
        GuardPostAssignment pending = GuardPostAssignmentAccess.get(guard);
        SquadMembership membership = GuardSquadAccess.getMembership(guard);
        ResourceLocation roleId = GuardRoleAccess.getRoleId(guard);
        send(source, "[VGG] Watchtower guard " + guard.getDisplayName().getString(), ChatFormatting.AQUA);
        send(source,
                "Post: " + identity.postId() + ", group=" + identity.groupId()
                        + ", variant=" + identity.variantName()
                        + ", member=" + identity.memberIndex(),
                ChatFormatting.GRAY);
        send(source,
                "Integration: " + (pending == null
                        ? "no pending assignment (consumed or unavailable)"
                        : "pending loadout=" + pending.loadoutPool()
                                + ", difficulty=" + pending.generationDifficulty()),
                pending == null ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        send(source,
                "Role: " + (roleId == null ? "none" : roleId.toString())
                        + ", squad=" + (membership == null
                                ? "none"
                                : membership.squadId().toString()),
                ChatFormatting.GRAY);
        return 1;
    }

    private static ChatFormatting registrationColor() {
        return ModStructures.WATCHTOWER.isPresent() && ModStructures.WATCHTOWER_PIECE.isPresent()
                ? ChatFormatting.GREEN
                : ChatFormatting.RED;
    }

    private static void send(
            CommandSourceStack source,
            String text,
            ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }
}
