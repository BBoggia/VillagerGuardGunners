package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.quartermaster.service.GuardServiceState;
import tallestegg.guardvillagers.entities.Guard;

public interface GuardEquipmentServiceAccess {
    GuardServiceState villagerGuardGunners$getEquipmentService();
    static GuardServiceState get(Guard guard) { return ((GuardEquipmentServiceAccess) guard).villagerGuardGunners$getEquipmentService(); }
}
