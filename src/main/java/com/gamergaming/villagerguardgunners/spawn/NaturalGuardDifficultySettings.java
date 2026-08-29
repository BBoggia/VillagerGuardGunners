package com.gamergaming.villagerguardgunners.spawn;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Per-difficulty frequency, population cap, and datapack pool selection. */
public record NaturalGuardDifficultySettings(
        double upgradeChance,
        int maximumNearby,
        @Nullable ResourceLocation poolId) {

    public NaturalGuardDifficultySettings {
        if (!Double.isFinite(upgradeChance)
                || upgradeChance < 0.0D
                || upgradeChance > 1.0D) {
            throw new IllegalArgumentException("Upgrade chance must be between zero and one");
        }
        if (maximumNearby < 0) {
            throw new IllegalArgumentException("Maximum nearby gunners cannot be negative");
        }
    }

    public boolean enabled() {
        return upgradeChance > 0.0D && maximumNearby > 0 && poolId != null;
    }
}
