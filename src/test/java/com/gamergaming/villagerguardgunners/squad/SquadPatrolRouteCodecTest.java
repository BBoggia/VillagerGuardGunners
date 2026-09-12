package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SquadPatrolRouteCodecTest {
    private static final UUID SQUAD =
            UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Test
    void strictCodecRoundTripsCurrentRoutesAndRejectsSchemaDrift() {
        SquadPatrolRoute route = SquadPatrolRoute.create(
                SQUAD,
                ResourceLocation.tryParse("minecraft:overworld"),
                List.of(new BlockPos(10, 64, 10), new BlockPos(20, 65, 15)),
                100L);
        CompoundTag encoded = SquadPatrolRouteCodec.encode(route);
        CompoundTag future = encoded.copy();
        future.putInt("Version", SquadPatrolRoute.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag extra = encoded.copy();
        extra.putBoolean("Unexpected", true);
        CompoundTag badDimension = encoded.copy();
        badDimension.putString("Dimension", "Not Canonical");

        assertAll(
                () -> assertEquals(route, SquadPatrolRouteCodec.decode(encoded)),
                () -> assertNull(SquadPatrolRouteCodec.decode(future)),
                () -> assertNull(SquadPatrolRouteCodec.decode(extra)),
                () -> assertNull(SquadPatrolRouteCodec.decode(badDimension)));
    }
}
