package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostOpaqueRecordTest {
    private static final UUID POST = UUID.fromString("ee83a902-ae58-41dc-aaac-d9002a820730");
    private static final ResourceLocation GROUP = id("villagerguardgunners", "watchtower");
    private static final ResourceLocation POOL = id("villagerguardgunners", "normal");
    private static final ResourceLocation LOOT = id(
            "villagerguardgunners", "chests/watchtower_armory");

    @Test
    void futureGuardIdentityAndAssignmentArePreservedWithoutInterpretation() {
        CompoundTag source = new CompoundTag();
        GuardPostIdentityCodec.writeGuardRecord(
                source,
                GuardPostIdentity.assigned(POST, GROUP, "normal_patrol", 1));
        GuardPostAssignmentCodec.writeGuardRecord(
                source,
                new GuardPostAssignment(
                        GuardPostAssignment.CURRENT_SCHEMA_VERSION,
                        POOL,
                        null,
                        NaturalGuardDifficulty.NORMAL));
        source.getCompound(GuardPostIdentityCodec.GUARD_POST_TAG)
                .putInt("Version", GuardPostIdentity.CURRENT_SCHEMA_VERSION + 1);
        source.getCompound(GuardPostAssignmentCodec.ASSIGNMENT_TAG)
                .putInt("Version", GuardPostAssignment.CURRENT_SCHEMA_VERSION + 1);
        Tag identity = GuardPostIdentityCodec.copyEncodedRecord(source);
        Tag assignment = GuardPostAssignmentCodec.copyEncodedRecord(source);

        assertAll(
                () -> assertNull(GuardPostIdentityCodec.readGuardRecord(source)),
                () -> assertNull(GuardPostAssignmentCodec.readGuardRecord(source)));

        CompoundTag saved = new CompoundTag();
        GuardPostIdentityCodec.writeGuardRecordPreservingUnreadable(saved, null, identity);
        GuardPostAssignmentCodec.writeGuardRecordPreservingUnreadable(saved, null, assignment);
        assertAll(
                () -> assertEquals(identity, saved.get(GuardPostIdentityCodec.GUARD_POST_TAG)),
                () -> assertEquals(
                        assignment,
                        saved.get(GuardPostAssignmentCodec.ASSIGNMENT_TAG)));
    }

    @Test
    void malformedTagTypesRemainOpaqueAndExact() {
        CompoundTag source = new CompoundTag();
        source.put(GuardPostIdentityCodec.GUARD_POST_TAG, StringTag.valueOf("future identity"));
        source.put(GuardPostAssignmentCodec.ASSIGNMENT_TAG, StringTag.valueOf("future work"));
        source.put(GuardPostArmoryIdentityCodec.IDENTITY_TAG, StringTag.valueOf("future armory"));

        CompoundTag saved = new CompoundTag();
        GuardPostIdentityCodec.writeGuardRecordPreservingUnreadable(
                saved, null, GuardPostIdentityCodec.copyEncodedRecord(source));
        GuardPostAssignmentCodec.writeGuardRecordPreservingUnreadable(
                saved, null, GuardPostAssignmentCodec.copyEncodedRecord(source));
        GuardPostArmoryIdentityCodec.writePreservingUnreadable(
                saved, null, GuardPostArmoryIdentityCodec.copyEncodedRecord(source));

        assertAll(
                () -> assertEquals(source.get(GuardPostIdentityCodec.GUARD_POST_TAG),
                        saved.get(GuardPostIdentityCodec.GUARD_POST_TAG)),
                () -> assertEquals(source.get(GuardPostAssignmentCodec.ASSIGNMENT_TAG),
                        saved.get(GuardPostAssignmentCodec.ASSIGNMENT_TAG)),
                () -> assertEquals(source.get(GuardPostArmoryIdentityCodec.IDENTITY_TAG),
                        saved.get(GuardPostArmoryIdentityCodec.IDENTITY_TAG)));
    }

    @Test
    void recognizedStateExplicitlyReplacesOpaqueData() {
        Tag opaque = StringTag.valueOf("future");
        CompoundTag guard = new CompoundTag();
        GuardPostIdentity identity = GuardPostIdentity.assigned(
                POST, GROUP, "normal_patrol", 0);
        GuardPostIdentityCodec.writeGuardRecordPreservingUnreadable(guard, identity, opaque);

        CompoundTag armory = new CompoundTag();
        GuardPostArmoryIdentity initialized =
                GuardPostArmoryIdentity.initialized(POST, LOOT);
        GuardPostArmoryIdentityCodec.writePreservingUnreadable(
                armory, initialized, opaque);

        assertAll(
                () -> assertEquals(identity, GuardPostIdentityCodec.readGuardRecord(guard)),
                () -> assertEquals(initialized, GuardPostArmoryIdentityCodec.read(armory)));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
