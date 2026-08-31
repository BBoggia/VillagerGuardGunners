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
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
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
        graphics.drawString(
                font,
                playerInventoryTitle,
                inventoryLabelX,
                inventoryLabelY,
                0x404040,
                false);

        DecodeStatus status = menu.getArmory().getLockStatus();
        ArmorySquadLock lock = menu.getArmory().getSquadLock();
        Component lockText;
        int color;
        if (status == DecodeStatus.BLANK) {
            lockText = Component.translatable(
                    "container.villagerguardgunners.armory.public");
            color = 0x557755;
        } else if (status == DecodeStatus.VALID && lock != null) {
            lockText = Component.translatable(
                    "container.villagerguardgunners.armory.squad",
                    lock.displayName());
            color = 0x8A5A18;
        } else {
            lockText = Component.translatable(
                    "container.villagerguardgunners.armory.invalid_lock");
            color = 0xAA3333;
        }
        graphics.drawString(font, lockText, 8, 64, color, false);
    }
}
