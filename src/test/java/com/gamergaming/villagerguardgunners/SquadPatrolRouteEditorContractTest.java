package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadPatrolRouteEditorContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void editorIsLedgerDrivenAtomicAndDoesNotExecuteRoutes() throws IOException {
        String editor = squadSource("SquadPatrolRouteEditor.java");
        String item = read(
                "src/main/java/com/gamergaming/villagerguardgunners/item/"
                        + "SquadCommandLedgerItem.java");
        String type = squadSource("SquadCommandType.java");

        assertAll(
                () -> assertTrue(type.contains("PATROL_ROUTE_EDITOR")),
                () -> assertTrue(type.contains("isPatrolRouteEditor()")),
                () -> assertTrue(editor.contains("EditAction.ADD_WAYPOINT")),
                () -> assertTrue(editor.contains("EditAction.REMOVE_LAST")),
                () -> assertTrue(editor.contains("EditAction.INSPECT")),
                () -> assertTrue(editor.contains("replacePatrolRoute(")),
                () -> assertTrue(item.contains(
                        "context.getClickedPos().relative(context.getClickedFace())")),
                () -> assertTrue(item.contains("reportRouteEdit(")),
                () -> assertFalse(editor.contains("getEntities")),
                () -> assertFalse(editor.contains("getChunk(")),
                () -> assertFalse(editor.contains("setChunkForced")),
                () -> assertFalse(editor.contains("moveTo(")),
                () -> assertFalse(editor.contains("TickEvent")));
    }

    @Test
    void liveValidationIsBoundedLoadedAndWalkable() throws IOException {
        String editor = squadSource("SquadPatrolRouteEditor.java");

        assertAll(
                () -> assertTrue(editor.contains("MAXIMUM_EDIT_DISTANCE = 8.0D")),
                () -> assertTrue(editor.contains("isInWorldBounds")),
                () -> assertTrue(editor.contains("getWorldBorder().isWithinBounds")),
                () -> assertTrue(editor.contains("hasChunkAt")),
                () -> assertTrue(editor.contains("getCollisionShape")),
                () -> assertTrue(editor.contains("getFluidState().isEmpty()")),
                () -> assertTrue(editor.contains("isFaceSturdy")));
    }

    @Test
    void releaseAndLocalizationContainTheEditorSurface() throws IOException {
        String build = read("build.gradle");
        String language = read(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json");

        assertAll(
                () -> assertTrue(build.contains("SquadPatrolRouteEditor.class")),
                () -> assertTrue(language.contains(
                        "squad_command_ledger.mode.patrol_route_editor")),
                () -> assertTrue(language.contains("patrol_route.added")),
                () -> assertTrue(language.contains("patrol_route.removed")),
                () -> assertTrue(language.contains("patrol_route.status")));
    }

    private static String squadSource(String fileName) throws IOException {
        return read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + fileName);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
