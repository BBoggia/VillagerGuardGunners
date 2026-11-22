package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import java.util.Objects;

/** Pure readiness-to-staffing and shift-cadence policy. */
public final class VillageDefenseShiftPolicy {
    public static final long PEACE_SHIFT_TICKS = 6_000L;
    public static final long RECOVERY_SHIFT_TICKS = 2_400L;
    public static final long WATCH_SHIFT_TICKS = 2_400L;

    private VillageDefenseShiftPolicy() {
    }

    public static boolean active(
            VillageDefenseState state,
            VillageDefenseDutySlotKind kind) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case SENTRY -> true;
            case DRILL -> state == VillageDefenseState.PEACE
                    || state == VillageDefenseState.RECOVERY;
            case OVERWATCH -> state.isAtLeast(VillageDefenseState.WATCH);
            case MUSTER -> state.isAtLeast(VillageDefenseState.ALERT);
        };
    }

    public static long shiftEnd(VillageDefenseState state, long gameTime) {
        Objects.requireNonNull(state, "state");
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Game time cannot be negative");
        }
        long duration = switch (state) {
            case PEACE -> PEACE_SHIFT_TICKS;
            case RECOVERY -> RECOVERY_SHIFT_TICKS;
            case WATCH -> WATCH_SHIFT_TICKS;
            case ALERT, SIEGE -> VillageDefenseDutyAssignment.NO_SHIFT_END;
        };
        if (duration == VillageDefenseDutyAssignment.NO_SHIFT_END) {
            return duration;
        }
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }

    public static long shiftNumber(VillageDefenseState state, long gameTime) {
        Objects.requireNonNull(state, "state");
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Game time cannot be negative");
        }
        long duration = switch (state) {
            case PEACE -> PEACE_SHIFT_TICKS;
            case RECOVERY -> RECOVERY_SHIFT_TICKS;
            case WATCH -> WATCH_SHIFT_TICKS;
            case ALERT, SIEGE -> 1L;
        };
        return gameTime / duration;
    }
}
