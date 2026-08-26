package com.gamergaming.villagerguardgunners.handlers;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

import com.gamergaming.villagerguardgunners.api.MovementState;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.util.AIUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

public class MovementHandler {
    private final PathNavigation navigation;
    private final Guard guard;

    private final RandomSource random = RandomSource.create();

    private static final double DISTANCE_TO_MAINTAIN_FROM_TARGET = Math.pow(getMinDistanceToMaintainFromTarget(), 2);
    private static final double MOVE_SPEED = getMoveSpeed();
    private static final int MIN_STATE_DURATION = 40; // ticks

    private MovementState movementState = MovementState.IDLE;
    private int stateTime = 0; // Time spent in the current state

    // Circling variables
    private int circlingTime = 0;
    private boolean strafingClockwise = true;
    private int strafingDuration = 100 + random.nextInt(100);

    public MovementHandler(Guard guard) {
        this.guard = guard;
        this.navigation = guard.getNavigation();
    }

    public void updateMovement(double distanceSq, boolean canSeeTarget) {
        if (guard.getTarget() == null) {
            transitionToState(MovementState.IDLE);
            return;
        }

        stateTime++;

        // Check if line of sight is blocked
        boolean lineOfSightBlocked = !canSeeTarget || isLineOfSightBlocked();

        // State transition logic
        switch (movementState) {
            case APPROACHING -> {
                if (distanceSq <= DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.0 && stateTime >= MIN_STATE_DURATION) {
                    transitionToState(MovementState.HOLDING);
                } else {
                    approachTarget();
                }
            }
            case RETREATING -> {
                if (distanceSq >= DISTANCE_TO_MAINTAIN_FROM_TARGET * 1.3 && stateTime >= MIN_STATE_DURATION) {
                    transitionToState(MovementState.HOLDING);
                } else {
                    retreatFromTarget();
                }
            }
            case CIRCLING -> {
                if ((distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2 || distanceSq < DISTANCE_TO_MAINTAIN_FROM_TARGET) && stateTime >= MIN_STATE_DURATION) {
                    if (distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2) {
                        transitionToState(MovementState.APPROACHING);
                    } else {
                        transitionToState(MovementState.RETREATING);
                    }
                } else if (lineOfSightBlocked) {
                    attemptToRegainLineOfSight();
                } else {
                    performCircling();
                }
            }
            case HOLDING -> {
                if ((distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2 || distanceSq < DISTANCE_TO_MAINTAIN_FROM_TARGET) && stateTime >= MIN_STATE_DURATION) {
                    if (distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2) {
                        transitionToState(MovementState.APPROACHING);
                    } else {
                        transitionToState(MovementState.RETREATING);
                    }
                } else if (lineOfSightBlocked) {
                    transitionToState(MovementState.CIRCLING);
                }
                // Hold position
            }
            case IDLE -> {
                if (stateTime >= MIN_STATE_DURATION && guard.getTarget() != null) {
                    transitionToState(MovementState.APPROACHING);
                }
                // Remain idle
            }
        }
    }

    private void transitionToState(MovementState newState) {
        movementState = newState;
        stateTime = 0; // Reset state time

        // Stop navigation when changing state
        navigation.stop();

        // Reset strafing variables if needed
        circlingTime = 0;
        strafingClockwise = random.nextBoolean();
    }

    private void approachTarget() {
        if (guard.getTarget() != null && navigation.isDone() || navigation.getPath() == null) {
            Vec3 direction = guard.getTarget().position().subtract(guard.position()).normalize(); // Direction towards target
            Vec3 newPosition = guard.position().add(direction.scale(2.0)); // Move 2 units towards target
            moveToPosition(newPosition);
        }
    }

    private void retreatFromTarget() {
        if (guard.getTarget() != null && navigation.isDone() || navigation.getPath() == null) {
            Vec3 direction = guard.position().subtract(guard.getTarget().position()).normalize(); // Direction away from target
            Vec3 newPosition = guard.position().add(direction.scale(5.0)); // Move 5 units away
            moveToPosition(newPosition);
        }
    }

    private void performCircling() {
        circlingTime++;

        if (circlingTime >= strafingDuration) { // Change direction periodically
            circlingTime = 0;
            strafingClockwise = !strafingClockwise;
            strafingDuration = 100 + random.nextInt(100);
        }

        float strafeSpeed = calculateStrafeSpeed();
        float forwardMovement = calculateForwardMovement();

        guard.getMoveControl().strafe(forwardMovement, strafingClockwise ? strafeSpeed : -strafeSpeed);
    }

    private float calculateStrafeSpeed() {
        float baseStrafeSpeed = 0.3F;
        float fluctuation = 0.2F * (float) Math.sin(circlingTime / 30.0F);
        return baseStrafeSpeed + fluctuation;
    }

    private float calculateForwardMovement() {
        float movement = 0.0F;
        if (random.nextInt(100) < 5) {
            movement = random.nextBoolean() ? 0.2F : -0.2F;
        }
        return movement;
    }

    private void moveToPosition(Vec3 position) {
        if (!isPositionSafe(new Vec3i((int) position.x(), (int) position.y(), (int) position.z()))) {
            // Attempt to find a nearby safe position
            position = findNearestSafePosition(position);
        }

        // Check if the path is clear
        if (isPathObstructed(position)) {
            // Recalculate or find an alternative path
            position = findAlternativePosition(position);
        }

        Path path = navigation.getPath();
        if (navigation.isDone() || path == null || path.getEndNode() == null || path.getEndNode().asVec3().distanceToSqr(position) > 1.0D) {
            navigation.moveTo(position.x, position.y, position.z, MOVE_SPEED);
        }
    }

    private boolean isPathObstructed(Vec3 position) {
        Path pathToPosition = navigation.createPath(position.x, position.y, position.z, 0);
        return pathToPosition == null || !pathToPosition.canReach();
    }

    private Vec3 findAlternativePosition(Vec3 originalPosition) {
        // Try to find nearby positions that are reachable
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 1.0 + random.nextDouble() * 2.0;
            Vec3 candidatePos = guard.position().add(
                distance * Math.cos(angle),
                0,
                distance * Math.sin(angle)
            );

            if (isPositionSafe(new Vec3i((int) candidatePos.x(), (int) candidatePos.y(), (int) candidatePos.z()))
                && !isPathObstructed(candidatePos)) {
                return candidatePos;
            }
        }
        // If no alternative found, stay in current position
        return guard.position();
    }

    private boolean isLineOfSightBlocked() {
        if (guard.getTarget() == null) {
            return false;
        }
        return AIUtils.isLineOfSightBlocked(guard, guard.getTarget());
    }

    private void attemptToRegainLineOfSight() {
        if (guard.getTarget() != null && navigation.isDone() || navigation.getPath() == null) {
            // Attempt to find a nearby position with a clear line of sight
            Vec3 targetPos = guard.getTarget().position();
            Vec3 guardPos = guard.position();

            // Sample positions around the guard to find a spot with clear line of sight
            for (int i = 0; i < 10; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = 2.0 + random.nextDouble() * 3.0; // Between 2 and 5 blocks away
                Vec3 candidatePos = guardPos.add(
                    distance * Math.cos(angle),
                    0,
                    distance * Math.sin(angle)
                );
                Vec3i candidatePosVec3i = new Vec3i((int) candidatePos.x(), (int) candidatePos.y(), (int) candidatePos.z());
                // Check if the position is valid and has line of sight
                if (isPositionValid(candidatePosVec3i) && AIUtils.hasLineOfSightFromPosition(guard.level(), candidatePos, targetPos, guard)) {
                    navigation.moveTo(candidatePos.x, candidatePos.y, candidatePos.z, MOVE_SPEED);
                    return;
                }
            }

            // If no position found, default to moving towards the target
            approachTarget();
        }
    }

    private boolean isPositionSafe(Vec3i position) {
        BlockPos blockPos = new BlockPos(position);
        return !isHazardous(blockPos) && !isHazardous(blockPos.below());
    }

    private boolean isHazardous(BlockPos pos) {
        // Check for lava, fire, cactus, etc.
        var blockState = guard.level().getBlockState(pos);
        return blockState.is(Blocks.LAVA) || blockState.is(Blocks.FIRE) || blockState.is(Blocks.CACTUS);
    }

    private Vec3 findNearestSafePosition(Vec3 position) {
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 1.0 + random.nextDouble() * 3.0; // Between 1 and 4 blocks
            Vec3 candidatePos = position.add(
                distance * Math.cos(angle),
                0,
                distance * Math.sin(angle)
            );

            if (isPositionSafe(new Vec3i((int) candidatePos.x(), (int) candidatePos.y(), (int) candidatePos.z()))) {
                return candidatePos;
            }
        }
        // If no safe position found, default to current position
        return guard.position();
    }

    private boolean isPositionValid(Vec3i position) {
        // Check if position is navigable and safe
        BlockPos blockPos = new BlockPos(position);
        return guard.level().isEmptyBlock(blockPos)
            && guard.level().isEmptyBlock(blockPos.above())
            && isPositionSafe(position);
    }

    private static double getMinDistanceToMaintainFromTarget() {
        return ModConfigs.GUARD_GUNNER.attackSettings.minDistanceToMaintainFromTarget.get();
    }

    private static double getMoveSpeed() {
        return ModConfigs.GUARD_GUNNER.movementSettings.movementSpeed.get();
    }
}