package com.gamergaming.villagerguardgunners.village.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import org.junit.jupiter.api.Test;

class IntegratedVillageTagCorrectionTest {
    @Test void onlyPinnedVersionResourceAndUnresolvedEntryQualify() {
        String version = IntegratedVillageTagCorrection.VERSION, hash = IntegratedVillageTagCorrection.RESOURCE_HASH;
        assertTrue(IntegratedVillageTagCorrection.verified(version, "47.4.10", hash, false));
        assertFalse(IntegratedVillageTagCorrection.verified("1.3.3+1.20.1-forge", "47.4.10", hash, false));
        assertFalse(IntegratedVillageTagCorrection.verified(version, "47.4.23", hash, false));
        assertFalse(IntegratedVillageTagCorrection.verified(version, "47.4.10", "changed", false));
        assertFalse(IntegratedVillageTagCorrection.verified(version, "47.4.10", hash, true));
    }
    @Test void missingAdditionStillBreaksTagAfterForgeRemovalButScopedCorrectionPreservesContributions() {
        var vanilla = ResourceLocation.parse("minecraft:village_plains");
        var clockwork = ResourceLocation.parse("integrated_villages:clockwork_village");
        var contributed = ResourceLocation.parse("test:contributed_village");
        var dependent = ResourceLocation.parse("test:dependent");
        var village = IntegratedVillageTagCorrection.VILLAGE;
        var missing = IntegratedVillageTagCorrection.MISSING;
        String owner = "integrated_villages-1.3.2+1.20.1-forge.jar";
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags = new HashMap<>();
        tags.put(village, new ArrayList<>(List.of(new TagLoader.EntryWithSource(TagEntry.element(vanilla), "vanilla"),
                new TagLoader.EntryWithSource(TagEntry.element(clockwork), owner),
                new TagLoader.EntryWithSource(TagEntry.element(missing), owner),
                new TagLoader.EntryWithSource(TagEntry.element(contributed), "second-pack"),
                new TagLoader.EntryWithSource(TagEntry.element(missing), "remove-pack", true))));
        tags.put(dependent, List.of(new TagLoader.EntryWithSource(TagEntry.tag(village), "second-pack")));
        var loader = new TagLoader<String>(id -> Set.of(vanilla, clockwork, contributed).contains(id) ? Optional.of(id.toString()) : Optional.empty(), "tags/worldgen/structure");
        assertFalse(loader.build(tags).containsKey(village));
        assertEquals(1, IntegratedVillageTagCorrection.removeBrokenEntry(tags, owner));
        var built = loader.build(tags);
        assertEquals(Set.of(vanilla.toString(), clockwork.toString(), contributed.toString()), Set.copyOf(built.get(village)));
        assertEquals(built.get(village), built.get(dependent));
        assertEquals(0, IntegratedVillageTagCorrection.removeBrokenEntry(tags, owner));
        tags.get(village).add(new TagLoader.EntryWithSource(TagEntry.element(missing), "other-broken-pack"));
        assertEquals(0, IntegratedVillageTagCorrection.removeBrokenEntry(tags, owner));
        assertFalse(loader.build(tags).containsKey(village));
    }
}
