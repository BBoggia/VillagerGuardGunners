package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlacement;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationRuntime;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationSavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Bounded read-only population diagnostics. This command never initializes a village. */
public final class VillagePopulationCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("population").executes(c -> show(c.getSource(), VillageRegistrySavedData.get(c.getSource().getLevel())
                .at(BlockPos.containing(c.getSource().getPosition())).unambiguous()))
                .then(Commands.argument("village", UuidArgument.uuid()).executes(c -> show(c.getSource(), UuidArgument.getUuid(c, "village"))));
    }
    private static int show(CommandSourceStack source, UUID id) {
        if (id == null || VillageRegistrySavedData.get(source.getLevel()).get(id) == null) {
            source.sendFailure(Component.literal("No unambiguous recorded village here. Supply a village UUID.")); return 0;
        }
        source.sendSuccess(() -> Component.literal("Village " + id + ": " + summary(source.getLevel(), id)), false);
        source.sendSuccess(() -> Component.literal("Managed generation=" + VillagePopulationRuntime.generationEnabled()
                + ", queued requests in dimension=" + VillagePopulationRuntime.pending(source.getLevel())
                + ", deferred errors=" + VillagePopulationRuntime.errors(source.getLevel())), false);
        return 1;
    }
    public static void ledgerSummary(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        UUID village = VillageRegistrySavedData.get(level).at(player.blockPosition()).unambiguous();
        if (village != null) player.displayClientMessage(Component.literal("Village population: " + summary(level, village)), false);
    }
    public static String summary(ServerLevel level, UUID village) {
        var registry = VillageRegistrySavedData.get(level);
        var data = VillagePopulationSavedData.get(level);
        if (!data.enabled()) return "population storage unavailable; automatic generation suspended";
        var plan = data.plan(village);
        String basis;
        if (plan != null) {
            basis = "frozen target=" + plan.target() + ", residences=" + plan.residences() + ", occupied cells=" + plan.areas()
                    + ", initial allowance=" + plan.allowance() + ", spent=" + data.spent(village);
        } else {
            var record = registry.get(village);
            var houses = new HashSet<UUID>(); var cells = new HashSet<Long>();
            if (record != null) {
                for (var piece : record.footprint().pieces()) if (VillagePopulationPlacement.isHouse(piece.template())
                        || ModConfigs.GUARD_GUNNERS.villagePopulation.residentialTemplates.get().contains(piece.template())) {
                    houses.add(VillagePopulationPlan.sourceId(piece)); cells.add(cell(piece.position(), 32));
                }
                // Cached home evidence is only a rough estimate for unclassified existing villages.
                if (houses.isEmpty()) for (BlockPos home : record.homes()) { houses.add(new UUID(0, cell(home, 8))); cells.add(cell(home, 32)); }
            }
            try {
                int target = ModConfigs.GUARD_GUNNERS.villagePopulation.settings().target(Math.min(128, houses.size()),
                        Math.min(128, cells.size()), Math.min(128, registry.buildingCount(village)), 0);
                basis = "estimated target=" + target + ", observation only; no generation allowance";
            } catch (RuntimeException exception) { basis = "invalid population settings; no generation allowance"; }
        }
        return "known living guards=" + data.living(village) + ", unassessed affiliations="
                + Math.max(0, registry.guardCount(village) - data.observed(village)) + ", " + basis;
    }
    private static long cell(BlockPos pos, int size) {
        return ((long) Math.floorDiv(pos.getX(), size) << 32) | (Math.floorDiv(pos.getZ(), size) & 0xffffffffL);
    }
    private VillagePopulationCommands() { }
}
