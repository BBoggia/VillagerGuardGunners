package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GunProviderSeamContractTest {
    private static final String MAIN = "src/main/java/com/gamergaming/villagerguardgunners/";
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void neutralGunPackageImportsNoGunMod() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(projectDirectory.resolve(MAIN + "guns"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("com.tacz") || source.contains("compat.tacz")
                        || source.contains("NativeGunController")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Gun-mod references in the neutral package: " + offenders);
    }

    @Test
    void combatGoalHoldsThePerGuardSwitchInsteadOfATaCZController() throws IOException {
        String goal = read(MAIN + "ai/GuardGunCombatGoal.java");
        String sidearm = read(MAIN + "sidearm/GuardSidearmRuntimeController.java");

        assertAll(
                () -> assertTrue(goal.contains("private final GuardWeaponControl gunController;")),
                () -> assertTrue(goal.contains("this.gunController = new GuardWeaponControl(guard);")),
                () -> assertFalse(goal.contains("new NativeGunController(")),
                () -> assertTrue(sidearm.contains("WeaponDriver nativeController,")),
                () -> assertFalse(sidearm.contains("NativeGunController")));
    }

    @Test
    void taczIsRegisteredAsAProviderWhoseDriverIsTheNativeController() throws IOException {
        String main = read(MAIN + "compat/tacz/TaczBootstrap.java");
        String provider = read(MAIN + "compat/tacz/provider/TaczGunProvider.java");
        String controller = read(MAIN + "combat/NativeGunController.java");

        assertAll(
                () -> assertTrue(main.contains("GunProviders.register(")),
                () -> assertTrue(main.contains("TaczGunProvider()")),
                () -> assertTrue(provider.contains("return new NativeGunController(guard);")),
                () -> assertTrue(controller.contains("public final class NativeGunController implements WeaponDriver")));
    }

    @Test
    void optionalGunModsAreReferencedOnlyFromTheirOwnPackages() throws IOException {
        // Mod package prefix -> the one compat package allowed to name it.
        var owners = java.util.Map.of(
                "com.mrcrayfish", "compat/cgm/",
                "com.vicmatskiv", "compat/pointblank/",
                "mod.pbj", "compat/pointblank/");
        List<String> offenders = new ArrayList<>();
        Path main = projectDirectory.resolve(MAIN);
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = main.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (var owner : owners.entrySet()) {
                    if (source.contains(owner.getKey()) && !relative.startsWith(owner.getValue())) {
                        offenders.add(relative + " names " + owner.getKey());
                    }
                }
                for (String compat : List.of("compat.cgm", "compat.pointblank")) {
                    if (source.contains(compat) && !relative.startsWith(compat.replace('.', '/') + "/")
                            && !relative.equals("VillagerGuardGunners.java")) {
                        offenders.add(relative + " names " + compat);
                    }
                }
            }
        }
        String entry = read(MAIN + "VillagerGuardGunners.java");
        String toml = read("src/main/resources/META-INF/mods.toml");

        assertAll(
                () -> assertTrue(offenders.isEmpty(), () -> "Optional gun mods referenced outside their packages: " + offenders),
                () -> assertBehindPresenceCheck(entry, "OptionalGunMods.CGM", "compat.cgm.CgmBootstrap.register()"),
                () -> assertBehindPresenceCheck(entry, "OptionalGunMods.POINT_BLANK",
                        "compat.pointblank.PointBlankBootstrap.register()"),
                () -> assertEquals(1, entry.split("compat\\.cgm\\.", -1).length - 1,
                        "The mod entry point may name only the CGM bootstrap"),
                () -> assertEquals(1, entry.split("compat\\.pointblank\\.", -1).length - 1,
                        "The mod entry point may name only the Point Blank bootstrap"),
                () -> assertTrue(toml.contains("modId=\"cgm\"\nmandatory=false")),
                () -> assertTrue(toml.contains("modId=\"pointblank\"\nmandatory=false")));
    }

    private static void assertBehindPresenceCheck(String entry, String modConstant, String registration) {
        int constant = entry.indexOf(modConstant);
        int check = entry.lastIndexOf("ModList.get().isLoaded(", constant);
        int call = entry.indexOf(registration);
        assertTrue(check >= 0 && constant > check && call > constant,
                () -> registration + " must be reached only behind a mod-presence check");
    }

    /** Only the build-specific adapter and the two mixins may name a Point Blank build's packages. */
    @Test
    void pointBlankBuildsAreReachedOnlyThroughTheAdapterAndItsMixins() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path root = projectDirectory.resolve(MAIN + "compat/pointblank");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file, StandardCharsets.UTF_8);
                boolean namesBuild = source.contains("import com.vicmatskiv") || source.contains("import mod.pbj");
                if (namesBuild && !relative.startsWith("vpb/")) {
                    offenders.add(relative);
                }
            }
        }
        String adapter = read(MAIN + "compat/pointblank/vpb/VpbAccess.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(offenders.isEmpty(), () -> "Point Blank classes imported outside the adapter: " + offenders),
                () -> assertFalse(adapter.contains("mod.pbj"), "The Jelly adapter is generated, never hand-written"),
                () -> assertFalse(Files.exists(root.resolve("pbj"))),
                () -> assertTrue(build.contains("line.replace('com.vicmatskiv.pointblank', 'mod.pbj')")),
                // The mod's block-hit and projectile code casts the shooter to a player.
                () -> assertFalse(adapter.contains("handleBlockHit")),
                () -> assertFalse(adapter.contains("createProjectile")),
                () -> assertTrue(adapter.contains("block -> false,"), "Guards must never break blocks"));
    }

    /** The renderer mixins exist once per build, differ only in their target, and never run on a server. */
    @Test
    void heldGunRendererMixinsAreClientOnlyAndIdenticalApartFromTheirTarget() throws IOException {
        String vpb = read(MAIN + "compat/pointblank/mixin/VpbGunItemRendererMixin.java");
        String pbj = read(MAIN + "compat/pointblank/mixin/PbjGunItemRendererMixin.java");
        String config = read("src/main/resources/mixins.villagerguardgunners.pointblank.json");
        String context = read(MAIN + "client/GuardRenderContext.java");
        String server = config.substring(config.indexOf("\"mixins\""), config.indexOf("\"client\""));

        assertAll(
                () -> assertEquals(pbj, vpb.replace("com.vicmatskiv.pointblank", "mod.pbj").replace("Vpb", "Pbj")),
                () -> assertTrue(vpb.contains("@Pseudo")),
                () -> assertTrue(vpb.contains("require = 0")),
                () -> assertFalse(server.contains("GunItemRendererMixin")),
                () -> assertTrue(config.contains("\"VpbGunItemRendererMixin\"")),
                () -> assertTrue(config.contains("\"PbjGunItemRendererMixin\"")),
                () -> assertTrue(config.contains("client.HeldItemRendererMixin")),
                // Only a guard with no holder found is redirected; players and other mobs are untouched.
                () -> assertTrue(context.contains("held && holder instanceof Guard")));
    }

    /** A driver method the switch forgot to forward would silently run an interface default. */
    @Test
    void switchForwardsEveryDriverMethod() throws ReflectiveOperationException {
        Class<?> driver = Class.forName("com.gamergaming.villagerguardgunners.guns.WeaponDriver");
        Class<?> control = Class.forName("com.gamergaming.villagerguardgunners.guns.GuardWeaponControl");
        List<String> missing = new ArrayList<>();
        for (Method method : driver.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            Method implemented = control.getMethod(method.getName(), method.getParameterTypes());
            if (implemented.getDeclaringClass() != control) {
                missing.add(method.getName());
            }
        }
        assertTrue(missing.isEmpty(), () -> "Driver methods not forwarded by GuardWeaponControl: " + missing);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
