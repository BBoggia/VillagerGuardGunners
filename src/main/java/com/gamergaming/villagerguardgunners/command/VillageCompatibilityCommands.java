package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.village.compat.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;

/** Read-only diagnostics; permission level is inherited from the VGG command root. */
final class VillageCompatibilityCommands {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("compatibility")
                .then(Commands.literal("status").executes(c -> {
                    var snapshot = SettlementDefinitions.snapshot();
                    send(c.getSource(), "Compatibility formats=1,2, generation=" + snapshot.generation()
                            + ", activeProfiles=" + snapshot.profiles().size() + ", inactiveProfiles=" + snapshot.inactive().size());
                    snapshot.profiles().forEach(p -> send(c.getSource(), p.id() + " from " + p.provider()
                            + ": intent=" + p.intent() + ", reader=" + p.reader() + ", priority=" + p.priority()));
                    snapshot.inactive().forEach((id, reason) -> send(c.getSource(), id + ": inactive — " + reason));
                    SettlementDefinitions.errors().stream().limit(8).forEach(e -> send(c.getSource(), "Rejected reload: " + e));
                    send(c.getSource(), "Integrated Villages tag correction: " + IntegratedVillageTagCorrection.status());
                    return 1;
                }))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("structure", ResourceLocationArgument.id()).executes(c -> {
                            var id = ResourceLocationArgument.getId(c, "structure");
                            var decision = SettlementDefinitions.lookup(id);
                            send(c.getSource(), id + ": intent=" + decision.intent() + ", reason=" + decision.reason()
                                    + ", reader=" + decision.reader() + ", profiles=" + decision.profiles()
                                    + ", providers=" + decision.providers() + ", templateMappings=" + decision.templates().size()
                                    + ", sizeFamily=" + decision.sizeFamily() + ", traversalLimits=" + decision.traversalLimits()
                                    + ", verifiedTemplates=" + decision.meanings().values().stream().filter(PieceMeaning::supported).count()
                                    + ", mappedBedPositions=" + decision.meanings().values().stream().mapToInt(m -> m.beds().size()).sum()
                                    + ", mappedEntranceCandidates=" + decision.meanings().values().stream().mapToInt(m -> m.entrances().size()).sum());
                            return 1;
                        })))
                .then(Commands.literal("template")
                        .then(Commands.argument("structure", ResourceLocationArgument.id())
                                .then(Commands.argument("template", ResourceLocationArgument.id()).executes(c -> {
                                    var structure = ResourceLocationArgument.getId(c, "structure");
                                    var template = ResourceLocationArgument.getId(c, "template");
                                    var meaning = SettlementDefinitions.lookup(structure).meaning(template.toString());
                                    send(c.getSource(), template + ": kind=" + meaning.kind() + ", reason=" + meaning.reason()
                                            + ", resourceProvider=" + meaning.resourceProvider() + ", sha256=" + meaning.fingerprint()
                                            + ", localBeds=" + meaning.beds() + ", localEntranceCandidates=" + meaning.entrances());
                                    return 1;
                                }))));
    }
    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
    private VillageCompatibilityCommands() { }
}
