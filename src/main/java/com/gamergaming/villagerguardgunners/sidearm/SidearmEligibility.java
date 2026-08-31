package com.gamergaming.villagerguardgunners.sidearm;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pure sidearm eligibility contract evaluated only when a stored weapon
 * changes or a transition is requested.
 *
 * <p>Exact inclusions may extend third-party gun packs beyond the default
 * pistol type, but they can never bypass stack, definition, exclusion, or
 * explosive-safety checks.</p>
 */
public final class SidearmEligibility {
    public static final Set<String> DEFAULT_ELIGIBLE_GUN_TYPES = Set.of("pistol");

    private SidearmEligibility() {
    }

    public static EligibilityStatus evaluate(
            @Nullable String gunId,
            @Nullable String gunType,
            boolean gunItem,
            int stackCount,
            boolean definitionReadable,
            boolean explosiveStateReadable,
            boolean explosive,
            Set<String> eligibleGunTypes,
            Set<String> additionalGunIds,
            Set<String> excludedGunIds) {
        Objects.requireNonNull(eligibleGunTypes, "eligibleGunTypes");
        Objects.requireNonNull(additionalGunIds, "additionalGunIds");
        Objects.requireNonNull(excludedGunIds, "excludedGunIds");

        if (!gunItem) {
            return EligibilityStatus.NOT_GUN;
        }
        if (stackCount != 1) {
            return EligibilityStatus.INVALID_STACK_COUNT;
        }
        if (!definitionReadable || gunId == null || gunId.isBlank()) {
            return EligibilityStatus.DEFINITION_UNAVAILABLE;
        }
        if (!explosiveStateReadable) {
            return EligibilityStatus.EXPLOSIVE_STATE_UNREADABLE;
        }
        if (explosive) {
            return EligibilityStatus.EXPLOSIVE_WEAPON;
        }
        if (excludedGunIds.contains(gunId)) {
            return EligibilityStatus.EXCLUDED_GUN;
        }
        if (additionalGunIds.contains(gunId)) {
            return EligibilityStatus.ELIGIBLE_BY_GUN_ID;
        }

        String normalizedType = normalizeType(gunType);
        if (!normalizedType.isEmpty() && containsNormalized(eligibleGunTypes, normalizedType)) {
            return EligibilityStatus.ELIGIBLE_BY_GUN_TYPE;
        }
        return EligibilityStatus.UNSUPPORTED_GUN_TYPE;
    }

    private static boolean containsNormalized(Set<String> configuredTypes, String normalizedType) {
        for (String configuredType : configuredTypes) {
            if (normalizedType.equals(normalizeType(configuredType))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeType(@Nullable String gunType) {
        return gunType == null ? "" : gunType.trim().toLowerCase(Locale.ROOT);
    }

    public enum EligibilityStatus {
        ELIGIBLE_BY_GUN_TYPE(true),
        ELIGIBLE_BY_GUN_ID(true),
        NOT_GUN(false),
        INVALID_STACK_COUNT(false),
        DEFINITION_UNAVAILABLE(false),
        EXPLOSIVE_STATE_UNREADABLE(false),
        EXPLOSIVE_WEAPON(false),
        EXCLUDED_GUN(false),
        UNSUPPORTED_GUN_TYPE(false);

        private final boolean eligible;

        EligibilityStatus(boolean eligible) {
            this.eligible = eligible;
        }

        public boolean eligible() {
            return eligible;
        }
    }
}
