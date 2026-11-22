package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardAmmoLootEntry;
import com.gamergaming.villagerguardgunners.worldgen.structure.VillageWatchtowerPiece;
import com.gamergaming.villagerguardgunners.worldgen.structure.VillageWatchtowerStructure;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registers the bounded world-generation types owned by the add-on. */
public final class ModStructures {
    private static final DeferredRegister<LootPoolEntryType> LOOT_ENTRIES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, VillagerGuardGunners.MODID);
    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> GUARD_AMMO =
            LOOT_ENTRIES.register("guard_ammo", () -> new LootPoolEntryType(GuardAmmoLootEntry.CODEC));

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_TYPE,
                    VillagerGuardGunners.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_PIECE,
                    VillagerGuardGunners.MODID);
    public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENT_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_POOL_ELEMENT,
                    VillagerGuardGunners.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<VillageWatchtowerStructure>>
            WATCHTOWER = STRUCTURE_TYPES.register(
                    "watchtower",
                    () -> () -> VillageWatchtowerStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> WATCHTOWER_PIECE =
            STRUCTURE_PIECE_TYPES.register(
                    "watchtower",
                    () -> (StructurePieceType.StructureTemplateType)
                            VillageWatchtowerPiece::new);
    public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<VillageBuildingPoolElement>>
            VILLAGE_BUILDING_POOL_ELEMENT = POOL_ELEMENT_TYPES.register(
                    "village_building",
                    () -> () -> VillageBuildingPoolElement.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_ENTRIES.register(eventBus);
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECE_TYPES.register(eventBus);
        POOL_ELEMENT_TYPES.register(eventBus);
    }

    private ModStructures() {
    }
}
