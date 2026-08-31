package com.gamergaming.villagerguardgunners.item;

import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.DecodeResult;
import com.gamergaming.villagerguardgunners.orders.GuardOrderText;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.OrderField;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A reusable carrier for one complete set of per-guard behavior orders. */
public final class OrdersLedgerItem extends Item {
    public OrdersLedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Keep this render-time check allocation-free. The full strict decode is
        // performed only when the tooltip is requested or the ledger is used.
        CompoundTag itemTag = stack.getTag();
        return (itemTag != null
                        && itemTag.contains(
                                GuardOrderTemplate.TEMPLATE_TAG,
                                Tag.TAG_COMPOUND))
                || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        DecodeResult decoded = GuardOrderTemplate.readFrom(stack.getTag());
        switch (decoded.status()) {
            case BLANK -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.orders_ledger.tooltip.blank")
                    .withStyle(ChatFormatting.GRAY));
            case CORRUPT -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.orders_ledger.tooltip.corrupt")
                    .withStyle(ChatFormatting.RED));
            case UNSUPPORTED_VERSION -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.orders_ledger.tooltip.unsupported",
                            decoded.storedVersion())
                    .withStyle(ChatFormatting.RED));
            case VALID -> appendStoredOrders(
                    tooltip,
                    decoded.orders(),
                    decoded.roleId(),
                    decoded.includesRoleAssignment());
        }
    }

    private static void appendStoredOrders(
            List<Component> tooltip,
            @Nullable GuardOrders orders,
            @Nullable net.minecraft.resources.ResourceLocation roleId,
            boolean includesRoleAssignment) {
        if (orders == null) {
            tooltip.add(Component.translatable(
                            "item.villagerguardgunners.orders_ledger.tooltip.corrupt")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.orders_ledger.tooltip.stored")
                .withStyle(ChatFormatting.GOLD));
        for (OrderField field : OrderField.values()) {
            tooltip.add(Component.translatable(
                            "container.villagerguardgunners.orders."
                                    + GuardOrderText.fieldKey(field),
                            GuardOrderText.value(field, orders))
                    .withStyle(ChatFormatting.GRAY));
        }
        net.minecraft.network.chat.MutableComponent roleLine = includesRoleAssignment
                ? Component.translatable(
                        "item.villagerguardgunners.orders_ledger.tooltip.role",
                        roleId == null ? "none" : roleId.toString())
                : Component.translatable(
                        "item.villagerguardgunners.orders_ledger.tooltip.legacy_role");
        tooltip.add(roleLine.withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.orders_ledger.tooltip.apply")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.orders_ledger.tooltip.overwrite")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
