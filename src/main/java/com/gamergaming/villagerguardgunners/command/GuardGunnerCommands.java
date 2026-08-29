package com.gamergaming.villagerguardgunners.command;

import java.util.Locale;

import javax.annotation.Nullable;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalEnvelope;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDiagnostics;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenance;
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
        GuardAmmoSummary.Snapshot ammunition = GuardAmmoSummary.capture(
                guard.getMainHandItem(),
                GuardAmmoAccess.getTaCZInventoryHandler(guard));
        GuardFriendlyFireSafety.DiagnosticResult safety = snapshot.safety();
        NaturalGuardLoadoutDiagnostics.Snapshot naturalLoadout =
                NaturalGuardLoadoutDiagnostics.capture(guard);
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
        send(source, line(
                "Rules of engagement",
                "mode=" + snapshot.engagementMode()
                        + ", reason=" + snapshot.engagementReason(),
                snapshot.engagementReason().mayEngage()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.RED));
        GuardOrders orders = GuardOrdersAccess.getOrders(guard);
        send(source, line(
                "Per-guard orders",
                "engagement=" + orders.engagement()
                        + " (effective=" + snapshot.engagementMode() + ")"
                        + ", tactics=" + orders.tactics()
                        + ", explosives=" + orders.explosives()
                        + ", ammoPickup=" + orders.ammoPickup()
                        + ", ammoSharing=" + orders.ammoSharing()
                        + ", movement=" + orders.movement()
                        + ", maximumRange=" + orders.maximumRange(),
                ChatFormatting.GRAY));
        send(source, line(
                "Natural loadout",
                naturalLoadoutDetails(naturalLoadout),
                switch (naturalLoadout.authentication()) {
                    case ACTIVE -> ChatFormatting.GREEN;
                    case GUN_MISMATCH -> ChatFormatting.RED;
                    case NO_RECORD -> ChatFormatting.GRAY;
                }));

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
                "minimum=" + decimal(snapshot.minimumRange())
                        + ", retreat=" + decimal(snapshot.retreatDistance())
                        + ", preferred=" + decimal(snapshot.preferredRange())
                        + ", maximum=" + decimal(snapshot.maximumRange())
                        + ", validWindow=" + snapshot.validFiringWindow()
                        + ", withinWindow=" + snapshot.withinFiringWindow()
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
        String protectionReason = safety.protectionReason() == null
                ? ""
                : ", allyReason=" + safety.protectionReason();
        send(source, line(
                "Safety",
                safety.status().name()
                        + ", muzzlePadding=" + decimal(snapshot.muzzleSafetyPadding())
                        + ", projectilePadding=" + decimal(snapshot.projectileSafetyPadding())
                        + ", blastPadding=" + decimal(snapshot.blastSafetyPadding())
                        + blocker
                        + protectionReason,
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
        NativeGunController.CachedTacticalState tactical = nativeState.tacticalState();
        TacticalEnvelope envelope = tactical.envelope();
        send(source, line(
                "Cached tactics",
                "gun=" + (tactical.gunId() == null ? "none" : tactical.gunId())
                        + ", profile=" + tactical.resolution().profile()
                        + ", source=" + tactical.resolution().source()
                        + ", type=" + stringOrUnavailable(
                                tactical.resolution().normalizedGunType())
                        + ", metadataReady=" + tactical.metadataReadable()
                        + ", explosive=" + tactical.traits().explosive()
                        + ", projectiles=" + tactical.traits().projectileCount()
                        + ", blastRadius=" + decimal(tactical.traits().explosionRadius()),
                tactical.metadataReadable() ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        send(source, line(
                "Cached envelope",
                "minimum=" + decimal(envelope.minimumFiringDistance())
                        + ", retreat=" + decimal(envelope.retreatDistance())
                        + ", preferred=" + decimal(envelope.preferredApproachDistance())
                        + ", maximum=" + decimal(envelope.maximumFiringDistance())
                        + ", visibleTicks=" + envelope.requiredVisibleTicks()
                        + ", aimTolerance=" + degrees(envelope.aimToleranceDegrees())
                        + ", validWindow=" + envelope.validFiringWindow(),
                envelope.validFiringWindow() ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        send(source, line(
                "Ammo",
                ammunitionDetails(ammunition, nativeState),
                ammunitionColor(ammunition)));
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

    private static String stringOrUnavailable(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    private static String naturalLoadoutDetails(
            NaturalGuardLoadoutDiagnostics.Snapshot snapshot) {
        String base = "state=" + snapshot.authentication()
                + ", attempted=" + snapshot.selectionAttempted()
                + ", decision=" + nullableName(snapshot.lastDecision())
                + ", issuance=" + nullableName(snapshot.lastIssuance());
        NaturalGuardLoadoutProvenance provenance = snapshot.provenance();
        if (provenance == null) {
            return base;
        }
        return base
                + ", schema=" + provenance.schemaVersion()
                + ", pool=" + provenance.poolId()
                + ", gun=" + provenance.gunId()
                + ", difficulty=" + provenance.difficulty()
                + ", source=" + provenance.spawnSource()
                + ", id=" + provenance.issuanceId();
    }

    private static String nullableName(@Nullable Enum<?> value) {
        return value == null ? "none" : value.name();
    }

    private static String ammunitionDetails(
            GuardAmmoSummary.Snapshot ammunition,
            NativeGunController.Diagnostics nativeState) {
        if (!ammunition.isReady()) {
            return "status=" + ammunition.status()
                    + ", gun=" + (ammunition.gunId() == null ? "none" : ammunition.gunId())
                    + ", dryRetryTicks=" + nativeState.dryRetryTicks()
                    + ", lastShare=" + nativeState.lastAmmoSharingStatus()
                    + "(" + nativeState.lastAmmoSharedItems() + ")";
        }
        String reserve = ammunition.infiniteReserve()
                ? "infinite"
                : Long.toString(ammunition.reserveUnits());
        return "status=" + ammunition.status()
                + ", feed=" + ammunition.feedType()
                + ", inventoryFed=" + nativeState.inventoryFed()
                + ", magazine=" + ammunition.loadedAmmo()
                + "/" + ammunition.magazineCapacity()
                + ", chamberSupported=" + ammunition.supportsChamber()
                + ", chambered=" + ammunition.chambered()
                + ", reserve=" + reserve
                + " (" + ammunition.reserveSource() + ")"
                + ", compatibleStacks=" + ammunition.compatibleStacks()
                + ", incompatibleStacks=" + ammunition.incompatibleAmmoStacks()
                + ", unknownStacks=" + ammunition.unknownAmmoStacks()
                + ", scanComplete=" + ammunition.reserveScanComplete()
                + ", dryRetryTicks=" + nativeState.dryRetryTicks()
                + ", lastShare=" + nativeState.lastAmmoSharingStatus()
                + "(" + nativeState.lastAmmoSharedItems() + ")";
    }

    private static ChatFormatting ammunitionColor(GuardAmmoSummary.Snapshot ammunition) {
        if (!ammunition.isReady()
                || ammunition.unknownAmmoStacks() > 0
                || !ammunition.reserveScanComplete()) {
            return ChatFormatting.YELLOW;
        }
        if (ammunition.hasCompatibleReserve()) {
            return ChatFormatting.GREEN;
        }
        return ammunition.displayedLoadedAmmo() > 0
                ? ChatFormatting.GOLD
                : ChatFormatting.RED;
    }
}
