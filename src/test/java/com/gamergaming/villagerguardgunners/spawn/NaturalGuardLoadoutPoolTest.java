package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutPoolTest {

    @Test
    void weightedSelectionUsesExactBoundaries() {
        NaturalGuardLoadoutPool.Entry pistol = entry("pistol", 2);
        NaturalGuardLoadoutPool.Entry smg = entry("smg", 3);
        NaturalGuardLoadoutPool.Entry rifle = entry("rifle", 1);
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("test_pool"),
                List.of(pistol, smg, rifle));

        assertAll(
                () -> assertEquals(6, pool.totalWeight()),
                () -> assertEquals(pistol, pool.select(0)),
                () -> assertEquals(pistol, pool.select(1)),
                () -> assertEquals(smg, pool.select(2)),
                () -> assertEquals(smg, pool.select(4)),
                () -> assertEquals(rifle, pool.select(5)),
                () -> assertThrows(IllegalArgumentException.class, () -> pool.select(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> pool.select(6)));
    }

    @Test
    void poolDefensivelyCopiesEntriesAndExposesNoMutableCollection() {
        List<NaturalGuardLoadoutPool.Entry> source = new ArrayList<>();
        source.add(entry("pistol", 1));
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(id("immutable"), source);
        source.add(entry("rifle", 1));

        assertAll(
                () -> assertEquals(1, pool.entries().size()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> pool.entries().add(entry("smg", 1))));
    }

    @Test
    void malformedPoolsFailBeforeTheyCanReachSpawnSelection() {
        NaturalGuardLoadoutPool.Entry maximum = entry("maximum", Integer.MAX_VALUE);
        NaturalGuardLoadoutPool.Entry overflow = entry("overflow", 1);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(id("empty"), List.of())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(
                                id("duplicate"),
                                List.of(entry("same", 1), entry("same", 2)))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(
                                id("overflow"),
                                List.of(maximum, overflow))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool.Entry(id("zero"), 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool.Entry(id("negative"), -1)));
    }

    private static NaturalGuardLoadoutPool.Entry entry(String path, int weight) {
        return new NaturalGuardLoadoutPool.Entry(id(path), weight);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
