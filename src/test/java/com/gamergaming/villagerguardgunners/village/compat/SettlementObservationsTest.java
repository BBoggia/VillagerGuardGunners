package com.gamergaming.villagerguardgunners.village.compat;

import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementObservationsTest {
    private static VillageFootprint.Piece piece(String id, String rotation) {
        return new VillageFootprint.Piece(new BlockPos(10,70,20),rotation,ResourceLocation.parse(id),
                new VillageFootprint.Bounds(0,60,0,30,90,30));
    }
    @Test void rotationsPreserveHeightAndTranslateLocalPoints() {
        var points=List.of(new PieceMeaning.Point(2,3,4));
        assertEquals(List.of(new BlockPos(6,73,22)),SettlementObservations.worldPoints(piece("test:house","CLOCKWISE_90"),points));
        assertEquals(List.of(),SettlementObservations.worldPoints(piece("test:house","UNKNOWN"),points));
    }
    @Test void duplicateFragmentsDoNotDoubleResidentialDemandOrEntrances() {
        var meaning=new PieceMeaning(SettlementProfile.Kind.RESIDENCE,List.of(new PieceMeaning.Point(2,3,4)),
                List.of(new PieceMeaning.Point(0,0,0)),"pack","a".repeat(64),"verified_template");
        var decision=new SettlementCatalog.Decision(SettlementProfile.Intent.FRIENDLY,SettlementProfile.NATIVE,Map.of(),
                List.of(),List.of(),"profile",Map.of("test:a",meaning,"test:b",meaning),"small",Set.of(),true);
        var observed=SettlementObservations.inspect(decision,List.of(piece("test:a","NONE"),piece("test:b","NONE")),Set.of("test:b"));
        assertEquals(1,observed.residences());assertEquals(1,observed.bedCapacity());assertEquals(1,observed.entranceCandidates());
        assertEquals(Set.of("test:b"),observed.nativeRequestTemplates());
    }
}
