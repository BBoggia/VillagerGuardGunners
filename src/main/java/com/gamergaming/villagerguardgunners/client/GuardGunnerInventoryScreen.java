package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.orders.GuardOrderText;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.OrderField;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.GuardPacketHandler;
import tallestegg.guardvillagers.GuardVillagers;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;
import tallestegg.guardvillagers.networking.GuardFollowPacket;
import tallestegg.guardvillagers.networking.GuardSetPatrolPosPacket;

public class GuardGunnerInventoryScreen extends AbstractContainerScreen<GuardGunnerContainer> {

    private static final ResourceLocation GUARD_GUI_TEXTURES = ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, "textures/gui/gunner_inventory.png");

    private static final ResourceLocation GUARD_FOLLOWING_ICON = ResourceLocation.fromNamespaceAndPath(GuardVillagers.MODID, "textures/container/following_icons.png");
    private static final ResourceLocation GUARD_NOT_FOLLOWING_ICON = ResourceLocation.fromNamespaceAndPath(GuardVillagers.MODID, "textures/container/not_following_icons.png");
    private static final ResourceLocation PATROL_ICON = ResourceLocation.fromNamespaceAndPath(GuardVillagers.MODID, "textures/container/patrollingui.png");
    private static final ResourceLocation NOT_PATROLLING_ICON = ResourceLocation.fromNamespaceAndPath(GuardVillagers.MODID, "textures/container/notpatrollingui.png");
    private static final ResourceLocation GUI_ICONS_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/icons.png");
    private static final int STATUS_ICON_X = 80;
    private static final int STATUS_ICON_SPACING = 6;
    private static final int EMPTY_HEART_TEXTURE_X = 16;
    private static final int FULL_HEART_TEXTURE_X = 34;
    private static final int HALF_HEART_TEXTURE_X = 43;
    private static final int AMMO_HEADER_X = 96;
    private static final int AMMO_HEADER_Y = 33;
    private static final int AMMO_HEADER_HEIGHT = 11;
    private static final int SIDEARM_SLOT_X = 153;
    private static final int SIDEARM_SLOT_Y = 44;
    private static final int SLOT_BACKGROUND_SOURCE_X = 98;
    private static final int SLOT_BACKGROUND_SOURCE_Y = 43;
    private static final int COMBAT_STATUS_X = 98;
    private static final int COMBAT_STATUS_Y = 69;
    private static final int COMBAT_STATUS_WIDTH = 72;
    private static final int COMBAT_STATUS_HEIGHT = 11;
    private static final int ORDER_PANEL_GAP = 4;
    private static final int ORDER_PANEL_WIDTH = 124;
    private static final int ORDER_BUTTON_X_PADDING = 4;
    private static final int ORDER_BUTTON_Y = 18;
    private static final int ORDER_BUTTON_WIDTH = ORDER_PANEL_WIDTH - ORDER_BUTTON_X_PADDING * 2;
    private static final int ORDER_BUTTON_HEIGHT = 16;
    private static final int ORDER_BUTTON_SPACING = 19;
    private static final int RECOVERY_BUTTON_Y = 150;
    private static final int AMMO_STATUS_REFRESH_TICKS = 20;
    private static final int MAX_CACHED_INVENTORY_SLOTS = 1_024;

    private final Guard guard;
    private final Player player;
    private final IItemHandler taczInventory;
    private ItemStack cachedGun = ItemStack.EMPTY;
    private ItemStack[] cachedInventory = new ItemStack[0];
    private int cachedInventorySlotCount = -1;
    private boolean cachedInventoryReadable;
    private GuardAmmoSummary.Snapshot cachedAmmoSummary;
    private GuardAmmoSummary.Snapshot cachedTooltipSummary;
    private List<Component> cachedAmmoTooltip = List.of();
    private long nextAmmoStatusRefreshTick = Long.MIN_VALUE;
    private long lastAmmoFingerprintTick = Long.MIN_VALUE;
    private float mousePosX;
    private float mousePosY;
    private boolean buttonPressed;
    private final Button[] orderButtons = new Button[OrderField.COUNT];
    private Button recoveryButton;
    private GuardOrders displayedOrders;

    public GuardGunnerInventoryScreen(GuardGunnerContainer container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        this.guard = container.getGuard(); // Access guard via the container
        this.titleLabelX = 80;
        this.inventoryLabelX = 100;
        this.player = playerInventory.player;
        this.taczInventory = GuardAmmoAccess.getTaCZInventoryHandler(guard);
    }

    @Override
    public void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth - ORDER_PANEL_GAP - ORDER_PANEL_WIDTH) / 2;
        int buttonWidth = 20;
        int buttonHeight = 18;
        int buttonY = this.topPos + 1;

        if ((GuardConfig.followHero && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) || !GuardConfig.followHero) {
            this.addRenderableWidget(new GuardGuiButton(this.leftPos + 136, buttonY, buttonWidth, buttonHeight, 0, 0, 19, GUARD_FOLLOWING_ICON, GUARD_NOT_FOLLOWING_ICON, true, (button) -> {
                GuardPacketHandler.INSTANCE.sendToServer(new GuardFollowPacket(guard.getId()));
            }));
        }
        if ((GuardConfig.setGuardPatrolHotv && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) || !GuardConfig.setGuardPatrolHotv) {
            this.addRenderableWidget(new GuardGuiButton(this.leftPos + 156, buttonY, buttonWidth, buttonHeight, 0, 0, 19, PATROL_ICON, NOT_PATROLLING_ICON, false, (button) -> {
                buttonPressed = !buttonPressed;
                GuardPacketHandler.INSTANCE.sendToServer(new GuardSetPatrolPosPacket(guard.getId(), buttonPressed));
            }));
        }

        int panelX = orderPanelX();
        for (OrderField field : OrderField.values()) {
            Button orderButton = Button.builder(
                            Component.empty(),
                            button -> sendOrderButton(field))
                    .bounds(
                            panelX + ORDER_BUTTON_X_PADDING,
                            this.topPos + ORDER_BUTTON_Y
                                    + field.buttonId() * ORDER_BUTTON_SPACING,
                            ORDER_BUTTON_WIDTH,
                            ORDER_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(
                            "container.villagerguardgunners.orders."
                                    + GuardOrderText.fieldKey(field)
                                    + ".description")))
                    .build();
            orderButtons[field.buttonId()] = orderButton;
            addRenderableWidget(orderButton);
        }
        recoveryButton = Button.builder(
                        Component.translatable(
                                "container.villagerguardgunners.sidearm.recover"),
                        button -> sendRecoveryButton())
                .bounds(
                        panelX + ORDER_BUTTON_X_PADDING,
                        this.topPos + RECOVERY_BUTTON_Y,
                        ORDER_BUTTON_WIDTH,
                        14)
                .tooltip(Tooltip.create(Component.translatable(
                        "container.villagerguardgunners.sidearm.recover.tooltip")))
                .build();
        addRenderableWidget(recoveryButton);
        displayedOrders = null;
        refreshOrderButtons();
        refreshRecoveryButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        GuardOrders orders = menu.getOrders();
        if (!orders.equals(displayedOrders)) {
            refreshOrderButtons();
        }
        refreshRecoveryButton();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int x, int y) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUARD_GUI_TEXTURES);
        int i = this.leftPos;
        int j = this.topPos;
        graphics.blit(GUARD_GUI_TEXTURES, i, j, 0, 0, this.imageWidth, this.imageHeight);
        graphics.blit(
                GUARD_GUI_TEXTURES,
                i + SIDEARM_SLOT_X - 1,
                j + SIDEARM_SLOT_Y - 1,
                SLOT_BACKGROUND_SOURCE_X,
                SLOT_BACKGROUND_SOURCE_Y,
                18,
                18);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,i + 51, j + 75, 30, (float) (i + 51) - this.mousePosX, (float) (j + 75 - 50) - this.mousePosY, this.guard);

        int panelX = orderPanelX();
        int panelBottom = this.topPos + this.imageHeight;
        graphics.fill(panelX, this.topPos, panelX + ORDER_PANEL_WIDTH, panelBottom, 0xE0101010);
        graphics.fill(panelX, this.topPos, panelX + ORDER_PANEL_WIDTH, this.topPos + 1, 0xFF8A8A8A);
        graphics.fill(panelX, panelBottom - 1, panelX + ORDER_PANEL_WIDTH, panelBottom, 0xFF303030);
        graphics.fill(panelX, this.topPos, panelX + 1, panelBottom, 0xFF8A8A8A);
        graphics.fill(panelX + ORDER_PANEL_WIDTH - 1, this.topPos, panelX + ORDER_PANEL_WIDTH, panelBottom, 0xFF303030);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {
        super.renderLabels(graphics, x, y);
        int health = Mth.ceil(guard.getHealth());
        int armor = guard.getArmorValue();
        boolean renderStatusIcons = GuardConfig.CLIENT.guardInventoryNumbers.get();
        Component guardHealthText = Component.translatable("guardinventory.health", health);
        Component guardArmorText = Component.translatable("guardinventory.armor", armor);

        if (!renderStatusIcons || guard.getMaxHealth() > 20.0F) {
            graphics.drawString(font, guardHealthText, STATUS_ICON_X, 30, 4210752, false);
        } else {
            int healthY = armor > 0 ? 30 : 20;
            for (int index = 0; index < 10; index++) {
                renderHeart(
                        graphics,
                        STATUS_ICON_X + index * STATUS_ICON_SPACING,
                        healthY,
                        true,
                        false);
            }
            int filledHearts = (health + 1) / 2;
            for (int index = 0; index < filledHearts; index++) {
                boolean halfHeart = (health & 1) != 0 && index == filledHearts - 1;
                renderHeart(
                        graphics,
                        STATUS_ICON_X + index * STATUS_ICON_SPACING,
                        healthY,
                        false,
                        halfHeart);
            }
        }

        if (!renderStatusIcons) {
            graphics.drawString(font, guardArmorText, STATUS_ICON_X, 20, 4210752, false);
        } else if (armor > 0) {
            for (int index = 0; index < 10; index++) {
                int armorPoint = index * 2 + 1;
                int textureX = armorPoint < armor ? 34 : armorPoint == armor ? 25 : 16;
                graphics.blit(
                        GUI_ICONS_LOCATION,
                        STATUS_ICON_X + index * STATUS_ICON_SPACING,
                        20,
                        textureX,
                        9,
                        9,
                        9);
            }
        }
        GuardAmmoSummary.Snapshot ammoSummary = currentAmmoSummary();
        Component ammoLabel = Component.translatable("container.villagerguardgunners.ammo");
        graphics.drawString(
                font,
                ammoLabel,
                this.imageWidth - 6 - font.width(ammoLabel),
                35,
                ammunitionStatusColor(ammoSummary),
                false);

        GuardCombatStatus combatStatus = menu.getCombatStatus();
        int combatStatusColor = combatStatusColor(combatStatus);
        graphics.fill(
                COMBAT_STATUS_X,
                COMBAT_STATUS_Y + 1,
                COMBAT_STATUS_X + 2,
                COMBAT_STATUS_Y + 9,
                0xFF000000 | combatStatusColor);
        graphics.drawString(
                font,
                Component.translatable(combatStatus.translationKey()),
                COMBAT_STATUS_X + 5,
                COMBAT_STATUS_Y,
                combatStatusColor,
                false);

        int panelLabelX = this.imageWidth + ORDER_PANEL_GAP + 6;
        graphics.drawString(
                font,
                Component.translatable("container.villagerguardgunners.orders"),
                panelLabelX,
                6,
                0xFFFFFF,
                false);
        if (menu.getSidearmState() != SidearmState.RECOVERY_REQUIRED) {
            graphics.drawString(
                    font,
                    Component.translatable("container.villagerguardgunners.orders.hint"),
                    panelLabelX,
                    154,
                    ChatFormatting.GRAY.getColor(),
                    false);
        }
    }

    private int orderPanelX() {
        return this.leftPos + this.imageWidth + ORDER_PANEL_GAP;
    }

    private void sendOrderButton(OrderField field) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, field.buttonId());
        }
    }

    private void sendRecoveryButton() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId,
                    GuardGunnerContainer.RECOVER_SIDEARM_BUTTON_ID);
        }
    }

    private void refreshRecoveryButton() {
        if (recoveryButton != null) {
            boolean recoveryRequired =
                    menu.getSidearmState() == SidearmState.RECOVERY_REQUIRED;
            recoveryButton.visible = recoveryRequired;
            recoveryButton.active = recoveryRequired;
        }
    }

    private void refreshOrderButtons() {
        GuardOrders orders = menu.getOrders();
        for (OrderField field : OrderField.values()) {
            Button button = orderButtons[field.buttonId()];
            if (button != null) {
                button.setMessage(Component.translatable(
                        "container.villagerguardgunners.orders."
                                + GuardOrderText.fieldKey(field),
                        GuardOrderText.value(field, orders)));
            }
        }
        displayedOrders = orders;
    }

    private static void renderHeart(
            GuiGraphics graphics,
            int x,
            int y,
            boolean container,
            boolean halfHeart) {
        int textureX = container
                ? EMPTY_HEART_TEXTURE_X
                : halfHeart ? HALF_HEART_TEXTURE_X : FULL_HEART_TEXTURE_X;
        graphics.blit(
                GUI_ICONS_LOCATION,
                x,
                y,
                textureX,
                0,
                9,
                9);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        this.mousePosX = (float) mouseX;
        this.mousePosY = (float) mouseY;
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderEmptySidearmTooltip(graphics, mouseX, mouseY);
        renderAmmunitionTooltip(graphics, mouseX, mouseY);
        renderCombatStatusTooltip(graphics, mouseX, mouseY);
    }

    private void renderEmptySidearmTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (menu.getSlot(GuardGunnerContainer.SIDEARM_SLOT_INDEX).hasItem()
                || !isHovering(
                        SIDEARM_SLOT_X,
                        SIDEARM_SLOT_Y,
                        16,
                        16,
                        mouseX,
                        mouseY)) {
            return;
        }
        graphics.renderComponentTooltip(
                font,
                List.of(
                        Component.translatable(
                                "container.villagerguardgunners.sidearm")
                                .withStyle(ChatFormatting.AQUA),
                        Component.translatable(
                                "container.villagerguardgunners.sidearm.tooltip")
                                .withStyle(ChatFormatting.GRAY)),
                mouseX,
                mouseY);
    }

    private void renderCombatStatusTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (!isHovering(
                COMBAT_STATUS_X,
                COMBAT_STATUS_Y,
                COMBAT_STATUS_WIDTH,
                COMBAT_STATUS_HEIGHT,
                mouseX,
                mouseY)) {
            return;
        }

        GuardCombatStatus status = menu.getCombatStatus();
        graphics.renderComponentTooltip(
                font,
                List.of(
                        Component.translatable(
                                "container.villagerguardgunners.combat_status.tooltip",
                                Component.translatable(status.translationKey()))
                                .withStyle(status == GuardCombatStatus.READY
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.AQUA),
                        Component.translatable(status.descriptionKey())
                                .withStyle(ChatFormatting.GRAY)),
                mouseX,
                mouseY);
    }

    private GuardAmmoSummary.Snapshot currentAmmoSummary() {
        long gameTime = guard.level().getGameTime();
        boolean refreshDue = cachedAmmoSummary == null || gameTime >= nextAmmoStatusRefreshTick;
        boolean inventoryChanged = false;
        if (!refreshDue && gameTime != lastAmmoFingerprintTick) {
            inventoryChanged = inventoryFingerprintChanged();
            lastAmmoFingerprintTick = gameTime;
        }
        if (refreshDue || inventoryChanged) {
            cachedAmmoSummary = GuardAmmoSummary.capture(guard.getMainHandItem(), taczInventory);
            captureInventoryFingerprint();
            nextAmmoStatusRefreshTick = gameTime + AMMO_STATUS_REFRESH_TICKS;
            lastAmmoFingerprintTick = gameTime;
        }
        return cachedAmmoSummary;
    }

    private boolean inventoryFingerprintChanged() {
        if (!stacksMatch(cachedGun, guard.getMainHandItem())) {
            return true;
        }
        if (!cachedInventoryReadable) {
            // A temporarily broken handler is retried on the bounded periodic
            // refresh instead of triggering one full scan per rendered frame.
            return false;
        }
        try {
            int slots = taczInventory.getSlots();
            if (slots != cachedInventorySlotCount
                    || cachedInventory.length != Math.min(slots, MAX_CACHED_INVENTORY_SLOTS)) {
                return true;
            }
            for (int slot = 0; slot < cachedInventory.length; slot++) {
                if (!stacksMatch(cachedInventory[slot], taczInventory.getStackInSlot(slot))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void captureInventoryFingerprint() {
        cachedGun = guard.getMainHandItem().copy();
        cachedInventoryReadable = false;
        try {
            int slots = taczInventory.getSlots();
            if (slots < 0) {
                cachedInventory = new ItemStack[0];
                cachedInventorySlotCount = slots;
                return;
            }
            int cachedSlots = Math.min(slots, MAX_CACHED_INVENTORY_SLOTS);
            ItemStack[] snapshot = new ItemStack[cachedSlots];
            for (int slot = 0; slot < cachedSlots; slot++) {
                ItemStack stack = taczInventory.getStackInSlot(slot);
                if (stack == null) {
                    cachedInventory = new ItemStack[0];
                    cachedInventorySlotCount = slots;
                    return;
                }
                snapshot[slot] = stack.copy();
            }
            cachedInventory = snapshot;
            cachedInventorySlotCount = slots;
            cachedInventoryReadable = true;
        } catch (RuntimeException exception) {
            cachedInventory = new ItemStack[0];
            cachedInventorySlotCount = -1;
        }
    }

    private void renderAmmunitionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int headerWidth = imageWidth - 6 - AMMO_HEADER_X;
        if (!isHovering(
                AMMO_HEADER_X,
                AMMO_HEADER_Y,
                headerWidth,
                AMMO_HEADER_HEIGHT,
                mouseX,
                mouseY)) {
            return;
        }
        graphics.renderComponentTooltip(
                font,
                currentAmmunitionTooltip(),
                mouseX,
                mouseY);
    }

    private List<Component> currentAmmunitionTooltip() {
        GuardAmmoSummary.Snapshot summary = currentAmmoSummary();
        if (summary != cachedTooltipSummary) {
            cachedTooltipSummary = summary;
            cachedAmmoTooltip = ammunitionTooltip(summary);
        }
        return cachedAmmoTooltip;
    }

    private static List<Component> ammunitionTooltip(GuardAmmoSummary.Snapshot summary) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("container.villagerguardgunners.ammo.tooltip.title")
                .withStyle(ChatFormatting.AQUA));
        if (!summary.isReady()) {
            lines.add(unavailableAmmunitionText(summary.status())
                    .copy()
                    .withStyle(ChatFormatting.YELLOW));
            return lines;
        }

        lines.add(Component.translatable(
                "container.villagerguardgunners.ammo.tooltip.feed",
                feedTypeText(summary))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(
                "container.villagerguardgunners.ammo.tooltip.magazine",
                summary.loadedAmmo(),
                summary.magazineCapacity())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(
                "container.villagerguardgunners.ammo.tooltip.chamber",
                Component.translatable(!summary.supportsChamber()
                        ? "container.villagerguardgunners.ammo.tooltip.not_used"
                        : summary.chambered()
                                ? "container.villagerguardgunners.ammo.tooltip.loaded"
                                : "container.villagerguardgunners.ammo.tooltip.empty"))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(
                "container.villagerguardgunners.ammo.tooltip.reserve",
                summary.infiniteReserve()
                        ? Component.translatable(
                                "container.villagerguardgunners.ammo.tooltip.infinite")
                        : Component.literal(Long.toString(summary.reserveUnits())))
                .withStyle(ChatFormatting.GRAY));

        if (summary.incompatibleAmmoStacks() > 0) {
            lines.add(Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.incompatible",
                    summary.incompatibleAmmoStacks())
                    .withStyle(ChatFormatting.RED));
        }
        if (summary.unknownAmmoStacks() > 0) {
            lines.add(Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.unknown",
                    summary.unknownAmmoStacks())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!summary.reserveScanComplete()) {
            lines.add(Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.incomplete")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static Component unavailableAmmunitionText(GuardAmmoSummary.Status status) {
        return switch (status) {
            case NO_GUN -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.no_gun");
            case UNKNOWN_GUN_DATA -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.unknown_gun");
            case UNREADABLE_GUN -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.unreadable_gun");
            case READY -> Component.empty();
        };
    }

    private static Component feedTypeText(GuardAmmoSummary.Snapshot summary) {
        if (summary.feedType() == null) {
            return Component.translatable("container.villagerguardgunners.ammo.tooltip.unavailable");
        }
        return switch (summary.feedType()) {
            case MAGAZINE -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.feed.magazine");
            case MANUAL -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.feed.manual");
            case FUEL -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.feed.fuel");
            case INVENTORY -> Component.translatable(
                    "container.villagerguardgunners.ammo.tooltip.feed.inventory");
        };
    }

    private static int ammunitionStatusColor(GuardAmmoSummary.Snapshot summary) {
        if (summary.status() == GuardAmmoSummary.Status.NO_GUN) {
            return 4210752;
        }
        if (!summary.isReady()
                || summary.unknownAmmoStacks() > 0
                || !summary.reserveScanComplete()) {
            return ChatFormatting.YELLOW.getColor();
        }
        if (summary.hasCompatibleReserve()) {
            return ChatFormatting.GREEN.getColor();
        }
        return summary.displayedLoadedAmmo() > 0
                ? ChatFormatting.GOLD.getColor()
                : ChatFormatting.RED.getColor();
    }

    private static int combatStatusColor(GuardCombatStatus status) {
        return switch (status) {
            case READY -> ChatFormatting.GREEN.getColor();
            case IDLE, UNKNOWN -> ChatFormatting.GRAY.getColor();
            case RELOADING, CYCLING_ACTION, DRAWING_WEAPON, CHARGING,
                    COOLING_DOWN, ACQUIRING_TARGET, AIMING -> ChatFormatting.AQUA.getColor();
            case DISABLED, HOLDING_FIRE, NO_LINE_OF_SIGHT, TOO_CLOSE,
                    OUT_OF_RANGE, REPOSITIONING -> ChatFormatting.GOLD.getColor();
            case UNARMED, WEAPON_DATA_NOT_READY, INVALID_FIRING_WINDOW,
                    UNSAFE_SHOT -> ChatFormatting.YELLOW.getColor();
            case WEAPON_ERROR, OUT_OF_AMMO, WEAPON_RESTRICTED,
                    RECOVERY_REQUIRED -> ChatFormatting.RED.getColor();
        };
    }

    private static boolean stacksMatch(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    class GuardGuiButton extends ImageButton {
        private ResourceLocation texture;
        private ResourceLocation newTexture;
        private boolean isFollowButton;

        public GuardGuiButton(int xIn, int yIn, int widthIn, int heightIn, int xTexStartIn, int yTexStartIn, int yDiffTextIn, ResourceLocation resourceLocationIn, ResourceLocation newTexture, boolean isFollowButton, OnPress onPressIn) {
            super(xIn, yIn, widthIn, heightIn, xTexStartIn, yTexStartIn, yDiffTextIn, resourceLocationIn, onPressIn);
            this.texture = resourceLocationIn;
            this.newTexture = newTexture;
            this.isFollowButton = isFollowButton;
        }

        public boolean requirementsForTexture() {
            boolean following = GuardGunnerInventoryScreen.this.guard.isFollowing();
            boolean patrol = GuardGunnerInventoryScreen.this.guard.isPatrolling();
            return this.isFollowButton ? following : patrol;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            ResourceLocation icon = this.requirementsForTexture() ? texture : newTexture;
            RenderSystem.setShaderTexture(0, icon);
            int i = this.yTexStart;
            if (this.isHoveredOrFocused()) {
                i += this.yDiffTex;
            }

            RenderSystem.enableDepthTest();
            graphics.blit(icon, this.getX(), this.getY(), (float) this.xTexStart, (float) i, this.width, this.height, this.textureWidth, this.textureHeight);
        }
    }

}
