package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnIntegration;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tallestegg.guardvillagers.VillagerToGuard;
import tallestegg.guardvillagers.entities.Guard;

/** Marks Guard Villagers' player conversion path, which bypasses finalizeSpawn. */
@Mixin(VillagerToGuard.class)
public abstract class VillagerToGuardMixin {

    @Redirect(
            method = "convertVillager",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = true),
            remap = false)
    private static boolean markConvertedGuardBeforeJoin(Level level, Entity entity) {
        if (entity instanceof Guard guard) {
            NaturalGuardSpawnIntegration.markInitialSpawn(
                    guard,
                    NaturalGuardSpawnSource.CONVERSION);
        }
        return level.addFreshEntity(entity);
    }
}
