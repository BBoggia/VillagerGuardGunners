package com.gamergaming.villagerguardgunners.quartermaster.service;

public enum ServiceStatus {
    MANUAL, READY, NO_STATION, OPERATOR_UNAVAILABLE, OUTSIDE_WORK_HOURS, RAID, DENIED,
    FIGHTING, PLAYER_ORDER, NATIVE_ACTION, PROFILE_MISSING, NO_MATCHING_STOCK, RETURN_STORAGE_FULL,
    SATISFIED, RESERVED, TRAVELLING, PATH_BLOCKED, SUPPLIED, RECOVERY_REQUIRED, RETRY;
    public String translationKey() { return "message.villagerguardgunners.service." + name().toLowerCase(java.util.Locale.ROOT); }
}
