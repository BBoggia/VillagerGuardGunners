package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tallestegg.guardvillagers.entities.Guard;

/** Constructor-only snapshot consumption; player bullets and later damage properties stay native. */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class TaCZGuardProjectileMixin {
    @Shadow private ResourceLocation gunId;

    @Shadow private <T> T modifyProperty(String name, Class<T> type, T value) {
        throw new AssertionError();
    }

    @Redirect(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;"
            + "Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;"
            + "Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/entity/EntityKineticBullet;modifyProperty(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;"))
    private <T> T villagerGuardGunners$useApprovedProperty(EntityKineticBullet bullet,
            String name, Class<T> type, T value) {
        if (bullet.getOwner() instanceof Guard guard) {
            var controller = GuardNativeShotAccess.controller(guard);
            T approved = controller == null ? null : controller.approvedNativeProperty(gunId, name, type);
            if (approved != null) return approved;
        }
        return modifyProperty(name, type, value);
    }
}
