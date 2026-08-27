package com.gamergaming.villagerguardgunners.command;

import java.util.Locale;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Registers explicit, zero-idle-cost administrator diagnostics. */
public final class GuardGunnerCommands {
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private GuardGunnerCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vgg")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION_LEVEL))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("guard", EntityArgument.entity())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "guard"))))));
    }

    private static int inspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Guard guard)) {
            source.sendFailure(Component.translatable(
                    "commands.villagerguardgunners.inspect.not_guard"));
            return 0;
        }

        GuardGunCombatGoal gunGoal = guard.goalSelector.getAvailableGoals().stream()
                .map(wrappedGoal -> wrappedGoal.getGoal())
                .filter(GuardGunCombatGoal.class::isInstance)
                .map(GuardGunCombatGoal.class::cast)
                .findFirst()
                .orElse(null);
        if (gunGoal == null) {
            source.sendFailure(Component.translatable(
                    "commands.villagerguardgunners.inspect.no_goal"));
            return 0;
        }

        GuardGunCombatGoal.Diagnostics snapshot = gunGoal.captureDiagnostics();
        NativeGunController.Diagnostics nativeState = snapshot.nativeState();
        GuardFriendlyFireSafety.DiagnosticResult safety = snapshot.safety();
        ChatFormatting decisionColor = snapshot.decision() == GuardGunCombatGoal.FireDecision.READY_TO_FIRE
                ? ChatFormatting.GREEN
                : ChatFormatting.YELLOW;

        send(source, Component.literal("[VGG] ")
                .withStyle(ChatFormatting.AQUA)
                .append(guard.getDisplayName())
                .append(Component.literal(" (#" + guard.getId() + ")")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        send(source, line("Decision", snapshot.decision().name(), decisionColor));
        send(source, line(
                "Goal",
                (snapshot.goalActive() ? "active" : "inactive")
                        + ", movement=" + snapshot.movementState(),
                ChatFormatting.GRAY));

        if (snapshot.target() == null) {
            send(source, line("Target", "none", ChatFormatting.GRAY));
        } else {
            send(source, line(
                    "Target",
                    snapshot.target().getDisplayName().getString()
                            + " (#" + snapshot.target().getId() + "), distance="
                            + decimal(snapshot.distance()),
                    ChatFormatting.GRAY));
        }

        send(source, line(
                "Range",
                "preferred=" + decimal(snapshot.preferredRange())
                        + ", maximum=" + decimal(snapshot.maximumRange())
                        + ", withinMaximum=" + snapshot.withinMaximumRange(),
                ChatFormatting.GRAY));
        send(source, line(
                "Aim",
                "lineOfSight=" + snapshot.cachedLineOfSight()
                        + ", visibleTicks=" + snapshot.visibleTicks()
                        + "/" + snapshot.requiredVisibleTicks()
                        + ", yawError=" + degrees(snapshot.yawErrorDegrees())
                        + ", pitchError=" + degrees(snapshot.pitchErrorDegrees())
                        + ", tolerance=" + degrees(snapshot.aimToleranceDegrees()),
                ChatFormatting.GRAY));

        String blocker = safety.blocker() == null
                ? ""
                : ", blocker=" + safety.blocker().getDisplayName().getString()
                        + " (#" + safety.blocker().getId() + ")";
        send(source, line(
                "Safety",
                safety.status().name() + ", padding=" + decimal(snapshot.safetyPadding()) + blocker,
                safety.clear() ? ChatFormatting.GREEN : ChatFormatting.RED));

        String gunId = nativeState.gunId() == null ? "none" : nativeState.gunId().toString();
        send(source, line(
                "Weapon",
                gunId + ", mode=" + nativeState.fireMode()
                        + ", readable=" + nativeState.readableGunState()
                        + ", bound=" + nativeState.controllerBound()
                        + ", dataReady=" + nativeState.weaponDataReady()
                        + ", suspended=" + nativeState.suspended(),
                ChatFormatting.GRAY));
        send(source, line(
                "Ammo",
                "loaded=" + integerOrUnavailable(nativeState.loadedAmmo())
                        + ", chambered=" + nativeState.chambered()
                        + ", inventoryFed=" + nativeState.inventoryFed()
                        + ", reserveItems=" + nativeState.reserveItems()
                        + " in " + nativeState.occupiedReserveSlots() + " slot(s)"
                        + ", dryRetryTicks=" + nativeState.dryRetryTicks(),
                ChatFormatting.GRAY));
        send(source, line(
                "TaCZ",
                "aiming=" + nativeState.aiming()
                        + ", reload=" + nativeState.reloadState()
                        + ", bolting=" + nativeState.bolting()
                        + ", drawCooldown=" + milliseconds(nativeState.drawCooldownMillis())
                        + ", shootCooldown=" + milliseconds(nativeState.shootCooldownMillis())
                        + ", charge=" + decimal(nativeState.chargeProgress())
                        + ", lastResult="
                        + (nativeState.lastShootResult() == null
                                ? "none"
                                : nativeState.lastShootResult().name()),
                ChatFormatting.GRAY));
        return 1;
    }

    private static Component line(String label, String value, ChatFormatting valueColor) {
        return Component.literal(label + ": ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(value).withStyle(valueColor));
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static String decimal(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "n/a";
    }

    private static String milliseconds(long value) {
        return value >= 0L ? value + "ms" : "n/a";
    }

    private static String degrees(double value) {
        return Double.isFinite(value) ? decimal(value) + "deg" : "n/a";
    }

    private static String integerOrUnavailable(int value) {
        return value >= 0 ? Integer.toString(value) : "n/a";
    }
}
