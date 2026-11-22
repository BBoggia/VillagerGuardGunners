package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostArmoryIdentityCodecTest {
    private static final UUID POST = UUID.fromString("bbce1ef2-cc1c-40bc-aa0d-0842156ec7f5");
    private static final ResourceLocation LOOT = ResourceLocation.fromNamespaceAndPath(
            "villagerguardgunners", "chests/watchtower_armory");

    @Test
    void emptyAndLootStockedRecordsRoundTripStrictly() {
        CompoundTag empty = new CompoundTag();
        GuardPostArmoryIdentity emptyIdentity =
                GuardPostArmoryIdentity.initialized(POST, null);
        GuardPostArmoryIdentityCodec.write(empty, emptyIdentity);
        assertEquals(emptyIdentity, GuardPostArmoryIdentityCodec.read(empty));

        CompoundTag stocked = new CompoundTag();
        GuardPostArmoryIdentity stockedIdentity =
                GuardPostArmoryIdentity.initialized(POST, LOOT);
        GuardPostArmoryIdentityCodec.write(stocked, stockedIdentity);
        assertEquals(stockedIdentity, GuardPostArmoryIdentityCodec.read(stocked));

        stocked.getCompound(GuardPostArmoryIdentityCodec.IDENTITY_TAG)
                .putString("Items", "not executable");
        assertNull(GuardPostArmoryIdentityCodec.read(stocked));
    }

    @Test
    void pendingWorkRoundTripsAndLegacyCompletionMigrates() {
        CompoundTag pendingTag = new CompoundTag();
        GuardPostArmoryIdentity pending =
                GuardPostArmoryIdentity.pending(POST, LOOT, 91L);
        GuardPostArmoryIdentityCodec.write(pendingTag, pending);
        assertEquals(pending, GuardPostArmoryIdentityCodec.read(pendingTag));
        assertTrue(pending.pending());
        assertFalse(pending.completed().pending());

        CompoundTag legacyIdentity = new CompoundTag();
        legacyIdentity.putInt("Version", 1);
        legacyIdentity.putUUID("PostId", POST);
        legacyIdentity.putString("LootTable", LOOT.toString());
        CompoundTag legacyParent = new CompoundTag();
        legacyParent.put(GuardPostArmoryIdentityCodec.IDENTITY_TAG, legacyIdentity);
        GuardPostArmoryIdentity migrated = GuardPostArmoryIdentityCodec.read(legacyParent);
        assertEquals(GuardPostArmoryIdentity.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertFalse(migrated.pending());
    }

    @Test
    void nilFutureAndNoncanonicalRecordsFailClosed() {
        CompoundTag parent = new CompoundTag();
        GuardPostArmoryIdentityCodec.write(
                parent,
                GuardPostArmoryIdentity.initialized(POST, LOOT));

        CompoundTag future = parent.copy();
        future.getCompound(GuardPostArmoryIdentityCodec.IDENTITY_TAG)
                .putInt("Version", GuardPostArmoryIdentity.CURRENT_SCHEMA_VERSION + 1);
        assertNull(GuardPostArmoryIdentityCodec.read(future));

        CompoundTag nil = parent.copy();
        nil.getCompound(GuardPostArmoryIdentityCodec.IDENTITY_TAG)
                .putUUID("PostId", new UUID(0L, 0L));
        assertNull(GuardPostArmoryIdentityCodec.read(nil));

        CompoundTag shorthand = parent.copy();
        shorthand.getCompound(GuardPostArmoryIdentityCodec.IDENTITY_TAG)
                .putString("LootTable", "watchtower_armory");
        assertNull(GuardPostArmoryIdentityCodec.read(shorthand));
    }
}
