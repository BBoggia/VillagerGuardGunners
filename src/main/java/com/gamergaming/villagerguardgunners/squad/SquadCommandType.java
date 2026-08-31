package com.gamergaming.villagerguardgunners.squad;

/** Player-selectable temporary squad command modes. */
public enum SquadCommandType {
    RALLY,
    DEFEND,
    HOLD,
    FOCUS_TARGET,
    FALL_BACK,
    RESUME_ORDERS;

    public SquadCommandType next() {
        SquadCommandType[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public boolean requiresPosition() {
        return this == RALLY || this == DEFEND || this == HOLD || this == FALL_BACK;
    }

    public boolean requiresTarget() {
        return this == FOCUS_TARGET;
    }

    public boolean isStoredCommand() {
        return this != RESUME_ORDERS;
    }

    public String translationKey() {
        return "item.villagerguardgunners.squad_command_ledger.mode."
                + name().toLowerCase(java.util.Locale.ROOT);
    }
}
