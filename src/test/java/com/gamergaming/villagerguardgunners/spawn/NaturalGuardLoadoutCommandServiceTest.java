package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandContract;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.CompatibilityDiagnostic;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.SidearmDiagnostic;
import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandService.WeaponRole;
import com.gamergaming.villagerguardgunners.sidearm.SidearmEligibility;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.SelectorGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutCommandServiceTest {

    @Test
    void validationIsGenerationStableCachedAndCapped() {
        List<NaturalGuardLoadoutPool.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            entries.add(new NaturalGuardLoadoutPool.Entry(id("gun_" + index), 1));
        }
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(id("large"), entries);
        NaturalGuardLoadoutReloadSnapshot snapshot = snapshot(pool);
        AtomicInteger calls = new AtomicInteger();

        var report = NaturalGuardLoadoutCommandService.validate(
                snapshot,
                null,
                gunId -> {
                    calls.incrementAndGet();
                    return failed(gunId);
                });

        assertAll(
                () -> assertEquals(7L, report.generation()),
                () -> assertEquals(1, report.pools()),
                () -> assertEquals(25, report.entries()),
                () -> assertEquals(25, report.errors()),
                () -> assertEquals(0, report.contextDependent()),
                () -> assertEquals(25, report.issueCount()),
                () -> assertEquals(
                        NaturalGuardLoadoutCommandContract.MAX_VALIDATION_ISSUES_IN_SUMMARY,
                        report.displayedIssues().size()),
                () -> assertEquals(5, report.omittedIssues()),
                () -> assertEquals(25, calls.get()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> report.displayedIssues().clear()));
    }

    @Test
    void contextDependentExplosiveAnalysisIsReportedWithoutWarningNoise() {
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("context_dependent"),
                List.of(new NaturalGuardLoadoutPool.Entry(id("usable_gun"), 1)));

        var report = NaturalGuardLoadoutCommandService.validate(
                snapshot(pool),
                pool.id(),
                gunId -> new CompatibilityDiagnostic(
                        CompatibilityStatus.COMPATIBLE,
                        id("ammo"),
                        60,
                        ExplosionState.CONTEXT_REQUIRED,
                        0.0F));

        assertAll(
                () -> assertEquals(1, report.compatible()),
                () -> assertEquals(1, report.contextDependent()),
                () -> assertEquals(0, report.warnings()),
                () -> assertEquals(0, report.errors()),
                () -> assertEquals(0, report.issueCount()),
                () -> assertTrue(report.displayedIssues().isEmpty()));
    }

    @Test
    void formatThreeReportsSidearmEligibilityAndContextSeparately() {
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("sidearm_diagnostics"),
                NaturalGuardLoadoutFormat.SIDEARM_VERSION,
                List.of(
                        new NaturalGuardLoadoutPool.Entry(
                                id("rifle"), id("context_sidearm"), 1),
                        new NaturalGuardLoadoutPool.Entry(
                                id("rifle"), id("bad_sidearm"), 1)));

        var report = NaturalGuardLoadoutCommandService.validate(
                snapshot(pool),
                pool.id(),
                ignored -> compatible(ExplosionState.NON_EXPLOSIVE),
                sidearmId -> sidearmId.equals(id("context_sidearm"))
                        ? new SidearmDiagnostic(
                                compatible(ExplosionState.CONTEXT_REQUIRED),
                                SidearmEligibility.EligibilityStatus.ELIGIBLE_BY_GUN_TYPE,
                                true)
                        : new SidearmDiagnostic(
                                compatible(ExplosionState.NON_EXPLOSIVE),
                                SidearmEligibility.EligibilityStatus.UNSUPPORTED_GUN_TYPE,
                                false));

        assertAll(
                () -> assertEquals(2, report.entries()),
                () -> assertEquals(1, report.compatible()),
                () -> assertEquals(1, report.contextDependent()),
                () -> assertEquals(1, report.warnings()),
                () -> assertEquals(1, report.errors()),
                () -> assertEquals(2, report.issueCount()),
                () -> assertTrue(report.displayedIssues().stream()
                        .allMatch(issue -> issue.role() == WeaponRole.SIDEARM)),
                () -> assertTrue(report.displayedIssues().stream()
                        .anyMatch(issue -> issue.sidearmEligibility()
                                == SidearmEligibility.EligibilityStatus
                                        .UNSUPPORTED_GUN_TYPE)));
    }

    @Test
    void inspectionValidatesPageBeforeAnalysisAndAnalyzesOnlyThatPage() {
        List<NaturalGuardLoadoutPool.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            entries.add(new NaturalGuardLoadoutPool.Entry(id("inspect_" + index), 1));
        }
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(id("inspect"), entries);
        AtomicInteger calls = new AtomicInteger();

        var page = NaturalGuardLoadoutCommandService.inspect(
                pool,
                2,
                gunId -> {
                    calls.incrementAndGet();
                    return failed(gunId);
                });

        assertAll(
                () -> assertEquals(2, page.page()),
                () -> assertEquals(2, page.pages()),
                () -> assertEquals(1, page.entries().size()),
                () -> assertEquals(9, page.entries().get(0).entryNumber()),
                () -> assertEquals(1, calls.get()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NaturalGuardLoadoutCommandService.inspect(
                                pool, 0, gunId -> failed(gunId))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NaturalGuardLoadoutCommandService.inspect(
                                pool, 3, gunId -> failed(gunId))));
    }

    @Test
    void previewSeparatesUnknownGuardTagsAndBoundsDisplayedMatches() {
        NaturalGuardLoadoutConditionSpec requiredTag = conditions(
                Set.of(NaturalGuardDifficulty.NORMAL), Set.of("elite"));
        NaturalGuardLoadoutConditionSpec wrongDifficultyAndTag = conditions(
                Set.of(NaturalGuardDifficulty.HARD), Set.of("elite"));
        List<NaturalGuardLoadoutPool.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            entries.add(new NaturalGuardLoadoutPool.Entry(id("preview_" + index), 1));
        }
        entries.add(new NaturalGuardLoadoutPool.Entry(id("tagged"), 4, requiredTag));
        entries.add(new NaturalGuardLoadoutPool.Entry(id("wrong"), 8, wrongDifficultyAndTag));
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("preview"), NaturalGuardLoadoutFormat.CONDITIONAL_VERSION, entries);
        NaturalGuardLoadoutMatchContext context = new NaturalGuardLoadoutMatchContext(
                id("overworld"),
                Set.of(),
                Set.of(),
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE,
                false,
                Set.of());

        var report = NaturalGuardLoadoutCommandService.preview(
                pool, context, false, NaturalGuardLoadoutCommandServiceTest::failed);

        assertAll(
                () -> assertEquals(25, report.matchingWeight()),
                () -> assertEquals(25, report.matchingEntries()),
                () -> assertEquals(1, report.contextRequiredEntries()),
                () -> assertEquals(
                        NaturalGuardLoadoutCommandContract.MAX_PREVIEW_ENTRIES,
                        report.displayedEntries().size()),
                () -> assertEquals(5, report.omittedMatchingEntries()),
                () -> assertTrue(report.displayedEntries().stream()
                        .allMatch(entry -> entry.probability() == 1.0D / 25.0D)));
    }

    private static NaturalGuardLoadoutConditionSpec conditions(
            Set<NaturalGuardDifficulty> difficulties,
            Set<String> requiredTags) {
        return new NaturalGuardLoadoutConditionSpec(
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                difficulties,
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                requiredTags,
                Set.of());
    }

    private static NaturalGuardLoadoutReloadSnapshot snapshot(NaturalGuardLoadoutPool pool) {
        Map<ResourceLocation, NaturalGuardLoadoutPool> pools = new LinkedHashMap<>();
        pools.put(pool.id(), pool);
        return NaturalGuardLoadoutReloadSnapshot.create(7L, pools, List.of());
    }

    private static CompatibilityDiagnostic failed(ResourceLocation gunId) {
        return new CompatibilityDiagnostic(
                CompatibilityStatus.GUN_DEFINITION_UNAVAILABLE,
                null,
                0,
                ExplosionState.NOT_EVALUATED,
                0.0F);
    }

    private static CompatibilityDiagnostic compatible(ExplosionState explosionState) {
        return new CompatibilityDiagnostic(
                CompatibilityStatus.COMPATIBLE,
                id("ammo"),
                60,
                explosionState,
                0.0F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
