package com.gamergaming.villagerguardgunners.role;

import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.ToggleOrder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/** Strict, bounded, atomic datapack registry for behavioral guard roles. */
public final class GuardRoles extends SimplePreparableReloadListener<GuardRoles.PreparedResources> {
    public static final String DIRECTORY = "guard_roles";
    public static final int FORMAT_VERSION = 1;
    public static final int MAXIMUM_ROLES = 256;
    public static final int MAXIMUM_PARENTS = 8;
    /** Maximum definitions in one inheritance path, including the assigned role. */
    public static final int MAXIMUM_INHERITANCE_DEPTH = 16;

    private static final Set<String> ROOT_FIELDS = Set.of("format", "inherits", "orders");
    private static final Set<String> ORDER_FIELDS = Set.of(
            "engagement",
            "tactics",
            "explosives",
            "ammo_pickup",
            "ammo_sharing",
            "movement",
            "maximum_range");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final GuardRoles INSTANCE = new GuardRoles();

    private volatile GuardRoleReloadSnapshot snapshot = GuardRoleReloadSnapshot.empty();

    GuardRoles() {
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event").addListener(INSTANCE);
    }

    public static @Nullable GuardRoleDefinition get(ResourceLocation id) {
        return INSTANCE.snapshot.get(id);
    }

    public static Map<ResourceLocation, GuardRoleDefinition> snapshot() {
        return INSTANCE.snapshot.roles();
    }

    public static GuardRoleReloadSnapshot reloadSnapshot() {
        return INSTANCE.snapshot;
    }

    GuardRoleReloadSnapshot currentSnapshot() {
        return snapshot;
    }

    public static long generation() {
        return INSTANCE.snapshot.generation();
    }

    @Override
    protected PreparedResources prepare(ResourceManager manager, ProfilerFiller profiler) {
        Objects.requireNonNull(manager, "manager");
        FileToIdConverter converter = FileToIdConverter.json(DIRECTORY);
        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources =
                converter.listMatchingResources(manager);
        if (resources.size() > MAXIMUM_ROLES) {
            return new PreparedResources(
                    Map.of(),
                    List.of("Found " + resources.size() + " role documents; maximum is "
                            + MAXIMUM_ROLES));
        }

        Map<ResourceLocation, JsonElement> parsed = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = converter.fileToId(entry.getKey());
                    try (var reader = entry.getValue().openAsReader()) {
                        JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                        if (json == null) {
                            json = JsonNull.INSTANCE;
                        }
                        if (parsed.put(id, json) != null) {
                            throw new IllegalArgumentException("Duplicate role ID " + id);
                        }
                    } catch (IOException | RuntimeException exception) {
                        errors.add(id + ": " + safeMessage(exception));
                    }
                });
        return new PreparedResources(parsed, errors);
    }

    @Override
    protected void apply(
            PreparedResources prepared,
            ResourceManager manager,
            ProfilerFiller profiler) {
        publish(prepared);
    }

    void publish(PreparedResources prepared) {
        List<String> errors = new ArrayList<>(prepared.errors());
        Map<ResourceLocation, GuardRoleDefinition> resolved = Map.of();
        if (errors.isEmpty()) {
            try {
                resolved = compile(prepared.documents());
            } catch (RuntimeException exception) {
                errors.add(safeMessage(exception));
            }
        }

        GuardRoleReloadSnapshot previous = snapshot;
        if (!errors.isEmpty()) {
            for (String error : errors) {
                LOGGER.error("Guard-role reload rejected atomically: {}", error);
            }
            snapshot = new GuardRoleReloadSnapshot(
                    previous.generation(), false, previous.roles(), errors);
            return;
        }

        long generation = previous.generation() == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : previous.generation() + 1L;
        snapshot = new GuardRoleReloadSnapshot(generation, true, resolved, List.of());
        LOGGER.info("Loaded {} guard role(s); generation {}", resolved.size(), generation);
    }

    static Map<ResourceLocation, GuardRoleDefinition> compile(
            Map<ResourceLocation, JsonElement> documents) {
        Objects.requireNonNull(documents, "documents");
        if (documents.size() > MAXIMUM_ROLES) {
            throw new JsonParseException(
                    "Role document count exceeds maximum " + MAXIMUM_ROLES);
        }

        Map<ResourceLocation, UnresolvedRole> unresolved = new LinkedHashMap<>();
        documents.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> unresolved.put(
                        Objects.requireNonNull(entry.getKey(), "role ID"),
                        parse(entry.getKey(), entry.getValue())));

        Map<ResourceLocation, GuardRoleDefinition> resolved = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> inheritanceDepths = new HashMap<>();
        Map<ResourceLocation, VisitState> states = new HashMap<>();
        for (ResourceLocation id : unresolved.keySet()) {
            resolve(id, unresolved, resolved, inheritanceDepths, states, new ArrayList<>());
        }
        return Map.copyOf(resolved);
    }

    private static UnresolvedRole parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Role " + id + " root must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        rejectUnknownFields(root, ROOT_FIELDS, "role " + id);
        int format = requiredPositiveInteger(root, "format", "role " + id);
        if (format != FORMAT_VERSION) {
            throw new JsonParseException(
                    "Role " + id + " uses unsupported format " + format
                            + "; supported format is " + FORMAT_VERSION);
        }

        List<ResourceLocation> parents = parseParents(root, id);
        JsonElement ordersElement = root.get("orders");
        GuardOrders defaults = ordersElement == null
                ? GuardOrders.DEFAULT
                : parseOrders(id, ordersElement);
        return new UnresolvedRole(id, parents, defaults);
    }

    private static List<ResourceLocation> parseParents(JsonObject root, ResourceLocation id) {
        JsonElement element = root.get("inherits");
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("Role " + id + " field 'inherits' must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > MAXIMUM_PARENTS) {
            throw new JsonParseException(
                    "Role " + id + " has too many parents; maximum is " + MAXIMUM_PARENTS);
        }
        List<ResourceLocation> parents = new ArrayList<>(array.size());
        Set<ResourceLocation> unique = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            ResourceLocation parent = canonicalId(
                    requiredString(array.get(index), "Role " + id + " parent " + index),
                    "Role " + id + " parent " + index);
            if (!unique.add(parent)) {
                throw new JsonParseException("Role " + id + " repeats parent " + parent);
            }
            parents.add(parent);
        }
        return List.copyOf(parents);
    }

    private static GuardOrders parseOrders(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("Role " + id + " field 'orders' must be an object");
        }
        JsonObject orders = element.getAsJsonObject();
        rejectUnknownFields(orders, ORDER_FIELDS, "role " + id + " orders");
        return new GuardOrders(
                optionalEnum(orders, "engagement", EngagementOrder.class, EngagementOrder.INHERIT),
                optionalEnum(orders, "tactics", TacticalOrder.class, TacticalOrder.INHERIT),
                optionalEnum(orders, "explosives", ToggleOrder.class, ToggleOrder.INHERIT),
                optionalEnum(orders, "ammo_pickup", ToggleOrder.class, ToggleOrder.INHERIT),
                optionalEnum(orders, "ammo_sharing", ToggleOrder.class, ToggleOrder.INHERIT),
                optionalEnum(orders, "movement", MovementOrder.class, MovementOrder.INHERIT),
                optionalEnum(orders, "maximum_range", RangeOrder.class, RangeOrder.INHERIT));
    }

    private static GuardRoleDefinition resolve(
            ResourceLocation id,
            Map<ResourceLocation, UnresolvedRole> unresolved,
            Map<ResourceLocation, GuardRoleDefinition> resolved,
            Map<ResourceLocation, Integer> inheritanceDepths,
            Map<ResourceLocation, VisitState> states,
            List<ResourceLocation> path) {
        GuardRoleDefinition cached = resolved.get(id);
        if (cached != null) {
            return cached;
        }
        if (states.get(id) == VisitState.VISITING) {
            path.add(id);
            throw new JsonParseException("Cyclic role inheritance: " + path);
        }
        UnresolvedRole role = unresolved.get(id);
        if (role == null) {
            throw new JsonParseException("Missing inherited role " + id);
        }

        states.put(id, VisitState.VISITING);
        path.add(id);
        GuardOrders inherited = GuardOrders.DEFAULT;
        int inheritanceDepth = 1;
        for (ResourceLocation parentId : role.parents()) {
            GuardRoleDefinition parent = resolve(
                    parentId,
                    unresolved,
                    resolved,
                    inheritanceDepths,
                    states,
                    path);
            inheritanceDepth = Math.max(
                    inheritanceDepth,
                    Math.addExact(inheritanceDepths.get(parentId), 1));
            inherited = mergeCompatible(id, inherited, parent.effectiveDefaults());
        }
        if (inheritanceDepth > MAXIMUM_INHERITANCE_DEPTH) {
            throw new JsonParseException(
                    "Role inheritance exceeds maximum depth " + MAXIMUM_INHERITANCE_DEPTH
                            + " at " + id);
        }
        path.remove(path.size() - 1);
        states.put(id, VisitState.VISITED);

        GuardRoleDefinition definition = new GuardRoleDefinition(
                role.id(),
                role.parents(),
                role.declaredDefaults(),
                role.declaredDefaults().withFallback(inherited));
        resolved.put(id, definition);
        inheritanceDepths.put(id, inheritanceDepth);
        return definition;
    }

    private static GuardOrders mergeCompatible(
            ResourceLocation child,
            GuardOrders current,
            GuardOrders next) {
        return new GuardOrders(
                mergeValue(child, "engagement", current.engagement(), next.engagement(), EngagementOrder.INHERIT),
                mergeValue(child, "tactics", current.tactics(), next.tactics(), TacticalOrder.INHERIT),
                mergeValue(child, "explosives", current.explosives(), next.explosives(), ToggleOrder.INHERIT),
                mergeValue(child, "ammo_pickup", current.ammoPickup(), next.ammoPickup(), ToggleOrder.INHERIT),
                mergeValue(child, "ammo_sharing", current.ammoSharing(), next.ammoSharing(), ToggleOrder.INHERIT),
                mergeValue(child, "movement", current.movement(), next.movement(), MovementOrder.INHERIT),
                mergeValue(child, "maximum_range", current.maximumRange(), next.maximumRange(), RangeOrder.INHERIT));
    }

    private static <E extends Enum<E>> E mergeValue(
            ResourceLocation child,
            String field,
            E current,
            E next,
            E inherit) {
        if (current == inherit) {
            return next;
        }
        if (next == inherit || current == next) {
            return current;
        }
        throw new JsonParseException(
                "Role " + child + " inherits conflicting values for '" + field
                        + "': " + current.name().toLowerCase(Locale.ROOT) + " and "
                        + next.name().toLowerCase(Locale.ROOT));
    }

    private static <E extends Enum<E>> E optionalEnum(
            JsonObject object,
            String field,
            Class<E> type,
            E fallback) {
        JsonElement element = object.get(field);
        if (element == null) {
            return fallback;
        }
        String value = requiredString(element, "Field '" + field + "'");
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(value)) {
                return candidate;
            }
        }
        throw new JsonParseException("Field '" + field + "' has unsupported value '" + value + "'");
    }

    private static int requiredPositiveInteger(JsonObject object, String field, String location) {
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
            if (integer <= 0) {
                throw new ArithmeticException("non-positive");
            }
            return integer;
        } catch (ArithmeticException exception) {
            throw new JsonParseException(
                    location + " field '" + field + "' must be a positive 32-bit integer");
        }
    }

    private static String requiredString(JsonElement element, String location) {
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

    private record UnresolvedRole(
            ResourceLocation id,
            List<ResourceLocation> parents,
            GuardOrders declaredDefaults) {
    }

    record PreparedResources(
            Map<ResourceLocation, JsonElement> documents,
            List<String> errors) {
        PreparedResources {
            documents = Map.copyOf(Objects.requireNonNull(documents, "documents"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }
}
