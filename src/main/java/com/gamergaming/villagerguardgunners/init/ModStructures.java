package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.worldgen.structure.VillageWatchtowerStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registers the bounded world-generation types owned by the add-on. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_TYPE,
                    VillagerGuardGunners.MODID);

    public static final RegistryObject<StructureType<VillageWatchtowerStructure>>
            WATCHTOWER = STRUCTURE_TYPES.register(
                    "watchtower",
                    () -> () -> VillageWatchtowerStructure.CODEC);

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
    }

    private ModStructures() {
    }
}
