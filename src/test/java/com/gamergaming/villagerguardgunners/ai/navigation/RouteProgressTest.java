package com.gamergaming.villagerguardgunners.ai.navigation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class RouteProgressTest {
    @Test void detoursAndNodeAdvancementRemainProgressEvenWhenReturningNearTheStart() {
        var tracker = new RouteProgress();
        var route = List.of(new Vec3(0, 0, 0), new Vec3(6, 0, 0), new Vec3(6, 1, 6), new Vec3(0, 1, 1));
        tracker.start(route, 0, Vec3.ZERO, 0);
        assertEquals(RouteProgress.Status.MOVING, tracker.sample(1, new Vec3(5, 0, 0), 30, 40, false, false));
        assertEquals(RouteProgress.Status.MOVING, tracker.sample(3, new Vec3(.1, 1, 1), 60, 40, false, false));
        assertEquals(60, tracker.lastProgressTick());
    }

    @Test void oscillationDoesNotContinuallyResetTheClock() {
        var tracker = new RouteProgress();
        tracker.start(List.of(new Vec3(10, 0, 0)), 0, Vec3.ZERO, 0);
        tracker.sample(0, new Vec3(.5, 0, 0), 10, 40, false, false);
        tracker.sample(0, Vec3.ZERO, 30, 40, false, false);
        assertEquals(RouteProgress.Status.STALLED, tracker.sample(0, new Vec3(.5, 0, 0), 50, 40, false, false));
    }

    @Test void temporaryCongestionHasGraceButCannotWaitForever() {
        var tracker = new RouteProgress();
        tracker.start(List.of(new Vec3(10, 0, 0)), 0, Vec3.ZERO, 0);
        assertEquals(RouteProgress.Status.WAITING_FOR_OBSTRUCTION, tracker.sample(0, Vec3.ZERO, 40, 40, true, false));
        assertEquals(RouteProgress.Status.MOVING, tracker.sample(0, Vec3.ZERO, 41, 40, false, false));
        assertEquals(RouteProgress.Status.STALLED, tracker.sample(0, Vec3.ZERO, 100, 40, false, false));
        assertEquals(RouteProgress.Status.MOVING, tracker.sample(0, new Vec3(1, 0, 0), 101, 40, false, false));
    }

    @Test void fractionalStepsAndSlowCumulativeMovementAreCounted() {
        var tracker = new RouteProgress();
        tracker.start(List.of(new Vec3(1, .5, 0), new Vec3(2, 1, 0)), 0, new Vec3(0, .5, 0), 0);
        tracker.sample(0, new Vec3(.1, .5, 0), 10, 40, false, false);
        tracker.sample(0, new Vec3(.2, .5, 0), 20, 40, false, false);
        assertEquals(RouteProgress.Status.MOVING, tracker.sample(0, new Vec3(.3, .5, 0), 39, 40, false, false));
        assertEquals(39, tracker.lastProgressTick());
        assertEquals(Math.sqrt(1.25) + .7, tracker.remaining(0, new Vec3(.3, .5, 0)), 1e-8);
    }
}
