package com.gamergaming.villagerguardgunners.api;

public interface IGuardReloading {
    boolean isReloading();
    void startReloading(int reloadTime);
    int getReloadingTimeRemaining();
}