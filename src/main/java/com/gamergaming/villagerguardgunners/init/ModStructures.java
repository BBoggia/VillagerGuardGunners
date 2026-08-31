package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.worldgen.structure.VillageWatchtowerPiece;
import com.gamergaming.villagerguardgunners.worldgen.structure.VillageWatchtowerStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
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
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_PIECE,
                    VillagerGuardGunners.MODID);

    public static final RegistryObject<StructureType<VillageWatchtowerStructure>>
            WATCHTOWER = STRUCTURE_TYPES.register(
                    "watchtower",
                    () -> () -> VillageWatchtowerStructure.CODEC);
    public static final RegistryObject<StructurePieceType> WATCHTOWER_PIECE =
            STRUCTURE_PIECE_TYPES.register(
                    "watchtower",
                    () -> (StructurePieceType.StructureTemplateType)
                            VillageWatchtowerPiece::new);

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECE_TYPES.register(eventBus);
    }

    private ModStructures() {
    }
}
