package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Explicit immutable settings request. Null assignment means Auto, not 'keep current'. */
public record ServiceSettings(GuardServiceState.Policy policy, ResourceLocation profile,
        boolean replaceExisting, ServiceStationAssignment assignment) {
    public ServiceSettings {
        Objects.requireNonNull(policy);
        if (profile != null && profile.toString().length() > 96) throw new IllegalArgumentException("Profile ID too long");
    }
}
