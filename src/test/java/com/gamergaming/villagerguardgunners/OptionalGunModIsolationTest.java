package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reads the compiled classes and proves that no gun mod has to be installed
 * for the rest of the mod to load.
 *
 * <p>A class that names a gun mod's classes fails to load when that mod is
 * absent. So every such class must sit in that mod's own group of files, and
 * nothing outside a group may name a class inside it, except the few listed
 * call sites that first check the mod is installed.</p>
 */
final class OptionalGunModIsolationTest {
    private static final String BASE = "com/gamergaming/villagerguardgunners/";

    /** A gun mod's packages and the only files of this mod that may name them. */
    private record Group(String name, List<String> modPackages, List<String> files) {
        boolean owns(String file) {
            return files.stream().anyMatch(file::startsWith);
        }
    }

    private static final List<Group> GROUPS = List.of(
            new Group("TaCZ", List.of("com/tacz/"), List.of(
                    "compat/tacz/",
                    "combat/NativeGunController",
                    "combat/TaCZExplosionResolver",
                    "combat/GuardFriendlyFireEvents",
                    "client/GuardThirdPersonAnimation",
                    "api/GuardNativeShotAccess",
                    "mixin/TaCZ")),
            new Group("MrCrayfish's Gun Mod", List.of("com/mrcrayfish/"), List.of("compat/cgm/")),
            new Group("Point Blank", List.of("com/vicmatskiv/", "mod/pbj/"), List.of(
                    "compat/pointblank/vpb/",
                    "compat/pointblank/pbj/")));

    /** Call sites that reach into a group only after checking that its mod is installed. */
    private static final Set<String> GUARDED_EDGES = Set.of(
            "VillagerGuardGunners -> compat/tacz/TaczBootstrap",
            "VillagerGuardGunners -> compat/cgm/CgmBootstrap",
            "compat/pointblank/PointBlankBootstrap -> compat/pointblank/vpb/VpbAccess",
            "compat/pointblank/PointBlankBootstrap -> compat/pointblank/pbj/PbjAccess",
            "mixin/client/GuardModelMixin -> client/GuardThirdPersonAnimation",
            "mixin/client/GuardSteveModelMixin -> client/GuardThirdPersonAnimation");

    private static final Pattern OWN_CLASS = Pattern.compile(Pattern.quote(BASE) + "[A-Za-z0-9_/$]+");

    private static final Map<String, List<String>> CONSTANTS = new HashMap<>();

    @BeforeAll
    static void readCompiledClasses() throws IOException {
        Path classes = Path.of(System.getProperty("villagerguardgunners.projectDir"))
                .resolve("build/neoforge/classes/java/main");
        Path root = classes.resolve(BASE);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                CONSTANTS.put(name.substring(0, name.length() - ".class".length()), utf8Constants(file));
            }
        }
        assertTrue(CONSTANTS.size() > 500, "Compiled classes were not found at " + root);
    }

    @Test
    void onlyAGunModsOwnFilesNameItsClasses() {
        Set<String> offenders = new TreeSet<>();
        for (var entry : CONSTANTS.entrySet()) {
            String file = topLevel(entry.getKey());
            for (Group group : GROUPS) {
                // A resource path such as ".../GunItem.class" is text used to detect a mod, not a class reference.
                boolean namesMod = entry.getValue().stream()
                        .filter(constant -> !constant.endsWith(".class"))
                        .anyMatch(constant -> group.modPackages().stream().anyMatch(constant::contains));
                if (namesMod && !group.owns(file)) {
                    offenders.add(file + " names " + group.name());
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Classes that cannot load without a gun mod: " + offenders);
    }

    @Test
    void nothingOutsideAGroupReachesIntoItExceptBehindAPresenceCheck() {
        Set<String> offenders = new TreeSet<>();
        Set<String> usedEdges = new TreeSet<>();
        for (var entry : CONSTANTS.entrySet()) {
            String from = topLevel(entry.getKey());
            for (String constant : entry.getValue()) {
                Matcher matcher = OWN_CLASS.matcher(constant);
                while (matcher.find()) {
                    String to = topLevel(matcher.group().substring(BASE.length()));
                    for (Group group : GROUPS) {
                        if (group.owns(to) && !group.owns(from)) {
                            String edge = from + " -> " + to;
                            if (GUARDED_EDGES.contains(edge)) {
                                usedEdges.add(edge);
                            } else {
                                offenders.add(edge);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Unguarded references into gun-mod-only code: " + offenders);
        assertTrue(usedEdges.containsAll(GUARDED_EDGES), () -> "Listed guarded edges that no longer exist: "
                + GUARDED_EDGES.stream().filter(edge -> !usedEdges.contains(edge)).toList());
    }

    /** The loader instantiates annotated classes whether or not their gun mod is installed. */
    @Test
    void gunModOnlyClassesAreNeverRegisteredByAnnotation() {
        List<String> offenders = new ArrayList<>();
        for (var entry : CONSTANTS.entrySet()) {
            String file = topLevel(entry.getKey());
            boolean grouped = GROUPS.stream().anyMatch(group -> group.owns(file));
            boolean annotated = entry.getValue().stream().anyMatch(constant ->
                    constant.equals("Lnet/neoforged/fml/common/EventBusSubscriber;")
                            || constant.equals("Lnet/neoforged/fml/common/Mod;"));
            if (grouped && annotated) {
                offenders.add(entry.getKey());
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Gun-mod-only classes registered by annotation: " + offenders);
    }

    @Test
    void entryPointChecksEachGunModBeforeTouchingIt() throws IOException {
        String entry = Files.readString(Path.of(System.getProperty("villagerguardgunners.projectDir"))
                .resolve("src/main/java/" + BASE + "VillagerGuardGunners.java"));
        for (String[] mod : new String[][] {
                {"OptionalGunMods.TACZ", "compat.tacz.TaczBootstrap.register("},
                {"OptionalGunMods.CGM", "compat.cgm.CgmBootstrap.register("},
                {"OptionalGunMods.POINT_BLANK", "compat.pointblank.PointBlankBootstrap.register("}}) {
            int constant = entry.indexOf(mod[0]);
            int check = entry.lastIndexOf("ModList.get().isLoaded(", constant);
            int call = entry.indexOf(mod[1]);
            assertTrue(check >= 0 && constant > check && call > constant, mod[1] + " must follow its presence check");
        }
        assertFalse(entry.contains("import com.gamergaming.villagerguardgunners.compat."));
    }

    private static String topLevel(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }

    /** Every text constant of a class file: the names of all classes, members, and descriptors it uses. */
    private static List<String> utf8Constants(Path file) throws IOException {
        List<String> constants = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            in.skipBytes(8);
            int count = in.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> constants.add(in.readUTF());
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                    case 15 -> in.skipBytes(3);
                    default -> throw new IOException("Unknown constant tag " + tag + " in " + file);
                }
            }
        }
        return constants;
    }
}
