package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/** Strict, bounded, atomic datapack registry for guard-post group composition. */
public final class GuardPostGroups
        extends SimplePreparableReloadListener<GuardPostGroups.PreparedResources> {
    public static final String DIRECTORY = "guard_post_groups";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "format", "inherits", "loadout_pool", "variants");
    private static final Set<String> VARIANT_FIELDS = Set.of(
            "name", "weight", "difficulties", "loadout_pool", "members");
    private static final Set<String> MEMBER_FIELDS = Set.of(
            "loadout_pool", "role", "chance");
    private static final Pattern VARIANT_NAME = Pattern.compile("[a-z0-9._/-]{1,64}");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GuardPostGroups INSTANCE = new GuardPostGroups();

    private volatile GuardPostGroupReloadSnapshot snapshot = GuardPostGroupReloadSnapshot.empty();

    GuardPostGroups() {
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event").addListener(INSTANCE);
    }

    public static @Nullable GuardPostGroupPool get(ResourceLocation id) {
        return INSTANCE.snapshot.get(id);
    }

    public static Map<ResourceLocation, GuardPostGroupPool> snapshot() {
        return INSTANCE.snapshot.groups();
    }

    public static GuardPostGroupReloadSnapshot reloadSnapshot() {
        return INSTANCE.snapshot;
    }

    public static long generation() {
        return INSTANCE.snapshot.generation();
    }

    GuardPostGroupReloadSnapshot currentSnapshot() {
        return snapshot;
    }

    @Override
    protected PreparedResources prepare(ResourceManager manager, ProfilerFiller profiler) {
        Objects.requireNonNull(manager, "manager");
        FileToIdConverter converter = FileToIdConverter.json(DIRECTORY);
        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources =
                converter.listMatchingResources(manager);
        if (resources.size() > GuardPostGroupFormat.MAXIMUM_DOCUMENTS) {
            return new PreparedResources(
                    Map.of(),
                    List.of(new GuardPostGroupReloadSnapshot.RejectedDocument(
                            null,
                            "Found " + resources.size() + " guard-post group documents; maximum is "
                                    + GuardPostGroupFormat.MAXIMUM_DOCUMENTS)));
        }

        Map<ResourceLocation, JsonElement> documents = new LinkedHashMap<>();
        List<GuardPostGroupReloadSnapshot.RejectedDocument> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement json = readBoundedJson(reader, id);
                        if (documents.put(id, json) != null) {
                            throw new JsonParseException("Duplicate guard-post group ID " + id);
                        }
                    } catch (IOException | RuntimeException exception) {
                        documents.remove(id);
                        errors.add(new GuardPostGroupReloadSnapshot.RejectedDocument(
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
        List<GuardPostGroupReloadSnapshot.RejectedDocument> errors =
                new ArrayList<>(prepared.errors());
        Map<ResourceLocation, GuardPostGroupPool> resolved = Map.of();
        if (errors.isEmpty()) {
            try {
                resolved = compile(prepared.documents());
            } catch (RuntimeException exception) {
                errors.add(new GuardPostGroupReloadSnapshot.RejectedDocument(
                        null,
                        safeMessage(exception)));
            }
        }

        GuardPostGroupReloadSnapshot previous = snapshot;
        if (!errors.isEmpty()) {
            for (GuardPostGroupReloadSnapshot.RejectedDocument error : errors) {
                if (error.id() == null) {
                    LOGGER.error("Guard-post group reload rejected atomically: {}", error.reason());
                } else {
                    LOGGER.error(
                            "Guard-post group reload rejected atomically at {}: {}",
                            error.id(),
                            error.reason());
                }
            }
            snapshot = new GuardPostGroupReloadSnapshot(
                    previous.generation(),
                    false,
                    previous.groups(),
                    errors);
            return;
        }

        long generation = previous.generation() == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : previous.generation() + 1L;
        snapshot = new GuardPostGroupReloadSnapshot(generation, true, resolved, List.of());
        LOGGER.info("Loaded {} guard-post group(s); generation {}", resolved.size(), generation);
    }

    static Map<ResourceLocation, GuardPostGroupPool> compile(
            Map<ResourceLocation, JsonElement> documents) {
        Objects.requireNonNull(documents, "documents");
        if (documents.size() > GuardPostGroupFormat.MAXIMUM_DOCUMENTS) {
            throw new JsonParseException(
                    "Guard-post group document count exceeds maximum "
                            + GuardPostGroupFormat.MAXIMUM_DOCUMENTS);
        }

        Map<ResourceLocation, UnresolvedGroup> unresolved = new LinkedHashMap<>();
        documents.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> unresolved.put(
                        Objects.requireNonNull(entry.getKey(), "group ID"),
                        parse(entry.getKey(), entry.getValue())));

        Map<ResourceLocation, ResolvedGroup> resolved = new LinkedHashMap<>();
        Map<ResourceLocation, VisitState> states = new HashMap<>();
        for (ResourceLocation id : unresolved.keySet()) {
            resolve(id, unresolved, resolved, states, new ArrayList<>());
        }

        Map<ResourceLocation, GuardPostGroupPool> pools = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ResolvedGroup> entry : resolved.entrySet()) {
            pools.put(entry.getKey(), entry.getValue().pool());
        }
        return Map.copyOf(pools);
    }

    private static UnresolvedGroup parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Guard-post group " + id + " root must be an object");
        }
        JsonObject root = element.getAsJsonObject();
        rejectUnknownFields(root, ROOT_FIELDS, "guard-post group " + id);
        int format = requiredInteger(root, "format", "guard-post group " + id, 1, Integer.MAX_VALUE);
        if (format != GuardPostGroupFormat.VERSION) {
            throw new JsonParseException(
                    "Guard-post group " + id + " uses unsupported format " + format
                            + "; supported format is " + GuardPostGroupFormat.VERSION);
        }

        List<ResourceLocation> parents = parseParents(root, id);
        ResourceLocation defaultLoadout = optionalId(
                root.get("loadout_pool"),
                "Guard-post group " + id + " field 'loadout_pool'");
        List<UnresolvedVariant> variants = parseVariants(root.get("variants"), id);
        if (parents.isEmpty() && variants.isEmpty()) {
            throw new JsonParseException(
                    "Guard-post group " + id + " must inherit or declare at least one variant");
        }
        return new UnresolvedGroup(id, parents, defaultLoadout, variants);
    }

    private static List<ResourceLocation> parseParents(JsonObject root, ResourceLocation id) {
        JsonElement element = root.get("inherits");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException(
                    "Guard-post group " + id + " field 'inherits' must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > GuardPostGroupFormat.MAXIMUM_PARENTS) {
            throw new JsonParseException(
                    "Guard-post group " + id + " has too many parents; maximum is "
                            + GuardPostGroupFormat.MAXIMUM_PARENTS);
        }
        List<ResourceLocation> parents = new ArrayList<>(array.size());
        Set<ResourceLocation> unique = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            ResourceLocation parent = canonicalId(
                    requiredString(array.get(index),
                            "Guard-post group " + id + " parent " + index),
                    "Guard-post group " + id + " parent " + index);
            if (!unique.add(parent)) {
                throw new JsonParseException(
                        "Guard-post group " + id + " repeats parent " + parent);
            }
            parents.add(parent);
        }
        return List.copyOf(parents);
    }

    private static List<UnresolvedVariant> parseVariants(
            @Nullable JsonElement element,
            ResourceLocation id) {
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException(
                    "Guard-post group " + id + " field 'variants' must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP) {
            throw new JsonParseException(
                    "Guard-post group " + id + " has too many local variants; maximum is "
                            + GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP);
        }
        List<UnresolvedVariant> variants = new ArrayList<>(array.size());
        Set<String> names = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement variantElement = array.get(index);
            String location = "Guard-post group " + id + " variant " + index;
            if (!variantElement.isJsonObject()) {
                throw new JsonParseException(location + " must be an object");
            }
            JsonObject variant = variantElement.getAsJsonObject();
            rejectUnknownFields(variant, VARIANT_FIELDS, location);
            String name = requiredString(variant.get("name"), location + " field 'name'");
            if (!VARIANT_NAME.matcher(name).matches()) {
                throw new JsonParseException(
                        location + " field 'name' must be a 1-64 character lowercase path key");
            }
            if (!names.add(name)) {
                throw new JsonParseException(
                        "Guard-post group " + id + " repeats local variant name " + name);
            }
            int weight = requiredInteger(variant, "weight", location, 1, Integer.MAX_VALUE);
            Set<NaturalGuardDifficulty> difficulties = parseDifficulties(
                    variant.get("difficulties"), location);
            ResourceLocation defaultLoadout = optionalId(
                    variant.get("loadout_pool"),
                    location + " field 'loadout_pool'");
            List<UnresolvedMember> members = parseMembers(variant.get("members"), location);
            variants.add(new UnresolvedVariant(
                    name,
                    weight,
                    difficulties,
                    defaultLoadout,
                    members));
        }
        return List.copyOf(variants);
    }

    private static Set<NaturalGuardDifficulty> parseDifficulties(
            @Nullable JsonElement element,
            String location) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException(location + " field 'difficulties' must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0 || array.size() > 3) {
            throw new JsonParseException(
                    location + " field 'difficulties' must contain 1 through 3 values");
        }
        EnumSet<NaturalGuardDifficulty> difficulties =
                EnumSet.noneOf(NaturalGuardDifficulty.class);
        for (int index = 0; index < array.size(); index++) {
            String value = requiredString(
                    array.get(index),
                    location + " difficulty " + index);
            NaturalGuardDifficulty difficulty;
            try {
                difficulty = NaturalGuardDifficulty.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException(
                        location + " has unsupported difficulty '" + value + "'");
            }
            if (difficulty == NaturalGuardDifficulty.PEACEFUL) {
                throw new JsonParseException(
                        location + " cannot declare Peaceful; guard-post population is disabled there");
            }
            if (!difficulty.name().toLowerCase(Locale.ROOT).equals(value)) {
                throw new JsonParseException(
                        location + " difficulty values must be canonical lowercase names");
            }
            if (!difficulties.add(difficulty)) {
                throw new JsonParseException(location + " repeats difficulty '" + value + "'");
            }
        }
        return Set.copyOf(difficulties);
    }

    private static List<UnresolvedMember> parseMembers(
            @Nullable JsonElement element,
            String location) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException(location + " field 'members' must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0
                || array.size() > GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new JsonParseException(
                    location + " member count must be from 1 through "
                            + GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT);
        }
        List<UnresolvedMember> members = new ArrayList<>(array.size());
        boolean hasRequiredMember = false;
        for (int index = 0; index < array.size(); index++) {
            JsonElement memberElement = array.get(index);
            String memberLocation = location + " member " + index;
            if (!memberElement.isJsonObject()) {
                throw new JsonParseException(memberLocation + " must be an object");
            }
            JsonObject member = memberElement.getAsJsonObject();
            rejectUnknownFields(member, MEMBER_FIELDS, memberLocation);
            ResourceLocation loadout = optionalId(
                    member.get("loadout_pool"),
                    memberLocation + " field 'loadout_pool'");
            ResourceLocation role = optionalId(
                    member.get("role"),
                    memberLocation + " field 'role'");
            int chance = member.has("chance")
                    ? requiredInteger(
                            member,
                            "chance",
                            memberLocation,
                            1,
                            GuardPostGroupFormat.MAXIMUM_CHANCE_PERCENT)
                    : GuardPostGroupFormat.MAXIMUM_CHANCE_PERCENT;
            hasRequiredMember |= chance == GuardPostGroupFormat.MAXIMUM_CHANCE_PERCENT;
            members.add(new UnresolvedMember(loadout, role, chance));
        }
        if (!hasRequiredMember) {
            throw new JsonParseException(location + " must contain at least one required member");
        }
        return List.copyOf(members);
    }

    private static ResolvedGroup resolve(
            ResourceLocation id,
            Map<ResourceLocation, UnresolvedGroup> unresolved,
            Map<ResourceLocation, ResolvedGroup> resolved,
            Map<ResourceLocation, VisitState> states,
            List<ResourceLocation> path) {
        ResolvedGroup cached = resolved.get(id);
        if (cached != null) {
            return cached;
        }
        if (states.get(id) == VisitState.VISITING) {
            List<ResourceLocation> cycle = new ArrayList<>(path);
            cycle.add(id);
            throw new JsonParseException("Cyclic guard-post group inheritance: " + cycle);
        }
        UnresolvedGroup group = unresolved.get(id);
        if (group == null) {
            throw new JsonParseException("Missing inherited guard-post group " + id);
        }

        states.put(id, VisitState.VISITING);
        path.add(id);
        int inheritanceDepth = 1;
        ResourceLocation inheritedDefault = null;
        Map<String, GuardPostGroupVariant> merged = new LinkedHashMap<>();
        for (ResourceLocation parentId : group.parents()) {
            ResolvedGroup parent = resolve(parentId, unresolved, resolved, states, path);
            inheritanceDepth = Math.max(
                    inheritanceDepth,
                    Math.addExact(parent.inheritanceDepth(), 1));
            if (group.defaultLoadout() == null && parent.effectiveDefaultLoadout() != null) {
                if (inheritedDefault != null
                        && !inheritedDefault.equals(parent.effectiveDefaultLoadout())) {
                    throw new JsonParseException(
                            "Guard-post group " + id
                                    + " inherits conflicting default loadout pools "
                                    + inheritedDefault + " and " + parent.effectiveDefaultLoadout());
                }
                inheritedDefault = parent.effectiveDefaultLoadout();
            }
            for (GuardPostGroupVariant variant : parent.pool().variants()) {
                GuardPostGroupVariant existing = merged.putIfAbsent(variant.name(), variant);
                if (existing != null && !existing.equals(variant)) {
                    throw new JsonParseException(
                            "Guard-post group " + id
                                    + " inherits conflicting variant name " + variant.name());
                }
            }
        }
        if (inheritanceDepth > GuardPostGroupFormat.MAXIMUM_INHERITANCE_DEPTH) {
            throw new JsonParseException(
                    "Guard-post group inheritance exceeds maximum depth "
                            + GuardPostGroupFormat.MAXIMUM_INHERITANCE_DEPTH + " at " + id);
        }

        ResourceLocation effectiveDefault = group.defaultLoadout() == null
                ? inheritedDefault
                : group.defaultLoadout();
        for (UnresolvedVariant unresolvedVariant : group.variants()) {
            ResourceLocation variantDefault = unresolvedVariant.defaultLoadout() == null
                    ? effectiveDefault
                    : unresolvedVariant.defaultLoadout();
            List<GuardPostGroupMember> members = new ArrayList<>(
                    unresolvedVariant.members().size());
            for (int index = 0; index < unresolvedVariant.members().size(); index++) {
                UnresolvedMember unresolvedMember = unresolvedVariant.members().get(index);
                ResourceLocation loadout = unresolvedMember.loadoutPool() == null
                        ? variantDefault
                        : unresolvedMember.loadoutPool();
                if (loadout == null) {
                    throw new JsonParseException(
                            "Guard-post group " + id + " variant " + unresolvedVariant.name()
                                    + " member " + index
                                    + " has no loadout_pool to inherit");
                }
                members.add(new GuardPostGroupMember(
                        loadout,
                        unresolvedMember.role(),
                        unresolvedMember.chancePercent()));
            }
            GuardPostGroupVariant variant = new GuardPostGroupVariant(
                    unresolvedVariant.name(),
                    unresolvedVariant.weight(),
                    unresolvedVariant.difficulties(),
                    members);
            if (merged.putIfAbsent(variant.name(), variant) != null) {
                throw new JsonParseException(
                        "Guard-post group " + id
                                + " declares conflicting variant name " + variant.name());
            }
            if (merged.size() > GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP) {
                throw new JsonParseException(
                        "Guard-post group " + id + " resolves to more than "
                                + GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP + " variants");
            }
        }
        if (merged.isEmpty()) {
            throw new JsonParseException("Guard-post group " + id + " resolves to no variants");
        }

        path.remove(path.size() - 1);
        states.put(id, VisitState.VISITED);
        GuardPostGroupPool pool = new GuardPostGroupPool(
                id,
                group.parents(),
                List.copyOf(merged.values()));
        ResolvedGroup result = new ResolvedGroup(pool, effectiveDefault, inheritanceDepth);
        resolved.put(id, result);
        return result;
    }

    static JsonElement readBoundedJson(Reader reader, ResourceLocation id)
            throws IOException {
        char[] buffer = new char[4096];
        StringBuilder source = new StringBuilder();
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (source.length() + read > GuardPostGroupFormat.MAXIMUM_DOCUMENT_CHARACTERS) {
                throw new JsonParseException(
                        "Guard-post group " + id + " exceeds the maximum document size of "
                                + GuardPostGroupFormat.MAXIMUM_DOCUMENT_CHARACTERS + " characters");
            }
            source.append(buffer, 0, read);
        }
        try (JsonReader jsonReader = new JsonReader(new StringReader(source.toString()))) {
            jsonReader.setLenient(false);
            JsonElement json = readStrictValue(jsonReader, id, 0);
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException(
                        "Guard-post group " + id + " contains trailing JSON data");
            }
            return json;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new JsonParseException("Malformed JSON for guard-post group " + id, exception);
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
                                "Guard-post group " + id + " repeats JSON field '" + name + "'");
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
                    "Guard-post group " + id + " contains unexpected JSON token " + token);
        };
    }

    private static void requireJsonDepth(ResourceLocation id, int depth) {
        if (depth >= GuardPostGroupFormat.MAXIMUM_JSON_DEPTH) {
            throw new JsonParseException(
                    "Guard-post group " + id + " exceeds maximum JSON depth "
                            + GuardPostGroupFormat.MAXIMUM_JSON_DEPTH);
        }
    }

    private static int requiredInteger(
            JsonObject object,
            String field,
            String location,
            int minimum,
            int maximum) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException(location + " field '" + field + "' must be an integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new JsonParseException(location + " field '" + field + "' must be an integer");
        }
        try {
            BigDecimal value = primitive.getAsBigDecimal().stripTrailingZeros();
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
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(location + " must be a string");
        }
        String value = element.getAsString();
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new JsonParseException(location + " must be nonblank without surrounding whitespace");
        }
        return value;
    }

    private static @Nullable ResourceLocation optionalId(
            @Nullable JsonElement element,
            String location) {
        return element == null ? null : canonicalId(requiredString(element, location), location);
    }

    private static ResourceLocation canonicalId(String value, String location) {
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
                    "Unknown field(s) in " + location + ": " + String.join(", ", unknown));
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
        if (safe.length() <= 512) {
            return safe;
        }
        int end = 509;
        if (Character.isHighSurrogate(safe.charAt(end - 1))) {
            end--;
        }
        return safe.substring(0, end) + "...";
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private record UnresolvedGroup(
            ResourceLocation id,
            List<ResourceLocation> parents,
            @Nullable ResourceLocation defaultLoadout,
            List<UnresolvedVariant> variants) {
    }

    private record UnresolvedVariant(
            String name,
            int weight,
            Set<NaturalGuardDifficulty> difficulties,
            @Nullable ResourceLocation defaultLoadout,
            List<UnresolvedMember> members) {
    }

    private record UnresolvedMember(
            @Nullable ResourceLocation loadoutPool,
            @Nullable ResourceLocation role,
            int chancePercent) {
    }

    private record ResolvedGroup(
            GuardPostGroupPool pool,
            @Nullable ResourceLocation effectiveDefaultLoadout,
            int inheritanceDepth) {
    }

    record PreparedResources(
            Map<ResourceLocation, JsonElement> documents,
            List<GuardPostGroupReloadSnapshot.RejectedDocument> errors) {
        PreparedResources {
            documents = Map.copyOf(Objects.requireNonNull(documents, "documents"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }
}
