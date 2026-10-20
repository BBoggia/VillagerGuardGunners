package com.gamergaming.villagerguardgunners.quartermaster;

import com.gamergaming.villagerguardgunners.inventory.ItemCustomData;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Portable stock must not nest through shulker boxes or bundles either. */
public final class QuartermasterItem extends BlockItem {
    public QuartermasterItem(Block block, Properties properties) { super(block, properties); }
    @Override public boolean canFitInsideContainerItems() { return false; }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, List<Component> lines, TooltipFlag flags) {
        super.appendHoverText(stack, level, lines, flags);
        if (stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA) && stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains(QuartermasterBlockEntity.DATA_TAG)) {
            lines.add(Component.translatable("item.villagerguardgunners.quartermaster.packed").withStyle(ChatFormatting.GRAY));
        }
    }
}
