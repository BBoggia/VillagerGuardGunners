package com.gamergaming.villagerguardgunners.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Atomically reloads immutable natural guard loadout pools from server datapacks.
 *
 * <p>Files live at {@code data/<namespace>/natural_guard_loadouts/<path>.json}
 * and resolve to pool ID {@code <namespace>:<path>}. TaCZ gun IDs are not
 * resolved during this reload because TaCZ's own gun definitions are reloadable;
 * live issuance performs that compatibility check after every reload has
 * completed.</p>
 */
public final class NaturalGuardLoadoutPools extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "natural_guard_loadouts";
    public static final int FORMAT_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Set<String> POOL_FIELDS = Set.of("format", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of("gun", "weight");
    private static final NaturalGuardLoadoutPools INSTANCE = new NaturalGuardLoadoutPools();

    private volatile Map<ResourceLocation, NaturalGuardLoadoutPool> pools = Map.of();
    private volatile Set<UnavailableGun> unavailableGunWarnings =
            ConcurrentHashMap.newKeySet();

    NaturalGuardLoadoutPools() {
        super(GSON, DIRECTORY);
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event").addListener(INSTANCE);
    }

    /** Returns the currently published immutable pool, or {@code null} if unavailable. */
    public static @Nullable NaturalGuardLoadoutPool get(ResourceLocation poolId) {
        return INSTANCE.getLoaded(poolId);
    }

    /** Returns the current immutable snapshot for diagnostics and tests. */
    public static Map<ResourceLocation, NaturalGuardLoadoutPool> snapshot() {
        return INSTANCE.loadedSnapshot();
    }

    /** Logs one actionable warning per unresolved pool entry per reload. */
    public static void reportUnavailableGun(NaturalGuardLoadoutCandidate candidate) {
        UnavailableGun unavailable = new UnavailableGun(
                candidate.poolId(),
                candidate.gunId());
        if (INSTANCE.unavailableGunWarnings.add(unavailable)) {
            LOGGER.warn(
                    "Natural guard loadout pool {} selected unavailable TaCZ gun {}; "
                            + "this guard's one-time loadout attempt was left unchanged",
                    unavailable.poolId(),
                    unavailable.gunId());
        }
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        LoadResult result = parseAll(prepared);
        for (RejectedPool rejected : result.rejected()) {
            LOGGER.error(
                    "Skipping invalid natural guard loadout pool {}: {}",
                    rejected.id(),
                    rejected.reason());
        }

        // A single volatile publication prevents spawn handling from observing
        // a partially rebuilt registry during /reload.
        unavailableGunWarnings = ConcurrentHashMap.newKeySet();
        pools = result.pools();
        LOGGER.info(
                "Loaded {} natural guard loadout pool(s); rejected {} invalid pool(s)",
                result.pools().size(),
                result.rejected().size());
    }

    @Nullable NaturalGuardLoadoutPool getLoaded(ResourceLocation poolId) {
        return pools.get(Objects.requireNonNull(poolId, "poolId"));
    }

    Map<ResourceLocation, NaturalGuardLoadoutPool> loadedSnapshot() {
        return pools;
    }

    static LoadResult parseAll(Map<ResourceLocation, JsonElement> prepared) {
        Objects.requireNonNull(prepared, "prepared");
        Map<ResourceLocation, NaturalGuardLoadoutPool> loaded = new LinkedHashMap<>();
        List<RejectedPool> rejected = new ArrayList<>();

        prepared.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = Objects.requireNonNull(entry.getKey(), "pool ID");
                    try {
                        loaded.put(id, parsePool(id, entry.getValue()));
                    } catch (JsonParseException | IllegalArgumentException exception) {
                        rejected.add(new RejectedPool(id, safeMessage(exception)));
                    }
                });
        return new LoadResult(Map.copyOf(loaded), List.copyOf(rejected));
    }

    static NaturalGuardLoadoutPool parsePool(ResourceLocation id, JsonElement json) {
        Objects.requireNonNull(id, "id");
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException("Pool root must be a JSON object");
        }

        JsonObject root = json.getAsJsonObject();
        rejectUnknownFields(root, POOL_FIELDS, "pool");
        int format = requiredPositiveInteger(root, "format");
        if (format != FORMAT_VERSION) {
            throw new JsonParseException(
                    "Unsupported format " + format + "; expected " + FORMAT_VERSION);
        }

        JsonElement entriesElement = root.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            throw new JsonParseException("Required field 'entries' must be an array");
        }

        JsonArray entriesJson = entriesElement.getAsJsonArray();
        List<NaturalGuardLoadoutPool.Entry> entries = new ArrayList<>(entriesJson.size());
        for (int index = 0; index < entriesJson.size(); index++) {
            JsonElement entryElement = entriesJson.get(index);
            if (!entryElement.isJsonObject()) {
                throw new JsonParseException("Entry " + index + " must be a JSON object");
            }
            JsonObject entryJson = entryElement.getAsJsonObject();
            rejectUnknownFields(entryJson, ENTRY_FIELDS, "entry " + index);
            ResourceLocation gunId = requiredIdentifier(entryJson, "gun", index);
            int weight = requiredPositiveInteger(entryJson, "weight");
            entries.add(new NaturalGuardLoadoutPool.Entry(gunId, weight));
        }
        return new NaturalGuardLoadoutPool(id, entries);
    }

    private static ResourceLocation requiredIdentifier(
            JsonObject object,
            String field,
            int entryIndex) {
        JsonElement element = object.get(field);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(
                    "Entry " + entryIndex + " field '" + field + "' must be a string");
        }
        String rawId = element.getAsString();
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !id.toString().equals(rawId)) {
            throw new JsonParseException(
                    "Entry " + entryIndex + " field '" + field
                            + "' must be a canonical namespaced ID");
        }
        return id;
    }

    private static int requiredPositiveInteger(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException("Required field '" + field + "' must be an integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new JsonParseException("Required field '" + field + "' must be an integer");
        }

        try {
            BigDecimal value = primitive.getAsBigDecimal().stripTrailingZeros();
            if (value.scale() > 0) {
                throw new ArithmeticException("fractional");
            }
            int integer = value.intValueExact();
            if (integer <= 0) {
                throw new JsonParseException(
                        "Required field '" + field + "' must be a positive integer");
            }
            return integer;
        } catch (ArithmeticException exception) {
            throw new JsonParseException(
                    "Required field '" + field + "' must be a positive 32-bit integer");
        }
    }

    private static void rejectUnknownFields(
            JsonObject object,
            Set<String> supported,
            String location) {
        List<String> unknown = object.keySet().stream()
                .filter(field -> !supported.contains(field))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new JsonParseException(
                    "Unknown field(s) in " + location + ": " + String.join(", ", unknown));
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    record LoadResult(
            Map<ResourceLocation, NaturalGuardLoadoutPool> pools,
            List<RejectedPool> rejected) {
        LoadResult {
            pools = Map.copyOf(Objects.requireNonNull(pools, "pools"));
            rejected = List.copyOf(Objects.requireNonNull(rejected, "rejected"));
        }
    }

    record RejectedPool(ResourceLocation id, String reason) {
        RejectedPool {
            id = Objects.requireNonNull(id, "id");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    private record UnavailableGun(ResourceLocation poolId, ResourceLocation gunId) {
    }
}
