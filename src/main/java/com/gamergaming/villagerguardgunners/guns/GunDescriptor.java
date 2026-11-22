package com.gamergaming.villagerguardgunners.guns;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * What a gun mod's own definitions say about one gun.
 *
 * @param type the mod's weapon class in the vocabulary used by tactical
 *     profiles and sidearm rules (pistol, smg, rifle, sniper, shotgun, ...),
 *     or null when the mod does not say
 * @param ammoId identity of the ammo the gun takes, or null when unknown
 * @param firingDataPresent false when the gun is defined but the data needed
 *     to fire or reload it is missing
 */
public record GunDescriptor(
        GunRef ref,
        @Nullable String type,
        @Nullable ResourceLocation ammoId,
        boolean firingDataPresent) {
    public GunDescriptor {
        Objects.requireNonNull(ref, "ref");
    }
}
