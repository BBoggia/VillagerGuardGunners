package com.gamergaming.villagerguardgunners.medical;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/** Separate from ITEM_HANDLER so TaCZ never enumerates or consumes pouch items. */
public final class MedicalCapabilities {
    public static final Capability<MedicalSupplies> SUPPLIES = CapabilityManager.get(new CapabilityToken<>() {});
    public static void register(RegisterCapabilitiesEvent event) { event.register(MedicalSupplies.class); }
}
