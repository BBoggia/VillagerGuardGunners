package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Paged operator diagnostics and a nearby, read-only ledger summary. */
public final class VillageCoverageCommands {
    private VillageCoverageCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("coverage")
                .executes(c -> show(c.getSource(), VillageRegistrySavedData.get(c.getSource().getLevel())
                        .at(BlockPos.containing(c.getSource().getPosition())).unambiguous(), 1))
                .then(Commands.argument("village", UuidArgument.uuid())
                        .executes(c -> show(c.getSource(), UuidArgument.getUuid(c, "village"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 4))
                                .executes(c -> show(c.getSource(), UuidArgument.getUuid(c, "village"),
                                        IntegerArgumentType.getInteger(c, "page")))));
    }
    private static int show(CommandSourceStack source, UUID id, int page) {
        if (id == null || VillageRegistrySavedData.get(source.getLevel()).get(id) == null) {
            source.sendFailure(Component.literal("No unambiguous recorded village here. Supply a village UUID.")); return 0;
        }
        var view = VillageCoverageRuntime.snapshot(source.getLevel(), id);
        source.sendSuccess(() -> Component.literal("Village " + id + ": " + summary(view)
                + "; coverageEnabled=" + VillageCoverageRuntime.enabled()), false);
        source.sendSuccess(() -> Component.literal("Dimension work: terrain=" + view.terrainUnits() + "/512, paths="
                + view.paths() + "/4, rejected=" + view.rejections()), false);
        if (view.plan() != null) {
            view.plan().areas().stream().skip((page - 1L) * 4).limit(4).forEach(area -> source.sendSuccess(() ->
                    Component.literal(area.areaId() + ": " + area.reason() + ", lastVisit=" + area.lastSuccessfulVisit()
                            + ", activeAge=" + area.activeAgeTicks()), false));
            source.sendSuccess(() -> Component.literal("Unavailable guards: " + view.plan().unavailableGuards()
                    + "; page " + page + "/" + Math.max(1, (view.plan().areas().size() + 3) / 4)), false);
        }
        return 1;
    }
    public static void ledgerSummary(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        UUID id = VillageRegistrySavedData.get(level).at(player.blockPosition()).unambiguous();
        if (id == null) return;
        var view = VillageCoverageRuntime.snapshot(level, id);
        // Only the player's current village, with no entity identities or remote pages.
        player.displayClientMessage(Component.literal("Village coverage: " + summary(view)), false);
    }
    private static String summary(VillageCoverageRuntime.Snapshot view) {
        if (view.plan() == null) return view.status() + ", loaded guards=" + view.loadedGuards();
        int unavailable = view.plan().unavailableGuards().values().stream().mapToInt(Integer::intValue).sum();
        long visited = view.plan().areas().stream().filter(a -> a.lastSuccessfulVisit() >= 0).count();
        return view.readiness().serializedName() + ", guards=" + view.loadedGuards()
                + ", available=" + (view.loadedGuards() - unavailable) + ", areas=" + view.plan().areas().size()
                + ", assigned=" + view.plan().assignments().size() + ", visited=" + visited
                + ", reserve=" + view.plan().reserveGuards().size() + ", " + view.status();
    }
}
