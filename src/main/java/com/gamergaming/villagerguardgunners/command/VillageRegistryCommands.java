package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.village.VillageDiscoveryRuntime;
import com.gamergaming.villagerguardgunners.village.VillageRecord;
import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import tallestegg.guardvillagers.entities.Guard;

/** Read-only, permission-level-2 diagnostics inherited from the /vgg root. */
final class VillageRegistryCommands {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("villages")
                .then(VillageCompatibilityCommands.build())
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("here").executes(c -> here(c.getSource())))
                .then(Commands.literal("list").executes(c -> list(c.getSource(), null))
                        .then(Commands.argument("after", UuidArgument.uuid())
                                .executes(c -> list(c.getSource(), UuidArgument.getUuid(c, "after")))))
                .then(Commands.literal("inspect").then(Commands.argument("village", UuidArgument.uuid())
                        .executes(c -> inspect(c.getSource(), UuidArgument.getUuid(c, "village")))))
                .then(Commands.literal("guard").then(Commands.argument("guard", EntityArgument.entity())
                        .executes(c -> {
                            var entity = EntityArgument.getEntity(c, "guard");
                            if (!(entity instanceof Guard)) return fail(c.getSource(), "The selected entity is not a guard.");
                            UUID village = VillageRegistrySavedData.get(c.getSource().getLevel()).guardVillage(entity.getUUID());
                            if (village == null) return fail(c.getSource(), "This guard has no recorded village affiliation.");
                            var data = VillageRegistrySavedData.get(c.getSource().getLevel());
                            if (data.membershipOutsideObservedRegion(village, entity.blockPosition())) {
                                send(c.getSource(), "Membership evidence: guard is outside the recorded village region/elevation. "
                                        + "This can reflect travel or an older horizontal association; saved affiliation is unchanged.");
                            }
                            return inspect(c.getSource(), village);
                        })));
    }
    private static int status(CommandSourceStack source) {
        var data = VillageRegistrySavedData.get(source.getLevel());
        var runtime = VillageDiscoveryRuntime.diagnostics(source.getLevel());
        send(source, "[VGG] Villages — " + source.getLevel().dimension().location() + ": records=" + data.size()
                + ", storage=" + (data.enabled() ? "enabled" : data.disabledReason()));
        send(source, "Discovery: loadedChunks=" + runtime.trackedChunks() + ", pendingGuards=" + runtime.pendingGuards()
                + ", completedReviews=" + runtime.completedReviews() + ", rejectedObservations=" + runtime.deferredOrRejected()
                + ", workUnitsLastTick=" + runtime.lastWorkUnits() + "/32, structureTypeLimit=" + runtime.structureTypeLimit());
        return 1;
    }
    private static int here(CommandSourceStack source) {
        BlockPos pos = BlockPos.containing(source.getPosition());
        var data = VillageRegistrySavedData.get(source.getLevel());
        var query = data.at(pos);
        var chunk = VillageDiscoveryRuntime.chunkStatus(source.getLevel(), new ChunkPos(pos).toLong());
        send(source, "Local discovery: tracked=" + chunk.tracked() + ", reviewed=" + chunk.complete()
                + ", unresolvedStarts=" + chunk.unresolvedStarts() + ", unknownPoiSections=" + chunk.unknownPoiSections());
        if (!query.complete()) return fail(source, "Village lookup is unresolved: storage is disabled or too many footprints overlap.");
        if (query.villages().isEmpty()) {
            send(source, "No village footprint has been recorded here. Missing structure data remains unresolved.");
            return 1;
        }
        send(source, "Recorded footprints here: " + query.villages().size() + ". Overlaps remain separate villages.");
        query.villages().stream().limit(8).forEach(id -> describe(source, data, data.get(id)));
        return 1;
    }
    private static int list(CommandSourceStack source, UUID after) {
        var data = VillageRegistrySavedData.get(source.getLevel());
        var page = data.page(after, 8);
        for (var record : page) describe(source, data, record);
        if (page.isEmpty()) send(source, "No further recorded villages in this dimension.");
        else send(source, "Next page: /vgg villages list " + page.get(page.size() - 1).identity().id());
        return 1;
    }
    private static int inspect(CommandSourceStack source, UUID id) {
        var data = VillageRegistrySavedData.get(source.getLevel());
        var record = data.get(id);
        if (record == null) return fail(source, "No village with that ID is recorded in this dimension.");
        describe(source, data, record);
        return 1;
    }
    private static void describe(CommandSourceStack source, VillageRegistrySavedData data, VillageRecord record) {
        var b = record.footprint().bounds();
        send(source, record.identity().id() + " — " + record.identity().structure() + ", start="
                + new ChunkPos(record.identity().startChunk()) + ", revision=" + record.revision());
        send(source, "Planned bounds: " + b.minX() + "," + b.minZ() + " to " + b.maxX() + "," + b.maxZ()
                + "; pieces=" + record.footprint().pieces().size() + ", observedHomes=" + record.homes().size()
                + ", affiliatedGuards=" + data.guardCount(record.identity().id())
                + ", associatedBuildings=" + data.buildingCount(record.identity().id()));
    }
    private static int fail(CommandSourceStack source, String message) { source.sendFailure(Component.literal(message)); return 0; }
    private static void send(CommandSourceStack source, String message) { source.sendSuccess(() -> Component.literal(message), false); }
    private VillageRegistryCommands() {}
}
