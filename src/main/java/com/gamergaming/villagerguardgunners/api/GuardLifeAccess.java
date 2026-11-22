package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.medical.GuardLifeState;
import tallestegg.guardvillagers.entities.Guard;

public interface GuardLifeAccess {
    com.gamergaming.villagerguardgunners.medical.CotRestState villagerGuardGunners$getCotRest();
    java.util.Optional<net.minecraft.core.BlockPos> villagerGuardGunners$getCotVisual();
    void villagerGuardGunners$setCotVisual(java.util.Optional<net.minecraft.core.BlockPos> head);
    int villagerGuardGunners$getCareVisual();
    void villagerGuardGunners$setCareVisual(int state);
    GuardLifeState villagerGuardGunners$getLifeState();
    int villagerGuardGunners$getLifeVisual();
    void villagerGuardGunners$setLifeVisual(int state);
    static GuardLifeState get(Guard guard) { return ((GuardLifeAccess) guard).villagerGuardGunners$getLifeState(); }
    static com.gamergaming.villagerguardgunners.medical.CotRestState cot(Guard guard) { return ((GuardLifeAccess) guard).villagerGuardGunners$getCotRest(); }
}
