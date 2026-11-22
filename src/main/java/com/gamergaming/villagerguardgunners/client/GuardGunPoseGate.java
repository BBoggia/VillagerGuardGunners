package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.guns.OptionalGunMods;
import net.minecraftforge.fml.ModList;

/** Decided once: whether the TaCZ third-person pose may be applied to guard models. */
public final class GuardGunPoseGate {
    public static final boolean TACZ_INSTALLED = ModList.get().isLoaded(OptionalGunMods.TACZ);

    private GuardGunPoseGate() {
    }
}
