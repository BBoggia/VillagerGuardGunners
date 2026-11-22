package com.gamergaming.villagerguardgunners.quartermaster.service;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
public final class EquipmentProfileTestAccess {
    public static void apply(Map<ResourceLocation, EquipmentProfile> profiles, String error) {
        new EquipmentProfiles().apply(new EquipmentProfiles.Prepared(profiles, error), null, InactiveProfiler.INSTANCE);
    }
}
