package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventRuntime;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventSource;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseSignal;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures every successful server bell ring, including redstone and arrows. */
@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
    @Inject(
            method = "attemptToRing(Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;)Z",
            at = @At("RETURN"))
    private void villagerGuardGunners$afterSuccessfulRing(
            @Nullable Entity source,
            Level level,
            BlockPos position,
            @Nullable Direction direction,
            CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())
                && level instanceof ServerLevel serverLevel) {
            com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.bell(serverLevel, position);
            VillageDefenseEventRuntime.signalAt(
                    serverLevel,
                    position,
                    VillageDefenseSignal.BELL_RUNG,
                    VillageDefenseEventSource.BELL);
        }
    }
}
