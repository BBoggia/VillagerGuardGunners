package com.gamergaming.villagerguardgunners.armory.resupply;

/** Events that are allowed to change the authoritative resupply state. */
public enum ArmoryResupplyEvent {
    REQUEST_CREATED,
    CANDIDATE_RESERVED,
    TRAVEL_STARTED,
    WITHDRAWAL_REACHED,
    TRANSFER_SUCCEEDED,
    NO_SUPPLY,
    PATH_FAILED,
    CANCELLED,
    RETRY_READY
}
