package com.gamergaming.villagerguardgunners.command;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDiagnosticSeverity;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutMatchContext;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPool;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutReloadSnapshot;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSidearmCompatibility;
import com.gamergaming.villagerguardgunners.sidearm.SidearmEligibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Bounded, non-mutating computations used by the natural-loadout commands. */
public final class NaturalGuardLoadoutCommandService {
    private NaturalGuardLoadoutCommandService() {
    }

    public static ValidationReport validate(
            NaturalGuardLoadoutReloadSnapshot snapshot,
            @Nullable ResourceLocation requestedPool) {
        return validate(
                snapshot,
                requestedPool,
                gunId -> diagnostic(NaturalGuardLoadoutCompatibilityAnalyzer.analyze(gunId)),
                NaturalGuardLoadoutCommandService::sidearmDiagnostic);
    }

    public static ValidationReport validate(
            NaturalGuardLoadoutReloadSnapshot snapshot,
            @Nullable ResourceLocation requestedPool,
            Function<ResourceLocation, CompatibilityDiagnostic> analyzer) {
        return validate(
                snapshot,
                requestedPool,
                analyzer,
                sidearmId -> assumedSidearmDiagnostic(analyzer.apply(sidearmId)));
    }

    public static ValidationReport validate(
            NaturalGuardLoadoutReloadSnapshot snapshot,
            @Nullable ResourceLocation requestedPool,
            Function<ResourceLocation, CompatibilityDiagnostic> analyzer,
            Function<ResourceLocation, SidearmDiagnostic> sidearmAnalyzer) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(analyzer, "analyzer");
        Objects.requireNonNull(sidearmAnalyzer, "sidearmAnalyzer");
        List<NaturalGuardLoadoutPool> pools = requestedPools(snapshot, requestedPool);
        Map<ResourceLocation, CompatibilityDiagnostic> analyses = new HashMap<>();
        Map<ResourceLocation, SidearmDiagnostic> sidearmAnalyses = new HashMap<>();
        List<ValidationIssue> issues = new ArrayList<>();
        int entries = 0;
        int compatible = 0;
        int contextDependent = 0;
        int warnings = 0;
        int errors = 0;
        int issueCount = 0;
        for (NaturalGuardLoadoutPool pool : pools) {
            for (int index = 0; index < pool.entries().size(); index++) {
                NaturalGuardLoadoutPool.Entry entry = pool.entries().get(index);
                entries++;
                CompatibilityDiagnostic analysis = analyses.computeIfAbsent(
                        entry.gunId(), analyzer);
                SidearmDiagnostic sidearm = entry.sidearmId() == null
                        ? null
                        : sidearmAnalyses.computeIfAbsent(
                                entry.sidearmId(), sidearmAnalyzer);
                NaturalGuardLoadoutDiagnosticSeverity severity =
                        entrySeverity(analysis, sidearm);
                boolean entryCompatible = analysis.compatible()
                        && (sidearm == null || sidearm.administrativelyCompatible());
                boolean entryContextDependent = analysis.explosionState()
                                == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState
                                        .CONTEXT_REQUIRED
                        || (sidearm != null && sidearm.contextRequired());
                if (entryCompatible) {
                    compatible++;
                    if (entryContextDependent) {
                        contextDependent++;
                    }
                }
                if (severity == NaturalGuardLoadoutDiagnosticSeverity.WARNING) {
                    warnings++;
                    issueCount++;
                } else if (severity == NaturalGuardLoadoutDiagnosticSeverity.ERROR) {
                    errors++;
                    issueCount++;
                }
                if (severity != NaturalGuardLoadoutDiagnosticSeverity.INFO
                        && issues.size()
                                < NaturalGuardLoadoutCommandContract
                                        .MAX_VALIDATION_ISSUES_IN_SUMMARY) {
                    boolean sidearmIssue = analysis.compatible() && sidearm != null;
                    CompatibilityDiagnostic issueAnalysis = sidearmIssue
                            ? sidearm.weapon()
                            : analysis;
                    issues.add(new ValidationIssue(
                            pool.id(),
                            index + 1,
                            sidearmIssue ? WeaponRole.SIDEARM : WeaponRole.PRIMARY,
                            sidearmIssue ? entry.sidearmId() : entry.gunId(),
                            severity,
                            issueAnalysis.status(),
                            issueAnalysis.explosionState(),
                            sidearmIssue ? sidearm.eligibility() : null));
                }
            }
        }
        return new ValidationReport(
                snapshot.generation(),
                pools.size(),
                entries,
                compatible,
                contextDependent,
                warnings,
                errors,
                requestedPool == null ? snapshot.rejectedPools().size() : 0,
                issueCount,
                issues);
    }

    public static InspectionPage inspect(NaturalGuardLoadoutPool pool, int page) {
        return inspect(
                pool,
                page,
                gunId -> diagnostic(NaturalGuardLoadoutCompatibilityAnalyzer.analyze(gunId)),
                NaturalGuardLoadoutCommandService::sidearmDiagnostic);
    }

    public static InspectionPage inspect(
            NaturalGuardLoadoutPool pool,
            int page,
            Function<ResourceLocation, CompatibilityDiagnostic> analyzer) {
        return inspect(
                pool,
                page,
                analyzer,
                sidearmId -> assumedSidearmDiagnostic(analyzer.apply(sidearmId)));
    }

    public static InspectionPage inspect(
            NaturalGuardLoadoutPool pool,
            int page,
            Function<ResourceLocation, CompatibilityDiagnostic> analyzer,
            Function<ResourceLocation, SidearmDiagnostic> sidearmAnalyzer) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(analyzer, "analyzer");
        Objects.requireNonNull(sidearmAnalyzer, "sidearmAnalyzer");
        int pageSize = NaturalGuardLoadoutCommandContract.ENTRIES_PER_PAGE;
        int pages = (pool.entries().size() + pageSize - 1) / pageSize;
        if (page < 1 || page > pages) {
            throw new IllegalArgumentException("Page must be between 1 and " + pages);
        }
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, pool.entries().size());
        Map<ResourceLocation, CompatibilityDiagnostic> analyses = new HashMap<>();
        Map<ResourceLocation, SidearmDiagnostic> sidearmAnalyses = new HashMap<>();
        List<InspectedEntry> inspected = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            NaturalGuardLoadoutPool.Entry entry = pool.entries().get(index);
            inspected.add(new InspectedEntry(
                    index + 1,
                    entry,
                    analyses.computeIfAbsent(entry.gunId(), analyzer),
                    entry.sidearmId() == null
                            ? null
                            : sidearmAnalyses.computeIfAbsent(
                                    entry.sidearmId(), sidearmAnalyzer)));
        }
        return new InspectionPage(pool, page, pages, inspected);
    }

    public static PreviewReport preview(
            NaturalGuardLoadoutPool pool,
            NaturalGuardLoadoutMatchContext context,
            boolean guardContextAvailable,
            @Nullable LivingEntity compatibilityContext) {
        return preview(
                pool,
                context,
                guardContextAvailable,
                gunId -> diagnostic(NaturalGuardLoadoutCompatibilityAnalyzer.analyze(
                        compatibilityContext,
                        gunId,
                        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REPORT)),
                sidearmId -> sidearmDiagnostic(NaturalGuardSidearmCompatibility.analyze(
                        compatibilityContext,
                        sidearmId,
                        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REPORT)));
    }

    public static PreviewReport preview(
            NaturalGuardLoadoutPool pool,
            NaturalGuardLoadoutMatchContext context,
            boolean guardContextAvailable,
            Function<ResourceLocation, CompatibilityDiagnostic> analyzer) {
        return preview(
                pool,
                context,
                guardContextAvailable,
                analyzer,
                sidearmId -> assumedSidearmDiagnostic(analyzer.apply(sidearmId)));
    }

    public static PreviewReport preview(
            NaturalGuardLoadoutPool pool,
            NaturalGuardLoadoutMatchContext context,
            boolean guardContextAvailable,
            Function<ResourceLocation, CompatibilityDiagnostic> analyzer,
            Function<ResourceLocation, SidearmDiagnostic> sidearmAnalyzer) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(analyzer, "analyzer");
        Objects.requireNonNull(sidearmAnalyzer, "sidearmAnalyzer");
        int matchingWeight = 0;
        int matchingEntries = 0;
        int contextRequiredEntries = 0;
        for (NaturalGuardLoadoutPool.Entry entry : pool.entries()) {
            if (!context.matchesEnvironment(entry.conditions())) {
                continue;
            }
            if (!guardContextAvailable && entry.conditions().requiresGuardTags()) {
                contextRequiredEntries++;
            } else if (context.matchesGuardTags(entry.conditions())) {
                matchingWeight += entry.weight();
                matchingEntries++;
            }
        }

        Map<ResourceLocation, CompatibilityDiagnostic> analyses = new HashMap<>();
        Map<ResourceLocation, SidearmDiagnostic> sidearmAnalyses = new HashMap<>();
        List<PreviewEntry> displayed = new ArrayList<>();
        if (matchingWeight > 0) {
            for (int index = 0; index < pool.entries().size(); index++) {
                NaturalGuardLoadoutPool.Entry entry = pool.entries().get(index);
                if (!context.matchesEnvironment(entry.conditions())
                        || (!guardContextAvailable && entry.conditions().requiresGuardTags())
                        || !context.matchesGuardTags(entry.conditions())) {
                    continue;
                }
                if (displayed.size() >= NaturalGuardLoadoutCommandContract.MAX_PREVIEW_ENTRIES) {
                    break;
                }
                CompatibilityDiagnostic analysis = analyses.computeIfAbsent(
                        entry.gunId(),
                        analyzer);
                displayed.add(new PreviewEntry(
                        index + 1,
                        entry,
                        analysis,
                        entry.sidearmId() == null
                                ? null
                                : sidearmAnalyses.computeIfAbsent(
                                        entry.sidearmId(), sidearmAnalyzer),
                        (double) entry.weight() / (double) matchingWeight));
            }
        }
        return new PreviewReport(
                pool.id(),
                matchingWeight,
                matchingEntries,
                contextRequiredEntries,
                displayed);
    }

    private static List<NaturalGuardLoadoutPool> requestedPools(
            NaturalGuardLoadoutReloadSnapshot snapshot,
            @Nullable ResourceLocation requestedPool) {
        if (requestedPool == null) {
            return List.copyOf(snapshot.pools().values());
        }
        NaturalGuardLoadoutPool pool = snapshot.get(requestedPool);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown natural loadout pool: " + requestedPool);
        }
        return List.of(pool);
    }

    private static NaturalGuardLoadoutDiagnosticSeverity entrySeverity(
            CompatibilityDiagnostic analysis,
            @Nullable SidearmDiagnostic sidearm) {
        if (!analysis.compatible()) {
            return NaturalGuardLoadoutDiagnosticSeverity.ERROR;
        }
        if (sidearm == null) {
            return NaturalGuardLoadoutDiagnosticSeverity.INFO;
        }
        if (!sidearm.administrativelyCompatible()) {
            return NaturalGuardLoadoutDiagnosticSeverity.ERROR;
        }
        if (sidearm.contextRequired()) {
            return NaturalGuardLoadoutDiagnosticSeverity.WARNING;
        }
        return NaturalGuardLoadoutDiagnosticSeverity.INFO;
    }

    private static CompatibilityDiagnostic diagnostic(
            NaturalGuardLoadoutCompatibilityAnalyzer.Analysis analysis) {
        return new CompatibilityDiagnostic(
                analysis.status(),
                analysis.ammoId(),
                analysis.ammunitionItems(),
                analysis.explosionState(),
                analysis.explosionRadius());
    }

    private static SidearmDiagnostic sidearmDiagnostic(ResourceLocation sidearmId) {
        return sidearmDiagnostic(NaturalGuardSidearmCompatibility.analyze(sidearmId));
    }

    private static SidearmDiagnostic sidearmDiagnostic(
            NaturalGuardSidearmCompatibility.Analysis analysis) {
        return new SidearmDiagnostic(
                diagnostic(analysis.weapon()),
                analysis.eligibility(),
                analysis.contextRequired());
    }

    private static SidearmDiagnostic assumedSidearmDiagnostic(
            CompatibilityDiagnostic weapon) {
        return new SidearmDiagnostic(
                weapon,
                weapon.compatible()
                        ? SidearmEligibility.EligibilityStatus.ELIGIBLE_BY_GUN_TYPE
                        : SidearmEligibility.EligibilityStatus.DEFINITION_UNAVAILABLE,
                false);
    }

    /** Item-free compatibility metadata retained by command reports. */
    public record CompatibilityDiagnostic(
            NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus status,
            @Nullable ResourceLocation ammoId,
            int ammunitionItems,
            NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState explosionState,
            float explosionRadius) {
        public CompatibilityDiagnostic {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(explosionState, "explosionState");
            if (ammunitionItems < 0
                    || !Float.isFinite(explosionRadius)
                    || explosionRadius < 0.0F
                    || (explosionState
                                    != NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState
                                            .EXPLOSIVE
                            && explosionRadius != 0.0F)) {
                throw new IllegalArgumentException("Compatibility diagnostic is inconsistent");
            }
        }

        public boolean compatible() {
            return status
                    == NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus.COMPATIBLE;
        }
    }

    public record SidearmDiagnostic(
            CompatibilityDiagnostic weapon,
            SidearmEligibility.EligibilityStatus eligibility,
            boolean contextRequired) {
        public SidearmDiagnostic {
            weapon = Objects.requireNonNull(weapon, "weapon");
            eligibility = Objects.requireNonNull(eligibility, "eligibility");
            if (contextRequired
                    && (!weapon.compatible()
                            || weapon.explosionState()
                                    != NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState
                                            .CONTEXT_REQUIRED)) {
                throw new IllegalArgumentException("Sidearm diagnostic is inconsistent");
            }
        }

        public boolean administrativelyCompatible() {
            return weapon.compatible() && eligibility.eligible();
        }
    }

    public enum WeaponRole {
        PRIMARY,
        SIDEARM
    }

    public record ValidationIssue(
            ResourceLocation poolId,
            int entryNumber,
            WeaponRole role,
            ResourceLocation gunId,
            NaturalGuardLoadoutDiagnosticSeverity severity,
            NaturalGuardLoadoutCompatibilityAnalyzer.CompatibilityStatus status,
            NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState explosionState,
            @Nullable SidearmEligibility.EligibilityStatus sidearmEligibility) {
        public ValidationIssue {
            Objects.requireNonNull(poolId, "poolId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(gunId, "gunId");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(explosionState, "explosionState");
            if (entryNumber < 1
                    || severity == NaturalGuardLoadoutDiagnosticSeverity.INFO
                    || (role == WeaponRole.PRIMARY && sidearmEligibility != null)
                    || (role == WeaponRole.SIDEARM && sidearmEligibility == null)) {
                throw new IllegalArgumentException("Validation issues must be warning or error entries");
            }
        }
    }

    public record ValidationReport(
            long generation,
            int pools,
            int entries,
            int compatible,
            int contextDependent,
            int warnings,
            int errors,
            int rejectedDocuments,
            int issueCount,
            List<ValidationIssue> displayedIssues) {
        public ValidationReport {
            displayedIssues = List.copyOf(Objects.requireNonNull(
                    displayedIssues, "displayedIssues"));
            if (generation < 0L
                    || pools < 0
                    || entries < 0
                    || compatible < 0
                    || contextDependent < 0
                    || warnings < 0
                    || errors < 0
                    || rejectedDocuments < 0
                    || issueCount < 0
                    || compatible + errors != entries
                    || contextDependent > compatible
                    || warnings > compatible
                    || warnings + errors != issueCount
                    || displayedIssues.size() > issueCount
                    || displayedIssues.size()
                            > NaturalGuardLoadoutCommandContract
                                    .MAX_VALIDATION_ISSUES_IN_SUMMARY) {
                throw new IllegalArgumentException("Validation report counts are inconsistent");
            }
        }

        public int omittedIssues() {
            return issueCount - displayedIssues.size();
        }
    }

    public record InspectedEntry(
            int entryNumber,
            NaturalGuardLoadoutPool.Entry entry,
            CompatibilityDiagnostic analysis,
            @Nullable SidearmDiagnostic sidearmAnalysis) {
        public InspectedEntry {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(analysis, "analysis");
            if (entryNumber < 1) {
                throw new IllegalArgumentException("Entry numbers are one-based");
            }
        }
    }

    public record InspectionPage(
            NaturalGuardLoadoutPool pool,
            int page,
            int pages,
            List<InspectedEntry> entries) {
        public InspectionPage {
            Objects.requireNonNull(pool, "pool");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            if (page < 1
                    || pages < 1
                    || page > pages
                    || entries.isEmpty()
                    || entries.size() > NaturalGuardLoadoutCommandContract.ENTRIES_PER_PAGE) {
                throw new IllegalArgumentException("Inspection page is inconsistent");
            }
        }
    }

    public record PreviewEntry(
            int entryNumber,
            NaturalGuardLoadoutPool.Entry entry,
            CompatibilityDiagnostic analysis,
            @Nullable SidearmDiagnostic sidearmAnalysis,
            double probability) {
        public PreviewEntry {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(analysis, "analysis");
            if (entryNumber < 1
                    || !Double.isFinite(probability)
                    || probability <= 0.0D
                    || probability > 1.0D) {
                throw new IllegalArgumentException("Preview entry is inconsistent");
            }
        }
    }

    public record PreviewReport(
            ResourceLocation poolId,
            int matchingWeight,
            int matchingEntries,
            int contextRequiredEntries,
            List<PreviewEntry> displayedEntries) {
        public PreviewReport {
            Objects.requireNonNull(poolId, "poolId");
            displayedEntries = List.copyOf(Objects.requireNonNull(
                    displayedEntries, "displayedEntries"));
            if (matchingWeight < 0
                    || matchingEntries < 0
                    || contextRequiredEntries < 0
                    || (matchingEntries == 0) != (matchingWeight == 0)
                    || displayedEntries.size() > matchingEntries
                    || displayedEntries.size()
                            > NaturalGuardLoadoutCommandContract.MAX_PREVIEW_ENTRIES) {
                throw new IllegalArgumentException("Preview report counts are inconsistent");
            }
        }

        public int omittedMatchingEntries() {
            return matchingEntries - displayedEntries.size();
        }
    }
}
