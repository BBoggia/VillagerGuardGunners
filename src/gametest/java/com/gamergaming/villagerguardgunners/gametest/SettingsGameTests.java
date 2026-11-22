package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class SettingsGameTests {
    @GameTest(templateNamespace = "minecraft", template = "villagerguardgunners/gametest_arena")
    public static void settingsRoundTrip(GameTestHelper h) {
        var original = new VillagerGuardGunnersConfig();
        original.villagePopulation.minimumGuards.validateAndSet(24);
        original.villagePopulation.maximumGuards.validateAndSet(32);
        original.armoryResupply.lowReserveMagazines.validateAndSet(8);
        original.armoryResupply.targetReserveMagazines.validateAndSet(16);
        var encoded = ConfigApi.serializeConfig(original);
        h.assertTrue(encoded.isValid(), encoded.toString());
        h.assertTrue(!encoded.get().contains("careAvailability"), "Preview must not be saved: careAvailability");
        h.assertTrue(!encoded.get().contains("revivalTimes"), "Preview must not be saved: revivalTimes");
        h.assertTrue(!encoded.get().contains("restTimes"), "Preview must not be saved: restTimes");
        var restored = new VillagerGuardGunnersConfig();
        var decoded = ConfigApi.deserializeConfig(restored, encoded.get());
        h.assertTrue(decoded.isValid(), decoded.toString());
        h.assertTrue(original.villagePopulation.settings().equals(restored.villagePopulation.settings()), "Population pair changed during round trip");
        h.assertTrue(original.armoryResupply.snapshot(true).equals(restored.armoryResupply.snapshot(true)), "Armory pair changed during round trip");
        h.succeed();
    }

}
