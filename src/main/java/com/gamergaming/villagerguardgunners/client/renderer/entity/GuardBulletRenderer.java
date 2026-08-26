package com.gamergaming.villagerguardgunners.client.renderer.entity;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.entity.GuardBulletEntity;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class GuardBulletRenderer extends EntityRenderer<GuardBulletEntity> {

    public GuardBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GuardBulletEntity entity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        if (ModConfigs.GENERAL.debugMode.get()) {
            drawBulletTrajectory(entity, matrixStack, buffer);
        }
        
        // Optionally, render the bullet model or a simple point
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    private void drawBulletTrajectory(GuardBulletEntity entity, PoseStack matrixStack, MultiBufferSource buffer) {
        Vec3 initialPos = entity.getInitialPosition();
        Vec3 currentPos = entity.position();

        // Check if current minecraft tick is divisible by 20
        if (entity.level().getGameTime() % 5 == 0) {
            if (entity.getShootingOwner() != null) {
                VillagerGuardGunners.LOGGER.info("SHOOTER POS: " + entity.getShootingOwner().getX() + ", " + entity.getShootingOwner().getY() + ", " + entity.getShootingOwner().getZ());
            }
        }
        
        if (initialPos == null) {
            return;
        } else if (initialPos.equals(new Vec3(0,0,0))) {
            if (entity.getOwner() instanceof LivingEntity shooter) {
                double posX = shooter.xOld + (shooter.getX() - shooter.xOld) / 2.0;
                double posY = shooter.yOld + (shooter.getY() - shooter.yOld) / 2.0 + shooter.getEyeHeight();
                double posZ = shooter.zOld + (shooter.getZ() - shooter.zOld) / 2.0;

                initialPos = new Vec3(posX, posY, posZ);
            }
        }

        // Prepare to draw lines
        VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.LINES);

        matrixStack.pushPose();

        // Translate to the render position
        Vec3 cameraPos = this.entityRenderDispatcher.camera.getPosition();

        matrixStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());

        // Set line color (e.g., yellow)
        float red = 1.0F;
        float green = 0.0F;
        float blue = 0.0F;
        float alpha = 1.0F;

        // Draw a line from initialPos to currentPos
        vertexBuilder.vertex(matrixStack.last().pose(), (float) initialPos.x, (float) initialPos.y, (float) initialPos.z)
                .color(red, green, blue, alpha)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();

        vertexBuilder.vertex(matrixStack.last().pose(), (float) currentPos.x, (float) currentPos.y, (float) currentPos.z)
                .color(red, green, blue, alpha)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();

        matrixStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GuardBulletEntity entity) {
        return null;
    }
}
