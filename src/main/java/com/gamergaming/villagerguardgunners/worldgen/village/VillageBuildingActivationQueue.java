package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Server-thread queue; active identities stay reserved while an attempt runs. */
final class VillageBuildingActivationQueue {
    static final int MAXIMUM_ATTEMPTS_PER_TICK = 16;
    private final Set<UUID> active = new HashSet<>();
    private final PriorityQueue<Attempt> pending = new PriorityQueue<>(
            Comparator.comparingLong(Attempt::dueTick).thenComparingLong(Attempt::sequence));
    private long sequence;

    void schedule(ResourceLocation dimension, UUID buildingId, long now) {
        if (active.add(buildingId)) {
            enqueue(dimension, buildingId, 0, now + 1);
        }
    }

    void retry(ResourceLocation dimension, UUID buildingId, int attempt, long now, int delay) {
        if (delay < 1) {
            throw new IllegalArgumentException("Activation retries must wait at least one tick");
        }
        if (active.contains(buildingId)) {
            enqueue(dimension, buildingId, attempt, now + delay);
        }
    }

    private void enqueue(ResourceLocation dimension, UUID buildingId, int attempt, long dueTick) {
        pending.add(new Attempt(dimension, buildingId, attempt, dueTick, sequence++));
    }

    List<Attempt> takeDue(long now) {
        if (pending.isEmpty() || pending.peek().dueTick() > now) {
            return List.of();
        }
        List<Attempt> due = new ArrayList<>();
        while (due.size() < MAXIMUM_ATTEMPTS_PER_TICK
                && !pending.isEmpty() && pending.peek().dueTick() <= now) {
            due.add(pending.remove());
        }
        return due;
    }

    void release(UUID buildingId) {
        active.remove(buildingId);
    }

    record Attempt(ResourceLocation dimension, UUID buildingId, int attempt,
                   long dueTick, long sequence) {
    }
}
