package com.gamergaming.villagerguardgunners.village.compat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompatibilityJsonTest {
    @Test void rejectsDuplicateKeysTrailingDataAndDeepNesting() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> CompatibilityJson.parse("{\"format\":1,\"format\":2}"));
        assertThrows(Exception.class, () -> CompatibilityJson.parse("{} {}"));
        assertThrows(IllegalArgumentException.class, () -> CompatibilityJson.parse("{\"x\":" + "[".repeat(10) + "0" + "]".repeat(10) + "}"));
        assertEquals(1, CompatibilityJson.parse("{\"format\":1}").get("format").getAsInt());
    }
}
