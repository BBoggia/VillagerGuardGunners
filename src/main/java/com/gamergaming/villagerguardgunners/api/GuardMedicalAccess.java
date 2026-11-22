package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.medical.MedicalStorage;
import tallestegg.guardvillagers.entities.Guard;

/** Dedicated medical storage is independent of a guard's current role and weapons. */
public interface GuardMedicalAccess {
    MedicalStorage villagerGuardGunners$getMedicalStorage();
    static MedicalStorage get(Guard guard) { return ((GuardMedicalAccess) guard).villagerGuardGunners$getMedicalStorage(); }
}
