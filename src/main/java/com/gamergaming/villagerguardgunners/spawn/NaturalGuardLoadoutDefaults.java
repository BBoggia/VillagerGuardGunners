package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Canonical conservative defaults shared by the synchronized config and policy tests. */
public final class NaturalGuardLoadoutDefaults {
    public static final NaturalGuardSpawnRules SPAWN_RULES = new NaturalGuardSpawnRules(
            Set.of(NaturalGuardSpawnSource.STRUCTURE),
            true,
            64.0D,
            false);

    public static final ResourceLocation EASY_POOL = pool("easy");
    public static final ResourceLocation NORMAL_POOL = pool("normal");
    public static final ResourceLocation HARD_POOL = pool("hard");

    private static final Map<NaturalGuardDifficulty, NaturalGuardDifficultySettings> BY_DIFFICULTY =
            Map.of(
                    NaturalGuardDifficulty.PEACEFUL,
                    new NaturalGuardDifficultySettings(0.0D, 0, null),
                    NaturalGuardDifficulty.EASY,
                    new NaturalGuardDifficultySettings(0.35D, 1, EASY_POOL),
                    NaturalGuardDifficulty.NORMAL,
                    new NaturalGuardDifficultySettings(0.50D, 2, NORMAL_POOL),
                    NaturalGuardDifficulty.HARD,
                    new NaturalGuardDifficultySettings(0.65D, 2, HARD_POOL));

    private NaturalGuardLoadoutDefaults() {
    }

    public static NaturalGuardDifficultySettings settingsFor(
            NaturalGuardDifficulty difficulty) {
        return BY_DIFFICULTY.get(Objects.requireNonNull(difficulty, "difficulty"));
    }

    private static ResourceLocation pool(String path) {
        return ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, path);
    }
}
