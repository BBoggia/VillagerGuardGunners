package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseEventRuntime;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emits only the first ongoing and terminal observation for each Raid object. */
@Mixin(Raid.class)
public abstract class RaidMixin {
    @Unique
    private byte villagerGuardGunners$observedState = -1;

    @Inject(method = "tick", at = @At("RETURN"))
    private void villagerGuardGunners$afterRaidTick(CallbackInfo callback) {
        villagerGuardGunners$observeTransition();
    }

    @Inject(method = "stop", at = @At("RETURN"))
    private void villagerGuardGunners$afterRaidStopped(CallbackInfo callback) {
        villagerGuardGunners$observeTransition();
    }

    @Unique
    private void villagerGuardGunners$observeTransition() {
        Raid raid = (Raid) (Object) this;
        byte current = (byte) ((raid.isStopped() || raid.isOver()) ? 1 : 0);
        if (current == villagerGuardGunners$observedState) {
            return;
        }
        villagerGuardGunners$observedState = current;
        VillageDefenseEventRuntime.observeRaid(raid);
    }
}
