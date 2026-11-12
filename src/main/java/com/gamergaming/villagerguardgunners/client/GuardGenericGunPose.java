package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Shoulders the gun for guards holding one from a mod that only poses player
 * models. Both arms come up and follow the guard's head, the way an
 * illager holds a loaded crossbow.
 */
public final class GuardGenericGunPose {
    private GuardGenericGunPose() {
    }

    public static void apply(Guard guard, ModelPart rightArm, ModelPart leftArm, ModelPart head) {
        if (!GunProviders.find(guard.getMainHandItem()).map(GunProvider::usesGenericHeldPose).orElse(false)) {
            return;
        }
        AnimationUtils.animateCrossbowHold(rightArm, leftArm, head, guard.getMainArm() == HumanoidArm.RIGHT);
    }
}
