package com.gamergaming.villagerguardgunners.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tallestegg.guardvillagers.entities.Guard;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
// import com.gamergaming.villagerguardgunners.ai.GuardMeleeCombatGoal;
import com.gamergaming.villagerguardgunners.ai.GuardReloadingGoal;
import com.gamergaming.villagerguardgunners.api.IGuardBTreeHelper;
import com.gamergaming.villagerguardgunners.api.IGuardReloading;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.util.GuardGunHelper;
import com.tacz.guns.api.entity.IGunOperator;

import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.injection.At;

@Mixin(Guard.class)
public abstract class GuardMixin extends Mob implements RangedAttackMob, IGuardReloading, IGunOperator, IGuardBTreeHelper {

    private List<BlockPos> staticPatrolPoints = new ArrayList<>();

    private int reloadingTimeRemaining = 0;

    public GuardMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        IGunOperator.fromLivingEntity(self()).initialData();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo info) {
        if (this.reloadingTimeRemaining > 0) {
            this.reloadingTimeRemaining--;
        }

    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/SimpleContainer", ordinal = 0))
    private SimpleContainer redirectSimpleContainer(int size) {
        return new SimpleContainer(9); // Set guardInventory size to 9
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void addAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        CompoundTag staticPatrolData = new CompoundTag();
        ListTag staticPatrolList = new ListTag();
        for (BlockPos pos : staticPatrolPoints) {
            staticPatrolList.add(NbtUtils.writeBlockPos(pos));
        }
        staticPatrolData.put("StaticPatrolPoints", staticPatrolList);
        compound.put("GuardPatrol", staticPatrolData);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void readAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("GuardPatrol")) {
            CompoundTag staticPatrolData = compound.getCompound("GuardPatrol");
            if (staticPatrolData.contains("StaticPatrolPoints")) {
                ListTag staticPatrolList = staticPatrolData.getList("StaticPatrolPoints", Tag.TAG_COMPOUND);
                staticPatrolPoints.clear();
                for (int i = 0; i < staticPatrolList.size(); i++) {
                    staticPatrolPoints.add(NbtUtils.readBlockPos(staticPatrolList.getCompound(i)));
                }
            }
        }
    }

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void addCustomGoals(CallbackInfo info) {
        Guard guard = (Guard) (Object) this;

        GoalSelector goalSelector = ((Mob)guard).goalSelector;

        if (ModList.get().isLoaded("tacz")) {
            // VillagerGuardGunners.LOGGER.info("GuardEntityMixin addCustomGoals");

            goalSelector.addGoal(2, new GuardGunCombatGoal(guard));
            // this.goalSelector.addGoal(2, new GuardMeleeCombatGoal(guard, 1.0D, true));
            this.goalSelector.addGoal(1, new GuardReloadingGoal(guard, ModConfigs.GUARD_GUNNER.reloadSettings.reloadThreshold.get()));   
        }
    }

    // @Override
    // public void performRangedAttack(LivingEntity target, float distanceFactor) {
    //     VillagerGuardGunners.LOGGER.info("GuardEntityMixin performRangedAttack");
    //     performRangedAttack(target, distanceFactor);
    // }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void onMobInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!player.isSecondaryUseActive()) {
            if (shouldOpenCustomContainer(player)) { // Implement your condition
                if (player instanceof ServerPlayer serverPlayer) {
                    openCustomContainer(serverPlayer);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    private boolean shouldOpenCustomContainer(Player player) {
        // Add your conditions here
        return true;
    }

    private void openCustomContainer(ServerPlayer player) {
        Guard guard = (Guard) (Object) this;
    
        // Directly access the public fields
        guard.interacting = true;
        Container guardInventory = guard.guardInventory;
    
        // Proceed to open the container
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return guard.getDisplayName();
            }
    
            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player playerEntity) {
                return new GuardGunnerContainer(windowId, playerInventory, guardInventory, guard);
            }
        }, buffer -> {
            buffer.writeInt(guard.getId()); // Send guard's entity ID to client
        });
    }

    public boolean isReloading() {
        return this.reloadingTimeRemaining > 0;
    }

    public void startReloading(int reloadTime) {
        // VillagerGuardGunners.LOGGER.info("GuardEntityMixin startReloading");
        this.reloadingTimeRemaining = reloadTime;
    }

    public int getReloadingTimeRemaining() {
        return this.reloadingTimeRemaining;
    }

    @Override
    public boolean isFacingTarget() {
        if (this.getTarget() != null) {
            return isFacingTarget(this.getTarget());
        } else {
            return false;
        }
    }

    @Override
    public boolean isFacingTarget(LivingEntity target) {
        return GuardGunHelper.isFacingTarget(this, target);
    }

    @Override
    public void faceTarget() {
        if (this.getTarget() != null) {
            faceTarget(this.getTarget());
        }
    }

    @Override
    public void faceTarget(LivingEntity target) {
        this.getLookControl().setLookAt(
            target.getX(), target.getEyeY(), target.getZ(),
            30.0F,
            30.0F
        );

        // Synchronize body rotation with head rotation
        this.yBodyRot = this.getYHeadRot();
        this.setYRot(this.getYHeadRot());
    }

    @Override
    public void faceTarget(float turnSpeed) {
        if (this.getTarget() != null) {
            faceTarget(this.getTarget(), turnSpeed);
        }
    }

    @Override
    public void faceTarget(LivingEntity target, float turnSpeed) {
        this.getLookControl().setLookAt(
            target.getX(), target.getEyeY(), target.getZ(),
            turnSpeed,
            turnSpeed
        );

        // Synchronize body rotation with head rotation
        this.yBodyRot = this.getYHeadRot();
        this.setYRot(this.getYHeadRot());
    }

    @Override
    public double getAngleToTarget() {
        if (this.getTarget() != null) {
            return getAngleToTarget(this.getTarget());
        } else {
            return 0.0;
        }
    }

    @Override
    public double getAngleToTarget(LivingEntity target) {
        Vec3 guardFacingVec = this.getViewVector(1.0F);
        Vec3 vecToTarget = target.position().subtract(this.position()).normalize();
        double angleRadians = Math.acos(guardFacingVec.dot(vecToTarget));
        return Math.toDegrees(angleRadians);
    }

    @Override
    public int getCurrentAmmoCount() {
        return GuardGunHelper.getCurrentAmmoCount(this);
    }

    @Override
    public int getMaxAmmoCount() {
        return GuardGunHelper.getMaxAmmoCount(this);
    }

    public List<BlockPos> getStaticPatrolPoints() {
        return staticPatrolPoints;
    }
    
    public void addStaticPatrolPoint(BlockPos point) {
        staticPatrolPoints.add(point);
    }
  
    public void setPatrolPoints(List<BlockPos> points) {
        staticPatrolPoints = points;
    }
  
    public boolean hasStaticPatrolPoints() {
        return !staticPatrolPoints.isEmpty();
    }

    // @Override
    // public void performAttack() {
    //     if (GuardGunHelper.isGun(this.getMainHandItem()) && !GuardGunHelper.isMagEmpty(this)) {
    //         switch (GuardGunHelper.getFireMode(this.getMainHandItem())) {
    //             case SEMI -> {
    //                 if (this.attackCooldown <= 0) {
    //                     fireSingleShot();
    //                     this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
    //                 }
    //             }
    
    //             case BURST -> handleBurstFiring();
    
    //             case AUTO -> fireFullAuto();
    
    //             default -> {
    //                 if (this.attackCooldown <= 0) {
    //                     fireSingleShot();
    //                     this.attackCooldown = MIN_ATTACK_INTERVAL + random.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
    //                 }
    //             }
    //         }
    //     } else if (GuardGunHelper.isMagEmpty(this)) {
    //         // Handle reloading
    //         if (GuardGunHelper.hasAmmoInInventory(this)) {
    //             // Start reloading
    //             if (this instanceof IGuardReloading reloadingGuard) {
    //                 int reloadTime = GuardGunHelper.getReloadTime((AbstractGunItem) this.getMainHandItem().getItem(), this.getMainHandItem());
    //                 reloadingGuard.startReloading(reloadTime);
    //             }
    //         }
    //     }
    // }
}
