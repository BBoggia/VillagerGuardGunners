package com.gamergaming.villagerguardgunners.mixin.accessor;

import java.util.Map;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only startup access for reconciling persisted Siege state. */
@Mixin(Raids.class)
public interface RaidsAccessor {
    @Accessor("raidMap")
    Map<Integer, Raid> villagerGuardGunners$getRaidMap();
}
