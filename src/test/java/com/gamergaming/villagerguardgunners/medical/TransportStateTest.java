package com.gamergaming.villagerguardgunners.medical;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportStateTest {
    @Test void absentDataDoesNotCreateATransport() { var state=new TransportState();state.load(new CompoundTag());assertTrue(state.known());assertFalse(state.present()); }
    @Test void saveKeepsOnlyRecoveryHintAndIdentity() {
        var state=new TransportState();var id=UUID.randomUUID();state.mark(id,ResourceLocation.parse("minecraft:overworld"),new Vec3(4.5,70,8.5));
        var root=new CompoundTag();state.save(root);var copy=new TransportState();copy.load(root);
        assertEquals(id,copy.carrier());assertEquals(new Vec3(4.5,70,8.5),copy.safePosition());assertTrue(copy.known());
        assertEquals(6,root.getCompound(TransportState.KEY).getAllKeys().size());
        copy.clear();copy.save(root);assertFalse(root.contains(TransportState.KEY));var clean=new CompoundTag();copy.save(clean);assertFalse(clean.contains(TransportState.KEY));
    }
    @Test void malformedAndFutureHintsArePreserved() {
        var raw=new CompoundTag();raw.putInt("Version",99);var root=new CompoundTag();root.put(TransportState.KEY,raw);
        var state=new TransportState();state.load(root);assertFalse(state.known());state.clear();var saved=new CompoundTag();state.save(saved);assertEquals(root,saved);
        assertThrows(IllegalArgumentException.class,()->state.mark(UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),Vec3.ZERO));
    }
    @Test void nonFiniteAndOutOfWorldHintsAreRejected() {
        var state=new TransportState();
        for(var point:new Vec3[]{new Vec3(Double.NaN,0,0),new Vec3(0,Double.POSITIVE_INFINITY,0),new Vec3(30_000_001,0,0)})
            assertThrows(IllegalArgumentException.class,()->state.mark(UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),point));
    }
    @Test void loadClearsPreviousHintBeforeDecoding() {
        var state=new TransportState();state.mark(UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),Vec3.ZERO);
        state.load(new CompoundTag());assertFalse(state.present());assertNull(state.safePosition());
    }
}
