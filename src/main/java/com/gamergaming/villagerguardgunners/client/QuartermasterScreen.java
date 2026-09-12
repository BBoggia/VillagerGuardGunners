package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.menu.QuartermasterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact stock/returns workbench using native slots and server-owned settings. */
public final class QuartermasterScreen extends AbstractContainerScreen<QuartermasterMenu> {
    private Button access, automation, squad, service, link, unlink;
    public QuartermasterScreen(QuartermasterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 228;
        inventoryLabelY = 135;
    }
    private static Component text(String key) {
        return Component.translatable("gui.villagerguardgunners.quartermaster." + key);
    }
    @Override protected void init() {
        super.init();
        leftPos = (width - 304) / 2;
        access = button(0, 7, 54);
        automation = button(1, 62, 54);
        squad = button(2, 117, 52);
        access.setTooltip(Tooltip.create(text("access_help")));
        automation.setTooltip(Tooltip.create(text("automation_help")));
        squad.setTooltip(Tooltip.create(text("squad_help")));
        service = serviceButton(3, 18);
        link = serviceButton(4, 44); link.setMessage(text("link_armories"));
        unlink = serviceButton(5, 68); unlink.setMessage(text("clear_armories"));
        service.setTooltip(Tooltip.create(text("service_access_help")));
        link.setTooltip(Tooltip.create(text("link_armories_help")));
        updateControls();
    }
    private Button button(int id, int x, int width) {
        return addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(leftPos + x, topPos + 18, width, 14).build());
    }
    private Button serviceButton(int id, int y) {
        return addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(leftPos + 184, topPos + y, 116, 18).build());
    }
    private void updateControls() {
        access.setMessage(text(switch (menu.accessMode()) { case 1 -> "squad"; case 2 -> "public"; default -> "private"; }));
        automation.setMessage(text(menu.automationEnabled() ? "auto_on" : "auto_off"));
        squad.setMessage(text(menu.hasSquad() ? "unbind" : "bind"));
        access.active = automation.active = squad.active = service.active = link.active = unlink.active = menu.canManage();
        service.setMessage(text(switch (menu.guardAccess()) { case 1 -> "owned_guards"; case 2 -> "squad_guards"; case 3 -> "all_guards"; default -> "service_off"; }));
    }
    @Override protected void containerTick() { super.containerTick(); updateControls(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff373737);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xffffffff);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xffc6c6c6);
        g.fill(leftPos + 180, topPos, leftPos + 304, topPos + imageHeight, 0xe0101010);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xffffffff);
            g.fill(x - 1, y - 1, x + 16, y + 16, 0xff373737);
            g.fill(x, y, x + 16, y + 16, 0xff8b8b8b);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, text("stock"), 8, 33, 0x404040, false);
        g.drawString(font, text("returns"), 8, 100, 0x404040, false);
        g.drawString(font, text("service"), 186, 6, 0xffffff, false);
        g.drawString(font, text("linked_count").copy().append(" " + menu.linkedArmories() + "/4"), 186, 94, 0xffffff, false);
        var statuses = com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus.values();
        var status = Component.translatable(statuses[Math.floorMod(menu.serviceStatus(), statuses.length)].translationKey());
        int y = 112; for (var line : font.split(status, 108)) { g.drawString(font, line, 186, y, 0xffffff, false); y += 10; }
    }
}
