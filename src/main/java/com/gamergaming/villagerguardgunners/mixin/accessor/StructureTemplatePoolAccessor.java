package com.gamergaming.villagerguardgunners.mixin.accessor;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Narrow startup-only access for copy-on-write village-pool augmentation.
 * No gameplay or tick path reads these internals through the accessor.
 */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> villagerGuardGunners$getRawTemplates();

    @Mutable
    @Accessor("rawTemplates")
    void villagerGuardGunners$setRawTemplates(
            List<Pair<StructurePoolElement, Integer>> templates);

    @Accessor("templates")
    ObjectArrayList<StructurePoolElement> villagerGuardGunners$getTemplates();

    @Mutable
    @Accessor("templates")
    void villagerGuardGunners$setTemplates(ObjectArrayList<StructurePoolElement> templates);

    @Accessor("maxSize")
    int villagerGuardGunners$getMaxSize();

    @Accessor("maxSize")
    void villagerGuardGunners$setMaxSize(int maxSize);
}
