package com.gamergaming.villagerguardgunners.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class VillageDefenseStatusCodecTest {
    @Test
    void strictCodecRoundTripsAValidScheduledStatus() {
        VillageDefenseStatus status = new VillageDefenseStatus(
                VillageDefenseStatus.CURRENT_SCHEMA_VERSION,
                VillageDefenseState.ALERT,
                100L,
                200L,
                4L);

        assertEquals(status, VillageDefenseStatusCodec.decode(
                VillageDefenseStatusCodec.encode(status)));
    }

    @Test
    void futureUnknownAndInconsistentPayloadsFailClosed() {
        CompoundTag future = VillageDefenseStatusCodec.encode(
                VillageDefenseStatus.peaceful());
        future.putInt("Version", VillageDefenseStatus.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag unknown = VillageDefenseStatusCodec.encode(
                VillageDefenseStatus.peaceful());
        unknown.putString("State", "future_state");
        CompoundTag extra = VillageDefenseStatusCodec.encode(
                VillageDefenseStatus.peaceful());
        extra.putBoolean("Unexpected", true);
        CompoundTag inconsistent = VillageDefenseStatusCodec.encode(
                VillageDefenseStatus.peaceful());
        inconsistent.putLong("NextReview", 40L);

        assertNull(VillageDefenseStatusCodec.decode(future));
        assertNull(VillageDefenseStatusCodec.decode(unknown));
        assertNull(VillageDefenseStatusCodec.decode(extra));
        assertNull(VillageDefenseStatusCodec.decode(inconsistent));
    }
}
