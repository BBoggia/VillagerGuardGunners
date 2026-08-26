// package com.gamergaming.villagerguardgunners.ai;

// import net.minecraft.world.entity.ai.goal.Goal;
// import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
// import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
// import net.minecraft.world.entity.Entity;
// import net.minecraft.world.entity.EquipmentSlot;
// import net.minecraft.world.entity.LivingEntity;
// import net.minecraft.world.entity.ai.control.MoveControl;
// import net.minecraft.world.entity.ai.navigation.PathNavigation;
// import net.minecraft.world.item.ItemStack;
// import net.minecraft.world.phys.AABB;
// import net.minecraftforge.registries.ForgeRegistries;
// import net.minecraft.resources.ResourceLocation;
// import net.minecraft.world.InteractionHand;
// import tallestegg.guardvillagers.entities.Guard;

// import java.util.EnumSet;

// import com.gamergaming.villagerguardgunners.config.ConfigHandler;

// public class GuardMeleeCombatGoal extends MeleeAttackGoal {
//     protected final Guard guard;
//     private LivingEntity target;
//     private int attackCooldown;
//     private int switchCooldown;
//     private int seeTime;
//     private PathNavigation navigation;

//     private final double stopDistanceSq = 2.0D; // Squared distance for stopping (within 1.4 blocks)

//     private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - (double) 0.4F;

//     public GuardMeleeCombatGoal(Guard guard, double speedIn, boolean useLongMemory) {
//         super(guard, speedIn, useLongMemory);
//         this.guard = guard;
//         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
//         this.navigation = guard.getNavigation();
//     }

//     @Override
//     public boolean canUse() {
//         return false;
//         // LivingEntity livingentity = this.guard.getTarget();
//         // if (livingentity == null || !livingentity.isAlive()) {
//         //     return false;
//         // }

//         // // Check if the guard is holding a melee weapon (not a gun)
//         // if (!isMeleeWeapon(this.guard.getMainHandItem())) {
//         //     return false;
//         // }

//         // this.target = livingentity;

//         // // Check if the target is within close-range threshold
//         // double distanceSq = this.guard.distanceToSqr(this.target);
//         // return distanceSq <= getCloseRangeThresholdSq();
//     }

//     @Override
//     public boolean canContinueToUse() {
//         return this.canUse() || (!this.guard.getNavigation().isDone() && this.target != null && this.target.isAlive());
//     }

//     @Override
//     public void start() {
//         this.guard.setAggressive(true);
//         this.attackCooldown = 0;
//         this.switchCooldown = 0;
//     }

//     @Override
//     public void stop() {
//         this.guard.setAggressive(false);
//         this.target = null;
//         this.navigation.stop();
//     }

//     @Override
//     public void tick() {
//         if (this.target == null || !this.target.isAlive()) {
//             return;
//         }

//         double distanceSq = this.guard.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
//         boolean canSee = this.guard.getSensing().hasLineOfSight(this.target);

//         // Check if the target has moved beyond the close-range threshold
//         if (distanceSq > getCloseRangeThresholdSq() + 4.0D) { // Added buffer to prevent rapid switching
//             // Switch back to primary weapon if available
//             if (hasPrimaryWeapon()) {
//                 switchToPrimaryWeapon();
//                 this.stop();
//                 return;
//             }
//         }

//         if (canSee) {
//             this.seeTime++;
//         } else {
//             this.seeTime = 0;
//         }

//         if (canPerformAttack(this.guard.getTarget())) {
//             this.guard.getNavigation().moveTo(this.guard.getTarget(), ConfigHandler.GENERAL.MOVE_SPEED.get());
//         } else {
//             this.guard.getNavigation().stop();
//         }

//         this.guard.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

//         this.switchCooldown = Math.max(0, this.switchCooldown - 1);

//         if (distanceSq > getCloseRangeThresholdSq() + 4.0D && this.switchCooldown == 0) {
//             // Switch back to primary weapon if available
//             if (hasPrimaryWeapon()) {
//                 switchToPrimaryWeapon();
//                 this.switchCooldown = 40; // Cooldown of 2 seconds (assuming 20 ticks per second)
//                 this.stop();
//                 return;
//             }
//         }

//         this.attackCooldown = Math.max(this.attackCooldown - 1, 0);

//         // if (distanceSq <= stopDistanceSq && this.attackCooldown <= 0) {
//         if (canPerformAttack(this.guard.getTarget()) && this.attackCooldown <= 0) {
//             this.performAttack();
//             this.attackCooldown = 20; // Reset attack cooldown (adjust as needed)
//         }

//         // Check if the target has moved beyond the close-range threshold
//         if (distanceSq > getCloseRangeThresholdSq() + 2.0D) {
//             // Switch back to ranged combat
//             this.stop();
//         }
//     }

//     private void performAttack() {
//         this.guard.swing(InteractionHand.MAIN_HAND);
//         this.guard.doHurtTarget(this.target);
//     }

//     protected boolean canPerformAttack(LivingEntity mob) {
//         return this.isTimeToAttack() && this.mobHitBox(this.mob).inflate(0.65).intersects(this.mobHitBox(mob)) && this.mob.getSensing().hasLineOfSight(mob);
//     }

//     protected AABB mobHitBox(LivingEntity mob) {
//         Entity entity = mob.getVehicle();
//         AABB aabb;
//         if (entity != null) {
//             AABB aabb1 = entity.getBoundingBox();
//             AABB aabb2 = mob.getBoundingBox();
//             aabb = new AABB(Math.min(aabb2.minX, aabb1.minX), aabb2.minY, Math.min(aabb2.minZ, aabb1.minZ), Math.max(aabb2.maxX, aabb1.maxX), aabb2.maxY, Math.max(aabb2.maxZ, aabb1.maxZ));
//         } else {
//             aabb = mob.getBoundingBox();
//         }

//         return aabb.inflate(DEFAULT_ATTACK_REACH, 0.0D, DEFAULT_ATTACK_REACH);
//     }

//     private boolean isMeleeWeapon(ItemStack itemStack) {
//         // Implement logic to determine if the itemStack is a melee weapon
//         // For simplicity, assume any item that is not a gun is a melee weapon
//         ResourceLocation itemRegistryName = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
//         if (itemRegistryName != null && itemRegistryName.getNamespace().equals("tacz")) {
//             // It's a gun
//             return false;
//         }
//         return true;
//     }

//     private double getCloseRangeThresholdSq() {
//         double closeRangeThreshold = 5.0D; // Close-range threshold (within 5 blocks)
//         return closeRangeThreshold * closeRangeThreshold;
//     }

//     private boolean hasPrimaryWeapon() {
//         // Check if the guard has a gun in slot 5
//         ItemStack primaryWeapon = this.guard.guardInventory.getItem(5);
//         return !primaryWeapon.isEmpty() && isGun(primaryWeapon);
//     }

//     private void switchToPrimaryWeapon() {
//         // Swap items between slot 4 and slot 5
//         ItemStack primaryWeapon = this.guard.guardInventory.getItem(5);
//         ItemStack secondaryWeapon = this.guard.guardInventory.getItem(4);

//         this.guard.guardInventory.setItem(5, secondaryWeapon);
//         this.guard.guardInventory.setItem(4, primaryWeapon);

//         // Update the guard's main hand item
//         this.guard.setItemSlot(EquipmentSlot.MAINHAND, primaryWeapon);
//     }

//     private boolean isGun(ItemStack itemStack) {
//         ResourceLocation gunRegistryName = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
//         return gunRegistryName != null && gunRegistryName.getNamespace().equals("tacz");
//     }
// }