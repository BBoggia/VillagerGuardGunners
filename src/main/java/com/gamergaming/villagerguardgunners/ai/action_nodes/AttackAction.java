package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.IGuardBTreeHelper;
import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.api.ai.UtilityNode;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.util.GuardGunHelper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

public class AttackAction extends GuardAction implements UtilityNode {

    public AttackAction(Guard guard) {
        super(guard);
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    @Override
    public double getUtility() {
        // Calculate utility based on factors
        double utility = 0;
        double healthFactor = guard.getHealth() / guard.getMaxHealth();
        double distanceFactor = guard.distanceToSqr(guard.getTarget()) / ModConfigs.GUARD_GUNNER.attackSettings.maxAttackRange.get().doubleValue();
        double ammoFactor = GuardGunHelper.getCurrentAmmoCount(guard) / Math.max(GuardGunHelper.getMaxAmmoCount(guard), 1.0);

        // Adjust weights as needed
        utility = (0.5 * healthFactor) + (0.3 * (1 - distanceFactor)) + (0.2 * ammoFactor);

        return utility;
    }

	@Override
    protected NodeStatus evaluate() {
        if (guard instanceof IGuardBTreeHelper guardB) {
            if (!isTargetAlive()) {
                return NodeStatus.FAILURE;
            }

            // Ensure facing target
            if (!guardB.isFacingTarget()) {
                guardB.faceTarget();
                return NodeStatus.RUNNING;
            }
            double angleToTarget = guardB.getAngleToTarget();
            if (angleToTarget > 20.0) { // degrees threshold
                guard.getLookControl().setLookAt(guard.getTarget(), 30.0F, 30.0F);
                return NodeStatus.RUNNING;
            }

            // Perform attack
            if (!guard.swinging) {
                guard.swing(InteractionHand.MAIN_HAND);
                // GuardGunHelper.shootGunWithAim(
            }
        }
        return NodeStatus.SUCCESS;
    }

    @Override
    protected boolean shouldReevaluate() {
        // Implement logic to determine if reevaluation is needed
        // For example, only reevaluate if the target's state has changed
        return true; // For simplicity, always reevaluate
    }
}
