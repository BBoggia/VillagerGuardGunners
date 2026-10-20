package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.init.ModBlockEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import tallestegg.guardvillagers.GuardEntityType;

/** Medical supplies stay separate so TaCZ can enumerate only equipment and ammo. */
public final class MedicalCapabilities {
    public static final EntityCapability<MedicalSupplies, Void> SUPPLIES = EntityCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("villagerguardgunners", "medical_supplies"), MedicalSupplies.class);
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.ItemHandler.ENTITY, GuardEntityType.GUARD.get(),
                (guard, context) -> ((GuardAmmoAccess) guard).villagerGuardGunners$getCombinedItemHandler());
        event.registerEntity(SUPPLIES, GuardEntityType.GUARD.get(), (guard, context) ->
                guard.isAlive() && !guard.isRemoved() ? ((GuardMedicalAccess) guard).villagerGuardGunners$getMedicalStorage().transport() : null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ARMORY.get(),
                (block, side) -> block.automationHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.QUARTERMASTER.get(),
                (block, side) -> block.automationHandler());
    }
}
