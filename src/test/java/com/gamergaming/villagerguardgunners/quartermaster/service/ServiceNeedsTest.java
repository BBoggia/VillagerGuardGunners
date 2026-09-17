package com.gamergaming.villagerguardgunners.quartermaster.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.ServiceNeeds.Stack;
import org.junit.jupiter.api.Test;
import com.gamergaming.villagerguardgunners.quartermaster.service.ServiceNeeds.*;

class ServiceNeedsTest {
    private static final UUID A=new UUID(0,1), B=new UUID(0,2);
    private static Map<String,Variant> facts() {
        return Map.of("rifle",new Variant("rifle","gun","pack:rifle","pack:ammo",Kind.GUN,true,true),
            "pistol",new Variant("pistol","gun","pack:pistol","pack:ammo",Kind.GUN,true,true),
            "otherGun",new Variant("otherGun","gun","pack:other","pack:other_ammo",Kind.GUN,true,true),
            "unknown",new Variant("unknown","gun","pack:missing",null,Kind.GUN,false,true),
            "ammo",new Variant("ammo","ammo",null,"pack:ammo",Kind.LOOSE_AMMO,true,true),
            "tagged",new Variant("tagged","ammo",null,"pack:ammo",Kind.LOOSE_AMMO,true,true),
            "otherAmmo",new Variant("otherAmmo","ammo",null,"pack:other_ammo",Kind.LOOSE_AMMO,true,true),
            "box",new Variant("box","box",null,null,Kind.AMMO_BOX,true,true),
            "bandage",new Variant("bandage","bandage",null,null,Kind.MEDICAL,true,true),
            "medicine",new Variant("medicine","medicine",null,null,Kind.MEDICAL,true,true));
    }
    private static final class Builder {
        UUID id=A; List<Stack> inv=new ArrayList<>(Collections.nCopies(12,Stack.empty()));
        List<Rule> rules=new ArrayList<>(Collections.nCopies(6,new Rule(false,Set.of())));
        boolean manual=false,missing=false,readable=true,replace=false,medical=false;
        Set<String> meds=Set.of(),returnable=Set.of("rifle","pistol","otherGun"),allowed=Set.of("station","armory");
        int ammo=64,doses=16;
        Builder() { inv.set(0,new Stack("rifle",1)); }
        GuardInput build() {
            Map<String,List<Integer>> caps=new HashMap<>();facts().keySet().forEach(k->caps.put(k,List.of(1,1,1,1,1,1,64,64,64,64,64,64)));
            return new GuardInput(id,inv,rules,manual,missing,readable,replace,ammo,doses,meds,medical,returnable,caps,allowed,false);
        }
    }
    private static Map<String,Set<String>> compatibility() {
        return Map.of("rifle",Set.of("ammo","tagged"),"pistol",Set.of("ammo","tagged"),"otherGun",Set.of("otherAmmo"));
    }
    private static Source supply(String token,int count,int slot) {return new Source("station",slot,new Stack(token,count),true,false);}
    private static Result evaluate(List<Source> stock,List<Integer> returns,GuardInput... guards) {
        return ServiceNeeds.evaluate(new Input(facts(),stock,returns,List.of(guards),compatibility()));
    }
    private static Row ammo(Result r, int guard) {return r.guards().get(guard).rows().stream().filter(x->x.category().equals("ammo")&&x.slot()==6).findFirst().orElseThrow();}
    @Test void twoWeaponsShareOneCaliberReserve() {
        var g=new Builder();g.inv.set(1,new Stack("pistol",1));g.inv.set(6,new Stack("ammo",10));
        var result=evaluate(List.of(supply("ammo",64,0)),List.of(),g.build());
        assertEquals(1,result.guards().get(0).rows().stream().filter(r->r.category().equals("ammo")&&r.slot()==6).count());
        assertEquals(54,ammo(result,0).requested());assertEquals(54,ammo(result,0).available());
    }
    @Test void scarceSharedStockIsAllocatedOnceInStableGuardOrder() {
        var a=new Builder();var b=new Builder();b.id=B;
        var result=evaluate(List.of(supply("ammo",80,0)),List.of(),b.build(),a.build());
        assertEquals(A,result.guards().get(0).id());assertEquals(64,ammo(result,0).available());assertEquals(16,ammo(result,1).available());
        assertEquals(48,ammo(result,1).missing());
    }
    @Test void existingReservationsExcludeEntireClaimedSourceSlot() {
        var source=new Source("station",0,new Stack("ammo",64),true,true);
        assertEquals(0,ammo(evaluate(List.of(source),List.of(),new Builder().build()),0).available());
    }
    @Test void taggedAmmoCannotMergeIntoOtherTagVariant() {
        var g=new Builder();g.inv.set(6,new Stack("ammo",60));g.inv.set(7,new Stack("box",1));g.inv.set(8,new Stack("box",1));
        var row=ammo(evaluate(List.of(supply("tagged",64,0)),List.of(),g.build()),0);
        assertEquals(4,row.requested());assertEquals(0,row.available());assertTrue(row.reasons().contains(Reason.INCOMPATIBLE_STOCK));
    }
    @Test void taggedAmmoUsesEmptySlotWhenOneExists() {
        var g=new Builder();g.inv.set(6,new Stack("ammo",60));
        var r=evaluate(List.of(supply("tagged",64,0)),List.of(),g.build());assertEquals(4,ammo(r,0).available());
        assertEquals(7,r.guards().get(0).transfers().get(0).destination());
    }
    @Test void ammoBoxesDoNotBecomeLooseAmmo() {
        var g=new Builder();g.inv.set(6,new Stack("box",1));
        var row=ammo(evaluate(List.of(supply("box",1,0)),List.of(),g.build()),0);
        assertEquals(64,row.requested());assertEquals(0,row.available());assertTrue(row.reasons().contains(Reason.MISSING_STOCK));
    }
    @Test void fullDestinationIsDistinctFromMissingStock() {
        var g=new Builder();for(int i=6;i<9;i++)g.inv.set(i,new Stack("otherAmmo",64));
        var row=ammo(evaluate(List.of(supply("ammo",64,0)),List.of(),g.build()),0);
        assertTrue(row.reasons().contains(Reason.DESTINATION_FULL));assertEquals(0,row.available());
    }
    @Test void protectedEquipmentIsNotAReplacementShortage() {
        var g=new Builder();g.rules.set(0,new Rule(true,Set.of("otherGun")));
        var row=evaluate(List.of(supply("otherGun",1,0)),List.of(27),g.build()).guards().get(0).rows().get(0);
        assertEquals(0,row.requested());assertTrue(row.reasons().contains(Reason.PROTECTED));
    }
    @Test void aSharedReturnSlotCannotBePromisedToTwoGuards() {
        var a=new Builder();a.replace=true;a.rules.set(0,new Rule(true,Set.of("otherGun")));var b=new Builder();b.replace=true;b.id=B;b.rules=a.rules;
        var result=evaluate(List.of(supply("otherGun",2,0)),List.of(27),a.build(),b.build());
        assertEquals(1,result.guards().get(0).transfers().stream().filter(t->t.returnSlot()==27).count());
        assertTrue(result.guards().get(1).rows().get(0).reasons().contains(Reason.RETURN_FULL));
    }
    @Test void chosenReplacementDeterminesProjectedAmmo() {
        var g=new Builder();g.replace=true;g.rules.set(0,new Rule(true,Set.of("otherGun")));
        var result=evaluate(List.of(supply("otherGun",1,0),supply("otherAmmo",64,1)),List.of(27),g.build());
        assertEquals("pack:other_ammo",ammo(result,0).item());assertEquals(64,ammo(result,0).available());
        assertEquals(0,result.guards().get(0).transfers().get(0).destination());
        assertEquals("rifle",g.inv.get(0).variant());
    }
    @Test void missingReplacementLeavesFutureAmmoConditional() {
        var g=new Builder();g.replace=true;g.rules.set(0,new Rule(true,Set.of("otherGun")));
        var result=evaluate(List.of(),List.of(27),g.build());
        assertTrue(result.guards().get(0).rows().stream().anyMatch(r->r.reasons().contains(Reason.CONDITIONAL)));
        assertEquals("pack:ammo",ammo(result,0).item());
    }
    @Test void medicalReserveIsSharedAcrossAcceptedMedicines() {
        var g=new Builder();g.ammo=0;g.medical=true;g.meds=Set.of("bandage","medicine");g.inv.set(9,new Stack("medicine",10));
        var result=evaluate(List.of(supply("bandage",20,0),supply("medicine",20,1)),List.of(),g.build());
        var row=result.guards().get(0).rows().stream().filter(r->r.category().equals("medical")).findFirst().orElseThrow();
        assertEquals(6,row.requested());assertEquals(6,row.available());
    }
    @Test void emptyRestockPouchDoesNotInventMedicalNeeds() {
        var result=evaluate(List.of(supply("bandage",64,0)),List.of(),new Builder().build());
        assertTrue(result.guards().get(0).rows().stream().anyMatch(r->r.reasons().contains(Reason.NOT_CONFIGURED)));
    }
    @Test void unavailableOrMissingProfileGuardsConsumeNothing() {
        var a=new Builder();a.readable=false;var b=new Builder();b.id=B;b.missing=true;
        var r=evaluate(List.of(supply("ammo",64,0)),List.of(),a.build(),b.build());
        assertTrue(r.guards().stream().allMatch(g->g.transfers().isEmpty()));
        assertTrue(r.guards().get(0).rows().get(0).reasons().contains(Reason.UNAVAILABLE));
        assertTrue(r.guards().get(1).rows().get(0).reasons().contains(Reason.PROFILE_MISSING));
    }
    @Test void unknownGunDefinitionsProduceNoGuessedAmmo() {
        var g=new Builder();g.inv.set(0,new Stack("unknown",1));
        var r=evaluate(List.of(supply("ammo",64,0)),List.of(),g.build());
        assertTrue(r.guards().get(0).transfers().isEmpty());assertTrue(r.guards().get(0).rows().stream().anyMatch(row->row.reasons().contains(Reason.DEFINITION_UNAVAILABLE)));
    }
    @Test void blockedSourceCannotSupplyGuard() {
        var g=new Builder();g.allowed=Set.of();assertEquals(0,ammo(evaluate(List.of(supply("ammo",64,0)),List.of(),g.build()),0).available());
    }
    @Test void duplicatePhysicalSourceAndGuardAreRejected() {
        var source=supply("ammo",64,0);var guard=new Builder().build();
        assertThrows(IllegalArgumentException.class,()->evaluate(List.of(source,source),List.of(),guard));
        assertThrows(IllegalArgumentException.class,()->evaluate(List.of(source),List.of(),guard,guard));
    }
    @Test void repeatedEvaluationIsImmutableAndConservesRandomStocks() {
        var random=new Random(92341);
        for(int test=0;test<100;test++) {
            int count=random.nextInt(129); var a=new Builder();var b=new Builder();b.id=B;
            a.inv.set(6,new Stack("ammo",random.nextInt(65)));b.inv.set(6,new Stack("ammo",random.nextInt(65)));
            var input=new Input(facts(),List.of(supply("ammo",count,0)),List.of(),List.of(a.build(),b.build()),compatibility());
            var first=ServiceNeeds.evaluate(input);assertEquals(first,ServiceNeeds.evaluate(input));
            assertTrue(first.guards().stream().flatMap(g->g.transfers().stream()).mapToInt(Transfer::amount).sum()<=count);
            assertEquals(count,input.sources().get(0).stack().count());
        }
    }
    @Test void nativeCompatibilityOverridesMatchingAmmoIds() {
        var input=new Input(facts(),List.of(supply("ammo",64,0)),List.of(),List.of(new Builder().build()),Map.of("rifle",Set.of()));
        assertEquals(0,ammo(ServiceNeeds.evaluate(input),0).available());
        assertTrue(ammo(ServiceNeeds.evaluate(input),0).reasons().contains(Reason.INCOMPATIBLE_STOCK));
    }
    @Test void sharedCaliberRequiresAmmoUsableByBothGuns() {
        var g=new Builder();g.inv.set(1,new Stack("pistol",1));
        var input=new Input(facts(),List.of(supply("ammo",64,0),supply("tagged",64,1)),List.of(),List.of(g.build()),
                Map.of("rifle",Set.of("ammo","tagged"),"pistol",Set.of("tagged")));
        var result=ServiceNeeds.evaluate(input);
        assertEquals(64,ammo(result,0).available());assertEquals("tagged",result.guards().get(0).transfers().get(0).variant());
    }
    @Test void nonPhysicalReserveDoesNotRequestLooseAmmo() {
        var variants=new HashMap<>(facts());variants.put("rifle",new Variant("rifle","gun","pack:rifle","pack:ammo",Kind.GUN,true,false));
        var result=ServiceNeeds.evaluate(new Input(variants,List.of(supply("ammo",64,0)),List.of(),List.of(new Builder().build()),compatibility()));
        assertTrue(result.guards().get(0).transfers().isEmpty());
        assertTrue(result.guards().get(0).rows().stream().anyMatch(r->r.reasons().contains(Reason.RESERVE_NOT_PHYSICAL)));
    }
    @Test void exhaustedStockAfterSkippingOccupiedSlotIsMissingStock() {
        var g=new Builder();g.inv.set(6,new Stack("otherAmmo",64));
        var row=ammo(evaluate(List.of(supply("ammo",8,0)),List.of(),g.build()),0);
        assertEquals(8,row.available());assertTrue(row.reasons().contains(Reason.MISSING_STOCK));
        assertFalse(row.reasons().contains(Reason.INCOMPATIBLE_STOCK));
    }
    @Test void bindingOrUnreturnableGearStaysProtectedWithReplacementEnabled() {
        var g=new Builder();g.replace=true;g.returnable=Set.of();g.rules.set(0,new Rule(true,Set.of("otherGun")));
        var row=evaluate(List.of(supply("otherGun",1,0)),List.of(27),g.build()).guards().get(0).rows().get(0);
        assertEquals(0,row.requested());assertTrue(row.reasons().contains(Reason.PROTECTED));
    }
    @Test void unknownDefinitionCannotBeProjectedEvenIfSlotAcceptsItemClass() {
        var g=new Builder();g.replace=true;g.rules.set(0,new Rule(true,Set.of("unknown")));
        var result=evaluate(List.of(supply("unknown",1,0)),List.of(27),g.build());
        assertTrue(result.guards().get(0).transfers().isEmpty());
        assertTrue(result.guards().get(0).rows().get(0).reasons().contains(Reason.DEFINITION_UNAVAILABLE));
    }
    private static Source reserved(String token,int count,int slot) {return new Source("station",slot,new Stack(token,count),true,true);}
    private static Input withIncoming(List<Source> stock,List<Integer> returns,List<GuardInput> guards,Transfer pending) {
        return new Input(facts(),stock,returns,guards,compatibility(),Map.of(pending.guard(),pending));
    }
    @Test void ownIncomingAmmoReducesUncoveredDemandWithoutReleasingReservedStock() {
        var a=new Builder();var b=new Builder();b.id=B;
        var pending=new Transfer(A,"station",0,6,-1,"ammo",60);
        var result=ServiceNeeds.evaluate(withIncoming(List.of(reserved("ammo",60,0),supply("ammo",10,1)),List.of(),List.of(a.build(),b.build()),pending));
        assertEquals(64,ammo(result,0).requested());assertEquals(60,ammo(result,0).incoming());
        assertEquals(4,ammo(result,0).available());assertEquals(0,ammo(result,0).missing());
        assertEquals(0,ammo(result,1).incoming());assertEquals(6,ammo(result,1).available());assertEquals(58,ammo(result,1).missing());
        assertEquals(10,result.guards().stream().flatMap(g->g.transfers().stream()).mapToInt(Transfer::amount).sum());
        assertTrue(result.guards().stream().flatMap(g->g.transfers().stream()).noneMatch(t->t.sourceSlot()==0));
    }
    @Test void incomingAmmoUsesDestinationCapacityAndOneSharedCaliberReserve() {
        var a=new Builder();a.inv.set(1,new Stack("pistol",1));a.inv.set(6,new Stack("ammo",10));
        var result=ServiceNeeds.evaluate(withIncoming(List.of(reserved("ammo",54,0)),List.of(),List.of(a.build()),new Transfer(A,"station",0,6,-1,"ammo",54)));
        assertEquals(54,ammo(result,0).requested());assertEquals(54,ammo(result,0).incoming());assertEquals(0,ammo(result,0).missing());
        assertTrue(result.guards().get(0).transfers().isEmpty());
    }
    @Test void pendingGunUsesItsOwnReservedReturnSlotAndProjectsTheIncomingCaliber() {
        var a=new Builder();a.replace=true;a.rules.set(0,new Rule(true,Set.of("otherGun")));
        var result=ServiceNeeds.evaluate(withIncoming(List.of(reserved("otherGun",1,0),supply("otherAmmo",64,1)),List.of(),List.of(a.build()),new Transfer(A,"station",0,0,27,"otherGun",1)));
        var equipment=result.guards().get(0).rows().get(0);
        assertEquals(1,equipment.incoming());assertEquals(0,equipment.missing());
        assertFalse(equipment.reasons().contains(Reason.RETURN_FULL));
        assertEquals("pack:other_ammo",ammo(result,0).item());assertEquals(64,ammo(result,0).available());
        assertTrue(result.guards().get(0).transfers().stream().allMatch(t->t.destination()>=6));
    }
    @Test void incomingMedicineCountsTowardCombinedTreatmentReserve() {
        var a=new Builder();a.ammo=0;a.medical=true;a.meds=Set.of("bandage","medicine");a.inv.set(9,new Stack("medicine",10));
        var result=ServiceNeeds.evaluate(withIncoming(List.of(reserved("bandage",6,0)),List.of(),List.of(a.build()),new Transfer(A,"station",0,10,-1,"bandage",6)));
        var medical=result.guards().get(0).rows().stream().filter(r->r.category().equals("medical")).findFirst().orElseThrow();
        assertEquals(6,medical.requested());assertEquals(6,medical.incoming());assertEquals(0,medical.missing());
    }
    @Test void unreservedInaccessibleAndIncompatibleClaimsCannotCoverDemand() {
        var pending=new Transfer(A,"station",0,6,-1,"ammo",60);var a=new Builder();
        assertEquals(0,ammo(ServiceNeeds.evaluate(withIncoming(List.of(supply("ammo",60,0)),List.of(),List.of(a.build()),pending)),0).incoming());
        a.allowed=Set.of();
        assertEquals(0,ammo(ServiceNeeds.evaluate(withIncoming(List.of(reserved("ammo",60,0)),List.of(),List.of(a.build()),pending)),0).incoming());
        a.allowed=Set.of("station");a.inv.set(6,new Stack("tagged",4));
        assertEquals(0,ammo(ServiceNeeds.evaluate(withIncoming(List.of(reserved("ammo",60,0)),List.of(),List.of(a.build()),pending)),0).incoming());
    }
    @Test void duplicatePendingSourceCannotCoverTwoGuards() {
        var a=new Builder();var b=new Builder();b.id=B;
        assertThrows(IllegalArgumentException.class,()->new Input(facts(),List.of(reserved("ammo",60,0)),List.of(),List.of(a.build(),b.build()),compatibility(),
                Map.of(A,new Transfer(A,"station",0,6,-1,"ammo",60),B,new Transfer(B,"station",0,6,-1,"ammo",60))));
    }
    @Test void anotherCaliberCannotOccupyTheIncomingDeliverySlot() {
        var a=new Builder();a.inv.set(1,new Stack("otherGun",1));a.inv.set(7,new Stack("box",1));a.inv.set(8,new Stack("box",1));
        var result=ServiceNeeds.evaluate(withIncoming(List.of(reserved("otherAmmo",64,0),supply("ammo",64,1)),List.of(),List.of(a.build()),new Transfer(A,"station",0,6,-1,"otherAmmo",64)));
        var rows=result.guards().get(0).rows();
        var primary=rows.stream().filter(r->r.item().equals("pack:ammo")).findFirst().orElseThrow();
        var sidearm=rows.stream().filter(r->r.item().equals("pack:other_ammo")).findFirst().orElseThrow();
        assertEquals(0,primary.available());assertTrue(primary.reasons().contains(Reason.DESTINATION_FULL));
        assertEquals(64,sidearm.incoming());assertEquals(0,sidearm.missing());assertTrue(result.guards().get(0).transfers().isEmpty());
    }
    @Test void aNativeCrossCompatibleAmmoVariantCannotProjectOneDeliveryTwice() {
        var a=new Builder();a.ammo=128;a.inv.set(1,new Stack("otherGun",1));
        var compatible=new HashMap<>(compatibility());compatible.put("otherGun",Set.of("ammo"));
        var input=new Input(facts(),List.of(reserved("ammo",60,0)),List.of(),List.of(a.build()),compatible,
                Map.of(A,new Transfer(A,"station",0,6,-1,"ammo",60)));
        var result=ServiceNeeds.evaluate(input);
        assertEquals(60,result.guards().get(0).rows().stream().mapToInt(Row::incoming).sum());
        assertTrue(result.guards().get(0).transfers().isEmpty());
    }
}
