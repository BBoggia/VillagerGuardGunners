package com.gamergaming.villagerguardgunners.sidearm;

/** Pure structural checks shared by persistence, menus, and runtime recovery. */
public final class SidearmStateContract {
    private SidearmStateContract() {
    }

    /**
     * Resolves the only two valid ownership layouts. Every other combination
     * retains its stacks but enters recovery instead of guessing their roles.
     */
    public static SidearmState resolve(
            boolean sidearmActiveFlag,
            boolean sidearmSlotOccupied,
            boolean stowedPrimarySlotOccupied,
            boolean mainHandOccupied) {
        if (!sidearmActiveFlag && !stowedPrimarySlotOccupied) {
            return SidearmState.PRIMARY_ACTIVE;
        }
        if (sidearmActiveFlag
                && !sidearmSlotOccupied
                && stowedPrimarySlotOccupied
                && mainHandOccupied) {
            return SidearmState.SIDEARM_ACTIVE;
        }
        return SidearmState.RECOVERY_REQUIRED;
    }
}
