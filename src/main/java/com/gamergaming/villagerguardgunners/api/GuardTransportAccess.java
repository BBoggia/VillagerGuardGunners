package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.medical.TransportState;
import tallestegg.guardvillagers.entities.Guard;

public interface GuardTransportAccess {
    TransportState villagerGuardGunners$getTransportState();
    static TransportState get(Guard guard) { return ((GuardTransportAccess) guard).villagerGuardGunners$getTransportState(); }
}
