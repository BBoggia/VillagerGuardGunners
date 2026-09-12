package com.gamergaming.villagerguardgunners.medical;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
class MedicStateTest {
    @Test void choicesDefaultOffAndPersistWithoutRuntimeLeaseState() {
        var state=new MedicState(); assertFalse(state.medic()); assertFalse(state.retreat());
        state.medic(true);state.retreat(true);state.recovering=true;state.nextAssessment=100;state.damageRevision=3;state.unreachablePatient=java.util.UUID.randomUUID();state.unreachableUntil=200;
        var root=new CompoundTag();state.save(root);var copy=new MedicState();copy.load(root);
        assertTrue(copy.medic());assertTrue(copy.retreat());assertFalse(copy.recovering);assertEquals(0,copy.nextAssessment);assertEquals(0,copy.damageRevision);assertNull(copy.unreachablePatient);assertEquals(0,copy.unreachableUntil);
    }
    @Test void unknownSettingsArePreservedWithoutEnablingAi() {
        var root=new CompoundTag();var raw=new CompoundTag();raw.putInt("Version",99);raw.putBoolean("Medic",true);root.put(MedicState.KEY,raw);
        var state=new MedicState();state.load(root);state.medic(true);assertFalse(state.known());assertFalse(state.medic());
        var saved=new CompoundTag();state.save(saved);assertEquals(root,saved);
    }
    @Test void malformedFieldTypesAreNotCoerced() {
        var state=new MedicState();var root=new CompoundTag();state.save(root);root.getCompound(MedicState.KEY).putString("Medic","true");
        state.load(root);assertFalse(state.known());assertFalse(state.medic());
    }
    @Test void loadingSameObjectClearsSearchProgress() {
        var state = new MedicState(); state.searchCell = 23; state.searchAfter.put(1L, java.util.UUID.randomUUID());
        var root = new CompoundTag(); state.save(root); state.load(root);
        assertEquals(0, state.searchCell); assertTrue(state.searchAfter.isEmpty());
    }
}
