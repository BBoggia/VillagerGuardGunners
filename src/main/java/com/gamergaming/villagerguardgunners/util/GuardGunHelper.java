package com.gamergaming.villagerguardgunners.util;

import java.util.Optional;

import com.gamergaming.villagerguardgunners.entity.GuardBulletEntity;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.sound.SoundManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

public class GuardGunHelper {

    public static void reloadGun(LivingEntity shooter, ItemStack gunItem) {
        // Implement logic to reload the gun

        Item gun = gunItem.getItem();

        if (gun instanceof AbstractGunItem) {

            if (ModConfigs.GUARD_GUNNER.reloadSettings.infiniteGuardAmmo.get()) {
                ((AbstractGunItem) gun).setCurrentAmmoCount(gunItem, getMaxAmmoCount(gunItem));
                ((AbstractGunItem) gun).setBulletInBarrel(gunItem, true);
            } else {
                int ammoCount = inventoryAmmoCount(shooter);
                if (ammoCount == 0) { return; }
                int ammoNeeded = getMaxAmmoCount(gunItem) - ((AbstractGunItem) gun).getCurrentAmmoCount(gunItem);
                int ammoToReload = Math.min(ammoCount, ammoNeeded) + ((AbstractGunItem) gun).getCurrentAmmoCount(gunItem);

                removeAmmoFromInventory(shooter, ammoToReload);
                ((AbstractGunItem) gun).setCurrentAmmoCount(gunItem, Math.max(0, ammoToReload - 1));
                ((AbstractGunItem) gun).setBulletInBarrel(gunItem, true);
            }
      }

        // USE gun.getAmmoAmount(gunItem) - gun.getCurrentAmmoCount(gunItem)
        // TO GET THE AMOUNT OF AMMO TO REMOVE FROM THE GUARD'S INVENTORY
    }

    public static void shootGun(LivingEntity shooter, ItemStack gunItem) {
        IGun gun = (IGun) gunItem.getItem();

        if (isMagEmpty(shooter)) {
            return;
        }

        Level world = shooter.level();

        // Determine the bullet's properties based on the gun and ammo
        float damage = 6.0F; 
        float speed = 3.0F;
        float inaccuracy = 0.0F;
        

        ResourceLocation gunId = gun.getGunId(gunItem);
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndexOptional.isPresent()) {
            GunData gunData = gunIndexOptional.get().getGunData();
            float bulletSpeed =  gunData.getBulletData().getSpeed();
            speed = (bulletSpeed > 20) ? 20 : bulletSpeed;
            damage = gunData.getBulletData().getDamageAmount();
            inaccuracy = gunData.getInaccuracy(InaccuracyType.AIM);
            
            // gun.getCurrentAmmoCount(gunItem) GETS THE CURRENT AMMO COUNT
            // gunData.getAmmoAmount() GETS THE MAX AMMO COUNT  
        } else {
            // Handle the case where the gun data is not found
        }

        // Create the bullet entity
        GuardBulletEntity bullet = new GuardBulletEntity(world, shooter);
        bullet.setDamage(damage);

        // Set bullet properties
        Vec3 lookVec = shooter.getLookAngle();
        bullet.setDeltaMovement(lookVec.scale(5)); // speed

        // Correct the bullet position to come from the gun
        // Vec3 shooterPos = shooter.position().add(0, shooter.getEyeHeight() - 0.1, 0);
        double posX = shooter.xOld + (shooter.getX() - shooter.xOld) / 2.0;
        double posY = shooter.yOld + (shooter.getY() - shooter.yOld) / 2.0 + shooter.getEyeHeight();
        double posZ = shooter.zOld + (shooter.getZ() - shooter.zOld) / 2.0;
        bullet.setPos(posX, posY, posZ);
        
        
        // Add the bullet to the world
        world.addFreshEntity(bullet);
        
        handleAmmoConsumption(shooter, gunItem);
        
        playGunSound(shooter, gunItem);
    }

    private static void playGunSound(LivingEntity shooter, ItemStack gunStack) {
        if (shooter.level().isClientSide) {
            return;
        }

        float volume = 0.8f;
        float pitch = 0.9f + shooter.getRandom().nextFloat() * 0.125f;
        ResourceLocation gunId = ((IGun) gunStack.getItem()).getGunId(gunStack);
        ResourceLocation ammoId = getAmmoIdForGun(gunStack);

        SoundManager.sendSoundToNearby(
                shooter,
                GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get(),
                gunId,
                ammoId,
                SoundManager.SHOOT_3P_SOUND,
                volume,
                pitch);
    }


    public static int getMaxAmmoCount(ItemStack gunItem) {
        // Retrieve the gun's maximum ammo capacity

        GunData gunData = getGunDataFromItem(gunItem);
        
        return gunData.getAmmoAmount();
    }

    public static int getMaxAmmoCount(LivingEntity shooter) {
        // Retrieve the guard
        Guard guard = (Guard) shooter;
        ItemStack gunItem = guard.getMainHandItem();
        return getMaxAmmoCount(gunItem);
    }

    private static void handleAmmoConsumption(LivingEntity shooter, ItemStack gunItem) {
        // Implement logic to consume ammo from the guard's inventory or gun's internal storage
        // For simplicity, you might decrement a custom NBT tag representing ammo count

        IGun gun = (IGun) gunItem.getItem();
        GunData gunData = getGunDataFromItem(gunItem);

        if (gun instanceof AbstractGunItem) {
            if (gun.getCurrentAmmoCount(gunItem) > 0) {
                gun.reduceCurrentAmmoCount(gunItem);
            } else {
                gun.setBulletInBarrel(gunItem, false);
            }
            // VillagerGuardGunners.LOGGER.info("Current Ammo: " + gun.getCurrentAmmoCount(gunItem));
        }
        
        // Placeholder implementation:
        if (gunItem.isDamageableItem()) {
            // gunItem.hurtAndBreak(1, shooter, (entity) -> {
            //     entity.broadcastBreakEvent(InteractionHand.MAIN_HAND);
            // });
        }
    }

    private static int inventoryAmmoCount(LivingEntity shooter) {
        // Implement logic to count the ammo items in the guard's inventory
        
        Guard guard = (Guard) shooter;
        SimpleContainer guardInventory = guard.guardInventory;

        if (guardInventory != null && !guardInventory.isEmpty()) {
            if (guardInventory.getContainerSize() < 9) {
                return 0;
            }

            int ammoCount = 0;
            for (int i = 6; i < 9; i++) {
                ItemStack stack = guardInventory.getItem(i);
                if (!stack.isEmpty()) {
                    ammoCount += stack.getCount();
                }
            }

            return ammoCount;
        }

        return 0;
    }

    private static void removeAmmoFromInventory(LivingEntity shooter, int ammoToRemove) {
        // Implement logic to remove ammo items from the guard's inventory
        
        Guard guard = (Guard) shooter;
        SimpleContainer guardInventory = guard.guardInventory;

        if (guardInventory != null && !guardInventory.isEmpty()) {
            if (guardInventory.getContainerSize() < 9) {
                return;
            }

            for (int i = 6; i < 9; i++) {
                ItemStack stack = guardInventory.getItem(i);
                if (!stack.isEmpty()) {
                    int count = stack.getCount();
                    if (count >= ammoToRemove) {
                        stack.shrink(ammoToRemove);
                        ammoToRemove = 0;
                        break;
                    } else {
                        stack.shrink(count);
                        ammoToRemove -= count;
                    }
                }
            }
        }
    }

    public static boolean isFacingTarget(LivingEntity shooter, LivingEntity target) {
        double angleDegrees = getAngleToTarget(shooter, target);
        double distanceSq = shooter.distanceToSqr(target);
    
        // Adjust threshold based on distance
        double maxThreshold = 20.0; // Max threshold at close range
        double minThreshold = 5.0;  // Min threshold at far range
        double maxDistanceSq = 100.0; // Distance at which threshold is minThreshold
    
        double distanceFactor = Math.min(distanceSq / maxDistanceSq, 1.0);
        double threshold = maxThreshold - (maxThreshold - minThreshold) * distanceFactor;
    
        return angleDegrees <= threshold;
    }

    public static double getAngleToTarget(LivingEntity shooter, LivingEntity target) {
        // Get the guard's facing direction
        Vec3 guardFacingVec = shooter.getViewVector(1.0F);
    
        // Get the vector pointing from the guard to the target
        Vec3 vecToTarget = target.position().subtract(shooter.position()).normalize();
    
        // Calculate the angle between the two vectors using dot product
        double angleRadians = Math.acos(guardFacingVec.dot(vecToTarget));
        
        // Convert angle to degrees
        double angleDegrees = Math.toDegrees(angleRadians);
    
        return angleDegrees;
    }

    public static boolean isMagFull(LivingEntity shooter) {
        // Check if the guard's gun is fully loaded
        
        ItemStack gunItem = shooter.getMainHandItem();
        IGun gun;
        if (gunItem.getItem() instanceof IGun) {
            gun = (IGun) gunItem.getItem();
        } else {
            return false;
        }
        return (gun.getCurrentAmmoCount(gunItem) >= getMaxAmmoCount(gunItem));
    }

    public static boolean isMagEmpty(LivingEntity shooter) {
        // Check if the guard has loaded ammo in the gun's internal storage
        
        ItemStack gunItem = shooter.getMainHandItem();
        IGun gun = (IGun) gunItem.getItem();
        boolean isBarrelEmpty = !gun.hasBulletInBarrel(gunItem);
        return (gun.getCurrentAmmoCount(gunItem) <= 0) && isBarrelEmpty;
    }

    public static boolean hasAmmoInInventory(LivingEntity shooter) {
        // Check the guard's inventory for ammo items
        
        // Get the guard's container inventory
        Guard guard = (Guard) shooter;
        SimpleContainer guardInventory = guard.guardInventory;
        if (guardInventory != null && !guardInventory.isEmpty()) {
            if (guardInventory.getContainerSize() < 9) {
                return false;
            }

            // Check if the guard has any ammo items in slots 6, 7, and 8
            for (int i = 6; i < 9; i++) {
                ItemStack stack = guardInventory.getItem(i);
                if (!stack.isEmpty() && stack.getItem() instanceof IAmmo) {
                    return ((IAmmo) stack.getItem()).isAmmoOfGun(guard.getMainHandItem(), stack);
                }
            }
            
        }

        return false;
    }

    public static int getAmmoCountInInventory(LivingEntity shooter) {
        // Get the total count of ammo items in the guard's inventory
        
        Guard guard = (Guard) shooter;
        SimpleContainer guardInventory = guard.guardInventory;
        if (guardInventory != null && !guardInventory.isEmpty()) {
            if (guardInventory.getContainerSize() < 9) {
                return 0;
            }

            int ammoCount = 0;
            for (int i = 6; i < 9; i++) {
                ItemStack stack = guardInventory.getItem(i);
                if (!stack.isEmpty() && stack.getItem() instanceof IAmmo) {
                    ammoCount += stack.getCount();
                }
            }

            return ammoCount;
        }

        return 0;
    }

    public static int getCurrentAmmoCount(LivingEntity shooter) {
        // Get the total count of ammo in the guard's gun and inventory
        
        ItemStack gunItem = shooter.getMainHandItem();
        IGun gun = (IGun) gunItem.getItem();
        int ammoCount = gun.getCurrentAmmoCount(gunItem);
        if (gun.hasBulletInBarrel(gunItem)) {
            ammoCount++;
        }
        return ammoCount;
    }

    public static GunData getGunDataFromItem(ItemStack gunItem) {
        // Retrieve the gun's data from the item stack

        // Check if the gun item is an instance of IGun
        if (!(gunItem.getItem() instanceof IGun)) {
            return null;
        }
        
        IGun gun = (IGun) gunItem.getItem();
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gun.getGunId(gunItem));
        return gunIndexOptional.get().getGunData();
    }

    public static ResourceLocation getAmmoIdForGun(ItemStack gunItem) {
        // Retrieve the gun's ammo item ID
        
        return getGunDataFromItem(gunItem).getAmmoId();
    }

    public static int getReloadTime(AbstractGunItem gunItem, ItemStack gunStack) {
        // Retrieve the reload time from the gun's data
        IGun gun = gunItem;
        ResourceLocation gunId = gun.getGunId(gunStack);

        // Access the gun's data from TaCz API
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndexOptional.isPresent()) {
            CommonGunIndex gunIndex = gunIndexOptional.get();
            GunData gunData = gunIndex.getGunData();
            float reloadTimeSeconds = gunData.getReloadData().getCooldown().getTacticalTime();
            int reloadTimeTicks = (int) (reloadTimeSeconds * 20); // Convert seconds to ticks
            return reloadTimeTicks;
        }
        // Default reload time if data not available, e.g., 2 seconds
        return 4;
    }

}
