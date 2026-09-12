package com.gamergaming.villagerguardgunners.ai.tactics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ThreatMemoryTest {
    private static ThreatMemory.Observation observation(UUID id, long tick) {
        return new ThreatMemory.Observation(id, "overworld", new Vec3(3, 1, 4), new Vec3(.2, 0, 0), tick, tick, false, false, false);
    }
    @Test void rememberedPositionDoesNotIntegrateVelocityAndExpiresExactly() {
        ThreatMemory memory = new ThreatMemory(); UUID id = UUID.randomUUID();
        memory.observe(observation(id, 100)); memory.expire(139, "overworld");
        assertEquals(new Vec3(3, 1, 4), memory.get(id).position());
        assertEquals(.025, memory.get(id).confidence(139), 1e-9);
        memory.expire(140, "overworld"); assertNull(memory.get(id));
    }
    @Test void dimensionChangeAndTimeRollbackDiscardMemory() {
        ThreatMemory memory = new ThreatMemory(); UUID id = UUID.randomUUID();
        memory.observe(observation(id, 100)); memory.expire(101, "nether"); assertEquals(0, memory.size());
        memory.observe(observation(id, 100)); memory.expire(99, "overworld"); assertEquals(0, memory.size());
    }
    @Test void capacityIsHardAndCurrentThreatCanReplaceOldEvidence() {
        ThreatMemory memory = new ThreatMemory(); UUID oldest = UUID.randomUUID();
        memory.observe(observation(oldest, 1));
        for (int i = 1; i < ThreatMemory.CAPACITY; i++) assertTrue(memory.observe(observation(UUID.randomUUID(), i + 1)));
        UUID current = UUID.randomUUID(); assertFalse(memory.observe(observation(current, 30)));
        assertTrue(memory.observe(observation(current, 30), true));
        assertEquals(ThreatMemory.CAPACITY, memory.size()); assertNull(memory.get(oldest)); assertNotNull(memory.get(current));
    }
}
