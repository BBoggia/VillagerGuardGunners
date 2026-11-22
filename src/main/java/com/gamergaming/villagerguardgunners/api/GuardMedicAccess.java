package com.gamergaming.villagerguardgunners.api;
import com.gamergaming.villagerguardgunners.medical.MedicState;
import tallestegg.guardvillagers.entities.Guard;
public interface GuardMedicAccess {
    MedicState villagerGuardGunners$getMedicState();
    static MedicState get(Guard guard) { return ((GuardMedicAccess)guard).villagerGuardGunners$getMedicState(); }
}
