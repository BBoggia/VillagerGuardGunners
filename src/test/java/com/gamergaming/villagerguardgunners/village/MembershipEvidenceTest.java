package com.gamergaming.villagerguardgunners.village;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
class MembershipEvidenceTest {
    static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    static VillageFootprint.Piece piece(int x, int y) {
        return new VillageFootprint.Piece(new BlockPos(x,y,0), "NONE", id("test:house"),
            new VillageFootprint.Bounds(x,y,0,x+7,y+8,15));
    }
    @Test void courtyardHomesRetainElevationAndSupportNewBeds() {
        var dim=id("minecraft:overworld");var data=new VillageRegistrySavedData(dim);
        var village=new VillageIdentity(dim,id("test:village"),0);
        data.observe(village,VillageFootprint.of(List.of(piece(0,60),piece(24,60))));
        var bed=new BlockPos(16,64,3);
        assertTrue(data.observeHomes(SectionPos.asLong(bed),List.of(bed)));
        assertTrue(data.get(village.id()).homes().contains(bed));
        assertTrue(data.observeHomes(SectionPos.asLong(bed),List.of(bed)));
        assertTrue(data.get(village.id()).homes().contains(bed));
        assertNull(data.membershipAt(new BlockPos(16,0,3)).unambiguous());
        assertTrue(data.observeHomes(SectionPos.asLong(bed),List.of()));
        assertTrue(data.get(village.id()).homes().isEmpty());
    }
    @Test void unknownAndTallVolumesRemainUnresolved() {
        for (var p : List.of(new VillageFootprint.Piece(BlockPos.ZERO,"UNKNOWN",id("villagerguardgunners:unknown_piece"),
                new VillageFootprint.Bounds(0,60,0,15,180,15)),
                new VillageFootprint.Piece(BlockPos.ZERO,"NONE",id("test:tall"),new VillageFootprint.Bounds(0,60,0,15,180,15)))) {
            var footprint=VillageFootprint.of(List.of(p));
            assertEquals(VillageFootprint.Evidence.UNRESOLVED,footprint.membershipEvidence(new BlockPos(3,64,3),0,new VillageFootprint.EvidenceBudget(512)));
            assertFalse(footprint.supportsMembership(new BlockPos(3,64,3)));
        }
    }
    @Test void sharedBudgetStopsAndRetainsOldSection() {
        var dim=id("minecraft:overworld");var data=new VillageRegistrySavedData(dim);
        var village=new VillageIdentity(dim,id("test:village"),0);
        data.observe(village,VillageFootprint.of(List.of(piece(0,60))));
        var old=new BlockPos(3,64,3);data.observeHomes(SectionPos.asLong(old),List.of(old));
        var pieces=new ArrayList<VillageFootprint.Piece>();
        for(int i=0;i<511;i++)pieces.add(piece(0,160));
        pieces.add(piece(0,60));data.observe(village,VillageFootprint.of(pieces));
        var positions=new ArrayList<BlockPos>();for(int x=0;x<16;x++)positions.add(new BlockPos(x,64,3));
        data.setDirty(false);
        assertFalse(data.observeHomes(SectionPos.asLong(old),positions));
        assertEquals(Set.of(old),data.get(village.id()).homes());assertFalse(data.isDirty());
        var budget=new VillageFootprint.EvidenceBudget(3);
        assertEquals(VillageFootprint.Evidence.UNRESOLVED,data.get(village.id()).footprint().membershipEvidence(old,0,budget));
        assertEquals(0,budget.remaining());
    }
    @Test void alarmsStayLocalAndRespectAffiliation() {
        var dim=id("minecraft:overworld");var data=new VillageRegistrySavedData(dim);
        var ground=new VillageIdentity(dim,id("test:ground"),0);
        var sky=new VillageIdentity(dim,id("test:sky"),0);
        data.observe(ground,VillageFootprint.of(List.of(piece(0,60),piece(24,60))));
        data.observe(sky,VillageFootprint.of(List.of(piece(0,160),piece(24,160))));
        assertEquals(ground.id(),data.alarmVillage(new BlockPos(3,64,3),null));
        assertEquals(sky.id(),data.alarmVillage(new BlockPos(3,164,3),null));
        assertNull(data.alarmVillage(new BlockPos(3,64,3),sky.id()));
        assertNull(data.alarmVillage(new BlockPos(3,110,3),null));
        assertEquals(ground.id(),data.alarmVillage(new BlockPos(16,64,3),ground.id()));
        assertNull(data.alarmVillage(new BlockPos(100,64,3),ground.id()));
        var overlap=new VillageIdentity(dim,id("test:overlap"),0);
        data.observe(overlap,VillageFootprint.of(List.of(piece(0,60))));
        assertNull(data.alarmVillage(new BlockPos(3,64,3),null));
    }

}
