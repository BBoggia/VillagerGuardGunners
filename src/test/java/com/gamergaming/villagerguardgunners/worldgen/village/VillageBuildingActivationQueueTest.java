package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageBuildingActivationQueueTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    @Test
    void retriesWaitForTheirDeadlineAndChunkEventsDoNotResetTheAttempt() {
        var queue = new VillageBuildingActivationQueue();
        UUID id = UUID.randomUUID();
        queue.schedule(DIMENSION, id, 100);
        assertTrue(queue.takeDue(100).isEmpty());
        var first = queue.takeDue(101);
        assertEquals(1, first.size());
        assertEquals(0, first.get(0).attempt());
        // The active reservation also covers execution outside the queue.
        queue.schedule(DIMENSION, id, 101);
        queue.retry(DIMENSION, id, 1, 101, 40);
        queue.schedule(DIMENSION, id, 110);
        assertTrue(queue.takeDue(140).isEmpty());
        var retried = queue.takeDue(141);
        assertEquals(1, retried.size());
        assertEquals(1, retried.get(0).attempt());
        assertTrue(queue.takeDue(200).isEmpty());
        queue.release(id);
        queue.schedule(DIMENSION, id, 200);
        assertEquals(0, queue.takeDue(201).get(0).attempt());
    }

    @Test
    void simultaneousActivationsAreBudgetedAndEventuallyAllProcessed() {
        var queue = new VillageBuildingActivationQueue();
        for (int index = 0; index < 100; index++) {
            queue.schedule(DIMENSION, new UUID(0, index), 0);
        }
        int processed = 0;
        for (int tick = 1; tick <= 7; tick++) {
            var due = queue.takeDue(tick);
            assertTrue(due.size() <= VillageBuildingActivationQueue.MAXIMUM_ATTEMPTS_PER_TICK);
            for (var attempt : due) {
                assertEquals(new UUID(0, processed++), attempt.buildingId());
                queue.release(attempt.buildingId());
            }
        }
        assertEquals(100, processed);
        assertTrue(queue.takeDue(8).isEmpty());
    }

    @Test
    void retryBackoffRemainsBoundedAndCannotRunInTheSameTick() {
        assertEquals(1, VillageBuildingAnchorLifecycle.retryDelayTicks(0));
        assertEquals(2, VillageBuildingAnchorLifecycle.retryDelayTicks(1));
        assertEquals(4, VillageBuildingAnchorLifecycle.retryDelayTicks(2));
        assertEquals(40, VillageBuildingAnchorLifecycle.retryDelayTicks(10));
        var queue = new VillageBuildingActivationQueue();
        assertThrows(IllegalArgumentException.class,
                () -> queue.retry(DIMENSION, UUID.randomUUID(), 1, 0, 0));
    }
}
