package com.gamergaming.villagerguardgunners.medical;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GuardLifeStateTest {
    @Test void cotLossCreditsRestAlreadyCompletedAcrossReload() {
        var state = new GuardLifeState(); state.recover(100, 400, true);
        for (int i = 0; i < 35; i++) state.tick(true);
        var data = new CompoundTag(); state.save(data); var restored = new GuardLifeState(); restored.load(data);
        assertTrue(restored.known()); restored.fieldRecovery(); assertEquals(365, restored.remaining());
        restored.fieldRecovery(); assertEquals(365, restored.remaining());
        for (int i = 0; i < 365; i++) restored.tick(); assertFalse(restored.blocked());
    }
    @Test void completedCotRestWaitsForSafeExitWithoutResettingRecovery() {
        var state = new GuardLifeState(); state.recover(100, 400, true);
        for (int i = 0; i < 200; i++) state.tick(true);
        assertEquals(0, state.remaining()); assertEquals(GuardLifeState.Stage.RECOVERING, state.stage());
        state.tick(false); assertFalse(state.blocked());
    }
    @Test void legacyRecoveringGuardFinishesItsSavedRest() {
        var state = new GuardLifeState(); state.load(record("RECOVERING", 35, 2200));
        state.fieldRecovery(); assertEquals(35, state.remaining());
        var saved = new CompoundTag(); state.save(saved); var copy = new GuardLifeState(); copy.load(saved);
        assertTrue(copy.known()); assertEquals(35, copy.remaining());
    }
    @Test void waitingForAnExitAlsoCountsAsCompletedRest() {
        var state = new GuardLifeState(); state.recover(100,400,true);
        for(int i=0;i<350;i++)state.tick(true);
        var tag=new CompoundTag();state.save(tag);var copy=new GuardLifeState();copy.load(tag);
        assertTrue(copy.known());copy.fieldRecovery();assertEquals(50,copy.remaining());
    }
    private static CompoundTag record(String stage, int ticks, int cooldown) {
        var root = new CompoundTag(); var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putString("Stage", stage);
        tag.putInt("Remaining", ticks); tag.putInt("Cooldown", cooldown); tag.putString("DamageType", "minecraft:generic");
        root.put(GuardLifeState.KEY, tag); return root;
    }
    @Test void oldGuardDefaultsToActive() { var s=new GuardLifeState();s.load(new CompoundTag());assertTrue(s.known());assertFalse(s.blocked());assertEquals(0,s.cooldown()); }
    @Test void loadedTicksPersistAndDoNotUseWallClock() {
        var s=new GuardLifeState();s.load(record("DOWNED",901,0));for(int i=0;i<101;i++)s.tick();var tag=new CompoundTag();s.save(tag);
        var copy=new GuardLifeState();copy.load(tag);assertEquals(800,copy.remaining());assertEquals(GuardLifeState.Stage.DOWNED,copy.stage());
    }
    @Test void twoTreatmentsRecoverOnlyOnce() {
        var s=new GuardLifeState();s.load(record("DOWNED",1,0));s.treated();assertEquals(GuardLifeState.Stage.STABILIZED,s.stage());assertEquals(1200,s.remaining());
        s.treated();assertEquals(GuardLifeState.Stage.RECOVERING,s.stage());long rev=s.revision();s.treated();assertEquals(rev,s.revision());
        for(int i=0;i<100;i++)s.tick();assertEquals(GuardLifeState.Stage.ACTIVE,s.stage());assertEquals(2300,s.cooldown());
    }
    @Test void finishingDamageIsBoundedAndDeduplicatedPerTick() {
        var s=new GuardLifeState();s.load(record("DOWNED",1200,0));s.finishDamage(1,10);assertEquals(1180,s.remaining());
        s.finishDamage(1000,10);assertEquals(1180,s.remaining());s.finishDamage(1000,11);assertEquals(980,s.remaining());
        s.finishDamage(Float.NaN,12);assertEquals(980,s.remaining());
    }
    @Test void invalidRecordsArePreservedWithoutImmobilization() {
        for(var tag:new CompoundTag[]{record("FUTURE",100,0),record("DOWNED",1201,0),record("STABILIZED",2401,0),record("ACTIVE",2,0),record("RECOVERING",101,0),record("DOWNED",1,-1)}){
            var s=new GuardLifeState();s.load(tag);assertFalse(s.known());assertFalse(s.blocked());var out=new CompoundTag();s.save(out);assertEquals(tag,out);
        }
    }
    @Test void restartDoesNotPersistTransientDeathOrDamageLatch() {
        var s=new GuardLifeState();s.load(record("STABILIZED",450,0));s.dying=true;s.lastDamageTick=500;var root=new CompoundTag();s.save(root);
        var copy=new GuardLifeState();copy.load(root);assertFalse(copy.dying);assertEquals(Long.MIN_VALUE,copy.lastDamageTick);assertEquals(450,copy.remaining());
    }
    @Test void cooldownSurvivesRecoveryAndReload() {
        var s=new GuardLifeState();s.recover();for(int i=0;i<200;i++)s.tick();var tag=new CompoundTag();s.save(tag);var copy=new GuardLifeState();copy.load(tag);
        assertEquals(2200,copy.cooldown());assertFalse(copy.blocked());assertEquals(0,copy.remaining());
    }
    @Test void stabilizationKeepsAFullLoadedWindowAfterTreatmentCooldownAcrossReload() {
        var state = new GuardLifeState(); state.load(record("DOWNED", 1, 0)); state.treated(1200);
        assertEquals(2400, state.remaining());
        var saved = new CompoundTag(); state.save(saved); var copy = new GuardLifeState(); copy.load(saved);
        assertTrue(copy.known()); assertEquals(GuardLifeState.Stage.STABILIZED, copy.stage());
        for (int i = 0; i < 1200; i++) copy.tick();
        assertEquals(1200, copy.remaining()); copy.treated(1200);
        assertEquals(GuardLifeState.Stage.RECOVERING, copy.stage());
    }
}
