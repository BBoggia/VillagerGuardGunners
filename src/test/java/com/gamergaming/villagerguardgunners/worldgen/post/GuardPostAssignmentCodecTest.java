package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostAssignmentCodecTest {
    private static final ResourceLocation POOL = id("example", "tower_rifles");
    private static final ResourceLocation ROLE = id("example", "sentry");

    @Test
    void optionalRoleRecordRoundTripsAndNullRemovesIt() {
        GuardPostAssignment assignment = new GuardPostAssignment(
                GuardPostAssignment.CURRENT_SCHEMA_VERSION,
                POOL,
                ROLE,
                NaturalGuardDifficulty.HARD);
        CompoundTag guard = new CompoundTag();

        GuardPostAssignmentCodec.writeGuardRecord(guard, assignment);
        assertEquals(assignment, GuardPostAssignmentCodec.readGuardRecord(guard));

        GuardPostAssignmentCodec.writeGuardRecord(guard, null);
        assertFalse(guard.contains(GuardPostAssignmentCodec.ASSIGNMENT_TAG));
        assertNull(GuardPostAssignmentCodec.readGuardRecord(guard));
    }

    @Test
    void malformedFutureAndExecutableLookingRecordsFailClosed() {
        CompoundTag valid = encoded();

        CompoundTag future = valid.copy();
        future.getCompound(GuardPostAssignmentCodec.ASSIGNMENT_TAG)
                .putInt("Version", GuardPostAssignment.CURRENT_SCHEMA_VERSION + 1);
        assertNull(GuardPostAssignmentCodec.readGuardRecord(future));

        CompoundTag extra = valid.copy();
        extra.getCompound(GuardPostAssignmentCodec.ASSIGNMENT_TAG)
                .putString("Command", "give @s tacz:modern_kinetic_gun");
        assertNull(GuardPostAssignmentCodec.readGuardRecord(extra));

        CompoundTag shorthand = valid.copy();
        shorthand.getCompound(GuardPostAssignmentCodec.ASSIGNMENT_TAG)
                .putString("LoadoutPool", "tower_rifles");
        assertNull(GuardPostAssignmentCodec.readGuardRecord(shorthand));

        CompoundTag unknownDifficulty = valid.copy();
        unknownDifficulty.getCompound(GuardPostAssignmentCodec.ASSIGNMENT_TAG)
                .putString("Difficulty", "NIGHTMARE");
        assertNull(GuardPostAssignmentCodec.readGuardRecord(unknownDifficulty));

        CompoundTag missingDifficulty = valid.copy();
        missingDifficulty.getCompound(GuardPostAssignmentCodec.ASSIGNMENT_TAG)
                .remove("Difficulty");
        assertNull(GuardPostAssignmentCodec.readGuardRecord(missingDifficulty));
    }

    private static CompoundTag encoded() {
        CompoundTag guard = new CompoundTag();
        GuardPostAssignmentCodec.writeGuardRecord(
                guard,
                new GuardPostAssignment(
                        GuardPostAssignment.CURRENT_SCHEMA_VERSION,
                        POOL,
                        null,
                        NaturalGuardDifficulty.NORMAL));
        return guard;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
