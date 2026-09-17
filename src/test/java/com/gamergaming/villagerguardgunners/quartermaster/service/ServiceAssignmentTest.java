package com.gamergaming.villagerguardgunners.quartermaster.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ServiceAssignmentTest {
    private static ServiceStationAssignment assigned() {
        return new ServiceStationAssignment(UUID.randomUUID(), ResourceLocation.parse("minecraft:overworld"), new BlockPos(2, 70, 3));
    }
    private static CompoundTag legacy() {
        var data = new CompoundTag(); data.putInt("Version", 1); data.putString("Policy", "PROFILE");
        data.putString("Profile", "example:custom"); data.putBoolean("ReplaceExisting", true);
        var root = new CompoundTag(); root.put(GuardServiceState.KEY, data); return root;
    }
    @Test void versionOneKeepsPolicyAndReplacementAndMigratesToAuto() {
        var state = new GuardServiceState(UUID.randomUUID()); state.load(legacy());
        assertTrue(state.readable()); assertEquals(GuardServiceState.Policy.PROFILE, state.policy());
        assertEquals(ResourceLocation.parse("example:custom"), state.profile()); assertTrue(state.replaceExisting());
        assertNull(state.assignment()); var saved = new CompoundTag(); state.save(saved);
        assertEquals(2, saved.getCompound(GuardServiceState.KEY).getInt("Version"));
        assertFalse(saved.getCompound(GuardServiceState.KEY).contains("Assignment"));
    }
    @Test void noExistingRecordStaysManualAndUnassigned() {
        var state = new GuardServiceState(UUID.randomUUID()); state.load(new CompoundTag());
        assertEquals(GuardServiceState.Policy.MANUAL, state.policy()); assertNull(state.assignment()); assertFalse(state.replaceExisting());
    }
    @Test void assignedManualGuardKeepsIdentityAcrossSaveAndPolicyChanges() {
        UUID id = UUID.randomUUID(); var state = new GuardServiceState(id); var assignment = assigned();
        state.assignment(assignment); state.policy(GuardServiceState.Policy.RESTOCK); state.policy(GuardServiceState.Policy.MANUAL);
        var saved = new CompoundTag(); state.save(saved); var restored = new GuardServiceState(id); restored.load(saved);
        assertEquals(assignment, restored.assignment()); assertEquals(GuardServiceState.Policy.MANUAL, restored.policy());
        assertEquals(state.receipt.revision(), restored.receipt.revision());
    }
    @Test void unknownOrMalformedRecordsSurviveWithoutBecomingAssignable() {
        for (int caseId = 0; caseId < 7; caseId++) {
            var root = legacy(); var raw = root.getCompound(GuardServiceState.KEY);
            switch (caseId) {
                case 0 -> raw.putInt("Version", 99);
                case 1 -> raw.putString("Policy", "NEW_POLICY");
                case 2 -> raw.putString("Profile", "bad id");
                case 3 -> raw.putByte("ReplaceExisting", (byte) 2);
                case 4 -> { raw.putInt("Version", 2); raw.putString("Assignment", "bad"); }
                case 5 -> raw.put("Assignment", assigned().save()); // Not legal in version 1.
                case 6 -> { raw.putInt("Version", 2); var a = assigned().save(); a.putString("Dimension", "overworld"); raw.put("Assignment", a); }
            }
            var state = new GuardServiceState(UUID.randomUUID()); state.load(root); assertFalse(state.readable());
            state.assignment(assigned()); state.policy(GuardServiceState.Policy.RESTOCK);
            var saved = new CompoundTag(); state.save(saved); assertEquals(raw, saved.get(GuardServiceState.KEY));
        }
    }
    @Test void settingChangesInvalidateOldRevisionsEvenWhenValuesAreRestored() {
        var state = new GuardServiceState(UUID.randomUUID()); var initial = state.settings(); long first = state.settingsRevision();
        state.assignment(assigned()); state.assignment(null); assertEquals(initial, state.settings());
        assertTrue(state.settingsRevision() > first); long now = state.settingsRevision();
        state.update(initial); assertEquals(now, state.settingsRevision());
    }
    @Test void lockedReceiptPreventsSettingsChanges() {
        var state = new GuardServiceState(UUID.randomUUID()); state.receipt.quarantine();
        assertFalse(state.update(new ServiceSettings(GuardServiceState.Policy.RESTOCK, null, false, assigned())));
        assertNull(state.assignment()); assertEquals(GuardServiceState.Policy.MANUAL, state.policy());
    }
    @Test void assignmentCopiesMutablePositionAndRejectsExtraFields() {
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var a = new ServiceStationAssignment(UUID.randomUUID(), ResourceLocation.parse("minecraft:overworld"), pos);
        pos.set(9, 9, 9); assertEquals(new BlockPos(1, 2, 3), a.hint()); assertEquals(a, ServiceStationAssignment.load(a.save()));
        var raw = a.save(); raw.putInt("Unexpected", 1); assertThrows(IllegalArgumentException.class, () -> ServiceStationAssignment.load(raw));
    }
    @Test void rawWrongTypeIsPreserved() {
        var root = new CompoundTag(); root.putString(GuardServiceState.KEY, "future format");
        var state = new GuardServiceState(UUID.randomUUID()); state.load(root); var saved = new CompoundTag(); state.save(saved);
        assertFalse(state.readable()); assertEquals(root.get(GuardServiceState.KEY), saved.get(GuardServiceState.KEY));
    }
    @Test void rosterPersistsOnlyLastSeenMetadataAndCanBeForgotten() {
        var roster = new ServiceRoster(); UUID id = UUID.randomUUID(); var a = assigned();
        var entry = new ServiceRoster.Entry(id, a, a.dimension(), new BlockPos(5, 70, 5), "Guard", 123);
        assertTrue(roster.record(entry)); var restored = ServiceRoster.load(roster.save(new CompoundTag()));
        assertEquals(entry, restored.find(id)); restored.forget(id); assertTrue(restored.entries().isEmpty());
    }
    @Test void fullRosterDoesNotEvictAnotherGuardAndAllowsUpdates() {
        var roster = new ServiceRoster(); var a = assigned(); UUID first = null;
        for (int i = 0; i < ServiceRoster.CAPACITY; i++) {
            UUID id = UUID.randomUUID(); if (first == null) first = id;
            assertTrue(roster.record(new ServiceRoster.Entry(id, a, a.dimension(), a.hint(), "Guard", i)));
        }
        assertFalse(roster.record(new ServiceRoster.Entry(UUID.randomUUID(), a, a.dimension(), a.hint(), "Extra", 0)));
        assertTrue(roster.record(new ServiceRoster.Entry(first, a, a.dimension(), a.hint(), "Updated", 9999)));
        assertEquals("Updated", roster.find(first).name());
    }
    @Test void duplicateOrFutureRosterIsPreservedRatherThanPartiallyTrusted() {
        var roster = new ServiceRoster(); var a = assigned();
        roster.record(new ServiceRoster.Entry(UUID.randomUUID(), a, a.dimension(), a.hint(), "Guard", 1));
        var raw = roster.save(new CompoundTag()); var list = raw.getList("Entries", Tag.TAG_COMPOUND); list.add(list.get(0).copy());
        var restored = ServiceRoster.load(raw); assertFalse(restored.readable()); assertTrue(restored.entries().isEmpty());
        assertEquals(raw, restored.save(new CompoundTag()));
        raw.putInt("Version", 2); assertEquals(raw, ServiceRoster.load(raw).save(new CompoundTag()));
    }
    @Test void explicitButtonRequestPreservesAssignmentAndUsesProvidedProfilesOnly() {
        var a = assigned(); var role = ResourceLocation.parse("example:medic");
        var profileId = ResourceLocation.parse("example:medic_loadout");
        var profile = new EquipmentProfile(java.util.List.of(), java.util.List.of(), java.util.Map.of(),
                64, java.util.List.of(), 16, java.util.List.of(role.toString()));
        var current = new ServiceSettings(GuardServiceState.Policy.RESTOCK, null, false, a);
        var request = ServiceControls.next(current, ServiceControls.POLICY_BUTTON, java.util.Map.of(profileId, profile), role);
        assertEquals(GuardServiceState.Policy.PROFILE, request.policy()); assertEquals(profileId, request.profile());
        assertEquals(a, request.assignment()); assertFalse(request.replaceExisting());
        assertEquals(GuardServiceState.Policy.RESTOCK, current.policy()); assertNull(current.profile());
    }
    @Test void togglingReplacementDoesNotEnrollGuardOrClearItsAssignment() {
        var initial = new ServiceSettings(GuardServiceState.Policy.MANUAL, null, false, assigned());
        var requested = ServiceControls.next(initial, ServiceControls.REPLACE_BUTTON, java.util.Map.of(), null);
        assertTrue(requested.replaceExisting()); assertEquals(initial.policy(), requested.policy());
        assertEquals(initial.assignment(), requested.assignment());
        assertNull(ServiceControls.next(initial, -100, java.util.Map.of(), null));
    }
}
