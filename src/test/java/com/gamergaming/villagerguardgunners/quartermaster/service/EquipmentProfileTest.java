package com.gamergaming.villagerguardgunners.quartermaster.service;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class EquipmentProfileTest {
    private static EquipmentProfile parse(String json) { return EquipmentProfile.parse(JsonParser.parseString(json).getAsJsonObject()); }
    @Test void defaultsAreBoundedAndCollectionsImmutable() {
        var profile = parse("{\"format\":1,\"primary\":[\"tacz:ak47\"]}");
        assertEquals(64, profile.ammoReserve()); assertEquals(16, profile.medicalReserve());
        assertThrows(UnsupportedOperationException.class, () -> profile.primary().clear());
    }
    @Test void rejectsUnknownFieldsVersionsAndSlots() {
        for (String json : new String[]{"{}", "{\"format\":2}", "{\"format\":1,\"repair\":true}", "{\"format\":1,\"armor\":{\"offhand\":[]}}"}) assertThrows(RuntimeException.class, () -> parse(json));
    }
    @Test void rejectsUnboundedOrFractionalReserves() {
        for (String value : new String[]{"-1", "513", "0.5", "1e999", "\"64\""}) assertThrows(RuntimeException.class, () -> parse("{\"format\":1,\"ammo_reserve\":" + value + "}"));
    }
    @Test void rejectsDuplicateNoncanonicalAndOversizeSelectors() {
        for (String selectors : new String[]{"[\"ak47\"]", "[\"tacz:ak47\",\"tacz:ak47\"]", "[true]", "[\"tacz:" + "a".repeat(96) + "\"]"}) assertThrows(RuntimeException.class, () -> parse("{\"format\":1,\"primary\":" + selectors + "}"));
    }
}
