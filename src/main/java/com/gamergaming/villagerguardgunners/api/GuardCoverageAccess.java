package com.gamergaming.villagerguardgunners.api;

/** Read-only access to the native follow order and the existing gun goal's queued actions. */
public interface GuardCoverageAccess {
    boolean villagerGuardGunners$hasFollowOrder();
    boolean villagerGuardGunners$nativeCoverageMovementSafe();
    boolean villagerGuardGunners$nativeEquipmentServiceSafe();
}
