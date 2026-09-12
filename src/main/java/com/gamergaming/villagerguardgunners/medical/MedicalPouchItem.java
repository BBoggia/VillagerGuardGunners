package com.gamergaming.villagerguardgunners.medical;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A sealed recovery item, created from a guard's pouch on death or conversion. */
public final class MedicalPouchItem extends Item {
    public MedicalPouchItem(Properties properties) { super(properties); }
    @Override public boolean canFitInsideContainerItems() { return false; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.villagerguardgunners.medical_pouch.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
