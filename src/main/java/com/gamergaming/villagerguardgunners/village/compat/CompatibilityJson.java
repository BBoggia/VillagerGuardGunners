package com.gamergaming.villagerguardgunners.village.compat;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;

/** Bounded nesting and duplicate-key rejection before schema validation. */
final class CompatibilityJson {
    static JsonObject parse(String text) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = read(reader, 0);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Expected one profile object");
            return value.getAsJsonObject();
        }
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 8) throw new IllegalArgumentException("Profile nesting exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (result.has(key)) throw new IllegalArgumentException("Duplicate key: " + key);
                    result.add(key, read(reader, depth + 1));
                }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) result.add(read(reader, depth + 1));
                reader.endArray(); yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Invalid profile JSON");
        };
    }
    private CompatibilityJson() { }
}
