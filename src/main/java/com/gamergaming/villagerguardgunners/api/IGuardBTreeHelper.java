package com.gamergaming.villagerguardgunners.api;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public interface IGuardBTreeHelper {

    public void faceTarget();
    public void faceTarget(LivingEntity entity);
    public void faceTarget(float turnSpeed);
    public void faceTarget(LivingEntity entity, float turnSpeed);

    
    public void facePosition(double x, double y, double z);
    public void facePosition(double x, double y, double z, float turnSpeed);

    public void performAttack();
    public void performAttack(LivingEntity entity);
    
    public boolean isFacingTarget();
    public boolean isFacingTarget(LivingEntity entity);
    public boolean isFacingTarget(LivingEntity entity, float minAngle, float maxAngle);
    public boolean isFacingTarget(LivingEntity entity, float minAngle, float maxAngle, float distance);

    public double getAngleToTarget();
    public double getAngleToTarget(LivingEntity entity);

    public int getCurrentAmmoCount();
    public int getMaxAmmoCount();


    public List<BlockPos> getStaticPatrolPoints();
    public void addStaticPatrolPoint(BlockPos point);
    public void setStaticPatrolPoints(List<BlockPos> points);
    public boolean hasStaticPatrolPoints();
}
