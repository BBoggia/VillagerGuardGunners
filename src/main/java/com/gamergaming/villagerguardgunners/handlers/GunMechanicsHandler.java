package com.gamergaming.villagerguardgunners.handlers;

import java.util.Map;
import java.util.Random;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.util.GuardGunHelper;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

public class GunMechanicsHandler {

    /**
     * Holds the recoil state for a shooter, including cumulative pitch and yaw recoil.
     */
    public static class RecoilState {
        public float cumulativeRecoilPitch = 0.0F;
        public float cumulativeRecoilYaw = 0.0F;

        public RecoilState() {}

        public RecoilState(float pitch, float yaw) {
            this.cumulativeRecoilPitch = pitch;
            this.cumulativeRecoilYaw = yaw;
        }

        public void reset() {
            this.cumulativeRecoilPitch = 0.0F;
            this.cumulativeRecoilYaw = 0.0F;
        }
    }

    /**
     * Applies recoil to the recoil state based on the gun data.
     *
     * @param recoilState The recoil state to update.
     * @param gunData     The gun data containing recoil information.
     * @param random      A Random instance for randomness.
     */
    public static void applyRecoil(RecoilState recoilState, GunData gunData, RandomSource random) {
        // Assuming we have access to recoil data
        GunRecoil recoil = gunData.getRecoil();
        if (recoil != null) {
            // For simplicity, sum up the recoil values from the first keyframe
            GunRecoilKeyFrame[] pitchFrames = recoil.getPitch();
            GunRecoilKeyFrame[] yawFrames = recoil.getYaw();

            float pitchRecoil = 0.0F;
            float yawRecoil = 0.0F;

            if (pitchFrames != null && pitchFrames.length > 0) {
                // Randomly select a value between the min and max of the keyframe
                float[] value = pitchFrames[0].getValue();
                pitchRecoil = (value[0] + random.nextFloat() * (value[1] - value[0])) * 0.7F;
                // Randomly flip pitch direction
                if (random.nextBoolean()) {
                    pitchRecoil = -pitchRecoil;
                }
            }
            if (yawFrames != null && yawFrames.length > 0) {
                float[] value = yawFrames[0].getValue();
                yawRecoil = (value[0] + random.nextFloat() * (value[1] - value[0])) * 0.7F;
                // Randomly flip yaw direction
                if (random.nextBoolean()) {
                    yawRecoil = -yawRecoil;
                }
            }

            // Accumulate recoil
            recoilState.cumulativeRecoilPitch += pitchRecoil;
            recoilState.cumulativeRecoilYaw += yawRecoil;
        }
    }

    /**
     * Gradually recovers recoil over time.
     *
     * @param recoilState        The recoil state to update.
     * @param recoilRecoveryRate The rate at which recoil recovers (degrees per tick).
     */
    public static void recoverRecoil(RecoilState recoilState, float recoilRecoveryRate) {
        // Reduce cumulative recoil over time
        float recoveryFactor = recoilRecoveryRate * 0.05F; // Adjust time delta (tick duration) as needed

        if (recoilState.cumulativeRecoilPitch > 0) {
            recoilState.cumulativeRecoilPitch = Math.max(recoilState.cumulativeRecoilPitch - recoveryFactor, 0);
        } else {
            recoilState.cumulativeRecoilPitch = Math.min(recoilState.cumulativeRecoilPitch + recoveryFactor, 0);
        }

        if (recoilState.cumulativeRecoilYaw > 0) {
            recoilState.cumulativeRecoilYaw = Math.max(recoilState.cumulativeRecoilYaw - recoveryFactor, 0);
        } else {
            recoilState.cumulativeRecoilYaw = Math.min(recoilState.cumulativeRecoilYaw + recoveryFactor, 0);
        }
    }

    /**
     * Updates the inaccuracy based on the shooter's movement and gun properties.
     *
     * @param shooter The entity doing the shooting.
     * @param gunData The gun data containing inaccuracy information.
     * @return The updated inaccuracy value.
     */
    public static float updateInaccuracy(LivingEntity shooter, GunData gunData) {
        InaccuracyType inaccuracyType = getInaccuracyType(shooter);
        Map<InaccuracyType, Float> inaccuracyMap = gunData.getInaccuracy();
        float baseInaccuracy = inaccuracyMap.getOrDefault(inaccuracyType, 0.0F);
        return baseInaccuracy;
    }

    /**
     * Determines the inaccuracy type based on the shooter's state.
     *
     * @param shooter The entity doing the shooting.
     * @return The inaccuracy type corresponding to the shooter's state.
     */
    private static InaccuracyType getInaccuracyType(LivingEntity shooter) {
        if (shooter.isSprinting()) {
            return InaccuracyType.MOVE;
        } else if (shooter.isCrouching()) {
            return InaccuracyType.SNEAK;
        } else {
            return InaccuracyType.STAND;
        }
    }

    public static Vec3 calculateAdjustedAimVector(Guard shooter, RecoilState recoilState, float currentInaccuracy, RandomSource random) {
        // Start with guard's look vector
        Vec3 aimDirection = shooter.getViewVector(1.0F);

        // Adjust for recoil
        float adjustedPitch = (float) (Math.toDegrees(Math.asin(aimDirection.y)) - recoilState.cumulativeRecoilPitch);
        float adjustedYaw = (float) (Math.toDegrees(Math.atan2(aimDirection.z, aimDirection.x)) - recoilState.cumulativeRecoilYaw);

        // Convert adjusted pitch and yaw back to a direction vector
        float f = (float) Math.cos(Math.toRadians(adjustedPitch));
        float adjustedX = f * (float) Math.cos(Math.toRadians(adjustedYaw));
        float adjustedY = (float) Math.sin(Math.toRadians(adjustedPitch));
        float adjustedZ = f * (float) Math.sin(Math.toRadians(adjustedYaw));
        Vec3 adjustedAim = new Vec3(adjustedX, adjustedY, adjustedZ);

        // Adjust for inaccuracy (add random spread)
        adjustedAim = adjustedAim.add(
            (random.nextDouble() - 0.5) * currentInaccuracy * 0.01,
            (random.nextDouble() - 0.5) * currentInaccuracy * 0.01,
            (random.nextDouble() - 0.5) * currentInaccuracy * 0.01).normalize();

        // Adjust for target prediction
        if (shooter.getTarget() != null) {
            // adjustedAim = GunMechanicsHandler.getLeadAimVec(shooter, shooter.getTarget(), adjustedAim);
        }

        return adjustedAim;
    }


    /**
     * Calculates the aim vector that leads the target based on the shooter's position, target's position, and target's velocity.
     * 
     * @param shooter
     * @param target
     * @param initialAim
     * @return
     */
    private static Vec3 getLeadAimVec(Guard shooter, LivingEntity target, Vec3 initialAim) {
        // Predict target movement and adjust aim accordingly
    
        GunData gunData = null;
    
        if (shooter.getMainHandItem().getItem() instanceof AbstractGunItem) {
            gunData = GuardGunHelper.getGunDataFromItem(shooter.getMainHandItem());
        } else {
            return initialAim;
        }
    
        double projectileSpeed = gunData.getBulletData().getSpeed(); // Assuming this value is available in gunData
    
        double distanceToTarget = shooter.distanceTo(target);
        Vec3 deltaPos = initialAim.scale(distanceToTarget);
        Vec3 targetVelocity = target.getDeltaMovement();
    
        double bulletSpeed = projectileSpeed;
    
        // Coefficients for the quadratic equation (a*t^2 + b*t + c = 0)
        double a = bulletSpeed * bulletSpeed - targetVelocity.lengthSqr();
        double b = -2 * deltaPos.dot(targetVelocity);
        double c = -deltaPos.lengthSqr();
    
        double discriminant = b * b - 4 * a * c;
    
        double timeToTarget;
    
        if (discriminant > 0) {
            double sqrtDiscriminant = Math.sqrt(discriminant);
            double t1 = (-b + sqrtDiscriminant) / (2 * a);
            double t2 = (-b - sqrtDiscriminant) / (2 * a);
    
            // Select the smallest positive time
            timeToTarget = Math.min(t1, t2);
            if (timeToTarget < 0) {
                // Both times are negative; cannot hit the target
                return initialAim;
            }
        } else if (discriminant == 0) {
            timeToTarget = -b / (2 * a);
            if (timeToTarget < 0) {
                // Time is negative; cannot hit the target
                return initialAim;
            }
        } else {
            // No real solutions; cannot hit the target
            return initialAim;
        }
    
        // Calculate the aim vector towards the predicted position
        Vec3 aimVec = deltaPos.add(targetVelocity.scale(timeToTarget)).normalize();
    
        return aimVec;
    }
}