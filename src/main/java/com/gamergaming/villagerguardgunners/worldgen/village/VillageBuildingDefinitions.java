package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig.VillageBuildingSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/** Strict, bounded, deterministic and atomic datapack registry for Step 3. */
public final class VillageBuildingDefinitions extends
        SimplePreparableReloadListener<VillageBuildingDefinitions.PreparedResources> {
    public static final String DIRECTORY = "village_buildings";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "format",
            "building_type",
            "target_pool",
            "template",
            "processors",
            "projection",
            "weight");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final VillageBuildingDefinitions INSTANCE =
            new VillageBuildingDefinitions();

    private volatile VillageBuildingDefinitionReloadSnapshot snapshot =
            VillageBuildingDefinitionReloadSnapshot.empty();

    VillageBuildingDefinitions() {
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event").addListener(INSTANCE);
    }

    /** Prevents one integrated-server session from becoming another's LKG. */
    public static void onServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        INSTANCE.resetSession();
    }

    public static @Nullable VillageBuildingPoolEntry get(ResourceLocation id) {
        return INSTANCE.snapshot.get(id);
    }

    public static Map<ResourceLocation, VillageBuildingPoolEntry> snapshot() {
        return INSTANCE.snapshot.definitions();
    }

    public static VillageBuildingDefinitionReloadSnapshot reloadSnapshot() {
        return INSTANCE.snapshot;
    }

    public static long generation() {
        return INSTANCE.snapshot.generation();
    }

    public static List<VillageBuildingPoolEntry> enabledEntries(
            VillageBuildingSettings settings) {
        return enabledEntries(INSTANCE.snapshot.definitions(), settings);
    }

    static List<VillageBuildingPoolEntry> enabledEntries(
            Map<ResourceLocation, VillageBuildingPoolEntry> definitions,
            VillageBuildingSettings settings) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(settings, "settings");
        return definitions.values().stream()
                .filter(entry -> settings.allows(entry.buildingType()))
                .toList();
    }

    VillageBuildingDefinitionReloadSnapshot currentSnapshot() {
        return snapshot;
    }

    void resetSession() {
        snapshot = VillageBuildingDefinitionReloadSnapshot.empty();
    }

    @Override
    protected PreparedResources prepare(ResourceManager manager, ProfilerFiller profiler) {
        Objects.requireNonNull(manager, "manager");
        FileToIdConverter converter = FileToIdConverter.json(DIRECTORY);
        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources =
                converter.listMatchingResources(manager);
        if (resources.size() > VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENTS) {
            return new PreparedResources(
                    Map.of(),
                    List.of(new VillageBuildingDefinitionReloadSnapshot.RejectedDocument(
                            null,
                            "Found " + resources.size()
                                    + " village-building documents; maximum is "
                                    + VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENTS)));
        }

        Map<ResourceLocation, JsonElement> documents = new LinkedHashMap<>();
        List<VillageBuildingDefinitionReloadSnapshot.RejectedDocument> errors =
                new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement json = readBoundedJson(reader, id);
                        if (documents.put(id, json) != null) {
                            throw new JsonParseException(
                                    "Duplicate village-building definition ID " + id);
                        }
                    } catch (IOException | RuntimeException exception) {
                        documents.remove(id);
                        errors.add(new VillageBuildingDefinitionReloadSnapshot.RejectedDocument(
                                id,
                                safeMessage(exception)));
                    }
                });
        return new PreparedResources(documents, errors);
    }

    @Override
    protected void apply(
            PreparedResources prepared,
            ResourceManager manager,
            ProfilerFiller profiler) {
        publish(prepared);
    }

    void publish(PreparedResources prepared) {
        Objects.requireNonNull(prepared, "prepared");
        List<VillageBuildingDefinitionReloadSnapshot.RejectedDocument> errors =
                new ArrayList<>(prepared.errors());
        Map<ResourceLocation, VillageBuildingPoolEntry> compiled = Map.of();
        if (errors.isEmpty()) {
            try {
                compiled = compile(prepared.documents());
            } catch (RuntimeException exception) {
                errors.add(new VillageBuildingDefinitionReloadSnapshot.RejectedDocument(
                        null,
                        safeMessage(exception)));
            }
        }

        VillageBuildingDefinitionReloadSnapshot previous = snapshot;
        if (!errors.isEmpty()) {
            for (VillageBuildingDefinitionReloadSnapshot.RejectedDocument error : errors) {
                if (error.id() == null) {
                    LOGGER.error(
                            "Village-building definition reload rejected atomically: {}",
                            error.reason());
                } else {
                    LOGGER.error(
                            "Village-building definition reload rejected atomically at {}: {}",
                            error.id(),
                            error.reason());
                }
            }
            snapshot = new VillageBuildingDefinitionReloadSnapshot(
                    previous.generation(),
                    false,
                    previous.definitions(),
                    errors);
            return;
        }

        long generation = previous.generation() == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : previous.generation() + 1L;
        snapshot = new VillageBuildingDefinitionReloadSnapshot(
                generation,
                true,
                compiled,
                List.of());
        LOGGER.info(
                "Loaded {} village-building definition(s); generation {}. Pool changes apply at server startup",
                compiled.size(),
                generation);
    }

    static Map<ResourceLocation, VillageBuildingPoolEntry> compile(
            Map<ResourceLocation, JsonElement> documents) {
        Objects.requireNonNull(documents, "documents");
        if (documents.size() > VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENTS) {
            throw new JsonParseException(
                    "Village-building document count exceeds maximum "
                            + VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENTS);
        }

        Map<ResourceLocation, VillageBuildingPoolEntry> compiled = new LinkedHashMap<>();
        Set<PlacementKey> placements = new HashSet<>();
        documents.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = Objects.requireNonNull(
                            entry.getKey(), "village-building definition ID");
                    VillageBuildingPoolEntry definition = parse(id, entry.getValue());
                    PlacementKey placement = new PlacementKey(
                            definition.buildingType(),
                            definition.targetPool(),
                            definition.template(),
                            definition.processors(),
                            definition.projection());
                    if (!placements.add(placement)) {
                        throw new JsonParseException(
                                "Village-building definition " + id
                                        + " duplicates an existing placement tuple");
                    }
                    compiled.put(id, definition);
                });
        VillageBuildingPoolIntegration.validateBatch(List.copyOf(compiled.values()));
        return java.util.Collections.unmodifiableMap(compiled);
    }

    private static VillageBuildingPoolEntry parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(
                    "Village-building definition " + id + " root must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        rejectUnknownFields(root, ROOT_FIELDS, "village-building definition " + id);
        int format = requiredInteger(
                root,
                "format",
                "Village-building definition " + id,
                1,
                Integer.MAX_VALUE);
        if (format != VillageBuildingDefinitionFormat.VERSION) {
            throw new JsonParseException(
                    "Village-building definition " + id + " uses unsupported format "
                            + format + "; supported format is "
                            + VillageBuildingDefinitionFormat.VERSION);
        }

        String typeName = requiredString(
                root.get("building_type"),
                "Village-building definition " + id + " field 'building_type'");
        VillageBuildingType buildingType = VillageBuildingType.byName(typeName)
                .orElseThrow(() -> new JsonParseException(
                        "Village-building definition " + id
                                + " has unknown building_type '" + typeName + "'"));
        ResourceLocation targetPool = requiredId(
                root.get("target_pool"),
                "Village-building definition " + id + " field 'target_pool'");
        ResourceLocation template = requiredId(
                root.get("template"),
                "Village-building definition " + id + " field 'template'");
        ResourceLocation processors = root.has("processors")
                ? requiredId(
                        root.get("processors"),
                        "Village-building definition " + id + " field 'processors'")
                : VillageBuildingPools.EMPTY_PROCESSORS;
        StructureTemplatePool.Projection projection = root.has("projection")
                ? parseProjection(
                        requiredString(
                                root.get("projection"),
                                "Village-building definition " + id + " field 'projection'"),
                        id)
                : StructureTemplatePool.Projection.RIGID;
        int weight = requiredInteger(
                root,
                "weight",
                "Village-building definition " + id,
                VillageBuildingPoolEntry.MINIMUM_WEIGHT,
                VillageBuildingPoolEntry.MAXIMUM_WEIGHT);
        return new VillageBuildingPoolEntry(
                id,
                buildingType,
                targetPool,
                template,
                processors,
                projection,
                weight);
    }

    private static StructureTemplatePool.Projection parseProjection(
            String name,
            ResourceLocation id) {
        return switch (name) {
            case "rigid" -> StructureTemplatePool.Projection.RIGID;
            case "terrain_matching" -> StructureTemplatePool.Projection.TERRAIN_MATCHING;
            default -> throw new JsonParseException(
                    "Village-building definition " + id
                            + " field 'projection' must be 'rigid' or 'terrain_matching'");
        };
    }

    static JsonElement readBoundedJson(Reader reader, ResourceLocation id)
            throws IOException {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(id, "id");
        char[] buffer = new char[4096];
        StringBuilder source = new StringBuilder();
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (source.length() + read
                    > VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENT_CHARACTERS) {
                throw new JsonParseException(
                        "Village-building definition " + id
                                + " exceeds the maximum document size of "
                                + VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENT_CHARACTERS
                                + " characters");
            }
            source.append(buffer, 0, read);
        }
        try (JsonReader jsonReader = new JsonReader(new StringReader(source.toString()))) {
            jsonReader.setLenient(false);
            JsonElement json = readStrictValue(jsonReader, id, 0);
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException(
                        "Village-building definition " + id
                                + " contains trailing JSON data");
            }
            return json;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new JsonParseException(
                    "Malformed JSON for village-building definition " + id,
                    exception);
        }
    }

    private static JsonElement readStrictValue(
            JsonReader reader,
            ResourceLocation id,
            int depth) throws IOException {
        JsonToken token = reader.peek();
        return switch (token) {
            case BEGIN_OBJECT -> {
                requireJsonDepth(id, depth);
                JsonObject object = new JsonObject();
                Set<String> names = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) {
                        throw new JsonParseException(
                                "Village-building definition " + id
                                        + " repeats JSON field '" + name + "'");
                    }
                    object.add(name, readStrictValue(reader, id, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                requireJsonDepth(id, depth);
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readStrictValue(reader, id, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new JsonParseException(
                    "Village-building definition " + id
                            + " contains unexpected JSON token " + token);
        };
    }

    private static void requireJsonDepth(ResourceLocation id, int depth) {
        if (depth >= VillageBuildingDefinitionFormat.MAXIMUM_JSON_DEPTH) {
            throw new JsonParseException(
                    "Village-building definition " + id + " exceeds maximum JSON depth "
                            + VillageBuildingDefinitionFormat.MAXIMUM_JSON_DEPTH);
        }
    }

    private static int requiredInteger(
            JsonObject object,
            String field,
            String location,
            int minimum,
            int maximum) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(location + " field '" + field + "' must be an integer");
        }
        try {
            BigDecimal value = element.getAsBigDecimal().stripTrailingZeros();
            if (value.scale() > 0) {
                throw new ArithmeticException("fractional");
            }
            int integer = value.intValueExact();
            if (integer < minimum || integer > maximum) {
                throw new ArithmeticException("outside range");
            }
            return integer;
        } catch (ArithmeticException exception) {
            throw new JsonParseException(
                    location + " field '" + field + "' must be an integer from "
                            + minimum + " through " + maximum);
        }
    }

    private static String requiredString(@Nullable JsonElement element, String location) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(location + " must be a string");
        }
        String value = element.getAsString();
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new JsonParseException(
                    location + " must be nonblank without surrounding whitespace");
        }
        return value;
    }

    private static ResourceLocation requiredId(@Nullable JsonElement element, String location) {
        String value = requiredString(element, location);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !id.toString().equals(value)) {
            throw new JsonParseException(location + " must be a canonical namespaced ID");
        }
        return id;
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
                    "Unknown field(s) in " + location + ": "
                            + String.join(", ", unknown));
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        String safe = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        StringBuilder normalized = new StringBuilder(safe.length());
        safe.codePoints().forEach(codePoint -> normalized.appendCodePoint(
                Character.isISOControl(codePoint) ? ' ' : codePoint));
        safe = normalized.toString().trim();
        if (safe.length() <= VillageBuildingDefinitionFormat.MAXIMUM_ERROR_CHARACTERS) {
            return safe;
        }
        int end = VillageBuildingDefinitionFormat.MAXIMUM_ERROR_CHARACTERS - 3;
        if (Character.isHighSurrogate(safe.charAt(end - 1))) {
            end--;
        }
        return safe.substring(0, end) + "...";
    }

    private record PlacementKey(
            VillageBuildingType buildingType,
            ResourceLocation targetPool,
            ResourceLocation template,
            ResourceLocation processors,
            StructureTemplatePool.Projection projection) {
    }

    record PreparedResources(
            Map<ResourceLocation, JsonElement> documents,
            List<VillageBuildingDefinitionReloadSnapshot.RejectedDocument> errors) {
        PreparedResources {
            documents = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(documents, "documents")));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }
}
