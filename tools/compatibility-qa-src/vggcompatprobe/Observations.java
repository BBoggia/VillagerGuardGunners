package vggcompatprobe;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.medical.TransportRuntime;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationSavedData;
import com.tacz.guns.api.item.IGun;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.fml.ModList;
import tallestegg.guardvillagers.entities.Guard;

/** Read-only evidence; controlled movement and lifecycle exercises live elsewhere. */
final class Observations {
    private static final List<Object> samples = new ArrayList<>();
    private static int discoveryMax, terrainMax, pathsMax, sampledTicks;

    static UUID village() {
        return new VillageIdentity(Probe.level.dimension().location(),
                ResourceLocation.parse((String) Probe.result.get("structure")),
                Probe.start.getChunkPos().toLong()).id();
    }

    static void verifyCodeSources() {
        Map<String, Object> sources = new LinkedHashMap<>();
        Map<String, Class<?>> classes = Map.of("villagerguardgunners", VillagerGuardGunners.class,
                "guardvillagers", Guard.class, "tacz", IGun.class, "vggcompatprobe", Probe.class);
        for (var entry : classes.entrySet()) {
            try {
                Path expected = Path.of(System.getProperty("vgg.compat.jar." + entry.getKey())).toRealPath();
                Path loaded = ModList.get().getModFileById(entry.getKey()).getFile().getFilePath().toRealPath();
                String source = entry.getValue().getProtectionDomain().getCodeSource().getLocation().toString();
                String decoded = URLDecoder.decode(source, StandardCharsets.UTF_8);
                if (!expected.equals(loaded) || !Files.isRegularFile(loaded)
                        || !decoded.contains(expected.toString() + "#") && !decoded.contains(expected.toString() + "!")) {
                    throw new IllegalStateException("Unexpected release code source for " + entry.getKey() + ": " + source);
                }
                sources.put(entry.getKey(), Map.of("jar", loaded.toString(), "classSource", source));
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot verify release code source " + entry.getKey(), failure);
            }
        }
        Probe.result.put("releaseCodeSources", sources);
        Probe.result.put("releaseCodeSourcesVerified", true);
    }

    static void resources() {
        var manager = Probe.level.getServer().getResourceManager();
        Set<ResourceLocation> selected = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        selected.add(ResourceLocation.parse("minecraft:tags/worldgen/structure/village.json"));
        ResourceLocation structure = ResourceLocation.parse((String) Probe.result.get("structure"));
        selected.add(ResourceLocation.parse(structure.getNamespace() + ":worldgen/structure/" + structure.getPath() + ".json"));
        var classification = com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions.lookup(structure);
        for (var piece : Probe.start.getPieces()) {
            var read = com.gamergaming.villagerguardgunners.village.compat.PieceReaders.read(piece, classification.reader());
            if (read.supported()) {
                var id = read.geometry().template();
                selected.add(ResourceLocation.parse(id.getNamespace() + ":structures/" + id.getPath() + ".nbt"));
            }
        }
        var geometry = Probe.start.getPieces().stream()
                .map(p -> com.gamergaming.villagerguardgunners.village.compat.PieceReaders.read(p, classification.reader()))
                .filter(r -> r.supported()).map(r -> r.geometry()).toList();
        var summary = com.gamergaming.villagerguardgunners.village.compat.SettlementObservations.inspect(classification, geometry,
                new HashSet<>(tallestegg.guardvillagers.configuration.GuardConfig.COMMON.structuresThatSpawnGuards.get()));
        Map<String, Object> meanings = new LinkedHashMap<>();
        meanings.put("intent", summary.intent().toString());
        meanings.put("sizeFamily", classification.sizeFamily());
        meanings.put("traversalLimits", classification.traversalLimits());
        meanings.put("residences", summary.residences());
        meanings.put("bedCapacity", summary.bedCapacity());
        meanings.put("entranceCandidates", summary.entranceCandidates());
        meanings.put("unverifiedPieces", summary.unverifiedPieces());
        meanings.put("nativeRequestTemplates", summary.nativeRequestTemplates());
        meanings.put("profiles", classification.profiles());
        meanings.put("reloadErrors", com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions.errors());
        meanings.put("pieces", geometry.stream().map(p -> Map.of("template", p.template().toString(),
                "meaning", classification.meaning(p.template().toString()))).toList());
        Probe.result.put("templateClassification", meanings);
        // Only pools from the settlement's own namespace plus vanilla village pools.
        manager.listResources("worldgen/template_pool", id -> id.getPath().endsWith(".json")
                && (id.getNamespace().equals(structure.getNamespace())
                || id.getNamespace().equals("minecraft") && id.getPath().startsWith("worldgen/template_pool/village/")))
                .keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).limit(512).forEach(selected::add);
        List<Object> rows = new ArrayList<>();
        for (var id : selected) {
            List<Object> stack = new ArrayList<>();
            for (var resource : manager.getResourceStack(id)) {
                try (var input = resource.open()) {
                    byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
                    if (bytes.length > 16 * 1024 * 1024) throw new IllegalStateException("Resource exceeds observer limit " + id);
                    stack.add(Map.of("provider", resource.sourcePackId(), "sha256",
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
                } catch (Exception failure) { throw new IllegalStateException("Cannot inspect " + id, failure); }
            }
            rows.add(Map.of("resource", id.toString(), "stackLowToHigh", stack,
                    "effectiveProvider", manager.getResource(id).map(r -> r.sourcePackId()).orElse("absent")));
        }
        Probe.result.put("resourceProviders", rows);
        Probe.result.put("resourceScope", "village tag, requested structure, readable templates, first 512 matching pools");
        Probe.write("resource-providers.json", rows);
    }

    static Map<String, Object> guard(Guard guard) {
        var service = GuardEquipmentServiceAccess.get(guard);
        var medic = GuardMedicAccess.get(guard);
        var path = guard.getNavigation().getPath();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("guard", guard.getUUID());
        row.put("position", List.of(guard.getX(), guard.getY(), guard.getZ()));
        row.put("movementOwners", guard.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.isRunning() && g.getFlags().contains(Goal.Flag.MOVE))
                .map(g -> g.getGoal().getClass().getName()).toList());
        row.put("navigationDone", guard.getNavigation().isDone());
        row.put("pathTarget", path == null ? "none" : path.getTarget().toShortString());
        row.put("pathCanReach", path != null && path.canReach());
        row.put("pathNextNode", path == null ? -1 : path.getNextNodeIndex());
        row.put("pathNodes", path == null ? 0 : path.getNodeCount());
        row.put("coverageAvailability", VillageCoverageRuntime.availability(guard).toString());
        row.put("servicePolicy", service.policy().toString());
        row.put("serviceStatus", service.status.toString());
        row.put("serviceReceiptRevision", service.receipt.revision());
        row.put("medicalStatus", medic.status.toString());
        row.put("transportReason", medic.transportReason);
        row.put("onRecoveryCot", TransportRuntime.onCot(guard));
        return row;
    }

    static void sample() {
        var discovery = VillageDiscoveryRuntime.diagnostics(Probe.level);
        var coverage = VillageCoverageRuntime.snapshot(Probe.level, village());
        discoveryMax = Math.max(discoveryMax, discovery.lastWorkUnits());
        terrainMax = Math.max(terrainMax, coverage.terrainUnits());
        pathsMax = Math.max(pathsMax, coverage.paths());
        sampledTicks++;
        if ((Probe.ticks - Probe.locatedTick) % 20 == 0 && samples.size() < 601) {
            samples.add(Map.of("elapsed", Probe.ticks - Probe.locatedTick, "gameTime", Probe.level.getGameTime(),
                    "guards", Probe.level.getEntitiesOfClass(Guard.class, Probe.area).stream()
                    .sorted(Comparator.comparing(Guard::getUUID)).limit(32).map(Observations::guard).toList()));
        }
    }

    static void diagnostics(Map<String, Object> result, UUID id) {
        result.put("villageIdentity", id.toString());
        result.put("gameTime", Probe.level.getGameTime());
        var population = VillagePopulationSavedData.get(Probe.level);
        var plan = population.plan(id);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("planPresent", plan != null);
        counts.put("observed", population.observed(id));
        counts.put("living", population.living(id));
        counts.put("spentCredits", population.spent(id));
        if (plan != null) {
            counts.put("target", plan.target());
            counts.put("allowance", plan.quotas().values().stream().mapToInt(Integer::intValue).sum());
            counts.put("residences", plan.residences());
            counts.put("sources", plan.sources().size());
        }
        result.put("issuance", counts);
        var snapshot = VillageCoverageRuntime.snapshot(Probe.level, id);
        if (snapshot.plan() != null) result.put("visitAges", snapshot.plan().areas().stream().map(area ->
                Map.of("area", area.areaId(), "reason", area.reason(), "lastSuccessfulVisit", area.lastSuccessfulVisit(),
                        "ageTicks", area.lastSuccessfulVisit() < 0 ? -1 : Probe.level.getGameTime() - area.lastSuccessfulVisit())).toList());
    }

    static void finish() {
        Probe.result.put("workBudgets", Map.of("sampledTicks", sampledTicks,
                "maximumDiscoveryUnits", discoveryMax, "maximumTerrainUnits", terrainMax,
                "maximumCoveragePaths", pathsMax, "discoveryLimit", 32,
                "terrainLimit", VillageCoverageRuntime.TERRAIN_UNITS_PER_TICK,
                "coveragePathLimit", VillageCoverageRuntime.PATHS_PER_TICK));
        Probe.result.put("serviceObservation", Map.of("kind", "passive_status_and_receipts",
                "controlledServiceScenario", "not_exercised", "sampleIntervalTicks", 20, "guardLimit", 32));
        Probe.write("movement-services.json", samples);
    }
}
