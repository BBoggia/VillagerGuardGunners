package com.gamergaming.villagerguardgunners.worldgen.structure;

import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Dormant Step 10 structure codec and registry boundary.
 *
 * <p>The built-in biome tag is intentionally empty and this generation point
 * remains empty until Step 11 supplies the reviewed template and terrain
 * contract. Keeping both gates closed prevents an incomplete structure from
 * appearing even if a modpack accidentally adds this structure to another
 * structure set during the skeleton phase.</p>
 */
public final class VillageWatchtowerStructure extends Structure {
    public static final Codec<VillageWatchtowerStructure> CODEC =
            simpleCodec(VillageWatchtowerStructure::new);

    public VillageWatchtowerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.WATCHTOWER.get();
    }
}
