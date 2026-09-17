package com.gamergaming.villagerguardgunners.quartermaster.service;

public enum ServiceStatus {
    MANUAL, READY, NO_STATION, OPERATOR_UNAVAILABLE, OUTSIDE_WORK_HOURS, RAID, DENIED,
    FIGHTING, PLAYER_ORDER, NATIVE_ACTION, PROFILE_MISSING, NO_MATCHING_STOCK, RETURN_STORAGE_FULL,
    SATISFIED, RESERVED, TRAVELLING, PATH_BLOCKED, SUPPLIED, RECOVERY_REQUIRED, RETRY,
    ASSIGNED_STATION_UNAVAILABLE, ASSIGNED_STATION_OUT_OF_RANGE, ASSIGNED_STATION_AMBIGUOUS;
    public String translationKey() { return "message.villagerguardgunners.service." + name().toLowerCase(java.util.Locale.ROOT); }
}
