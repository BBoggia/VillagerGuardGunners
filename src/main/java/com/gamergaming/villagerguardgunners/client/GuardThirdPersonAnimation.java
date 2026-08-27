package com.gamergaming.villagerguardgunners.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import net.minecraft.client.model.geom.ModelPart;
import tallestegg.guardvillagers.entities.Guard;

/** Reapplies TaCZ's native third-person pose after Guard-specific animation. */
public final class GuardThirdPersonAnimation {
    private GuardThirdPersonAnimation() {
    }

    public static void reapply(
            Guard guard,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart body,
            ModelPart head,
            float limbSwingAmount,
            float ageInTicks) {
        if (ageInTicks == 0.0F || !(guard.getMainHandItem().getItem() instanceof IGun)) {
            return;
        }

        /*
         * HumanoidModel has already run TaCZ's first animation pass. Preserve
         * those part positions while reapplying its rotations after Guard
         * Villagers' extra setup. This matters for TaCZ's minigun animation:
         * it rotates arm positions and would compound that transform if called
         * twice without restoration.
         */
        float rightX = rightArm.x;
        float rightY = rightArm.y;
        float rightZ = rightArm.z;
        float leftX = leftArm.x;
        float leftY = leftArm.y;
        float leftZ = leftArm.z;
        float bodyX = body.x;
        float bodyY = body.y;
        float bodyZ = body.z;
        float headX = head.x;
        float headY = head.y;
        float headZ = head.z;

        InnerThirdPersonManager.setRotationAnglesHead(
                guard,
                rightArm,
                leftArm,
                body,
                head,
                limbSwingAmount);

        rightArm.x = rightX;
        rightArm.y = rightY;
        rightArm.z = rightZ;
        leftArm.x = leftX;
        leftArm.y = leftY;
        leftArm.z = leftZ;
        body.x = bodyX;
        body.y = bodyY;
        body.z = bodyZ;
        head.x = headX;
        head.y = headY;
        head.z = headZ;
    }
}
