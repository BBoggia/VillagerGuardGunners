package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostIdentityCodecTest {
    private static final UUID POST_ID = UUID.fromString("6f052ec1-3e2c-4d9d-9cb1-e303125ee2dd");
    private static final ResourceLocation GROUP = ResourceLocation.fromNamespaceAndPath(
            "villagerguardgunners",
            "watchtower");

    @Test
    void exactRecordRoundTripsAndNullRemovesIt() {
        GuardPostIdentity identity = GuardPostIdentity.assigned(
                POST_ID,
                GROUP,
                "normal_patrol",
                2);
        CompoundTag guard = new CompoundTag();

        GuardPostIdentityCodec.writeGuardRecord(guard, identity);
        assertEquals(identity, GuardPostIdentityCodec.readGuardRecord(guard));

        GuardPostIdentityCodec.writeGuardRecord(guard, null);
        assertFalse(guard.contains(GuardPostIdentityCodec.GUARD_POST_TAG));
        assertNull(GuardPostIdentityCodec.readGuardRecord(guard));
    }

    @Test
    void futureMalformedAndNoncanonicalRecordsFailClosed() {
        CompoundTag valid = encoded();

        CompoundTag future = valid.copy();
        future.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG).putInt("Version", 2);
        assertNull(GuardPostIdentityCodec.readGuardRecord(future));

        CompoundTag extra = valid.copy();
        extra.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG).putString("Command", "say hi");
        assertNull(GuardPostIdentityCodec.readGuardRecord(extra));

        CompoundTag shorthand = valid.copy();
        shorthand.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG).putString("Group", "watchtower");
        assertNull(GuardPostIdentityCodec.readGuardRecord(shorthand));

        CompoundTag nil = valid.copy();
        nil.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG).putUUID("PostId", new UUID(0L, 0L));
        assertNull(GuardPostIdentityCodec.readGuardRecord(nil));

        CompoundTag outOfRange = valid.copy();
        outOfRange.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG).putInt("Member", 4);
        assertNull(GuardPostIdentityCodec.readGuardRecord(outOfRange));

        CompoundTag invalidVariant = valid.copy();
        invalidVariant.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG)
                .putString("Variant", "Normal Patrol");
        assertNull(GuardPostIdentityCodec.readGuardRecord(invalidVariant));
    }

    private static CompoundTag encoded() {
        CompoundTag guard = new CompoundTag();
        GuardPostIdentityCodec.writeGuardRecord(
                guard,
                GuardPostIdentity.assigned(POST_ID, GROUP, "normal_patrol", 0));
        return guard;
    }
}
