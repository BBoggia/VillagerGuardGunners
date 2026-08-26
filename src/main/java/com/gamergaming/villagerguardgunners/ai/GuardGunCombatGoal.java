package com.gamergaming.villagerguardgunners.ai;

import java.util.EnumSet;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.IGuardReloading;
import com.gamergaming.villagerguardgunners.handlers.GunMechanicsHandler;
import com.gamergaming.villagerguardgunners.handlers.MovementHandler;
// import com.gamergaming.villagerguardgunners.config.ConfigHandler;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.util.AIUtils;
import com.gamergaming.villagerguardgunners.util.GuardGunHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.entities.Guard;

public class GuardGunCombatGoal extends Goal {
    private final Guard guard;
    private final PathNavigation navigation;
    private LivingEntity target;

    private final RandomSource random = RandomSource.create();

    private MovementHandler movementHandler;

    private static final int MIN_ATTACK_INTERVAL = getAttackIntervalMin();
    private static final int MAX_ATTACK_INTERVAL = getAttackIntervalMax();

    // See time and attack cooldown measured in ticks
    private int seeTime;
    private int attackCooldown = 0;

    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND;

    // State variables for recoil and inaccuracy
    private GunMechanicsHandler.RecoilState recoilState = new GunMechanicsHandler.RecoilState();
    // private float cumulativeRecoilPitch = 0.0F;
    // private float cumulativeRecoilYaw = 0.0F;
    private float recoilRecoveryRate = 4.5F; // This can be adjusted or retrieved from weapon data
    private float currentInaccuracy = 0.0F;

    // Offset angle for aiming to make sure the gun is actually aiming at the target
    private double offsetDegrees = 0.0; 

    // Weapon data
    private GunData gunData;

    private final double switchToMeleeRangeThresholdSqr = Math.pow(getSwitchToMeleeRange(), 2);

    private FireMode currentFireMode;

    
    private int burstShotIntervalTicks = 0; // Ticks between shots in a burst
    private int burstShotIntervalCounter = 0; // Counter for timing shots within a burst

    private int burstCooldownTicks = 0; // Cooldown after a burst
    private int burstCooldownCounter = 0; // Counter for cooldown between bursts

    private int burstBPM = 0; // Burst rounds per minute
    private int burstShotsRemaining = 0;
    private int burstCount = 0; // Number of shots per burst
    private long burstShotIntervalMillis = 0; // Milliseconds between shots in a burst

    // For full-auto firing
    private int fullAutoShotCooldown = 0;
    private int fullAutoShotInterval = 4; // Default ticks between shots in full-auto (will set dynamically)

    // The gun's fire rate (shots per second)
    private float gunFireRate;

    public GuardGunCombatGoal(Guard guard) {
        // VillagerGuardGunners.LOGGER.info("GuardUseGunGoal constructor");
        this.guard = guard;
        this.navigation = guard.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));

        this.movementHandler = new MovementHandler(guard);
    }

    @Override
    public boolean canUse() {
        target = this.guard.getTarget();
        if (target == null || !target.isAlive())
            return false;
        else if (!isGun(this.guard.getMainHandItem()))
            if (hasSecondaryGun(this.guard))
                switchToSecondaryWeapon();
            else
                return false;
        else if (GuardGunHelper.isMagEmpty(guard) || (this.guard instanceof IGuardReloading reloadingGuard && reloadingGuard.isReloading()))
            return false;

        double distanceSq = this.guard.distanceToSqr(this.target);
        if (distanceSq <= switchToMeleeRangeThresholdSqr && hasSecondaryMeleeWeapon()) {
            VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Target is too close; switching to melee weapon");
            switchToSecondaryWeapon();
            return false; // Stop using this goal
        }

        // Check if the guard is holding a gun in main hand
        this.gunData = GuardGunHelper.getGunDataFromItem(guard.getMainHandItem());
        return this.gunData != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.canUse()) {
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal started");
        this.guard.setAggressive(true);
        this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);

        // Reset recoil state
        this.recoilState.reset();
        VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Starting goal");
        // Retrieve the current fire mode
        ItemStack gunItem = this.guard.getMainHandItem();
        if (gunItem.getItem() instanceof IGun gun) {
            this.currentFireMode = gun.getFireMode(gunItem);

            // Retrieve gun data
            this.gunData = GuardGunHelper.getGunDataFromItem(gunItem);

            if (this.gunData != null) {
                // Get the gun's fire rate for full-auto
                this.gunFireRate = this.gunData.getRoundsPerMinute(currentFireMode);
                if (this.gunFireRate > 0) {
                    this.fullAutoShotInterval = (int) (TICKS_PER_MINUTE / this.gunFireRate); // Convert RPM to ticks between shots
                } else {
                    this.fullAutoShotInterval = 4; // Default to 5 shots per second
                }

                // Get BurstData for burst mode
                BurstData burstData = this.gunData.getBurstData();
                if (burstData != null) {
                    this.burstCount = burstData.getCount();
                    this.burstBPM = burstData.getBpm();

                    if (this.burstBPM > 0) {
                        this.burstShotIntervalMillis = 60_000L / this.burstBPM;
                        this.burstShotIntervalTicks = (int) (burstShotIntervalMillis / 50); // Convert milliseconds to ticks
                    } else {
                        this.burstShotIntervalTicks = 4; // Default value if BPM is invalid
                    }

                    // Calculate minimum interval between bursts
                    this.burstCooldownTicks = (int) (burstData.getMinInterval() * 20); // Convert seconds to ticks
                } else {
                    // Default values if burst data is not available
                    this.burstCount = 3;
                    this.burstShotIntervalTicks = 4;
                    this.burstCooldownTicks = 20;
                }
            }
        } else {
            this.currentFireMode = FireMode.SEMI; // Default to semi-auto if not found
        }

        // Initialize burst tracking variables
        this.burstShotsRemaining = 0;
        this.burstShotIntervalCounter = 0; // Counter for timing shots within a burst
        this.burstCooldownCounter = 0; // Counter for cooldown between bursts
        this.fullAutoShotCooldown = 0;

        
    }

    @Override
    public void stop() {
        this.guard.setAggressive(false);
        this.navigation.stop();
        this.target = null;
        VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Stopping goal");
        // Reset recoil state
        this.recoilState.reset();
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive() || isGuardReloading()) {
            return;
        }

        double distanceSq = this.guard.distanceToSqr(this.target);
        // boolean canSeeTarget = this.guard.getSensing().hasLineOfSight(this.target);
        boolean canSeeTarget = AIUtils.hasLineOfSight(guard, target);
        if (distanceSq <= switchToMeleeRangeThresholdSqr) {
            // Target is too close; switch to melee weapon if available
            if (hasSecondaryMeleeWeapon()) {
                switchToSecondaryWeapon();
                VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Target is too close; switching to melee weapon");
                return; // Exit tick to allow melee goal to take over
            }
        }

        // Update seeTime
        this.seeTime = canSeeTarget ? this.seeTime + 1 : 0;

        // Rotate towards the target smoothly with an offset
        // Offset used to make sure the gun is acutally aiming at the target
        // Without offset, guard doesn't actually aim gun at the target
        double offsetRadians = Math.toRadians(offsetDegrees);

        // Get the direction vector from the guard to the target
        Vec3 directionToTarget = this.target.getEyePosition().subtract(this.guard.getEyePosition());
        
        // Rotate the direction vector around the Y-axis by the offset angle
        Vec3 adjustedDirection = rotateVectorY(directionToTarget, offsetRadians);

        // Get the adjusted target position
        Vec3 adjustedTargetPos = this.guard.getEyePosition().add(adjustedDirection);

        // Set the look control to the adjusted position
        // this.guard.getLookControl().setLookAt(adjustedTargetPos.x, adjustedTargetPos.y, adjustedTargetPos.z, 30.0F, 30.0F);

        // Adjust rotation speed when the guard is not facing the target
        if (!isFacingTarget(this.guard, this.target)) {
            // Set higher rotation speed to turn towards the target
            this.guard.getLookControl().setLookAt(
                //target.getX(), target.getEyeY(), target.getZ(),
                adjustedTargetPos.x, adjustedTargetPos.y, adjustedTargetPos.z,
                55.0F, // Increase these values to make the guard rotate faster
                55.0F
            );
        } else {
            // Normal rotation speed
            this.guard.getLookControl().setLookAt(
                //target.getX(), target.getEyeY(), target.getZ(),
                adjustedTargetPos.x, adjustedTargetPos.y, adjustedTargetPos.z,
                30.0F,
                30.0F
            );
        }

        // Synchronize body rotation with head rotation
        this.guard.yBodyRot = guard.getYHeadRot();
        this.guard.setYRot(guard.getYHeadRot());
   

        // Update movement based on distance and visibility
        movementHandler.updateMovement(distanceSq, canSeeTarget);

        // Attack logic remains the same
        handleAttackLogic(canSeeTarget);
    }

    private void handleAttackLogic(boolean canSeeTarget) {
        //  Recoil recovery
        GunMechanicsHandler.recoverRecoil(recoilState, recoilRecoveryRate);

        // Update inaccuracy based on movement
        this.currentInaccuracy = GunMechanicsHandler.updateInaccuracy(this.guard, this.gunData);

        // Decrement cooldowns and counters
        if (burstShotIntervalCounter > 0) {
            burstShotIntervalCounter--;
        }

        if (burstCooldownCounter > 0) {
            burstCooldownCounter--;
            // Reset burst shot remaining when cooling down
            if (burstCooldownCounter == 0) {
                burstShotsRemaining = 0;
            }
        }

        if (fullAutoShotCooldown > 0) {
            fullAutoShotCooldown--;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        // Check if it's time to attack and if the guard is facing the target
        if (canSeeTarget && this.seeTime >= getSeeTimeRequired() && isFacingTarget(this.guard, this.target)) {
            performAttack();
        } else if (this.seeTime < getSeeTimeRequired()) {
            // Reset cooldowns if target not seen for required time
            this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
            this.burstCooldownCounter = 0;
            this.burstShotsRemaining = 0;
            this.fullAutoShotCooldown = 0;
        }
    }

    private void performAttack() {
        if (isGun(this.guard.getMainHandItem()) && !GuardGunHelper.isMagEmpty(this.guard)) {
            switch (this.currentFireMode) {
                case SEMI -> {
                    if (this.attackCooldown <= 0) {
                        fireSingleShot();
                        this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
                    }
                }
    
                case BURST -> handleBurstFiring();
    
                case AUTO -> fireFullAuto();
    
                default -> {
                    if (this.attackCooldown <= 0) {
                        fireSingleShot();
                        this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
                    }
                }
            }
        } else if (GuardGunHelper.isMagEmpty(this.guard)) {
            // Handle reloading
            if (GuardGunHelper.hasAmmoInInventory(this.guard)) {
                // Start reloading
                if (this.guard instanceof IGuardReloading reloadingGuard) {
                    int reloadTime = GuardGunHelper.getReloadTime((AbstractGunItem) this.guard.getMainHandItem().getItem(), this.guard.getMainHandItem());
                    reloadingGuard.startReloading(reloadTime);
                }
            }
        }
    }

    private void fireSingleShot() {
        ItemStack gunItem = this.guard.getMainHandItem();
        // if (hasLineOfSight(this.target)) {
        if (AIUtils.hasLineOfSight(guard, target)) {
            Vec3 aimVector = GunMechanicsHandler.calculateAdjustedAimVector(this.guard, this.recoilState, this.currentInaccuracy, this.random);
            // GuardGunHelper.shootGunWithAim(this.guard, gunItem, aimVector);
            GuardGunHelper.shootGun(guard, gunItem);
            GunMechanicsHandler.applyRecoil(recoilState, gunData, random);
            this.guard.swing(InteractionHand.MAIN_HAND); // Animate attack
        } else {
            VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Firing single shot but Target not in line of sight");
        }
    }

    private void handleBurstFiring() {
        // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Handling burst firing");
        if (burstCooldownCounter <= 0) {
            if (burstShotsRemaining <= 0) {
                // Start a new burst
                burstShotsRemaining = burstCount;
                burstShotIntervalCounter = 0;
            }
    
            if (burstShotIntervalCounter <= 0 && burstShotsRemaining > 0) {
                // Time to fire a shot in the burst
                fireSingleShot();
                burstShotsRemaining--;
    
                // Reset the interval counter for next shot
                burstShotIntervalCounter = burstShotIntervalTicks;
            }
    
            if (burstShotsRemaining <= 0) {
                // Burst is complete, start cooldown
                burstCooldownCounter = burstCooldownTicks;
            }
        }
    }

    private void fireFullAuto() {
        // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Firing full-auto");
        if (fullAutoShotCooldown <= 0) {
            fireSingleShot();
            fullAutoShotCooldown = fullAutoShotInterval;
        }
    }

    private boolean isRangedAttackTargetAimingAtGuard(LivingEntity currentTarget) {
        // Implement logic to determine if the target is aiming at the guard
        if (currentTarget == null) return false;
        if (currentTarget instanceof RangedAttackMob) {
            Vec3 targetLookVec = currentTarget.getViewVector(1.0F).normalize();
            Vec3 guardToTargetVec = this.guard.position().subtract(currentTarget.position()).normalize();
            double dotProduct = targetLookVec.dot(guardToTargetVec);
            return dotProduct > 0.95D; // Adjust the threshold as needed
        }
        return false;        
    }

    private Vec3 rotateVectorY(Vec3 vector, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        double x = vector.x * cos - vector.z * sin;
        double z = vector.x * sin + vector.z * cos;
        return new Vec3(x, vector.y, z);
    }

    private boolean isGuardReloading() {
        return (this.guard instanceof IGuardReloading reloadingGuard && reloadingGuard.isReloading());
    }

    private boolean isFacingTarget(LivingEntity shooter, LivingEntity target) {
        double thresholdDegrees = calculateDynamicThreshold(shooter, target);
        return AIUtils.isFacingTargetWithOffset(shooter, target, thresholdDegrees, -offsetDegrees);
    }

    private double calculateDynamicThreshold(LivingEntity shooter, LivingEntity target) {
        double distanceSq = shooter.distanceToSqr(target);
        double maxThreshold = 20.0; // Max threshold at close range
        double minThreshold = 5.0;  // Min threshold at far range
        double maxDistanceSq = 50.0; // Distance at which threshold is minThreshold

        double distanceFactor = Math.min(distanceSq / maxDistanceSq, 1.0);
        double threshold = maxThreshold - (maxThreshold - minThreshold) * distanceFactor;

        return threshold;
    }

    private boolean hasSecondaryGun(LivingEntity shooter) {
        // Check if the guard has a gun in slot 4
        if (!(shooter instanceof Guard)) return false;
        ItemStack secondaryGun = ((Guard) shooter).guardInventory.getItem(4);
        // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Checking for secondary gun - " + secondaryGun.getDisplayName().getString());
        return !secondaryGun.isEmpty() && isGun(secondaryGun);
    }

    private boolean hasSecondaryMeleeWeapon() {
        // Check if the guard has a melee weapon in slot 4
        ItemStack secondaryWeapon = this.guard.guardInventory.getItem(4);
        // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Checking for secondary weapon - " + secondaryWeapon.getDisplayName().getString());
        return !secondaryWeapon.isEmpty() && isMeleeWeapon(secondaryWeapon) && !"[Air]".equals(secondaryWeapon.getDisplayName().getString());
    }

    private void switchToSecondaryWeapon() {
        // Swap items between slot 4 and slot 5
        ItemStack primaryWeapon = this.guard.guardInventory.getItem(5);
        ItemStack secondaryWeapon = this.guard.guardInventory.getItem(4);

        this.guard.guardInventory.setItem(5, secondaryWeapon);
        this.guard.guardInventory.setItem(4, primaryWeapon);

        // Update the guard's main hand item
        this.guard.setItemSlot(EquipmentSlot.MAINHAND, secondaryWeapon);
    }

    private boolean isMeleeWeapon(ItemStack itemStack) {
        // Assuming any item that is not a gun is a melee weapon
        ResourceLocation itemRegistryName = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        return !(itemRegistryName != null && itemRegistryName.getNamespace().equals("tacz"));
        // return itemStack.getItem() instanceof SwordItem || 
        //        itemStack.getItem() instanceof AxeItem || 
        //        itemStack.getItem() instanceof TridentItem;
    }

    private double getSwitchToMeleeThreshold() {
        return ModConfigs.GUARD_GUNNER.attackSettings.switchToMeleeRange.get();
    }

    private boolean isGun(ItemStack itemStack) {
        ResourceLocation gunRegistryName = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        return gunRegistryName != null && gunRegistryName.getNamespace().equals("tacz");
    }

    private static double getSwitchToMeleeRange() {
        return ModConfigs.GUARD_GUNNER.attackSettings.switchToMeleeRange.get();
    }

    private static double getMinDistanceToMaintainFromTarget() {
        return ModConfigs.GUARD_GUNNER.attackSettings.minDistanceToMaintainFromTarget.get();
    }

    private static double getMaxAttackRange() {
        return ModConfigs.GUARD_GUNNER.attackSettings.maxAttackRange.get();
    }

    private static double getMoveSpeed() {
        return ModConfigs.GUARD_GUNNER.movementSettings.movementSpeed.get();
    }

    private static int getAttackIntervalMin() {
        return ModConfigs.GUARD_GUNNER.attackSettings.minAttackInterval.get();
    }

    private static int getAttackIntervalMax() {
        return ModConfigs.GUARD_GUNNER.attackSettings.maxAttackInterval.get();
    }

    private int getSeeTimeRequired() {
        return ModConfigs.GUARD_GUNNER.attackSettings.seeTimeRequired.get();
    }
}







































// public class GuardGunCombatGoal extends Goal {
//     private final Guard guard;
//     private final PathNavigation navigation;
//     private LivingEntity target;

//     private final RandomSource random = RandomSource.create();

//     // Movement related constants
//     private static final double MIN_ATTACK_RANGE_SQ = Math.pow(getSwitchToMeleeRange(), 2);
//     private static final double MAX_ATTACK_RANGE_SQ = Math.pow(getMaxAttackRange(), 2);

//     private static final double DISTANCE_TO_MAINTAIN_FROM_TARGET = Math.pow(getMinDistanceToMaintainFromTarget(), 2);

//     private static final double MOVE_SPEED = getMoveSpeed();

//     private static final int MIN_ATTACK_INTERVAL = getAttackIntervalMin();
//     private static final int MAX_ATTACK_INTERVAL = getAttackIntervalMax();

//     private MovementState movementState = MovementState.IDLE;
//     private int stateTime = 0; // Time spent in the current state
//     private static final int MIN_STATE_DURATION = 40; // Minimum time in state (ticks)

//     // See time and attack cooldown measured in ticks
//     private int seeTime;
//     private int attackCooldown = 0;

//     private static final int TICKS_PER_SECOND = 20;
//     private static final int TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND;

//     private int circlingTime = 0;
//     private boolean strafingClockwise = true;
//     private int strafingDuration = 100 + random.nextInt(100); 

//     // State variables for recoil and inaccuracy
//     private GunMechanicsHandler.RecoilState recoilState = new GunMechanicsHandler.RecoilState();
//     // private float cumulativeRecoilPitch = 0.0F;
//     // private float cumulativeRecoilYaw = 0.0F;
//     private float recoilRecoveryRate = 5.0F; // This can be adjusted or retrieved from weapon data
//     private float currentInaccuracy = 0.0F;

//     // Weapon data
//     private GunData gunData;

//     private final double switchToMeleeRangeThresholdSqr = getswitchToMeleeRangeThresholdSqr();

//     private FireMode currentFireMode;

    
//     private int burstShotIntervalTicks = 0; // Ticks between shots in a burst
//     private int burstShotIntervalCounter = 0; // Counter for timing shots within a burst

//     private int burstCooldownTicks = 0; // Cooldown after a burst
//     private int burstCooldownCounter = 0; // Counter for cooldown between bursts

//     private int burstBPM = 0; // Burst rounds per minute
//     private int burstShotsRemaining = 0;
//     private int burstCount = 0; // Number of shots per burst
//     private long burstShotIntervalMillis = 0; // Milliseconds between shots in a burst

//     // For full-auto firing
//     private int fullAutoShotCooldown = 0;
//     private int fullAutoShotInterval = 4; // Default ticks between shots in full-auto (will set dynamically)

//     // The gun's fire rate (shots per second)
//     private float gunFireRate;

//     public GuardGunCombatGoal(Guard guard) {
//         // VillagerGuardGunners.LOGGER.info("GuardUseGunGoal constructor");
//         this.guard = guard;
//         this.navigation = guard.getNavigation();
//         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
//     }

//     @Override
//     public boolean canUse() {
//         target = this.guard.getTarget();
//         if (target == null || !target.isAlive())
//             return false;
//         else if (!isGun(this.guard.getMainHandItem()))
//             if (hasSecondaryGun(this.guard))
//                 switchToSecondaryWeapon();
//             else
//                 return false;
//         else if (GuardGunHelper.isMagEmpty(guard) || (this.guard instanceof IGuardReloading reloadingGuard && reloadingGuard.isReloading()))
//             return false;

//         double distanceSq = this.guard.distanceToSqr(this.target);
//         if (distanceSq <= switchToMeleeRangeThresholdSqr && hasSecondaryMeleeWeapon()) {
//             VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Target is too close; switching to melee weapon");
//             switchToSecondaryWeapon();
//             return false; // Stop using this goal
//         }

//         // Check if the guard is holding a gun in main hand
//         this.gunData = GuardGunHelper.getGunDataFromItem(guard.getMainHandItem());
//         return this.gunData != null;
//     }

//     @Override
//     public boolean canContinueToUse() {
//         if (this.canUse() && isGun(this.guard.getMainHandItem()) && this.guard.getTarget() != null) {
//             if (this.guard.distanceToSqr(this.guard.getTarget()) > switchToMeleeRangeThresholdSqr && !GuardGunHelper.isMagEmpty(guard)) {
//                 return true;
//             }
//         }
//         return false;
//     }

//     @Override
//     public void start() {
//         // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal started");
//         this.guard.setAggressive(true);
//         this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
//         this.circlingTime = 0;
//         this.strafingClockwise = this.random.nextBoolean();

//         // Reset recoil state
//         this.recoilState.reset();

//         // Retrieve the current fire mode
//         ItemStack gunItem = this.guard.getMainHandItem();
//         if (gunItem.getItem() instanceof IGun gun) {
//             this.currentFireMode = gun.getFireMode(gunItem);

//             // Retrieve gun data
//             this.gunData = GuardGunHelper.getGunDataFromItem(gunItem);

//             if (this.gunData != null) {
//                 // Get the gun's fire rate for full-auto
//                 this.gunFireRate = this.gunData.getRoundsPerMinute(currentFireMode);
//                 VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Fire rate: " + this.gunFireRate);
//                 if (this.gunFireRate > 0) {
//                     this.fullAutoShotInterval = (int) (TICKS_PER_MINUTE / this.gunFireRate); // Convert RPM to ticks between shots
//                 } else {
//                     this.fullAutoShotInterval = 4; // Default to 5 shots per second
//                 }

//                 // Get BurstData for burst mode
//                 BurstData burstData = this.gunData.getBurstData();
//                 if (burstData != null) {
//                     this.burstCount = burstData.getCount();
//                     this.burstBPM = burstData.getBpm();

//                     if (this.burstBPM > 0) {
//                         this.burstShotIntervalMillis = 60_000L / this.burstBPM;
//                         this.burstShotIntervalTicks = (int) (burstShotIntervalMillis / 50); // Convert milliseconds to ticks
//                     } else {
//                         this.burstShotIntervalTicks = 4; // Default value if BPM is invalid
//                     }

//                     // Calculate minimum interval between bursts
//                     this.burstCooldownTicks = (int) (burstData.getMinInterval() * 20); // Convert seconds to ticks
//                 } else {
//                     // Default values if burst data is not available
//                     this.burstCount = 3;
//                     this.burstShotIntervalTicks = 4;
//                     this.burstCooldownTicks = 20;
//                 }
//             }
//         } else {
//             this.currentFireMode = FireMode.SEMI; // Default to semi-auto if not found
//         }

//         // Initialize burst tracking variables
//         this.burstShotsRemaining = 0;
//         this.burstShotIntervalCounter = 0; // Counter for timing shots within a burst
//         this.burstCooldownCounter = 0; // Counter for cooldown between bursts
//         this.fullAutoShotCooldown = 0;

        
//     }

//     @Override
//     public void stop() {
//         this.guard.setAggressive(false);
//         this.navigation.stop();
//         this.target = null;
//         this.circlingTime = 0;

//         // Reset recoil state
//         this.recoilState.cumulativeRecoilPitch = 0.0F;
//         this.recoilState.cumulativeRecoilYaw = 0.0F;
//     }

//     @Override
//     public void tick() {
//         if (this.target == null || !this.target.isAlive() || isGuardReloading()) {
//             return;
//         }

//         double distanceSq = this.guard.distanceToSqr(this.target);
//         boolean canSeeTarget = this.guard.getSensing().hasLineOfSight(this.target);

//         if (distanceSq <= switchToMeleeRangeThresholdSqr) {
//             // Target is too close; switch to melee weapon if available
//             if (hasSecondaryMeleeWeapon()) {
//                 switchToSecondaryWeapon();
//                 // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Target is too close; switching to melee weapon");
//                 return; // Exit tick to allow melee goal to take over
//             }
//         }

//         // Update seeTime
//         this.seeTime = canSeeTarget ? this.seeTime + 1 : 0;

//         // Rotate towards the target smoothly with an offset
//         double offsetDegrees = 10.0; // Negative value to adjust aim to the right
//         double offsetRadians = Math.toRadians(offsetDegrees);

//         // Get the direction vector from the guard to the target
//         Vec3 directionToTarget = this.target.getEyePosition().subtract(this.guard.getEyePosition());

//         // Rotate the direction vector around the Y-axis by the offset angle
//         Vec3 adjustedDirection = rotateVectorY(directionToTarget, offsetRadians);

//         // Get the adjusted target position
//         Vec3 adjustedTargetPos = this.guard.getEyePosition().add(adjustedDirection);

//         // Set the look control to the adjusted position
//         // this.guard.getLookControl().setLookAt(adjustedTargetPos.x, adjustedTargetPos.y, adjustedTargetPos.z, 30.0F, 30.0F);

//         // Adjust rotation speed when the guard is not facing the target
//         if (!isFacingTarget(this.guard, this.target)) {
//             // Set higher rotation speed to turn towards the target
//             this.guard.getLookControl().setLookAt(
//                 //target.getX(), target.getEyeY(), target.getZ(),
//                 adjustedTargetPos.x, adjustedTargetPos.y, adjustedTargetPos.z,
//                 55.0F, // Increase these values to make the guard rotate faster
//                 55.0F
//             );
//         } else {
//             // Normal rotation speed
//             this.guard.getLookControl().setLookAt(
//                 //target.getX(), target.getEyeY(), target.getZ(),
//                 adjustedTargetPos.x, adjustedTargetPos.y, adjustedTargetPos.z,
//                 30.0F,
//                 30.0F
//             );
//         }

//         // Synchronize body rotation with head rotation
//         this.guard.yBodyRot = guard.getYHeadRot();
//         this.guard.setYRot(guard.getYHeadRot());
   

//         // Update movement based on distance and visibility
//         updateMovementState(distanceSq, canSeeTarget);

//         // Attack logic remains the same
//         handleAttackLogic(canSeeTarget);
//     }

//     private void performAttack() {
//         if (isGun(this.guard.getMainHandItem()) && !GuardGunHelper.isMagEmpty(this.guard)) {
//             switch (this.currentFireMode) {
//                 case SEMI -> {
//                     if (this.attackCooldown <= 0) {
//                         fireSingleShot();
//                         this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
//                     }
//                 }
    
//                 case BURST -> handleBurstFiring();
    
//                 case AUTO -> fireFullAuto();
    
//                 default -> {
//                     if (this.attackCooldown <= 0) {
//                         fireSingleShot();
//                         this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
//                     }
//                 }
//             }
//         } else if (GuardGunHelper.isMagEmpty(this.guard)) {
//             // Handle reloading
//             if (GuardGunHelper.hasAmmoInInventory(this.guard)) {
//                 // Start reloading
//                 if (this.guard instanceof IGuardReloading reloadingGuard) {
//                     int reloadTime = GuardGunHelper.getReloadTime((AbstractGunItem) this.guard.getMainHandItem().getItem(), this.guard.getMainHandItem());
//                     reloadingGuard.startReloading(reloadTime);
//                 }
//             }
//         }
//     }

//     private void fireSingleShot() {
//         ItemStack gunItem = this.guard.getMainHandItem();
//         // if (hasLineOfSight(this.target)) {
//         if (AIUtils.hasLineOfSight(guard, target)) {
//             Vec3 aimVector = GunMechanicsHandler.calculateAdjustedAimVector(this.guard, this.recoilState, this.currentInaccuracy, this.random);
//             GuardGunHelper.shootGunWithAim(this.guard, gunItem, aimVector);
//             this.guard.swing(InteractionHand.MAIN_HAND); // Animate attack
//             GunMechanicsHandler.applyRecoil(recoilState, gunData, random);
//         }
//     }

//     private void handleBurstFiring() {
//         if (burstCooldownCounter <= 0) {
//             if (burstShotsRemaining <= 0) {
//                 // Start a new burst
//                 burstShotsRemaining = burstCount;
//                 burstShotIntervalCounter = 0;
//             }
    
//             if (burstShotIntervalCounter <= 0 && burstShotsRemaining > 0) {
//                 // Time to fire a shot in the burst
//                 fireSingleShot();
//                 burstShotsRemaining--;
    
//                 // Reset the interval counter for next shot
//                 burstShotIntervalCounter = burstShotIntervalTicks;
//             }
    
//             if (burstShotsRemaining <= 0) {
//                 // Burst is complete, start cooldown
//                 burstCooldownCounter = burstCooldownTicks;
//             }
//         }
//     }

//     private void fireFullAuto() {
//         if (fullAutoShotCooldown <= 0) {
//             fireSingleShot();
//             fullAutoShotCooldown = fullAutoShotInterval;
//         }
//     }

//     private void updateMovementState(double distanceSq, boolean canSeeTarget) {
//         // Check if line of sight is blocked
//         boolean lineOfSightBlocked = !canSeeTarget || isLineOfSightBlocked();

//         stateTime++;
    
//         // States transition logic
//         switch (movementState) {
//             case APPROACHING -> {
//                 if (distanceSq <= DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.0 && stateTime >= MIN_STATE_DURATION) {
//                     transitionToState(MovementState.HOLDING);
//                 } else {
//                     approachTarget();
//                 }
//             }
//             case RETREATING -> {
//                 if (distanceSq >= DISTANCE_TO_MAINTAIN_FROM_TARGET * 1.3 && stateTime >= MIN_STATE_DURATION) {
//                     transitionToState(MovementState.HOLDING);
//                 } else {
//                     retreatFromTarget();
//                 }
//             }
//             case CIRCLING -> {
//                 if ((distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2 || distanceSq < DISTANCE_TO_MAINTAIN_FROM_TARGET) && stateTime >= MIN_STATE_DURATION) {
//                     if (distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2) {
//                         transitionToState(MovementState.APPROACHING);
//                     } else {
//                         transitionToState(MovementState.RETREATING);
//                     }
//                 } else if (lineOfSightBlocked) {
//                     attemptToRegainLineOfSight();
//                 } else {
//                     performCircling();
//                 }
//             }
//             case HOLDING -> {
//                 if ((distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2 || distanceSq < DISTANCE_TO_MAINTAIN_FROM_TARGET) && stateTime >= MIN_STATE_DURATION) {
//                     if (distanceSq > DISTANCE_TO_MAINTAIN_FROM_TARGET * 2.2) {
//                         transitionToState(MovementState.APPROACHING);
//                     } else {
//                         transitionToState(MovementState.RETREATING);
//                     }
//                 } else if (lineOfSightBlocked) {
//                     transitionToState(MovementState.CIRCLING);
//                 }
//                 // Hold position
//             }
//             case IDLE -> {
//                 if (stateTime >= MIN_STATE_DURATION && target != null) {
//                     transitionToState(MovementState.APPROACHING);
//                 }
//                 // Remain idle
//             }
//         }
//     }

//     private void transitionToState(MovementState newState) {
//         movementState = newState;
//         stateTime = 0; // Reset state time
    
//         // Optional: Stop navigation when changing state
//         navigation.stop();
    
//         // Optional: Reset strafing variables if needed
//         circlingTime = 0;
//         strafingClockwise = random.nextBoolean();
//     }

//     private void performCircling() {
//         circlingTime++;

//         if (circlingTime >= this.strafingDuration) { // Change direction every 100 - 200 ticks (approx 5 - 10 seconds)
//             circlingTime = 0;
//             strafingClockwise = !strafingClockwise;
//             strafingDuration = 100 + random.nextInt(100);
//         }
    
//         float strafeSpeed = calculateStrafeSpeed();
//         float forwardMovement = calculateForwardMovement();
    
//         guard.getMoveControl().strafe(forwardMovement, strafingClockwise ? strafeSpeed : -strafeSpeed);
//     }

//     private void approachTarget() {
//         if (navigation.isDone() || navigation.getPath() == null) {
//             Vec3 direction = this.target.position().subtract(this.guard.position()).normalize(); // Direction towards target
//             Vec3 newPosition = this.guard.position().add(direction.scale(2.0)); // Move 2 units towards target
//             moveToPosition(newPosition);
//         }
//     }

//     private void retreatFromTarget() {
//         if (navigation.isDone() || navigation.getPath() == null) {
//             Vec3 direction = this.guard.position().subtract(this.target.position()).normalize(); // Direction away from target
//             Vec3 newPosition = this.guard.position().add(direction.scale(5.0)); // Move 2 units away
//             moveToPosition(newPosition);
//         }
//     }

//     private float calculateStrafeSpeed() {
//         float baseStrafeSpeed = 0.3F;
//         float fluctuation = 0.2F * (float) Math.sin(circlingTime / 30.0F);
//         return baseStrafeSpeed + fluctuation;
//     }

//     private float calculateForwardMovement() {
//         float movement = 0.0F;
//         if (random.nextInt(100) < 5) {
//             movement = random.nextBoolean() ? 0.2F : -0.2F;
//         }
//         return movement;
//     }


//     private void moveToPosition(Vec3 position) {
//         if (!isPositionSafe(new Vec3i((int) position.x, (int) position.y, (int) position.z))) {
//             // Attempt to find a nearby safe position
//             position = findNearestSafePosition(position);
//         }
    
//         // Check if the path is clear
//         if (isPathObstructed(position)) {
//             // Recalculate or find an alternative path
//             position = findAlternativePosition(position);
//         }
    
//         Path path = navigation.getPath();
//         if (navigation.isDone() || path == null || path.getEndNode() == null || path.getEndNode().asVec3().distanceToSqr(position) > 1.0D) {
//             navigation.moveTo(position.x, position.y, position.z, MOVE_SPEED);
//         }
//     }

//     private boolean isPathObstructed(Vec3 position) {
//         Path pathToPosition = navigation.createPath(position.x, position.y, position.z, 0);
//         return pathToPosition == null || !pathToPosition.canReach();
//     }

//     private Vec3 findAlternativePosition(Vec3 originalPosition) {
//         // Try to find nearby positions that are reachable
//         for (int i = 0; i < 10; i++) {
//             double angle = random.nextDouble() * 2 * Math.PI;
//             double distance = 1.0 + random.nextDouble() * 2.0;
//             Vec3 candidatePos = guard.position().add(
//                 distance * Math.cos(angle),
//                 0,
//                 distance * Math.sin(angle)
//             );
    
//             if (isPositionSafe(new Vec3i((int) candidatePos.x, (int) candidatePos.y, (int) candidatePos.z)) && !isPathObstructed(candidatePos)) {
//                 return candidatePos;
//             }
//         }
//         // If no alternative found, stay in current position
//         return guard.position();
//     }

//     private boolean isLineOfSightBlocked() {
//         Vec3 guardEyePos = guard.getEyePosition();
//         Vec3 targetEyePos = target.getEyePosition();
//         BlockHitResult hitResult = guard.level().clip(new ClipContext(
//             guardEyePos, targetEyePos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, guard));

//         return hitResult.getType() != HitResult.Type.MISS && !hitResult.getBlockPos().equals(target.blockPosition());
//     }

//     private void attemptToRegainLineOfSight() {
//         if (navigation.isDone() || navigation.getPath() == null) {
//             // Attempt to find a nearby position with a clear line of sight
//             Vec3 targetPos = target.position();
//             Vec3 guardPos = guard.position();
    
//             // Sample positions around the guard to find a spot with clear line of sight
//             for (int i = 0; i < 10; i++) {
//                 double angle = random.nextDouble() * 2 * Math.PI;
//                 double distance = 2.0 + random.nextDouble() * 3.0; // Between 2 and 5 blocks away
//                 Vec3 candidatePos = guardPos.add(
//                     distance * Math.cos(angle),
//                     0,
//                     distance * Math.sin(angle)
//                 );
//                 Vec3i candidatePosVec3i = new Vec3i((int) candidatePos.x, (int) candidatePos.y, (int) candidatePos.z);
//                 // Check if the position is valid and has line of sight
//                 // if (isPositionValid(candidatePosVec3i) && hasLineOfSightFromPosition(candidatePos, targetPos)) {
//                 if (isPositionValid(candidatePosVec3i) && AIUtils.hasLineOfSightFromPosition(guard.level(), candidatePos, targetPos, guard)) {
//                     navigation.moveTo(candidatePos.x, candidatePos.y, candidatePos.z, MOVE_SPEED);
//                     return;
//                 }
//             }
    
//             // If no position found, default to moving towards the target
//             approachTarget();
//         }
//     }

//     private boolean isRangedAttackTargetAimingAtGuard(LivingEntity currentTarget) {
//         // Implement logic to determine if the target is aiming at the guard
//         if (currentTarget == null) return false;
//         if (currentTarget instanceof RangedAttackMob) {
//             Vec3 targetLookVec = currentTarget.getViewVector(1.0F).normalize();
//             Vec3 guardToTargetVec = this.guard.position().subtract(currentTarget.position()).normalize();
//             double dotProduct = targetLookVec.dot(guardToTargetVec);
//             return dotProduct > 0.95D; // Adjust the threshold as needed
//         }
//         return false;        
//     }

//     private Vec3 rotateVectorY(Vec3 vector, double angleRadians) {
//         double cos = Math.cos(angleRadians);
//         double sin = Math.sin(angleRadians);
//         double x = vector.x * cos - vector.z * sin;
//         double z = vector.x * sin + vector.z * cos;
//         return new Vec3(x, vector.y, z);
//     }

//     // private void recoverRecoil() {
//     //     // Reduce cumulative recoil over time
//     //     float recoveryFactor = recoilRecoveryRate * 0.05F; // Adjust time delta as needed
//     //     if (this.recoilState.cumulativeRecoilPitch > 0) {
//     //         this.recoilState.cumulativeRecoilPitch = Math.max(this.recoilState.cumulativeRecoilPitch - recoveryFactor, 0);
//     //     } else {
//     //         this.recoilState.cumulativeRecoilPitch = Math.min(this.recoilState.cumulativeRecoilPitch + recoveryFactor, 0);
//     //     }
//     //     if (this.recoilState.cumulativeRecoilYaw > 0) {
//     //         this.recoilState.cumulativeRecoilYaw = Math.max(this.recoilState.cumulativeRecoilYaw - recoveryFactor, 0);
//     //     } else {
//     //         this.recoilState.cumulativeRecoilYaw = Math.min(this.recoilState.cumulativeRecoilYaw + recoveryFactor, 0);
//     //     }
//     // }

//     // private void updateInaccuracy() {
//     //     // Update inaccuracy based on movement and weapon properties
//     //     InaccuracyType inaccuracyType = getInaccuracyType();
//     //     float baseInaccuracy = this.gunData.getInaccuracy().getOrDefault(inaccuracyType, 0.0F);
//     //     this.currentInaccuracy = baseInaccuracy;
//     // }

//     private void handleAttackLogic(boolean canSeeTarget) {

//         //  Recoil recovery
//         //this.recoverRecoil();
//         GunMechanicsHandler.recoverRecoil(recoilState, recoilRecoveryRate);

//         // Update inaccuracy based on movement
//         // this.updateInaccuracy();
//         this.currentInaccuracy = GunMechanicsHandler.updateInaccuracy(this.guard, this.gunData);

//         // Decrement cooldowns and counters
//         if (burstShotIntervalCounter > 0) {
//             burstShotIntervalCounter--;
//         }

//         if (burstCooldownCounter > 0) {
//             burstCooldownCounter--;
//             // Reset burst shot remaining when cooling down
//             if (burstCooldownCounter == 0) {
//                 burstShotsRemaining = 0;
//             }
//         }

//         if (fullAutoShotCooldown > 0) {
//             fullAutoShotCooldown--;
//         }

//         if (attackCooldown > 0) {
//             attackCooldown--;
//         }

//         // Check if it's time to attack and if the guard is facing the target
//         if (canSeeTarget && this.seeTime >= getSeeTimeRequired() && isFacingTarget(this.guard, this.target)) {
//             performAttack();
//         } else if (this.seeTime < getSeeTimeRequired()) {
//             // Reset cooldowns if target not seen for required time
//             this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
//             this.burstCooldownCounter = 0;
//             this.burstShotsRemaining = 0;
//             this.fullAutoShotCooldown = 0;
//         }
//     }

//     private boolean isGuardReloading() {
//         return (this.guard instanceof IGuardReloading reloadingGuard && reloadingGuard.isReloading());
//     }

//     // private Vec3 calculateAdjustedAimVector() {
//     //     // Start with guard's look vector
//     //     Vec3 aimDirection = this.guard.getViewVector(1.0F);

//     //     // Adjust for recoil
//     //     float adjustedPitch = (float) (Math.toDegrees(Math.asin(aimDirection.y)) - cumulativeRecoilPitch);
//     //     float adjustedYaw = (float) (Math.toDegrees(Math.atan2(aimDirection.z, aimDirection.x)) - cumulativeRecoilYaw);

//     //     // Convert adjusted pitch and yaw back to a direction vector
//     //     float f = (float) Math.cos(Math.toRadians(adjustedPitch));
//     //     float adjustedX = f * (float) Math.cos(Math.toRadians(adjustedYaw));
//     //     float adjustedY = (float) Math.sin(Math.toRadians(adjustedPitch));
//     //     float adjustedZ = f * (float) Math.sin(Math.toRadians(adjustedYaw));
//     //     Vec3 adjustedAim = new Vec3(adjustedX, adjustedY, adjustedZ);

//     //     // Adjust for inaccuracy (add random spread)
//     //     adjustedAim = adjustedAim.add(
//     //         (random.nextDouble() - 0.5) * this.currentInaccuracy * 0.01,
//     //         (random.nextDouble() - 0.5) * this.currentInaccuracy * 0.01,
//     //         (random.nextDouble() - 0.5) * this.currentInaccuracy * 0.01).normalize();

//     //     // Adjust for target prediction
//     //     adjustedAim = this.getLeadAimVec(adjustedAim);

//     //     return adjustedAim;
//     // }

//     // private Vec3 getLeadAimVec(Vec3 initialAim) {
//     //     // Predict target movement and adjust aim accordingly

//     //     double distanceToTarget = this.guard.distanceTo(this.target);
//     //     double projectileSpeed = this.gunData.getBulletData().getSpeed(); // Assuming this value is available in gunData

//     //     double timeToTarget = distanceToTarget / projectileSpeed;

//     //     Vec3 targetMotion = this.target.getDeltaMovement();
//     //     Vec3 predictedTargetPos = this.target.position().add(targetMotion.scale(timeToTarget));

//     //     // Recalculate aim vector towards predicted position
//     //     Vec3 guardEyePos = this.guard.getEyePosition();
//     //     Vec3 aimVec = predictedTargetPos.subtract(guardEyePos).normalize();

//     //     return aimVec;
//     // }

//     private InaccuracyType getInaccuracyType() {
//         if (this.guard.isSprinting()) {
//             return InaccuracyType.MOVE;
//         } else if (this.guard.isCrouching()) {
//             return InaccuracyType.SNEAK;
//         } else {
//             return InaccuracyType.STAND;
//         }
//     }

//     private boolean isPositionValid(Vec3i position) {
//         // Check if position is navigable and safe
//         BlockPos blockPos = new BlockPos(position);
//         return guard.level().isEmptyBlock(blockPos) && guard.level().isEmptyBlock(blockPos.above()) && isPositionSafe(position);
//     }

//     private boolean isHazardous(BlockPos pos) {
//         // Check for lava, fire, cactus, etc.
//         BlockState blockState = guard.level().getBlockState(pos);

//         return blockState.is(Blocks.LAVA) || blockState.is(Blocks.FIRE) || blockState.is(Blocks.CACTUS);
//     }

//     private boolean isPositionSafe(Vec3i position) {
//         BlockPos blockPos = new BlockPos(position);
//         return !isHazardous(blockPos) && !isHazardous(blockPos.below());
//     }

//     private Vec3 findNearestSafePosition(Vec3 position) {
//         for (int i = 0; i < 10; i++) {
//             double angle = random.nextDouble() * 2 * Math.PI;
//             double distance = 1.0 + random.nextDouble() * 3.0; // Between 1 and 4 blocks
//             Vec3 candidatePos = position.add(
//                 distance * Math.cos(angle),
//                 0,
//                 distance * Math.sin(angle)
//             );
    
//             if (isPositionSafe(new Vec3i((int) candidatePos.x, (int) candidatePos.y, (int) candidatePos.z))) {
//                 return candidatePos;
//             }
//         }
//         // If no safe position found, default to current position
//         return guard.position();
//     }

//     private boolean isFacingTarget(LivingEntity shooter, LivingEntity target) {
//         double thresholdDegrees = calculateDynamicThreshold(shooter, target);
//         return AIUtils.isFacingTarget(shooter, target, thresholdDegrees);
//     }

//     private double calculateDynamicThreshold(LivingEntity shooter, LivingEntity target) {
//         double distanceSq = shooter.distanceToSqr(target);
//         double maxThreshold = 20.0; // Max threshold at close range
//         double minThreshold = 5.0;  // Min threshold at far range
//         double maxDistanceSq = 50.0; // Distance at which threshold is minThreshold

//         double distanceFactor = Math.min(distanceSq / maxDistanceSq, 1.0);
//         double threshold = maxThreshold - (maxThreshold - minThreshold) * distanceFactor;

//         return threshold;
//     }

//     // private boolean hasLineOfSightFromPosition(Vec3 fromPos, Vec3 toPos) {
//     //     BlockHitResult hitResult = guard.level().clip(new ClipContext(
//     //         fromPos, toPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, guard));
//     //     return hitResult.getType() == HitResult.Type.MISS || hitResult.getBlockPos().equals(target.blockPosition());
//     // }

//     private boolean hasSecondaryGun(LivingEntity shooter) {
//         // Check if the guard has a gun in slot 4
//         if (!(shooter instanceof Guard)) return false;
//         ItemStack secondaryGun = ((Guard) shooter).guardInventory.getItem(4);
//         // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Checking for secondary gun - " + secondaryGun.getDisplayName().getString());
//         return !secondaryGun.isEmpty() && isGun(secondaryGun);
//     }

//     private boolean hasSecondaryMeleeWeapon() {
//         // Check if the guard has a melee weapon in slot 4
//         ItemStack secondaryWeapon = this.guard.guardInventory.getItem(4);
//         // VillagerGuardGunners.LOGGER.info("GuardFireGunAtTargetGoal: Checking for secondary weapon - " + secondaryWeapon.getDisplayName().getString());
//         return !secondaryWeapon.isEmpty() && isMeleeWeapon(secondaryWeapon) && !"[Air]".equals(secondaryWeapon.getDisplayName().getString());
//     }

//     private void switchToSecondaryWeapon() {
//         // Swap items between slot 4 and slot 5
//         ItemStack primaryWeapon = this.guard.guardInventory.getItem(5);
//         ItemStack secondaryWeapon = this.guard.guardInventory.getItem(4);

//         this.guard.guardInventory.setItem(5, secondaryWeapon);
//         this.guard.guardInventory.setItem(4, primaryWeapon);

//         // Update the guard's main hand item
//         this.guard.setItemSlot(EquipmentSlot.MAINHAND, secondaryWeapon);
//     }

//     private boolean isMeleeWeapon(ItemStack itemStack) {
//         // Implement logic to determine if the itemStack is a melee weapon
//         // For simplicity, assume any item that is not a gun is a melee weapon
//         ResourceLocation itemRegistryName = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
//         return !(itemRegistryName != null && itemRegistryName.getNamespace().equals("tacz"));
//     }

//     private double getswitchToMeleeRangeThresholdSqr() {
//         double closeRangeThreshold = 5.0D; // Close-range threshold (within 5 blocks)
//         return closeRangeThreshold * closeRangeThreshold;
//     }

//     private boolean isGun(ItemStack itemStack) {
//         ResourceLocation gunRegistryName = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
//         return gunRegistryName != null && gunRegistryName.getNamespace().equals("tacz");
//     }

//     private static double getSwitchToMeleeRange() {
//         return ModConfigs.GUARD_GUNNER.attackSettings.switchToMeleeRange.get();
//     }

//     private static double getMinDistanceToMaintainFromTarget() {
//         return ModConfigs.GUARD_GUNNER.attackSettings.minDistanceToMaintainFromTarget.get();
//     }

//     private static double getMaxAttackRange() {
//         return ModConfigs.GUARD_GUNNER.attackSettings.maxAttackRange.get();
//     }

//     private static double getMoveSpeed() {
//         return ModConfigs.GUARD_GUNNER.movementSettings.movementSpeed.get();
//     }

//     private static int getAttackIntervalMin() {
//         return ModConfigs.GUARD_GUNNER.attackSettings.minAttackInterval.get();
//     }

//     private static int getAttackIntervalMax() {
//         return ModConfigs.GUARD_GUNNER.attackSettings.maxAttackInterval.get();
//     }

//     private int getSeeTimeRequired() {
//         return ModConfigs.GUARD_GUNNER.attackSettings.seeTimeRequired.get();
//     }

//     // private boolean hasLineOfSight(LivingEntity target) {
//     //     // Implement more sophisticated line-of-sight check if needed
//     //     return this.guard.getSensing().hasLineOfSight(target);
//     // }
// }