package com.gamergaming.villagerguardgunners.combat.tactics;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable tactical snapshot for one gun, read by the combat loop without touching the gun mod. */
public record CachedTacticalState(
        @Nullable ResourceLocation gunId,
        boolean metadataReadable,
        WeaponTacticalResolver.Resolution resolution,
        WeaponTacticalTraits traits,
        TacticalEnvelope envelope) {
}
