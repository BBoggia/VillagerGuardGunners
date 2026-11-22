package com.gamergaming.villagerguardgunners.medical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CotRestStateTest {
    @Test void allFacingsRoundTripAndReleaseRemovesAnchor() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = new CotRestState(); state.anchor(new BlockPos(-17,61,18), facing, ResourceLocation.parse("minecraft:overworld"));
            var tag = new CompoundTag(); state.save(tag); var copy = new CotRestState(); copy.load(tag);
            assertTrue(copy.known()); assertEquals(state.head(), copy.head()); assertEquals(facing, copy.facing());
            copy.clear(); copy.save(tag); assertFalse(tag.contains(CotRestState.KEY));
        }
    }
    @Test void malformedFutureAnchorIsPreservedWithoutAttachingPatient() {
        var root = new CompoundTag(); var data = new CompoundTag(); data.putInt("Version", 90); root.put(CotRestState.KEY, data);
        var state = new CotRestState(); state.load(root); assertFalse(state.known()); assertNull(state.head());
        var saved = new CompoundTag(); state.save(saved); assertEquals(root, saved);
    }
}
