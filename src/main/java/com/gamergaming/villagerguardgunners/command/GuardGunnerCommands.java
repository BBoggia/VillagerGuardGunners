package com.gamergaming.villagerguardgunners.command;

import java.util.Locale;

import javax.annotation.Nullable;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyDiagnostics;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplySettings;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalEnvelope;
import com.gamergaming.villagerguardgunners.defense.duty.GuardAutomaticDutyMovementController;
import com.gamergaming.villagerguardgunners.defense.duty.GuardPatrolRouteMovementController;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.role.GuardRoleDefinition;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
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
                .then(com.gamergaming.villagerguardgunners.quartermaster.service.ServiceCommands.build())
                .then(NaturalGuardLoadoutCommands.build())
                .then(SquadRegistryCommands.build())
                .then(GuardRoleCommands.build())
                .then(WatchtowerCommands.build())
                .then(VillageBuildingCommands.build())
                .then(VillageDefenseCommands.build())
                .then(VillageRegistryCommands.build())
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
        GuardAutomaticDutyMovementGoal dutyGoal = guard.goalSelector.getAvailableGoals().stream()
                .map(wrappedGoal -> wrappedGoal.getGoal())
                .filter(GuardAutomaticDutyMovementGoal.class::isInstance)
                .map(GuardAutomaticDutyMovementGoal.class::cast)
                .findFirst()
                .orElse(null);
        GuardAutomaticDutyMovementController.RuntimeSnapshot automaticDuty =
                dutyGoal == null ? null : dutyGoal.captureDiagnostics();
        GuardPatrolRouteMovementController.RuntimeSnapshot patrolRoute =
                dutyGoal == null ? null : dutyGoal.capturePatrolDiagnostics();
        NativeGunController.Diagnostics nativeState = snapshot.nativeState();
        var tacticalController = gunGoal.tacticalController();
        var threat = tacticalController.assessment();
        var route = gunGoal.tacticalNavigation();
        send(source, line("Movement ownership", gunGoal.movementOwner()
                + ", combatRetained=" + gunGoal.retainsCombatOwnership()
                + ", lastInterruption=" + gunGoal.lastInterruption(), ChatFormatting.GRAY));
        send(source, line("Tactical route", route.status() + ", destination=" + route.destination()
                + ", ownsPath=" + route.ownsPath() + ", moving=" + route.isMoving() + ", laneChange=" + route.isLaneChange()
                + ", pathAttempts=" + route.pathAttempts() + ", failedDestinations=" + route.failedDestinations()
                + ", candidates=" + route.candidatesChecked(), ChatFormatting.GRAY));
        var progress = com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteProgress.snapshot(guard);
        send(source, line("Navigation progress", progress.status() + ", node=" + progress.nextNode()
                + ", remaining=" + decimal(progress.remainingDistance())
                + ", lastProgressTick=" + progress.lastProgressTick()
                + ", transition=" + com.gamergaming.villagerguardgunners.ai.navigation.GuardNavigationRuntime.status(guard)
                + ", search=" + com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.status(guard), ChatFormatting.GRAY));
        var combatDuty = com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard);
        var combatSupply = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        send(source, line("Combat duty", combatDuty == null ? "none" : "anchor=" + combatDuty.anchor()
                + ", radius=" + combatDuty.radius() + ", source=" + combatDuty.source()
                + ", returning=" + (dutyGoal != null && dutyGoal.activeOwner() == GuardAutomaticDutyMovementGoal.MovementOwner.RETURN), ChatFormatting.GRAY));
        send(source, line("Emergency resupply", combatSupply != null && combatSupply.isEmergency()
                ? combatSupply.snapshot().state().name() : "inactive", ChatFormatting.GRAY));
        long now = guard.level().getGameTime();
        send(source, line("Target ranking", gunGoal.targetRankingStatus(), ChatFormatting.GRAY));
        send(source, line("Investigation", gunGoal.investigation().status()
                + ", lastSeen=" + gunGoal.investigation().lastSeenPosition()
                + ", destination=" + gunGoal.investigation().destination()
                + ", attempts=" + gunGoal.investigation().attempts(), ChatFormatting.GRAY));
        send(source, line("Tactical intent", tacticalController.decision().intent()
                + ", reason=" + tacticalController.decision().reason()
                + ", readiness=" + nativeState.readiness()
                + ", enteredTicksAgo=" + Math.max(0, now - tacticalController.enteredAt())
                + ", continuation=" + tacticalController.hasContinuation(), ChatFormatting.GRAY));
        send(source, line("Observed threats", "count=" + threat.threats()
                + ", complete=" + threat.complete()
                + ", assessmentAge=" + (threat.sampledAt() == Long.MIN_VALUE ? "never" : now - threat.sampledAt())
                + ", nearest=" + decimal(threat.nearestDistance())
                + ", pressure=" + decimal(threat.pressure()) + ", support=" + decimal(threat.support())
                + ", sectors=" + threat.occupiedSectors() + ", explosive=" + threat.explosiveDanger()
                + ", recentDamage=" + decimal(threat.recentDamageFraction())
                + ", candidates=" + tacticalController.tracker().candidateCount()
                + ", sightChecks=" + tacticalController.tracker().sightChecks(), ChatFormatting.GRAY));

        GuardWeaponAmmunition.LoadoutSummary ammunition =
                GuardWeaponAmmunition.captureSummary(
                        guard,
                        GuardAmmoAccess.getTaCZInventoryHandler(guard));
        var safety = snapshot.safety();
        NaturalGuardLoadoutDiagnostics.Snapshot naturalLoadout =
                NaturalGuardLoadoutDiagnostics.capture(guard);
        ArmoryResupplySettings armorySettings =
                ModConfigs.GUARD_GUNNERS.armoryResupply.snapshot(
                        ModConfigs.GUARD_GUNNERS.enabled.get());
        ArmoryResupplyDiagnostics.Snapshot armoryResupply =
                ArmoryResupplyDiagnostics.capture(guard, armorySettings);
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
        GuardOrders effectiveOrders = GuardRoleResolver.effectiveOrders(guard);
        var roleId = GuardRoleAccess.getRoleId(guard);
        GuardRoleDefinition role = GuardRoleResolver.definition(guard);
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
                "Guard role",
                "assigned=" + (roleId == null ? "none" : roleId)
                        + ", state=" + (roleId == null
                                ? "roleless"
                                : role == null ? "missing" : "loaded")
                        + ", effective={engagement=" + effectiveOrders.engagement()
                        + ", tactics=" + effectiveOrders.tactics()
                        + ", explosives=" + effectiveOrders.explosives()
                        + ", ammoPickup=" + effectiveOrders.ammoPickup()
                        + ", ammoSharing=" + effectiveOrders.ammoSharing()
                        + ", movement=" + effectiveOrders.movement()
                        + ", maximumRange=" + effectiveOrders.maximumRange() + "}",
                roleId != null && role == null ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        send(source, line(
                "Automatic duty",
                "owner=" + (dutyGoal == null ? "unavailable" : dutyGoal.activeOwner())
                        + ", " + automaticDutyDetails(automaticDuty),
                automaticDuty != null
                                && (automaticDuty.status()
                                                == GuardAutomaticDutyMovementController
                                                        .MovementStatus.TRAVELLING
                                        || automaticDuty.status()
                                                == GuardAutomaticDutyMovementController
                                                        .MovementStatus.HOLDING_POST)
                        ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY));
        send(source, line(
                "Squad patrol route",
                patrolRouteDetails(patrolRoute),
                patrolRoute != null
                                && (patrolRoute.status()
                                                == GuardPatrolRouteMovementController
                                                        .MovementStatus.TRAVELLING
                                        || patrolRoute.status()
                                                == GuardPatrolRouteMovementController
                                                        .MovementStatus.DWELLING)
                        ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY));
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

        send(source, line(
                "Last shot safety",
                safety.status().name()
                        + (safety.entityId() < 0 ? "" : ", entity=#" + safety.entityId())
                        + (safety.block() == null ? "" : ", block=" + safety.block().toShortString())
                        + (safety.envelope() == null ? "" : ", tick=" + safety.envelope().aim().gameTick()
                                + ", yaw=" + degrees(safety.envelope().aim().yaw())
                                + ", pitch=" + degrees(safety.envelope().aim().pitch())),
                safety.clear() ? ChatFormatting.GREEN : ChatFormatting.GRAY));

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
                "Primary ammo",
                ammunitionDetails(
                        ammunition.primary(),
                        ammunition.activeRole()
                                == GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        nativeState),
                ammunitionColor(ammunition.primary())));
        send(source, line(
                "Sidearm ammo",
                ammunitionDetails(
                        ammunition.sidearm(),
                        ammunition.activeRole()
                                == GuardWeaponAmmunition.ActiveRole.SIDEARM,
                        nativeState),
                ammunitionColor(ammunition.sidearm())));
        send(source, line(
                "Armory resupply",
                armoryResupplyDetails(armoryResupply),
                armoryResupply.runtimeStatus()
                                == ArmoryResupplyDiagnostics.RuntimeStatus.REGISTERED
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW));
        send(source, line(
                "Armory policy",
                armoryPolicyDetails(armorySettings),
                armorySettings.validReserveThresholds()
                        ? ChatFormatting.GRAY
                        : ChatFormatting.RED));
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
            boolean active,
            NativeGunController.Diagnostics nativeState) {
        String activeState = ", active=" + active;
        if (!ammunition.isReady()) {
            return "status=" + ammunition.status()
                    + ", gun=" + (ammunition.gunId() == null ? "none" : ammunition.gunId())
                    + activeState
                    + activeNativeDetails(active, nativeState);
        }
        String reserve = ammunition.infiniteReserve()
                ? "infinite"
                : Long.toString(ammunition.reserveUnits());
        return "status=" + ammunition.status()
                + ", gun=" + ammunition.gunId()
                + activeState
                + ", feed=" + ammunition.feedType()
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
                + activeNativeDetails(active, nativeState);
    }

    private static String activeNativeDetails(
            boolean active,
            NativeGunController.Diagnostics nativeState) {
        if (!active) {
            return "";
        }
        return ", inventoryFed=" + nativeState.inventoryFed()
                + ", readiness=" + nativeState.readiness()
                + ", offensiveMovementBlocked=" + !nativeState.readiness().permitsOffensiveMovement()
                + ", dryConfirmed=" + nativeState.dryConfirmed()
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

    private static String armoryResupplyDetails(
            ArmoryResupplyDiagnostics.Snapshot snapshot) {
        String details = "runtime=" + snapshot.runtimeStatus()
                + ", state=" + snapshot.state()
                + ", outcome=" + nullableName(snapshot.lastOutcome())
                + ", gate=" + snapshot.gateStatus()
                + ", needEvaluation=" + snapshot.needEvaluationStatus()
                + ", need=" + nullableName(snapshot.needStatus());
        if (snapshot.weaponRole() != null && snapshot.gunId() != null) {
            details += ", role=" + snapshot.weaponRole()
                    + ", gun=" + snapshot.gunId()
                    + ", urgency=" + nullableName(snapshot.urgency())
                    + ", reserve=" + snapshot.observedReserveUnits()
                    + "/" + snapshot.targetReserveUnits()
                    + ", requested=" + snapshot.requestedUnits();
        }
        if (snapshot.armoryPosition() != null) {
            details += ", armory=" + snapshot.armoryPosition().toShortString()
                    + ", slot=" + snapshot.sourceSlot();
        }
        return details
                + ", candidates=" + snapshot.candidateInspections()
                + ", paths=" + snapshot.pathAttempts()
                + ", lastTransferred=" + snapshot.lastTransferredUnits()
                + ", nextAction="
                + (snapshot.nextActionGameTick() < 0L
                        ? "n/a"
                        : snapshot.nextActionGameTick());
    }

    private static String armoryPolicyDetails(ArmoryResupplySettings settings) {
        return "enabled=" + settings.enabled()
                + ", interval=" + settings.checkIntervalTicks()
                + ", radius=" + decimal(settings.searchRadius())
                + ", candidateCap=" + settings.maximumCandidateArmories()
                + ", reserveMagazines=" + settings.lowReserveMagazines()
                + "->" + settings.targetReserveMagazines()
                + ", thresholdsValid=" + settings.validReserveThresholds()
                + ", speed=" + decimal(settings.movementSpeed())
                + ", requestLifetime=" + settings.requestLifetimeTicks()
                + ", reservationLifetime=" + settings.reservationLifetimeTicks()
                + ", successCooldown=" + settings.successCooldownTicks()
                + ", noSupplyRetry=" + settings.noSupplyRetryTicks()
                + ", unreachableRetry=" + settings.unreachableRetryTicks();
    }

    private static String automaticDutyDetails(
            @Nullable GuardAutomaticDutyMovementController.RuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return "runtime=unavailable";
        }
        String assignment = snapshot.slotId() == null
                ? "none"
                : snapshot.slotId().toString();
        String destination = snapshot.destination() == null
                ? "none"
                : snapshot.destination().toShortString();
        return "status=" + snapshot.status()
                + ", gate=" + snapshot.gateStatus()
                + ", slot=" + assignment
                + ", destination=" + destination
                + ", loaded=" + snapshot.destinationLoaded()
                + ", arrived=" + snapshot.atDutyPosition()
                + ", failures=" + snapshot.consecutivePathFailures()
                + ", paths=" + snapshot.pathAttempts()
                + ", nextPath=" + snapshot.nextPathAttemptGameTime();
    }

    private static String patrolRouteDetails(
            @Nullable GuardPatrolRouteMovementController.RuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return "runtime=unavailable";
        }
        String squad = snapshot.squadId() == null
                ? "none"
                : snapshot.squadId().toString();
        String destination = snapshot.destination() == null
                ? "none"
                : snapshot.destination().toShortString();
        return "status=" + snapshot.status()
                + ", gate=" + snapshot.gateStatus()
                + ", squad=" + squad
                + ", revision=" + snapshot.routeRevision()
                + ", waypoint=" + (snapshot.waypointIndex() < 0
                        ? "none"
                        : (snapshot.waypointIndex() + 1) + "/" + snapshot.waypointCount())
                + ", destination=" + destination
                + ", loaded=" + snapshot.destinationLoaded()
                + ", valid=" + snapshot.destinationValid()
                + ", arrived=" + snapshot.atWaypoint()
                + ", failures=" + snapshot.consecutivePathFailures()
                + ", skipped=" + snapshot.skippedWaypoints()
                + ", paths=" + snapshot.pathAttempts()
                + ", nextPath=" + snapshot.nextPathAttemptGameTime()
                + ", policy={enabled=" + snapshot.executionEnabled()
                + ", speed=" + decimal(snapshot.movementSpeed())
                + ", dwell=" + snapshot.waypointDwellTicks()
                + ", failures=" + snapshot.maximumFailuresPerWaypoint() + "}";
    }
}
