package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSelection;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureTemplatePool.class)
public abstract class VillageBuildingSelectionMixin {
    @Inject(method = "getShuffledTemplates", at = @At("RETURN"), cancellable = true)
    private void selectBuildings(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> callback) {
        callback.setReturnValue(VillageBuildingSelection.filter(callback.getReturnValue(),
                element -> element instanceof VillageBuildingPoolElement building ? building.selectionChance() : 1.0,
                random::nextDouble));
    }
}
