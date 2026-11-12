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
        String main = read(MAIN + "VillagerGuardGunners.java");
        String provider = read(MAIN + "compat/tacz/provider/TaczGunProvider.java");
        String controller = read(MAIN + "combat/NativeGunController.java");

        assertAll(
                () -> assertTrue(main.contains("GunProviders.register(")),
                () -> assertTrue(main.contains("TaczGunProvider()")),
                () -> assertTrue(provider.contains("return new NativeGunController(guard);")),
                () -> assertTrue(controller.contains("public final class NativeGunController implements WeaponDriver")));
    }

    @Test
    void optionalGunModIsReferencedOnlyFromItsOwnPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path main = projectDirectory.resolve(MAIN);
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = main.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("compat/cgm/")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("com.mrcrayfish")
                        || source.contains("compat.cgm") && !relative.equals("VillagerGuardGunners.java")) {
                    offenders.add(relative);
                }
            }
        }
        String entry = read(MAIN + "VillagerGuardGunners.java");
        int presenceCheck = entry.indexOf("ModList.get().isLoaded(");
        int registration = entry.indexOf("compat.cgm.CgmBootstrap.register()");

        assertAll(
                () -> assertTrue(offenders.isEmpty(), () -> "MrCrayfish's Gun Mod referenced outside compat/cgm: " + offenders),
                () -> assertTrue(presenceCheck >= 0 && registration > presenceCheck,
                        "The CGM bootstrap must be reached only behind a mod-presence check"),
                () -> assertEquals(1, entry.split("compat\\.cgm\\.", -1).length - 1,
                        "The mod entry point may name only the CGM bootstrap"),
                () -> assertTrue(read("src/main/resources/META-INF/neoforge.mods.toml")
                        .contains("modId=\"cgm\"\ntype=\"optional\"")));
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
