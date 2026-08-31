package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Constant-time validation and target resolution for one guard's temporary
 * squad command. This deliberately performs no entity search or chunk load.
 */
public final class SquadCommandRuntime {
    public static final double MAXIMUM_RUNTIME_COMMAND_DISTANCE = 256.0D;

    private static final double MAXIMUM_RUNTIME_COMMAND_DISTANCE_SQUARED =
            MAXIMUM_RUNTIME_COMMAND_DISTANCE * MAXIMUM_RUNTIME_COMMAND_DISTANCE;

    private SquadCommandRuntime() {
    }

    /**
     * Returns the current command after clearing expired, cross-dimension, or
     * otherwise unsafe records. Position validation never loads a chunk.
     */
    @Nullable
    public static SquadCommand activeCommand(Guard guard) {
        SquadCommand command = GuardSquadCommandAccess.getCommand(guard);
        if (command == null) {
            return null;
        }
        if (!(guard.level() instanceof ServerLevel level)
                || GuardSquadAccess.getMembership(guard) == null
                || command.schemaVersion() != SquadCommand.CURRENT_SCHEMA_VERSION
                || command.isExpired(guard.level().getGameTime())
                || !command.dimension().equals(guard.level().dimension().location())) {
            clearIfCurrent(guard, command);
            return null;
        }

        BlockPos position = command.position();
        if (position != null
                && (!level.isInWorldBounds(position)
                        || !level.getWorldBorder().isWithinBounds(position)
                        || !withinRuntimeDistance(guard, position))) {
            clearIfCurrent(guard, command);
            return null;
        }
        return command;
    }

    /**
     * Resolves and installs a loaded Focus Target only after the existing
     * rules-of-engagement and ally classifier permit it.
     */
    @Nullable
    public static LivingEntity synchronizeFocusTarget(
            Guard guard,
            SquadCommand command) {
        if (command.type() != SquadCommandType.FOCUS_TARGET
                || command.targetId() == null
                || !(guard.level() instanceof ServerLevel level)) {
            clearIfCurrent(guard, command);
            return null;
        }

        Entity resolved = level.getEntity(command.targetId());
        if (!(resolved instanceof LivingEntity candidate)
                || candidate == guard
                || !candidate.isAlive()
                || candidate.isRemoved()
                || candidate.level() != guard.level()
                || !withinRuntimeDistance(guard, candidate)) {
            clearIfCurrent(guard, command);
            return null;
        }

        GuardRulesOfEngagement.EngagementReason reason =
                GuardRulesOfEngagement.classify(guard, candidate);
        if (!reason.mayEngage()) {
            clearIfCurrent(guard, command);
            return null;
        }

        if (guard.getTarget() != candidate) {
            guard.setTarget(candidate);
        }
        return candidate;
    }

    public static boolean clearIfCurrent(Guard guard, SquadCommand expected) {
        if (!expected.equals(GuardSquadCommandAccess.getCommand(guard))) {
            return false;
        }
        releaseOwnedFocusTarget(guard, expected);
        return GuardSquadCommandAccess.setCommand(guard, null);
    }

    /**
     * Replaces a command while releasing only a Focus target installed by the
     * previous command. An unrelated self-defense target is preserved.
     */
    public static boolean replaceCommand(
            Guard guard,
            @Nullable SquadCommand replacement) {
        SquadCommand previous = GuardSquadCommandAccess.getCommand(guard);
        if (Objects.equals(previous, replacement)) {
            return true;
        }
        if (previous != null
                && (replacement == null
                        || previous.type() != SquadCommandType.FOCUS_TARGET
                        || replacement.type() != SquadCommandType.FOCUS_TARGET
                        || !Objects.equals(previous.targetId(), replacement.targetId()))) {
            releaseOwnedFocusTarget(guard, previous);
        }
        return GuardSquadCommandAccess.setCommand(guard, replacement);
    }

    private static boolean withinRuntimeDistance(Guard guard, BlockPos position) {
        double distanceSquared = guard.distanceToSqr(
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D);
        return Double.isFinite(distanceSquared)
                && distanceSquared <= MAXIMUM_RUNTIME_COMMAND_DISTANCE_SQUARED;
    }

    private static boolean withinRuntimeDistance(Guard guard, LivingEntity target) {
        double distanceSquared = guard.distanceToSqr(target);
        return Double.isFinite(distanceSquared)
                && distanceSquared <= MAXIMUM_RUNTIME_COMMAND_DISTANCE_SQUARED;
    }

    private static void releaseOwnedFocusTarget(Guard guard, SquadCommand command) {
        LivingEntity currentTarget = guard.getTarget();
        if (command.type() == SquadCommandType.FOCUS_TARGET
                && command.targetId() != null
                && currentTarget != null
                && command.targetId().equals(currentTarget.getUUID())) {
            guard.setTarget(null);
        }
    }
}
