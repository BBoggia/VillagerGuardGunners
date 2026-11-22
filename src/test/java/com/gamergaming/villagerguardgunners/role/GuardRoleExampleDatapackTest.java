package com.gamergaming.villagerguardgunners.role;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardRoleExampleDatapackTest {
    @Test
    void everyExampleRolePassesTheProductionCompiler() throws IOException {
        Path project = Path.of(System.getProperty("villagerguardgunners.projectDir"));
        Path directory = project.resolve(
                "docs/examples/natural-loadout-datapack/data/vgg_example/guard_roles");
        Map<ResourceLocation, JsonElement> documents = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                String relative = directory.relativize(file).toString()
                        .replace(file.getFileSystem().getSeparator(), "/");
                String path = relative.substring(0, relative.length() - 5);
                documents.put(
                        ResourceLocation.fromNamespaceAndPath("vgg_example", path),
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
            }
        }

        Map<ResourceLocation, GuardRoleDefinition> roles = GuardRoles.compile(documents);
        GuardRoleDefinition marksman = roles.get(ResourceLocation.fromNamespaceAndPath(
                "vgg_example", "village_marksman"));
        assertEquals(1, roles.size());
        assertEquals(TacticalOrder.MARKSMAN, marksman.effectiveDefaults().tactics());
        assertEquals(MovementOrder.HOLD_POSITION, marksman.effectiveDefaults().movement());
        assertEquals(RangeOrder.BLOCKS_48, marksman.effectiveDefaults().maximumRange());
    }
}
