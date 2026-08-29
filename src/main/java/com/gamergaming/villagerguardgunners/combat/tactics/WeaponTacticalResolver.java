package com.gamergaming.villagerguardgunners.combat.tactics;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/** Pure, low-frequency profile classification from TaCZ metadata and overrides. */
public final class WeaponTacticalResolver {
    public static final Map<String, TacticalProfile> DEFAULT_TYPE_PROFILES = Map.ofEntries(
            Map.entry("pistol", TacticalProfile.SKIRMISHER),
            Map.entry("smg", TacticalProfile.SKIRMISHER),
            Map.entry("rifle", TacticalProfile.GENERALIST),
            Map.entry("sniper", TacticalProfile.MARKSMAN),
            Map.entry("shotgun", TacticalProfile.BREACHER),
            Map.entry("mg", TacticalProfile.SUPPORT),
            Map.entry("rpg", TacticalProfile.DEMOLITIONS));

    private WeaponTacticalResolver() {
    }

    public static Resolution resolve(
            @Nullable TacticalProfile gunOverride,
            @Nullable String gunType,
            WeaponTacticalTraits traits) {
        return resolve(gunOverride, gunType, traits, DEFAULT_TYPE_PROFILES);
    }

    public static Resolution resolve(
            @Nullable TacticalProfile gunOverride,
            @Nullable String gunType,
            WeaponTacticalTraits traits,
            Map<String, ? extends TacticalProfile> typeProfiles) {
        return resolve(null, gunOverride, gunType, traits, typeProfiles);
    }

    public static Resolution resolve(
            @Nullable TacticalProfile guardOverride,
            @Nullable TacticalProfile gunOverride,
            @Nullable String gunType,
            WeaponTacticalTraits traits,
            Map<String, ? extends TacticalProfile> typeProfiles) {
        String normalizedType = normalizeType(gunType);
        WeaponTacticalTraits normalizedTraits = Objects.requireNonNullElse(
                traits,
                WeaponTacticalTraits.NONE);

        if (guardOverride != null) {
            return new Resolution(guardOverride, Source.PER_GUARD_ORDER, normalizedType);
        }
        if (gunOverride != null) {
            return new Resolution(gunOverride, Source.GUN_OVERRIDE, normalizedType);
        }

        TacticalProfile typeProfile = findTypeProfile(normalizedType, typeProfiles);
        if (typeProfile != null) {
            return new Resolution(typeProfile, Source.TYPE_MAPPING, normalizedType);
        }
        if (normalizedTraits.explosive()) {
            return new Resolution(
                    TacticalProfile.DEMOLITIONS,
                    Source.EXPLOSIVE_TRAIT,
                    normalizedType);
        }
        if (normalizedTraits.multiProjectile()) {
            return new Resolution(
                    TacticalProfile.BREACHER,
                    Source.MULTI_PROJECTILE_TRAIT,
                    normalizedType);
        }
        return new Resolution(TacticalProfile.GENERALIST, Source.FALLBACK, normalizedType);
    }

    private static @Nullable TacticalProfile findTypeProfile(
            String normalizedType,
            Map<String, ? extends TacticalProfile> typeProfiles) {
        if (normalizedType.isEmpty() || typeProfiles == null || typeProfiles.isEmpty()) {
            return null;
        }

        TacticalProfile resolved = null;
        for (Map.Entry<String, ? extends TacticalProfile> entry : typeProfiles.entrySet()) {
            if (normalizedType.equals(normalizeType(entry.getKey()))) {
                TacticalProfile candidate = entry.getValue();
                if (candidate == null) {
                    continue;
                }
                if (resolved != null && resolved != candidate) {
                    // Reject contradictory aliases instead of allowing map
                    // iteration order to choose a guard's combat behavior.
                    return null;
                }
                resolved = candidate;
            }
        }
        return resolved;
    }

    private static String normalizeType(@Nullable String gunType) {
        return gunType == null ? "" : gunType.trim().toLowerCase(Locale.ROOT);
    }

    public enum Source {
        PER_GUARD_ORDER,
        GUN_OVERRIDE,
        TYPE_MAPPING,
        EXPLOSIVE_TRAIT,
        MULTI_PROJECTILE_TRAIT,
        FALLBACK
    }

    public record Resolution(
            TacticalProfile profile,
            Source source,
            String normalizedGunType) {
        public Resolution {
            profile = Objects.requireNonNull(profile, "profile");
            source = Objects.requireNonNull(source, "source");
            normalizedGunType = Objects.requireNonNull(normalizedGunType, "normalizedGunType");
        }
    }
}
