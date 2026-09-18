package com.gamergaming.villagerguardgunners.village.coverage;

import com.gamergaming.villagerguardgunners.village.VillageRecord;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Occupied spatial areas, never evidence of traversable terrain. No world access. */
public final class VillageCoverageLayout {
    public static final int CELL_SIZE = 32;
    public static final int MAX_AREAS = 16;
    public static final int MAX_CELLS_PER_AREA = 4;
    public static final int MAX_SITES = 2_048;
    public static final int MAX_DESTINATIONS = 64;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator
            .comparingInt((BlockPos position) -> position.getX()).thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);

    private final UUID villageId;
    private final long revision;
    private final List<Area> areas;
    private final Status status;

    private VillageCoverageLayout(UUID villageId, long revision, List<Area> areas, Status status) {
        this.villageId = villageId;
        this.revision = revision;
        this.areas = List.copyOf(areas);
        this.status = status;
    }

    public UUID villageId() { return villageId; }
    public long revision() { return revision; }
    public List<Area> areas() { return areas; }
    public Status status() { return status; }

    /**
     * Template classification is explicit: unknown pieces, roads and decorations
     * do not establish occupancy. Cached homes are anchors, not walk targets or
     * population credits. Services must already belong to this village.
     */
    public static VillageCoverageLayout fromRecord(VillageRecord record,
            Map<ResourceLocation, SiteKind> templates, Collection<Site> services) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(templates, "templates");
        Objects.requireNonNull(services, "services");
        if (services.size() > MAX_SITES) {
            throw new IllegalArgumentException("Too many service observations");
        }
        List<Site> sites = new ArrayList<>();
        record.homes().forEach(pos -> sites.add(new Site(pos, SiteKind.HOME)));
        for (var piece : record.footprint().pieces()) {
            SiteKind kind = templates.get(piece.template());
            if (kind != null && kind.importance > 0) {
                var box = piece.bounds();
                sites.add(new Site(new BlockPos(
                        box.minX() + (box.maxX() - box.minX()) / 2, box.minY(),
                        box.minZ() + (box.maxZ() - box.minZ()) / 2), kind));
            }
        }
        for (Site site : services) {
            if (!record.footprint().bounds().contains(site.position())) {
                throw new IllegalArgumentException("Service is outside the observed footprint");
            }
            sites.add(site);
        }
        return partition(record.identity().id(), record.revision(), sites);
    }

    /** Rebuild once per changed observation publication, not once per guard. */
    public static VillageCoverageLayout partition(UUID villageId, long revision, Collection<Site> sites) {
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(sites, "sites");
        if (revision < 1 || sites.size() > MAX_SITES) {
            throw new IllegalArgumentException("Coverage observation exceeds its bound");
        }
        Map<RegionCell, List<Site>> occupied = new TreeMap<>();
        for (Site site : sites) {
            Objects.requireNonNull(site, "site");
            if (site.kind().importance > 0) {
                occupied.computeIfAbsent(new RegionCell(Cell.at(site.position()), site.component()),
                        key -> new ArrayList<>()).add(site);
            }
        }
        List<Area> areas = new ArrayList<>();
        TreeSet<RegionCell> remaining = new TreeSet<>(occupied.keySet());
        while (!remaining.isEmpty()) {
            if (areas.size() == MAX_AREAS) {
                // Retain independent areas without claiming that the whole village is covered.
                areas.sort(Comparator.comparing(Area::id));
                return new VillageCoverageLayout(villageId, revision, areas, Status.AREA_LIMIT);
            }
            TreeSet<RegionCell> cells = new TreeSet<>();
            ArrayDeque<RegionCell> queue = new ArrayDeque<>();
            RegionCell seed = remaining.first();
            Cell origin = seed.cell();
            List<RegionCell> square = List.of(seed,
                    new RegionCell(new Cell(origin.x() + 1, origin.y(), origin.z()), seed.component()),
                    new RegionCell(new Cell(origin.x(), origin.y(), origin.z() + 1), seed.component()),
                    new RegionCell(new Cell(origin.x() + 1, origin.y(), origin.z() + 1), seed.component()));
            if (remaining.containsAll(square)) {
                // Compact squares avoid fragmenting a regular village into leftover single cells.
                cells.addAll(square);
                remaining.removeAll(square);
            } else {
                queue.add(seed);
            }
            while (!queue.isEmpty() && cells.size() < MAX_CELLS_PER_AREA) {
                RegionCell cell = queue.removeFirst();
                if (!remaining.remove(cell)) { continue; }
                cells.add(cell);
                cell.cell().neighbors().stream().map(neighbor -> new RegionCell(neighbor, cell.component()))
                        .filter(remaining::contains).forEach(queue::addLast);
            }
            TreeSet<BlockPos> anchors = new TreeSet<>(POSITION_ORDER);
            int importance = 0;
            for (RegionCell cell : cells) {
                for (Site site : occupied.get(cell)) {
                    anchors.add(site.position());
                    importance = Math.max(importance, site.kind().importance);
                }
            }
            String key = "villagerguardgunners:coverage:1\0" + villageId + "\0" + cells;
            areas.add(new Area(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                    cells.stream().map(RegionCell::cell).toList(), List.copyOf(anchors), importance,
                    cells.first().component()));
        }
        areas.sort(Comparator.comparing(Area::id));
        return new VillageCoverageLayout(villageId, revision, areas,
                areas.isEmpty() ? Status.NO_OCCUPIED_SITES : Status.READY);
    }

    public enum Status { READY, NO_OCCUPIED_SITES, AREA_LIMIT }

    public enum SiteKind {
        HOME(1), RESIDENTIAL(1), SERVICE(2), DEFENSE(3), ROAD(0), DECORATION(0);
        private final int importance;
        SiteKind(int importance) { this.importance = importance; }
    }

    public record Site(BlockPos position, SiteKind kind, @Nullable UUID component) {
        public Site(BlockPos position, SiteKind kind) { this(position, kind, null); }
        public Site {
            position = checkedPosition(position);
            Objects.requireNonNull(kind, "kind");
        }
    }

    static BlockPos checkedPosition(BlockPos position) {
        Objects.requireNonNull(position, "position");
        if (Math.abs((long) position.getX()) > 30_000_000
                || Math.abs((long) position.getZ()) > 30_000_000
                || Math.abs((long) position.getY()) > 20_000) {
            throw new IllegalArgumentException("Position exceeds supported bounds");
        }
        return position.immutable();
    }

    public record Cell(int x, int y, int z) implements Comparable<Cell> {
        public static Cell at(BlockPos pos) {
            return new Cell(Math.floorDiv(pos.getX(), CELL_SIZE),
                    Math.floorDiv(pos.getY(), CELL_SIZE), Math.floorDiv(pos.getZ(), CELL_SIZE));
        }
        private List<Cell> neighbors() {
            // Horizontal adjacency only. Vertical layers remain distinct.
            return List.of(new Cell(x - 1, y, z), new Cell(x, y, z - 1),
                    new Cell(x, y, z + 1), new Cell(x + 1, y, z));
        }
        @Override public int compareTo(Cell other) {
            int order = Integer.compare(x, other.x);
            if (order == 0) { order = Integer.compare(y, other.y); }
            return order == 0 ? Integer.compare(z, other.z) : order;
        }
    }

    private record RegionCell(Cell cell, @Nullable UUID component) implements Comparable<RegionCell> {
        @Override public int compareTo(RegionCell other) {
            int order = cell.compareTo(other.cell);
            return order != 0 ? order : Comparator.nullsFirst(UUID::compareTo).compare(component, other.component);
        }
    }

    public record Area(UUID id, List<Cell> cells, List<BlockPos> anchors, int importance,
            @Nullable UUID component) {
        public Area {
            Objects.requireNonNull(id, "id");
            cells = List.copyOf(cells);
            anchors = List.copyOf(anchors);
        }
        public boolean contains(BlockPos position) { return cells.contains(Cell.at(position)); }
    }
}
