package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandRuntime;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Stateful, bounded navigation adapter shared by command and gun-combat goals.
 * It never changes Guard Villagers' permanent patrol or follow state.
 */
public final class SquadCommandMovementController {
    static final double ARRIVAL_HORIZONTAL_DISTANCE = 1.5D;
    static final double ARRIVAL_VERTICAL_DISTANCE = 2.0D;
    static final double HOLD_HORIZONTAL_DISTANCE = 0.85D;
    static final double HOLD_VERTICAL_DISTANCE = 1.5D;
    static final double DEFEND_BOUNDARY_MARGIN = 1.0D;

    private final Guard guard;
    private final PathNavigation navigation;
    private final VillagerGuardGunnersConfig config;

    private int observedRevision;
    private long nextPathRecalculationGameTick = Long.MIN_VALUE;
    private MovementOutcome lastOutcome = MovementOutcome.NOT_HANDLED;
    private boolean positionalCommandActive;

    public SquadCommandMovementController(Guard guard) {
        this.guard = guard;
        this.navigation = guard.getNavigation();
        this.config = ModConfigs.GUARD_GUNNERS;
        this.observedRevision = GuardSquadCommandAccess.getRevision(guard);
    }

    /**
     * Validates the current command and optionally applies Focus Target. A
     * revision change stops only navigation owned by the previous command.
     */
    @Nullable
    public SquadCommand synchronize(boolean synchronizeFocusTarget) {
        int revision = GuardSquadCommandAccess.getRevision(guard);
        boolean revisionChanged = revision != observedRevision;
        if (revision != observedRevision) {
            observedRevision = revision;
            if (positionalCommandActive) {
                navigation.stop();
            }
            lastOutcome = MovementOutcome.NOT_HANDLED;
            nextPathRecalculationGameTick = guard.level().getGameTime();
        }

        SquadCommand command = SquadCommandRuntime.activeCommand(guard);
        if (command != null
                && synchronizeFocusTarget
                && command.type() == SquadCommandType.FOCUS_TARGET
                && SquadCommandRuntime.synchronizeFocusTarget(guard, command) == null) {
            positionalCommandActive = false;
            return null;
        }
        boolean nextPositional = command != null && command.type().requiresPosition();
        if (nextPositional && revisionChanged && !positionalCommandActive) {
            navigation.stop();
        } else if (positionalCommandActive && !nextPositional) {
            navigation.stop();
        }
        positionalCommandActive = nextPositional;
        return command;
    }

    /**
     * Synchronizes Focus for conventional guards without adding a held-weapon
     * inspection to the overwhelmingly common no-command and positional paths.
     */
    @Nullable
    public SquadCommand synchronizeSharedFocusTarget() {
        SquadCommand command = synchronize(false);
        if (command == null
                || command.type() != SquadCommandType.FOCUS_TARGET
                || NativeGunController.isGun(guard.getMainHandItem())) {
            return command;
        }
        if (SquadCommandRuntime.synchronizeFocusTarget(guard, command) == null) {
            positionalCommandActive = false;
            return null;
        }
        return command;
    }

    public boolean takePathSlot(long gameTime) {
        if (gameTime < nextPathRecalculationGameTick) {
            return false;
        }
        nextPathRecalculationGameTick = gameTime
                + config.performance.pathRecalculationIntervalTicks.get();
        return true;
    }

    /** Applies a positional command before ordinary tactical movement. */
    public MovementOutcome apply(
            SquadCommand command,
            boolean outsideCombat,
            boolean pathSlotDue,
            double speed) {
        BlockPos position = command.position();
        if (position == null) {
            return finish(MovementOutcome.NOT_HANDLED);
        }

        return switch (command.type()) {
            case RALLY -> {
                if (atPosition(
                        guard.getX(),
                        guard.getY(),
                        guard.getZ(),
                        position,
                        ARRIVAL_HORIZONTAL_DISTANCE,
                        ARRIVAL_VERTICAL_DISTANCE)) {
                    navigation.stop();
                    SquadCommandRuntime.clearIfCurrent(guard, command);
                    yield finish(MovementOutcome.COMPLETED);
                }
                moveTo(position, pathSlotDue, speed);
                yield finish(MovementOutcome.MOVING_TO_COMMAND);
            }
            case DEFEND -> {
                if (outsideDefendBoundary(
                        guard.getX(), guard.getZ(), position, command.radius())) {
                    moveTo(position, pathSlotDue, speed);
                    yield finish(MovementOutcome.MOVING_TO_COMMAND);
                }
                yield finish(outsideCombat
                        ? MovementOutcome.HOLDING_POSITION
                        : MovementOutcome.NOT_HANDLED);
            }
            case HOLD -> {
                if (atPosition(
                        guard.getX(),
                        guard.getY(),
                        guard.getZ(),
                        position,
                        HOLD_HORIZONTAL_DISTANCE,
                        HOLD_VERTICAL_DISTANCE)) {
                    yield finish(MovementOutcome.HOLDING_POSITION);
                }
                moveTo(position, pathSlotDue, speed);
                yield finish(MovementOutcome.MOVING_TO_COMMAND);
            }
            case FALL_BACK -> {
                if (atPosition(
                        guard.getX(),
                        guard.getY(),
                        guard.getZ(),
                        position,
                        ARRIVAL_HORIZONTAL_DISTANCE,
                        ARRIVAL_VERTICAL_DISTANCE)) {
                    yield finish(MovementOutcome.HOLDING_POSITION);
                }
                moveTo(position, pathSlotDue, speed);
                yield finish(MovementOutcome.MOVING_TO_COMMAND);
            }
            case FOCUS_TARGET, RESUME_ORDERS -> finish(MovementOutcome.NOT_HANDLED);
        };
    }

    /**
     * Preserves ordinary target approach for Focus/no command and clamps a
     * Defend approach destination inside its configured boundary.
     */
    public void moveTowardCombatTarget(
            @Nullable SquadCommand command,
            LivingEntity target,
            double speed) {
        if (command == null || command.type() != SquadCommandType.DEFEND) {
            navigation.moveTo(target, speed);
            return;
        }
        Vec3 destination = constrainToDefendBoundary(
                target.getX(),
                target.getY(),
                target.getZ(),
                command.position(),
                command.radius());
        BlockPos destinationBlock = BlockPos.containing(destination);
        if (isLoaded(destinationBlock)) {
            navigation.moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    speed);
        }
    }

    public void stop() {
        navigation.stop();
        lastOutcome = MovementOutcome.NOT_HANDLED;
    }

    private void moveTo(BlockPos position, boolean pathSlotDue, double speed) {
        if (pathSlotDue && isLoaded(position)) {
            navigation.moveTo(
                    position.getX() + 0.5D,
                    position.getY(),
                    position.getZ() + 0.5D,
                    speed);
        }
    }

    private boolean isLoaded(BlockPos position) {
        return guard.level() instanceof ServerLevel level
                && level.hasChunk(
                        SectionPos.blockToSectionCoord(position.getX()),
                        SectionPos.blockToSectionCoord(position.getZ()));
    }

    private MovementOutcome finish(MovementOutcome outcome) {
        if (outcome == MovementOutcome.HOLDING_POSITION
                && lastOutcome != MovementOutcome.HOLDING_POSITION) {
            navigation.stop();
        } else if (outcome == MovementOutcome.NOT_HANDLED
                && lastOutcome == MovementOutcome.MOVING_TO_COMMAND) {
            // A Defend guard has crossed back inside its boundary. Release the
            // return-to-center path before ordinary bounded tactics resume.
            navigation.stop();
        }
        lastOutcome = outcome;
        return outcome;
    }

    static boolean atPosition(
            double x,
            double y,
            double z,
            BlockPos position,
            double horizontalTolerance,
            double verticalTolerance) {
        double deltaX = x - (position.getX() + 0.5D);
        double deltaZ = z - (position.getZ() + 0.5D);
        double horizontalLimit = Math.max(0.0D, horizontalTolerance);
        return deltaX * deltaX + deltaZ * deltaZ
                        <= horizontalLimit * horizontalLimit
                && Math.abs(y - position.getY())
                        <= Math.max(0.0D, verticalTolerance);
    }

    static boolean outsideDefendBoundary(
            double x,
            double z,
            BlockPos center,
            double radius) {
        double allowedRadius = defendMovementRadius(radius);
        double deltaX = x - (center.getX() + 0.5D);
        double deltaZ = z - (center.getZ() + 0.5D);
        return deltaX * deltaX + deltaZ * deltaZ
                > allowedRadius * allowedRadius;
    }

    static Vec3 constrainToDefendBoundary(
            double x,
            double y,
            double z,
            BlockPos center,
            double radius) {
        double centerX = center.getX() + 0.5D;
        double centerZ = center.getZ() + 0.5D;
        double deltaX = x - centerX;
        double deltaZ = z - centerZ;
        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        double allowedRadius = defendMovementRadius(radius);
        if (distanceSquared <= allowedRadius * allowedRadius) {
            return new Vec3(x, y, z);
        }
        if (!Double.isFinite(distanceSquared) || distanceSquared <= 0.0D) {
            return new Vec3(centerX, center.getY(), centerZ);
        }
        double scale = allowedRadius / Math.sqrt(distanceSquared);
        return new Vec3(
                centerX + deltaX * scale,
                center.getY(),
                centerZ + deltaZ * scale);
    }

    private static double defendMovementRadius(double radius) {
        if (!Double.isFinite(radius)) {
            return 0.0D;
        }
        return Math.max(0.5D, radius - DEFEND_BOUNDARY_MARGIN);
    }

    public enum MovementOutcome {
        NOT_HANDLED,
        MOVING_TO_COMMAND,
        HOLDING_POSITION,
        COMPLETED
    }
}
