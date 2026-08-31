package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
