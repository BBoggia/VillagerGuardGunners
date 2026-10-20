package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.armory.ArmorySquadLockCodec.DecodeStatus;
import com.gamergaming.villagerguardgunners.menu.ArmoryMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Compact vanilla-style 3x3 armory inventory. */
public final class ArmoryScreen extends AbstractContainerScreen<ArmoryMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/gui/container/dispenser.png");

    public ArmoryScreen(
            ArmoryMenu menu,
            Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 73, imageWidth - 16, 9, mouseX, mouseY)) {
            graphics.renderTooltip(font, accessLabel(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(
                TEXTURE,
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        DecodeStatus status = menu.getArmory().getLockStatus();
        int color = status == DecodeStatus.BLANK ? 0x557755
                : status == DecodeStatus.VALID ? 0x8A5A18 : 0xAA3333;
        String label = accessLabel().getString();
        int availableWidth = imageWidth - 16;
        if (font.width(label) > availableWidth) {
            label = font.plainSubstrByWidth(label, availableWidth - font.width("…")) + "…";
        }
        // The dispenser's last slot row ends at y=69. Use the inventory heading
        // area for access information, leaving all item slots unobstructed.
        graphics.drawString(font, label, 8, 73, color, false);
    }

    private Component accessLabel() {
        DecodeStatus status = menu.getArmory().getLockStatus();
        ArmorySquadLock lock = menu.getArmory().getSquadLock();
        if (status == DecodeStatus.BLANK) {
            return Component.translatable(
                    "container.villagerguardgunners.armory.public");
        } else if (status == DecodeStatus.VALID && lock != null) {
            return Component.translatable(
                    "container.villagerguardgunners.armory.squad",
                    lock.displayName());
        } else {
            return Component.translatable(
                    "container.villagerguardgunners.armory.invalid_lock");
        }
    }
}
