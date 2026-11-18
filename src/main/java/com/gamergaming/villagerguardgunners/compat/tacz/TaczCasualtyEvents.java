package com.gamergaming.villagerguardgunners.compat.tacz;

import com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/** Stops TaCZ reloads and melee for a guard that is down or being carried. */
final class TaczCasualtyEvents {
    private TaczCasualtyEvents() {
    }

    static void reload(GunReloadEvent event) {
        if (event.getEntity() instanceof Guard guard && GuardLifeRuntime.weaponActionsBlocked(guard)) event.setCanceled(true);
    }

    static void melee(GunMeleeEvent event) {
        if (event.getShooter() instanceof Guard guard && GuardLifeRuntime.weaponActionsBlocked(guard)) event.setCanceled(true);
    }
}
