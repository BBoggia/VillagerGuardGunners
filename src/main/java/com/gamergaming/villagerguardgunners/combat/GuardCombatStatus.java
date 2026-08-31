package com.gamergaming.villagerguardgunners.combat;

/**
 * Compact, player-facing explanation of a guard's current gun behavior.
 *
 * <p>The server synchronizes only the explicit network ID while a guard menu
 * is open. Keep existing IDs stable if values are added in a later release.</p>
 */
public enum GuardCombatStatus {
    UNKNOWN(0, "unknown"),
    DISABLED(1, "disabled"),
    UNARMED(2, "unarmed"),
    IDLE(3, "idle"),
    HOLDING_FIRE(4, "holding_fire"),
    WEAPON_DATA_NOT_READY(5, "weapon_data_not_ready"),
    WEAPON_ERROR(6, "weapon_error"),
    OUT_OF_AMMO(7, "out_of_ammo"),
    NO_LINE_OF_SIGHT(8, "no_line_of_sight"),
    ACQUIRING_TARGET(9, "acquiring_target"),
    AIMING(10, "aiming"),
    INVALID_FIRING_WINDOW(11, "invalid_firing_window"),
    TOO_CLOSE(12, "too_close"),
    OUT_OF_RANGE(13, "out_of_range"),
    UNSAFE_SHOT(14, "unsafe_shot"),
    REPOSITIONING(15, "repositioning"),
    RELOADING(16, "reloading"),
    CYCLING_ACTION(17, "cycling_action"),
    DRAWING_WEAPON(18, "drawing_weapon"),
    CHARGING(19, "charging"),
    COOLING_DOWN(20, "cooling_down"),
    READY(21, "ready"),
    WEAPON_RESTRICTED(22, "weapon_restricted"),
    RECOVERY_REQUIRED(23, "recovery_required");

    private static final String TRANSLATION_PREFIX =
            "container.villagerguardgunners.combat_status.";
    private static final GuardCombatStatus[] BY_NETWORK_ID = createNetworkIndex();

    private final int networkId;
    private final String translationKey;
    private final String descriptionKey;

    GuardCombatStatus(int networkId, String translationSuffix) {
        this.networkId = networkId;
        this.translationKey = TRANSLATION_PREFIX + translationSuffix;
        this.descriptionKey = this.translationKey + ".description";
    }

    public int networkId() {
        return networkId;
    }

    public String translationKey() {
        return translationKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    /** Invalid or newer-server values fail closed to a neutral display. */
    public static GuardCombatStatus fromNetworkId(int networkId) {
        return networkId >= 0 && networkId < BY_NETWORK_ID.length
                ? BY_NETWORK_ID[networkId]
                : UNKNOWN;
    }

    /**
     * Resolves the display from data the live goal and TaCZ controller already
     * cache. This method performs no world query, pathfinding, inventory scan,
     * allocation, or gun-definition/property evaluation.
     */
    public static GuardCombatStatus resolveActiveCombat(
            boolean mayEngage,
            boolean weaponAllowed,
            boolean trackingCurrentTarget,
            boolean cachedLineOfSight,
            boolean visibilityRequirementMet,
            boolean aimRequirementMet,
            boolean validFiringWindow,
            boolean belowMinimumRange,
            boolean withinMaximumRange,
            boolean unsafeShot,
            boolean repositioning,
            WeaponActivity weaponActivity) {
        if (!mayEngage) {
            return HOLDING_FIRE;
        }
        if (!weaponAllowed) {
            return WEAPON_RESTRICTED;
        }
        if (weaponActivity == null) {
            return UNKNOWN;
        }
        switch (weaponActivity) {
            case WEAPON_ERROR:
                return WEAPON_ERROR;
            case OUT_OF_AMMO:
                return OUT_OF_AMMO;
            case WEAPON_DATA_NOT_READY:
                return WEAPON_DATA_NOT_READY;
            case RELOADING:
                return RELOADING;
            case CYCLING_ACTION:
                return CYCLING_ACTION;
            case DRAWING_WEAPON:
                return DRAWING_WEAPON;
            default:
                break;
        }
        if (!validFiringWindow) {
            return INVALID_FIRING_WINDOW;
        }
        if (belowMinimumRange) {
            return TOO_CLOSE;
        }
        if (!withinMaximumRange) {
            return OUT_OF_RANGE;
        }
        if (!trackingCurrentTarget) {
            return ACQUIRING_TARGET;
        }
        if (!cachedLineOfSight) {
            return NO_LINE_OF_SIGHT;
        }
        if (!visibilityRequirementMet) {
            return ACQUIRING_TARGET;
        }
        if (!aimRequirementMet) {
            return AIMING;
        }
        if (unsafeShot) {
            return repositioning ? REPOSITIONING : UNSAFE_SHOT;
        }
        if (weaponActivity == WeaponActivity.CHARGING) {
            return CHARGING;
        }
        if (weaponActivity == WeaponActivity.COOLING_DOWN) {
            return COOLING_DOWN;
        }
        return READY;
    }

    private static GuardCombatStatus[] createNetworkIndex() {
        int maximumId = 0;
        for (GuardCombatStatus status : values()) {
            maximumId = Math.max(maximumId, status.networkId);
        }
        GuardCombatStatus[] index = new GuardCombatStatus[maximumId + 1];
        java.util.Arrays.fill(index, UNKNOWN);
        for (GuardCombatStatus status : values()) {
            index[status.networkId] = status;
        }
        return index;
    }

    public enum WeaponActivity {
        WEAPON_DATA_NOT_READY,
        WEAPON_ERROR,
        OUT_OF_AMMO,
        RELOADING,
        CYCLING_ACTION,
        DRAWING_WEAPON,
        CHARGING,
        COOLING_DOWN,
        READY
    }
}
