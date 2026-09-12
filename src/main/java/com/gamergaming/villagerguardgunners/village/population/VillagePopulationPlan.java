package com.gamergaming.villagerguardgunners.village.population;

import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.gamergaming.villagerguardgunners.village.VillageIdentity;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Frozen issuance inputs. Placement order, later beds, and deaths cannot enlarge a plan. */
public record VillagePopulationPlan(VillageIdentity identity, List<Source> sources,
                                   int residences, int areas, int defenseBuildings, int target,
                                   Map<UUID, Integer> quotas) {
    public static final int MAX_SOURCES = 128, LEGACY_CAP = 32, HARD_CAP = 64;
    public VillagePopulationPlan {
        Objects.requireNonNull(identity);
        sources = List.copyOf(sources);
        quotas = Map.copyOf(quotas);
        if (sources.isEmpty() || sources.size() > MAX_SOURCES || residences < 0 || residences > MAX_SOURCES
                || areas < 0 || areas > MAX_SOURCES || defenseBuildings < 0 || defenseBuildings > MAX_SOURCES
                || target < 0 || target > HARD_CAP) throw new IllegalArgumentException("Population plan outside bounds");
        Set<UUID> ids = new HashSet<>();
        int total = 0;
        for (Source source : sources) {
            if (!ids.add(source.id())) throw new IllegalArgumentException("Duplicate population source");
            int quota = quotas.getOrDefault(source.id(), -1);
            if (quota < 0 || quota > source.capacity()) throw new IllegalArgumentException("Invalid source quota");
            total += quota;
        }
        if (!ids.equals(quotas.keySet()) || total > target) throw new IllegalArgumentException("Invalid allowance");
        VillageFootprint.of(sources.stream().map(Source::piece).toList());
    }
    public static VillagePopulationPlan create(VillageIdentity identity, List<Source> observations, Settings settings, int adjustment) {
        if (observations.isEmpty() || observations.size() > MAX_SOURCES || (adjustment < -HARD_CAP || adjustment > HARD_CAP)) {
            throw new IllegalArgumentException("Unresolved population observations");
        }
        List<Source> sources = observations.stream().distinct().sorted(Comparator.comparingInt(Source::priority)
                .thenComparing(Source::id)).toList();
        int homes = (int) sources.stream().filter(Source::residence).count();
        Set<Long> occupied = new HashSet<>();
        for (Source source : sources) {
            if (!source.residence() && !source.building()) continue;
            var b = source.piece().bounds();
            int x = Math.floorDiv(Math.floorDiv(b.minX() + b.maxX(), 2), 32), z = Math.floorDiv(Math.floorDiv(b.minZ() + b.maxZ(), 2), 32);
            occupied.add(((long) x << 32) | (z & 0xffffffffL));
        }
        int buildings = (int) sources.stream().filter(s -> s.building() && s.capacity() > 0).count();
        int target = settings.target(homes, occupied.size(), buildings, adjustment);
        Map<UUID, Integer> quotas = new LinkedHashMap<>();
        sources.forEach(s -> quotas.put(s.id(), 0));
        int remaining = target;
        // Distribute each tier in rounds; a late-loading garrison keeps its reserved share.
        for (boolean buildingsFirst : new boolean[]{true, false}) {
            for (int round = 0; round < HARD_CAP && remaining > 0; round++) {
                for (Source source : sources) if (source.building() == buildingsFirst && source.capacity() > round && remaining > 0) {
                    quotas.put(source.id(), round + 1);
                    remaining--;
                }
            }
        }
        return new VillagePopulationPlan(identity, sources, homes, occupied.size(), buildings, target, quotas);
    }
    public Source source(UUID id) { return sources.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public int allowance() { return quotas.values().stream().mapToInt(Integer::intValue).sum(); }
    public UUID slot(UUID source, int ordinal) {
        if (ordinal < 0 || ordinal >= quotas.getOrDefault(source, 0)) throw new IllegalArgumentException("Slot outside allowance");
        return id(identity.id() + ":population:" + source + ":" + ordinal);
    }
    public static UUID sourceId(VillageFootprint.Piece piece) {
        // Native terrain adaptation changes Y and reconstructed bounding boxes after placement.
        return id(piece.template() + ":" + piece.position().getX() + ":" + piece.position().getZ() + ":" + piece.rotation());
    }
    private static UUID id(String text) { return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)); }
    public record Source(UUID id, VillageFootprint.Piece piece, boolean building, boolean residence, int capacity, int priority) {
        /** Existing callers describe residences or defense buildings. */
        public Source(UUID id, VillageFootprint.Piece piece, boolean building, int capacity, int priority) {
            this(id, piece, building, !building, capacity, priority);
        }
        public Source {
            Objects.requireNonNull(piece);
            if (!sourceId(piece).equals(id) || capacity < 0 || capacity > (building ? 4 : residence ? 2 : HARD_CAP)
                    || (building && residence)
                    || priority < 0 || priority > 10) throw new IllegalArgumentException("Invalid population source");
        }
    }
    public record Settings(int minimum, int maximum, int homesPerGuard, int areasPerGuard, int defenseBonusCap) {
        public static final Settings DEFAULT = new Settings(2, 16, 3, 2, 2);
        public Settings {
            if (minimum < 0 || maximum < minimum || maximum > HARD_CAP || homesPerGuard < 1 || homesPerGuard > 64
                    || areasPerGuard < 1 || areasPerGuard > 64 || defenseBonusCap < 0 || defenseBonusCap > HARD_CAP) {
                throw new IllegalArgumentException("Invalid population settings");
            }
        }
        public int target(int homes, int areas, int buildings, int adjustment) {
            if (homes < 0 || homes > MAX_SOURCES || areas < 0 || areas > MAX_SOURCES || buildings < 0 || buildings > MAX_SOURCES
                    || (adjustment < -HARD_CAP || adjustment > HARD_CAP)) throw new IllegalArgumentException("Invalid staffing input");
            return Math.max(minimum, Math.min(maximum, (homes + homesPerGuard - 1) / homesPerGuard
                    + (areas + areasPerGuard - 1) / areasPerGuard + Math.min(defenseBonusCap, buildings) + adjustment));
        }
    }
}
