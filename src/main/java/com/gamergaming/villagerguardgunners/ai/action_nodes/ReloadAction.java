package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.IGuardReloading;
import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.util.GuardGunHelper;
import com.tacz.guns.api.item.gun.AbstractGunItem;

import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

public class ReloadAction extends GuardAction {

    public ReloadAction(Guard guard) {
        super(guard);
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    @Override
    protected NodeStatus evaluate() {
        if (!(guard instanceof IGuardReloading reloadingGuard)) {
            return NodeStatus.FAILURE;
        }
        if (reloadingGuard.isReloading()) {
            // Continue reloading
            return NodeStatus.RUNNING;
        } else {
            // Start reloading
            ItemStack gunItem = guard.getMainHandItem();
            if (!(gunItem.getItem() instanceof AbstractGunItem gun)) {
                return NodeStatus.FAILURE;
            }
            int reloadTime = GuardGunHelper.getReloadTime(gun, gunItem);
            reloadingGuard.startReloading(reloadTime);
            return NodeStatus.RUNNING;
        }
    }

    @Override
    protected boolean shouldReevaluate() {
        // Implement logic to determine if reevaluation is needed
        // For example, only reevaluate if the target's state has changed
        return true; // For simplicity, always reevaluate
    }
}