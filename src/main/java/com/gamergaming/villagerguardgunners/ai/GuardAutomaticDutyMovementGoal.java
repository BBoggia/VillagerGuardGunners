package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.defense.duty.GuardAutomaticDutyMovementController;
import com.gamergaming.villagerguardgunners.defense.duty.GuardPatrolRouteMovementController;
import com.gamergaming.villagerguardgunners.village.coverage.live.GuardCoverageMovementController;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import tallestegg.guardvillagers.entities.Guard;

/** Lowest VGG movement owner for squad routes, defense posts, and village coverage. */
public final class GuardAutomaticDutyMovementGoal extends Goal {
    private final GuardCoverageMovementController coverageController;
    private final GuardAutomaticDutyMovementController postController;
    private final GuardPatrolRouteMovementController routeController;
    private MovementOwner pendingOwner = MovementOwner.NONE;
    private MovementOwner activeOwner = MovementOwner.NONE;

    public GuardAutomaticDutyMovementGoal(Guard guard) {
        coverageController = new GuardCoverageMovementController(guard);
        postController = new GuardAutomaticDutyMovementController(guard);
        routeController = new GuardPatrolRouteMovementController(guard);
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (routeController.hasExecutableRoute()) {
            pendingOwner = MovementOwner.ROUTE;
            return routeController.canStart();
        }
        pendingOwner = MovementOwner.POST;
        if (postController.canStart()) return true;
        pendingOwner = MovementOwner.COVERAGE;
        return coverageController.canStart();
    }

    @Override
    public boolean canContinueToUse() {
        return switch (activeOwner) {
            case ROUTE -> routeController.shouldContinue();
            case POST -> !routeController.hasExecutableRoute()
                    && postController.shouldContinue();
            case COVERAGE -> !routeController.hasExecutableRoute() && coverageController.shouldContinue();
            case NONE -> false;
        };
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        activeOwner = pendingOwner;
        if (activeOwner == MovementOwner.ROUTE) {
            routeController.start();
        } else if (activeOwner == MovementOwner.POST) {
            postController.start();
        } else if (activeOwner == MovementOwner.COVERAGE) {
            coverageController.start();
        }
    }

    @Override
    public void tick() {
        if (activeOwner == MovementOwner.ROUTE) {
            routeController.tick();
        } else if (activeOwner == MovementOwner.POST) {
            postController.tick();
        } else if (activeOwner == MovementOwner.COVERAGE) {
            coverageController.tick();
        }
    }

    @Override
    public void stop() {
        if (activeOwner == MovementOwner.ROUTE) {
            routeController.stopForHigherPriorityGoal();
        } else if (activeOwner == MovementOwner.POST) {
            postController.stopForHigherPriorityGoal();
        } else if (activeOwner == MovementOwner.COVERAGE) {
            coverageController.stop();
        }
        activeOwner = MovementOwner.NONE;
        pendingOwner = MovementOwner.NONE;
    }

    public GuardAutomaticDutyMovementController.RuntimeSnapshot captureDiagnostics() {
        return postController.snapshot();
    }

    public GuardPatrolRouteMovementController.RuntimeSnapshot capturePatrolDiagnostics() {
        return routeController.snapshot();
    }

    public MovementOwner activeOwner() {
        return activeOwner;
    }

    /** A road-end defense post can lie outside vanilla's village POI boundary. */
    public boolean prioritizesDefensePost() {
        return !routeController.hasExecutableRoute() && postController.shouldContinue();
    }

    public enum MovementOwner {
        NONE,
        COVERAGE,
        POST,
        ROUTE
    }
}
