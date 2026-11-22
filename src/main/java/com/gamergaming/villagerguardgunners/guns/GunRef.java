package com.gamergaming.villagerguardgunners.guns;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable identity of a gun across gun mods.
 *
 * <p>The meaning of {@code id} belongs to the provider. TaCZ uses its gun-pack
 * identifier, because every TaCZ gun shares one item. Mods that register one
 * item per gun use the item identifier.</p>
 */
public record GunRef(String provider, ResourceLocation id) {
    public GunRef {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(id, "id");
    }
}
