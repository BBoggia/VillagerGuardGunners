package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RaidRequirement;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.SelectorGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** Strict syntax and semantic parser for natural-loadout formats 1 through 4. */
final class NaturalGuardLoadoutParser {
    private NaturalGuardLoadoutParser() {
    }

    static NaturalGuardLoadoutPool parse(ResourceLocation id, JsonElement json) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException("Pool root must be a JSON object");
        }

        JsonObject root = json.getAsJsonObject();
        rejectUnknownFields(root, NaturalGuardLoadoutFormat.ROOT_FIELDS, "pool");
        int format = requiredPositiveInteger(root, "format", "pool");
        if (format != NaturalGuardLoadoutFormat.LEGACY_VERSION
                && format != NaturalGuardLoadoutFormat.CONDITIONAL_VERSION
                && format != NaturalGuardLoadoutFormat.SIDEARM_VERSION
                && format != NaturalGuardLoadoutFormat.ROLE_VERSION) {
            throw new JsonParseException(
                    "Unsupported format " + format + "; supported formats are 1, 2, 3, and 4");
        }

        JsonElement entriesElement = root.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            throw new JsonParseException("Required field 'entries' must be an array");
        }

        JsonArray entriesJson = entriesElement.getAsJsonArray();
        if (entriesJson.size() > NaturalGuardLoadoutFormat.MAX_ENTRIES_PER_POOL) {
            throw new JsonParseException(
                    "Pool contains " + entriesJson.size() + " entries; the maximum is "
                            + NaturalGuardLoadoutFormat.MAX_ENTRIES_PER_POOL);
        }
        List<NaturalGuardLoadoutPool.Entry> entries = new ArrayList<>(entriesJson.size());
        Set<String> entryFields = switch (format) {
            case NaturalGuardLoadoutFormat.LEGACY_VERSION ->
                    NaturalGuardLoadoutFormat.VERSION_1_ENTRY_FIELDS;
            case NaturalGuardLoadoutFormat.CONDITIONAL_VERSION ->
                    NaturalGuardLoadoutFormat.VERSION_2_ENTRY_FIELDS;
            case NaturalGuardLoadoutFormat.SIDEARM_VERSION ->
                    NaturalGuardLoadoutFormat.VERSION_3_ENTRY_FIELDS;
            default -> NaturalGuardLoadoutFormat.VERSION_4_ENTRY_FIELDS;
        };
        for (int index = 0; index < entriesJson.size(); index++) {
            String location = "entry " + index;
            JsonElement entryElement = entriesJson.get(index);
            if (!entryElement.isJsonObject()) {
                throw new JsonParseException(location + " must be a JSON object");
            }
            JsonObject entryJson = entryElement.getAsJsonObject();
            rejectUnknownFields(entryJson, entryFields, location);
            ResourceLocation gunId = requiredCanonicalId(entryJson, "gun", location);
            ResourceLocation sidearmId = format >= NaturalGuardLoadoutFormat.SIDEARM_VERSION
                    ? optionalCanonicalId(entryJson, "sidearm", location)
                    : null;
            ResourceLocation roleId = format >= NaturalGuardLoadoutFormat.ROLE_VERSION
                    ? optionalCanonicalId(entryJson, "role", location)
                    : null;
            int weight = requiredPositiveInteger(entryJson, "weight", location);
            NaturalGuardLoadoutConditionSpec conditions =
                    format >= NaturalGuardLoadoutFormat.CONDITIONAL_VERSION
                            ? optionalConditions(entryJson, location)
                            : NaturalGuardLoadoutConditionSpec.ANY;
            entries.add(new NaturalGuardLoadoutPool.Entry(
                    gunId, sidearmId, roleId, weight, conditions));
        }
        return new NaturalGuardLoadoutPool(id, format, entries);
    }

    private static NaturalGuardLoadoutConditionSpec optionalConditions(
            JsonObject entry,
            String entryLocation) {
        JsonElement element = entry.get("conditions");
        if (element == null) {
            return NaturalGuardLoadoutConditionSpec.ANY;
        }
        String location = entryLocation + " conditions";
        if (!element.isJsonObject()) {
            throw new JsonParseException(location + " must be a JSON object");
        }
        JsonObject conditions = element.getAsJsonObject();
        rejectUnknownFields(
                conditions,
                NaturalGuardLoadoutFormat.CONDITION_FIELDS,
                location);

        return new NaturalGuardLoadoutConditionSpec(
                optionalSelectorGroup(
                        conditions,
                        "dimensions",
                        value -> canonicalId(value, location + " dimensions")),
                optionalSelectorGroup(
                        conditions,
                        "biomes",
                        value -> registrySelector(value, location + " biomes")),
                optionalSelectorGroup(
                        conditions,
                        "structures",
                        value -> registrySelector(value, location + " structures")),
                optionalEnumSet(
                        conditions,
                        "difficulties",
                        NaturalGuardDifficulty.class,
                        location),
                optionalEnumSet(
                        conditions,
                        "spawn_sources",
                        NaturalGuardSpawnSource.class,
                        location),
                optionalRaidRequirement(conditions, location),
                optionalStringSet(
                        conditions,
                        "required_guard_tags",
                        location,
                        NaturalGuardLoadoutParser::guardTag),
                optionalStringSet(
                        conditions,
                        "excluded_guard_tags",
                        location,
                        NaturalGuardLoadoutParser::guardTag));
    }

    private static <T> SelectorGroup<T> optionalSelectorGroup(
            JsonObject conditions,
            String field,
            Function<String, T> parser) {
        JsonElement element = conditions.get(field);
        if (element == null) {
            return SelectorGroup.unrestricted();
        }
        String location = "condition '" + field + "'";
        if (!element.isJsonObject()) {
            throw new JsonParseException(location + " must be a JSON object");
        }
        JsonObject group = element.getAsJsonObject();
        rejectUnknownFields(
                group,
                NaturalGuardLoadoutFormat.SELECTOR_GROUP_FIELDS,
                location);
        if (group.size() == 0) {
            throw new JsonParseException(location + " cannot be an empty object");
        }
        return new SelectorGroup<>(
                optionalStringSet(group, "include", location, parser),
                optionalStringSet(group, "exclude", location, parser));
    }

    private static <E extends Enum<E>> Set<E> optionalEnumSet(
            JsonObject conditions,
            String field,
            Class<E> enumType,
            String parentLocation) {
        return optionalStringSet(
                conditions,
                field,
                parentLocation,
                value -> exactLowercaseEnum(value, enumType, field));
    }

    private static RaidRequirement optionalRaidRequirement(
            JsonObject conditions,
            String location) {
        JsonElement element = conditions.get("raid");
        if (element == null) {
            return RaidRequirement.ANY;
        }
        String value = requiredString(element, location + " field 'raid'");
        return exactLowercaseEnum(value, RaidRequirement.class, "raid");
    }

    private static <T> Set<T> optionalStringSet(
            JsonObject object,
            String field,
            String parentLocation,
            Function<String, T> parser) {
        JsonElement element = object.get(field);
        if (element == null) {
            return Set.of();
        }
        String location = parentLocation + " field '" + field + "'";
        if (!element.isJsonArray()) {
            throw new JsonParseException(location + " must be an array");
        }
        JsonArray values = element.getAsJsonArray();
        if (values.size() == 0) {
            throw new JsonParseException(location + " cannot be empty");
        }
        if (values.size()
                > NaturalGuardLoadoutFormat.MAX_VALUES_PER_CONDITION_COLLECTION) {
            throw new JsonParseException(
                    location + " contains " + values.size() + " values; the maximum is "
                            + NaturalGuardLoadoutFormat.MAX_VALUES_PER_CONDITION_COLLECTION);
        }
        Set<T> parsed = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            T value = parser.apply(requiredString(
                    values.get(index),
                    location + " value " + index));
            if (!parsed.add(value)) {
                throw new JsonParseException(location + " contains a duplicate value");
            }
        }
        return Set.copyOf(parsed);
    }

    private static ResourceLocation requiredCanonicalId(
            JsonObject object,
            String field,
            String location) {
        JsonElement element = object.get(field);
        if (element == null) {
            throw new JsonParseException(location + " requires field '" + field + "'");
        }
        return canonicalId(
                requiredString(element, location + " field '" + field + "'"),
                location + " field '" + field + "'");
    }

    private static ResourceLocation optionalCanonicalId(
            JsonObject object,
            String field,
            String location) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        return canonicalId(
                requiredString(element, location + " field '" + field + "'"),
                location + " field '" + field + "'");
    }

    private static ResourceLocation canonicalId(String value, String location) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !id.toString().equals(value)) {
            throw new JsonParseException(location + " must be a canonical namespaced ID");
        }
        return id;
    }

    private static RegistrySelector registrySelector(String value, String location) {
        boolean tag = value.startsWith("#");
        String rawId = tag ? value.substring(1) : value;
        ResourceLocation id = canonicalId(rawId, location);
        return tag ? RegistrySelector.tag(id) : RegistrySelector.exact(id);
    }

    private static String guardTag(String value) {
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new JsonParseException(
                    "Guard tags must be nonblank and must not contain surrounding whitespace");
        }
        return value;
    }

    private static <E extends Enum<E>> E exactLowercaseEnum(
            String value,
            Class<E> enumType,
            String field) {
        for (E candidate : enumType.getEnumConstants()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(value)) {
                return candidate;
            }
        }
        throw new JsonParseException(
                "Field '" + field + "' contains unsupported value '" + value + "'");
    }

    private static String requiredString(JsonElement element, String location) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(location + " must be a string");
        }
        return element.getAsString();
    }

    private static int requiredPositiveInteger(
            JsonObject object,
            String field,
            String location) {
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
                throw new JsonParseException(
                        location + " field '" + field + "' must be a positive integer");
            }
            return integer;
        } catch (ArithmeticException exception) {
            throw new JsonParseException(
                    location + " field '" + field
                            + "' must be a positive 32-bit integer");
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
}
