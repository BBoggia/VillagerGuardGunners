package com.gamergaming.villagerguardgunners.village.compat;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetainedTemplateEvidenceTest {
    static final String TEMPLATE="test:house", HASH="a".repeat(64);
    static SettlementCatalog catalog() {
        var meaning=new PieceMeaning(SettlementProfile.Kind.RESIDENCE,List.of(new PieceMeaning.Point(1,2,3)),
                List.of(new PieceMeaning.Point(0,1,0)),"old-pack",HASH,"verified_template");
        var profile=new SettlementProfile("test:profile","definition-pack",20,SettlementProfile.Intent.FRIENDLY,
                SettlementProfile.NATIVE,Set.of("test:village"),Set.of(),Map.of(TEMPLATE,meaning.kind()),Set.of(),
                Map.of(TEMPLATE,meaning),"small",Set.of(),true);
        return new SettlementCatalog(5,List.of(profile),Map.of(),Map.of("test:village",SettlementCatalog.resolve(List.of(profile),"test:village",Set.of())));
    }
    @Test void invalidProfileWithUnchangedResourcesRetainsExactSnapshot() {
        var before=catalog();assertSame(before,before.revalidateTemplates(Map.of(TEMPLATE,new PieceMeaning.ResourceEvidence("old-pack",HASH))));
    }
    @Test void invalidProfileCannotRetainHintsForReplacedTemplate() {
        var before=catalog();var after=before.revalidateTemplates(Map.of(TEMPLATE,new PieceMeaning.ResourceEvidence("new-pack","b".repeat(64))));
        assertEquals(6,after.generation());assertTrue(after.get("test:village").friendly());
        assertEquals(before.get("test:village").profiles(),after.get("test:village").profiles());
        assertEquals("small",after.get("test:village").sizeFamily());
        assertEquals(SettlementProfile.Kind.UNSUPPORTED,after.get("test:village").kind(TEMPLATE));
        assertEquals(List.of(),after.get("test:village").meaning(TEMPLATE).beds());
        assertEquals("new-pack",after.get("test:village").meaning(TEMPLATE).resourceProvider());
    }
    @Test void MissingEvidenceRevokesHintsAndRepeatedFailureDoesNotAdvanceAgain() {
        var after=catalog().revalidateTemplates(Map.of());
        assertEquals(6,after.generation());assertEquals(List.of(),after.get("test:village").meaning(TEMPLATE).entrances());
        assertSame(after,after.revalidateTemplates(Map.of()));
    }
}
