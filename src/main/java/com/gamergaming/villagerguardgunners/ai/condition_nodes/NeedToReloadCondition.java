package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.tacz.guns.api.item.IGun;

import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

public class NeedToReloadCondition extends GuardCondition {

    public NeedToReloadCondition(Guard guard) {
        super(guard);
    }

    @Override
    protected boolean condition() {
        ItemStack gunItem = guard.getMainHandItem();
        if (!(gunItem.getItem() instanceof IGun gun)) {
            return false;
        }
        return gun.getCurrentAmmoCount(gunItem) <= 0 && !gun.hasBulletInBarrel(gunItem);
    }
}