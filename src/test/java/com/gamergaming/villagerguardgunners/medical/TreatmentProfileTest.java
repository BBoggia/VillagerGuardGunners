package com.gamergaming.villagerguardgunners.medical;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class TreatmentProfileTest {
    private static final String VALID = "{\"format\":1,\"item\":\"villagerguardgunners:bandage\",\"healing\":4,\"cooldown_ticks\":40}";
    @Test void parsesFiniteDoseAndCooldown() {
        var p = TreatmentProfile.parse(JsonParser.parseString(VALID).getAsJsonObject());
        assertEquals(4, p.healing()); assertEquals(40, p.cooldownTicks()); assertNull(p.returnedItem());
    }
    @Test void rejectsUnknownFormatAndFields() {
        assertThrows(RuntimeException.class, () -> TreatmentProfile.parse(JsonParser.parseString(VALID.replace("\"format\":1", "\"format\":2")).getAsJsonObject()));
        assertThrows(RuntimeException.class, () -> TreatmentProfile.parse(JsonParser.parseString(VALID.replace("{", "{\"effect\":\"arbitrary\"," )).getAsJsonObject()));
    }
    @Test void rejectsNonfiniteOrUnboundedHealing() {
        for (String value : new String[]{"0", "-1", "65", "1e999", "1e-999", "\"4\""}) {
            assertThrows(RuntimeException.class, () -> TreatmentProfile.parse(JsonParser.parseString(VALID.replace("\"healing\":4", "\"healing\":" + value)).getAsJsonObject()));
        }
    }
    @Test void rejectsInvalidCooldownsAndIdentifiers() {
        for (String value : new String[]{"0", "-1", "1201", "4.5", "\"40\""}) {
            assertThrows(RuntimeException.class, () -> TreatmentProfile.parse(JsonParser.parseString(VALID.replace("\"cooldown_ticks\":40", "\"cooldown_ticks\":" + value)).getAsJsonObject()));
        }
        assertThrows(RuntimeException.class, () -> TreatmentProfile.parse(JsonParser.parseString(VALID.replace("villagerguardgunners:bandage", "bandage")).getAsJsonObject()));
    }
}
