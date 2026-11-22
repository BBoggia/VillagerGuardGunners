package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIds;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSavedData;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/** Passive, per-dimension registry of exact Village Defense Building duty cells. */
public final class VillageDefenseDutySavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_defense_duties";
    public static final int DATA_VERSION = 1;
    public static final int MAXIMUM_LAYOUTS = VillageBuildingSavedData.MAXIMUM_RECORDS;
    public static final int MAXIMUM_SLOTS =
            MAXIMUM_LAYOUTS * VillageDefenseDutyLayout.MAXIMUM_SLOTS_PER_BUILDING;
    public static final int MAXIMUM_QUERY_BUILDINGS = 4_096;
    public static final int MAXIMUM_QUERY_SLOTS = MAXIMUM_QUERY_BUILDINGS
            * VillageDefenseDutyLayout.MAXIMUM_SLOTS_PER_BUILDING;

    private static final String VERSION_TAG = "Version";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String LAYOUTS_TAG = "Layouts";
    private static final java.util.Set<String> ROOT_KEYS = java.util.Set.of(
            VERSION_TAG, DIMENSION_TAG, LAYOUTS_TAG);
    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean enabled;
    @Nullable
    private final ResourceLocation dimensionId;
    @Nullable
    private final Long worldSeed;
    private final LinkedHashMap<UUID, VillageDefenseDutyLayout> layouts;
    private final Map<UUID, VillageDefenseDutySlot> slotsById;
    private final Map<BlockPos, UUID> slotByPosition;
    @Nullable
    private final CompoundTag unreadableRoot;
    @Nullable
    private final String disabledReason;
    private final EnumSet<ClaimStatus> reportedClaimFailures =
            EnumSet.noneOf(ClaimStatus.class);
    private boolean disabledStateReported;

    public VillageDefenseDutySavedData(ResourceLocation dimensionId) {
        this(true, Objects.requireNonNull(dimensionId, "dimensionId"), null,
                Map.of(), null, null);
    }

    private VillageDefenseDutySavedData(
            boolean enabled,
            @Nullable ResourceLocation dimensionId,
            @Nullable Long worldSeed,
            Map<UUID, VillageDefenseDutyLayout> layouts,
            @Nullable CompoundTag unreadableRoot,
            @Nullable String disabledReason) {
        this.enabled = enabled;
        this.dimensionId = dimensionId;
        this.worldSeed = worldSeed;
        this.layouts = new LinkedHashMap<>(layouts);
        this.slotsById = new HashMap<>();
        this.slotByPosition = new HashMap<>();
        this.unreadableRoot = unreadableRoot == null ? null : unreadableRoot.copy();
        this.disabledReason = disabledReason;
        for (VillageDefenseDutyLayout layout : layouts.values()) {
            index(layout);
        }
    }

    public static VillageDefenseDutySavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceLocation dimension = level.dimension().location();
        VillageDefenseDutySavedData data = level.getDataStorage().computeIfAbsent(
                encoded -> loadForLevel(encoded, dimension, level.getSeed()),
                () -> new VillageDefenseDutySavedData(
                        true, dimension, level.getSeed(), Map.of(), null, null),
                DATA_NAME);
        data.reportDisabledStateOnce();
        return data;
    }

    public static VillageDefenseDutySavedData load(CompoundTag encoded) {
        return load(encoded, null, null);
    }

    static VillageDefenseDutySavedData loadForLevel(
            CompoundTag encoded,
            ResourceLocation expectedDimension,
            long worldSeed) {
        return load(encoded, Objects.requireNonNull(
                expectedDimension, "expectedDimension"), worldSeed);
    }

    private static VillageDefenseDutySavedData load(
            CompoundTag encoded,
            @Nullable ResourceLocation expectedDimension,
            @Nullable Long worldSeed) {
        Objects.requireNonNull(encoded, "encoded");
        ResourceLocation storedDimension = encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(encoded.getString(DIMENSION_TAG))
                : null;
        if (!encoded.getAllKeys().equals(ROOT_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != DATA_VERSION
                || storedDimension == null
                || (expectedDimension != null && !expectedDimension.equals(storedDimension))
                || !encoded.contains(LAYOUTS_TAG, Tag.TAG_LIST)) {
            return disabled(encoded, expectedDimension != null
                    ? expectedDimension : storedDimension,
                    "unsupported or malformed root schema");
        }
        Tag rawLayouts = encoded.get(LAYOUTS_TAG);
        if (!(rawLayouts instanceof ListTag encodedLayouts)
                || encodedLayouts.size() > MAXIMUM_LAYOUTS
                || (!encodedLayouts.isEmpty()
                        && encodedLayouts.getElementType() != Tag.TAG_COMPOUND)) {
            return disabled(encoded, storedDimension, "Layouts is not a bounded compound list");
        }

        Map<UUID, VillageDefenseDutyLayout> layouts = new LinkedHashMap<>();
        Map<UUID, UUID> slotOwners = new HashMap<>();
        Map<BlockPos, UUID> positions = new HashMap<>();
        long slotCount = 0L;
        for (Tag rawLayout : encodedLayouts) {
            if (!(rawLayout instanceof CompoundTag layoutTag)) {
                return disabled(encoded, storedDimension,
                        "Layouts contains a non-compound value");
            }
            VillageDefenseDutyLayout layout = VillageDefenseDutyLayoutCodec.decode(layoutTag);
            if (layout == null || !storedDimension.equals(
                    layout.building().dimensionId())) {
                return disabled(encoded, storedDimension,
                        "a duty layout failed strict decoding or crosses dimensions");
            }
            VillageBuildingIdentity building = layout.building();
            if (worldSeed != null
                    && !VillageBuildingIds.buildingId(
                            worldSeed,
                            building.dimensionId(),
                            building.entryId(),
                            building.buildingType(),
                            building.anchorPosition()).equals(building.buildingId())) {
                return disabled(encoded, storedDimension,
                        "a duty layout has a non-deterministic building ID");
            }
            if (layouts.put(building.buildingId(), layout) != null) {
                return disabled(encoded, storedDimension, "duplicate duty building ID");
            }
            slotCount += layout.slots().size();
            if (slotCount > MAXIMUM_SLOTS) {
                return disabled(encoded, storedDimension, "global duty-slot limit exceeded");
            }
            for (VillageDefenseDutySlot slot : layout.slots()) {
                if (slotOwners.put(slot.slotId(), building.buildingId()) != null
                        || positions.put(slot.position(), slot.slotId()) != null) {
                    return disabled(encoded, storedDimension,
                            "duplicate duty-slot ID or physical position");
                }
            }
        }
        return new VillageDefenseDutySavedData(
                true, storedDimension, worldSeed, layouts, null, null);
    }

    private static VillageDefenseDutySavedData disabled(
            CompoundTag encoded,
            @Nullable ResourceLocation dimensionId,
            String reason) {
        return new VillageDefenseDutySavedData(
                false, dimensionId, null, Map.of(), encoded, reason);
    }

    public synchronized ClaimStatus claimBuilding(
            VillageBuildingIdentity building,
            Rotation rotation) {
        return claimLayout(VillageDefenseDutyLayouts.forBuilding(
                Objects.requireNonNull(building, "building"),
                Objects.requireNonNull(rotation, "rotation")));
    }

    /** Claims one already-resolved layout without consulting mutable datapack state. */
    public synchronized ClaimStatus claimLayout(VillageDefenseDutyLayout candidate) {
        Objects.requireNonNull(candidate, "candidate");
        VillageBuildingIdentity identity = candidate.building();
        if (!enabled) {
            return reportClaimFailure(ClaimStatus.DISABLED, identity);
        }
        if (!Objects.equals(dimensionId, identity.dimensionId())) {
            return reportClaimFailure(ClaimStatus.WRONG_DIMENSION, identity);
        }
        if (worldSeed != null
                && !VillageBuildingIds.buildingId(
                        worldSeed,
                        identity.dimensionId(),
                        identity.entryId(),
                        identity.buildingType(),
                        identity.anchorPosition()).equals(identity.buildingId())) {
            return reportClaimFailure(ClaimStatus.NON_DETERMINISTIC_BUILDING, identity);
        }
        VillageDefenseDutyLayout existing = layouts.get(identity.buildingId());
        if (existing != null) {
            return existing.equals(candidate)
                    ? ClaimStatus.ALREADY_CLAIMED
                    : reportClaimFailure(ClaimStatus.CONFLICT, identity);
        }
        if (layouts.size() >= MAXIMUM_LAYOUTS
                || (long) slotsById.size() + candidate.slots().size() > MAXIMUM_SLOTS) {
            return reportClaimFailure(ClaimStatus.LIMIT_REACHED, identity);
        }
        for (VillageDefenseDutySlot slot : candidate.slots()) {
            if (slotsById.containsKey(slot.slotId())
                    || slotByPosition.containsKey(slot.position())) {
                return reportClaimFailure(ClaimStatus.CONFLICT, identity);
            }
        }
        layouts.put(identity.buildingId(), candidate);
        index(candidate);
        setDirty();
        return ClaimStatus.CLAIMED;
    }

    public synchronized @Nullable VillageDefenseDutyLayout layout(UUID buildingId) {
        return layouts.get(Objects.requireNonNull(buildingId, "buildingId"));
    }

    public synchronized @Nullable VillageDefenseDutySlot slot(UUID slotId) {
        return slotsById.get(Objects.requireNonNull(slotId, "slotId"));
    }

    /** Indexed, allocation-bounded lookup for a future network staffing pass. */
    public synchronized List<VillageDefenseDutySlot> slotsForBuildings(
            Collection<UUID> buildingIds,
            int maximumResults) {
        Objects.requireNonNull(buildingIds, "buildingIds");
        if (buildingIds.size() > MAXIMUM_QUERY_BUILDINGS
                || maximumResults < 0
                || maximumResults > MAXIMUM_QUERY_SLOTS) {
            throw new IllegalArgumentException("Duty-slot query limit is outside its hard bound");
        }
        if (!enabled || maximumResults == 0 || buildingIds.isEmpty()) {
            return List.of();
        }
        ArrayList<VillageDefenseDutySlot> result = new ArrayList<>(
                Math.min(maximumResults, 16));
        HashSet<UUID> visited = new HashSet<>();
        for (UUID buildingId : buildingIds) {
            if (buildingId == null || !visited.add(buildingId)) {
                continue;
            }
            VillageDefenseDutyLayout layout = layouts.get(buildingId);
            if (layout == null) {
                continue;
            }
            for (VillageDefenseDutySlot slot : layout.slots()) {
                if (result.size() >= maximumResults) {
                    return List.copyOf(result);
                }
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    /** Count-only indexed coverage for explicit operator diagnostics. */
    public synchronized DutyCoverage coverageForBuildings(
            Collection<UUID> buildingIds) {
        Objects.requireNonNull(buildingIds, "buildingIds");
        if (buildingIds.size() > MAXIMUM_QUERY_BUILDINGS) {
            throw new IllegalArgumentException("Duty coverage query exceeds its hard bound");
        }
        int matchedLayouts = 0;
        int matchedSlots = 0;
        HashSet<UUID> visited = new HashSet<>();
        for (UUID buildingId : buildingIds) {
            if (buildingId == null || !visited.add(buildingId)) {
                continue;
            }
            VillageDefenseDutyLayout layout = layouts.get(buildingId);
            if (layout != null) {
                matchedLayouts++;
                matchedSlots += layout.slots().size();
            }
        }
        return new DutyCoverage(matchedLayouts, matchedSlots);
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized int layoutCount() {
        return layouts.size();
    }

    public synchronized int slotCount() {
        return slotsById.size();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output) {
        if (!enabled) {
            return unreadableRoot == null ? new CompoundTag() : unreadableRoot.copy();
        }
        output.putInt(VERSION_TAG, DATA_VERSION);
        output.putString(DIMENSION_TAG, Objects.requireNonNull(dimensionId).toString());
        ListTag encodedLayouts = new ListTag();
        for (VillageDefenseDutyLayout layout : layouts.values()) {
            encodedLayouts.add(VillageDefenseDutyLayoutCodec.encode(layout));
        }
        output.put(LAYOUTS_TAG, encodedLayouts);
        return output;
    }

    private void index(VillageDefenseDutyLayout layout) {
        for (VillageDefenseDutySlot slot : layout.slots()) {
            slotsById.put(slot.slotId(), slot);
            slotByPosition.put(slot.position(), slot.slotId());
        }
    }

    private ClaimStatus reportClaimFailure(
            ClaimStatus status,
            VillageBuildingIdentity identity) {
        if (reportedClaimFailures.add(status)) {
            LOGGER.warn(
                    "Village defense duty-slot claim failed once in {}: result={}, building={}, anchor={}",
                    identity.dimensionId(),
                    status,
                    identity.buildingId(),
                    identity.anchorPosition());
        }
        return status;
    }

    private synchronized void reportDisabledStateOnce() {
        if (enabled || disabledStateReported) {
            return;
        }
        disabledStateReported = true;
        LOGGER.error(
                "Village defense duty-slot state is disabled in {}: {}. The original data will be preserved unchanged",
                dimensionId,
                disabledReason == null ? "unknown strict-decoding failure" : disabledReason);
    }

    public enum ClaimStatus {
        CLAIMED,
        ALREADY_CLAIMED,
        WRONG_DIMENSION,
        NON_DETERMINISTIC_BUILDING,
        CONFLICT,
        LIMIT_REACHED,
        DISABLED
    }

    public record DutyCoverage(int layouts, int slots) {
        public DutyCoverage {
            if (layouts < 0
                    || layouts > MAXIMUM_QUERY_BUILDINGS
                    || slots < 0
                    || slots > MAXIMUM_QUERY_SLOTS) {
                throw new IllegalArgumentException("Duty coverage is outside its hard bound");
            }
        }
    }
}
