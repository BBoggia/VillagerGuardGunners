package com.gamergaming.villagerguardgunners.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.IThirdPersonAnimation;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.fml.ModList;
import tallestegg.guardvillagers.ModCompat;
import tallestegg.guardvillagers.client.renderer.GuardRenderer;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(GuardRenderer.class)
public class GuardRendererMixin {

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void getArmPose(Guard entityIn, ItemStack itemStackMain, ItemStack itemStackOff, InteractionHand handIn, CallbackInfoReturnable<ArmPose> cir) {
        ArmPose bipedmodel$armpose = ArmPose.EMPTY;
        ItemStack itemstack = handIn == InteractionHand.MAIN_HAND ? itemStackMain : itemStackOff;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            if (!itemstack.isEmpty()) {
                cir.setReturnValue(ArmPose.ITEM);
            } else {
                cir.setReturnValue(ArmPose.EMPTY);
            }
            return;
        }
            

        if (!itemstack.isEmpty()) {
            bipedmodel$armpose = ArmPose.ITEM;
            if (entityIn.getUseItemRemainingTicks() > 0) {
                UseAnim useaction = itemstack.getUseAnimation();
                switch (useaction) {
                    case BLOCK:
                        bipedmodel$armpose = ArmPose.BLOCK;
                        break;
                    case BOW:
                        bipedmodel$armpose = ArmPose.BOW_AND_ARROW;
                        break;
                    case SPEAR:
                        bipedmodel$armpose = ArmPose.THROW_SPEAR;
                        break;
                    case CROSSBOW:
                        if (handIn == entityIn.getUsedItemHand()) {
                            bipedmodel$armpose = ArmPose.CROSSBOW_CHARGE;
                        }
                        break;
                    default:
                        bipedmodel$armpose = ArmPose.EMPTY;
                        break;
                }
                if (ModList.get().isLoaded("musketmod"))
                    bipedmodel$armpose = ModCompat.reloadMusketAnim(itemstack, handIn, entityIn, bipedmodel$armpose);
            } else {
                if (ModList.get().isLoaded("musketmod"))
                    bipedmodel$armpose = ModCompat.holdMusketAnim(itemstack, entityIn, bipedmodel$armpose);
                boolean flag1 = itemStackMain.getItem() instanceof CrossbowItem;
                boolean flag2 = itemStackOff.getItem() instanceof CrossbowItem;
                if (flag1 && entityIn.isAggressive()) {
                    bipedmodel$armpose = ArmPose.CROSSBOW_HOLD;
                }

                if (flag2 && itemStackMain.getItem().getUseAnimation(itemStackMain) == UseAnim.NONE
                        && entityIn.isAggressive()) {
                    bipedmodel$armpose = ArmPose.CROSSBOW_HOLD;
                }
            }

            // Integrate TACZ's animation logic
            // if (itemstack.getItem() instanceof IGun && entityIn instanceof IGunOperator) {
            //     IGun iGun = (IGun) itemstack.getItem();
            //     TimelessAPI.getClientGunIndex(iGun.getGunId(itemstack)).ifPresent(index -> {
            //         if (PlayerAnimatorCompat.hasPlayerAnimator3rd(entityIn, index)) {       // THIS WILL NEVER RUN REOVE AT SOME POINT
            //             VillagerGuardGunners.LOGGER4J.info("!!!!!PLAYER ANIMATOR 3RD: " + index.getThirdPersonAnimation().toString());
            //             PlayerAnimatorCompat.playAnimation(entityIn, index, 1); 
            //         } else {

            //             EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            //             EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(entityIn);

            //             if (!(renderer instanceof LivingEntityRenderer)) {
            //                 return;
            //             }

            //             HumanoidMobRenderer<?, ?> livingRenderer = (HumanoidMobRenderer<?, ?>) renderer;
            //             EntityModel<?> model = livingRenderer.getModel();
            //             HumanoidModel<LivingEntity> humanoidModel;

            //             if (model instanceof HumanoidModel) {
            //                 humanoidModel = (HumanoidModel<LivingEntity>) model;
            //                 if (entityIn instanceof IGunOperator) 
            //                     VillagerGuardGunners.LOGGER4J.info("!!!!!IS GUN OPERATOR!!!!!!  -  " + entityIn.getMainHandItem().getDisplayName().getString());
            //             }  else {
            //                 VillagerGuardGunners.LOGGER4J.info("!!!!!NOOOO  MODEL: " + model.getClass().getName());
            //                 return;
            //             }
                        
            //             ThirdPersonManager.getAnimation(index.getThirdPersonAnimation()).animateGunHold(entityIn.self(), humanoidModel.rightArm, humanoidModel.leftArm, humanoidModel.body, humanoidModel.head);
            //             VillagerGuardGunners.LOGGER4J.info("!!!!!WORKSSSSS ANIMATING: " + index.getThirdPersonAnimation().toString());

            //             // IGun iGun = (IGun) itemstack.getItem();
            //             // TimelessAPI.getClientGunIndex(iGun.getGunId(itemstack)).ifPresent(index -> {
            //             //     IThirdPersonAnimation animation = ThirdPersonManager.getAnimation(index.getThirdPersonAnimation());
            //             //     animation.animateGunHold(entityIn, entityIn.getMainArm(), entityIn.getMainArm().getOpposite(), entityIn.self(), head);
            //             // });
            //         }
            //     });
            //     bipedmodel$armpose = ArmPose.CROSSBOW_HOLD;
            // }
        }
        cir.setReturnValue(bipedmodel$armpose);
    }

    // @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true, remap = false)
    // private void getArmPose(Guard entityIn, ItemStack itemStackMain, ItemStack itemStackOff, InteractionHand handIn, CallbackInfoReturnable<ArmPose> cir) {
    //     ArmPose bipedmodel$armpose = ArmPose.EMPTY;
    //     ItemStack itemstack = handIn == InteractionHand.MAIN_HAND ? itemStackMain : itemStackOff;
    //     if (!itemstack.isEmpty()) {
    //         bipedmodel$armpose = ArmPose.ITEM;
    //         if (entityIn.getUseItemRemainingTicks() > 0) {
    //             UseAnim useaction = itemstack.getUseAnimation();
    //             VillagerGuardGunners.LOGGER4J.info("!!!!!USEACTION: " + useaction.name());
    //             switch (useaction) {
    //                 case BLOCK:
    //                     bipedmodel$armpose = ArmPose.BLOCK;
    //                     break;
    //                 case BOW:
    //                     bipedmodel$armpose = ArmPose.BOW_AND_ARROW;
    //                     break;
    //                 case SPEAR:
    //                     bipedmodel$armpose = ArmPose.THROW_SPEAR;
    //                     break;
    //                 case CROSSBOW:
    //                     if (handIn == entityIn.getUsedItemHand()) {
    //                         bipedmodel$armpose = ArmPose.CROSSBOW_CHARGE;
    //                     }
    //                     break;
    //                 default:
    //                     bipedmodel$armpose = ArmPose.EMPTY;
    //                     break;
    //             }
    //             if (ModList.get().isLoaded("musketmod"))
    //                 bipedmodel$armpose = ModCompat.reloadMusketAnim(itemstack, handIn, entityIn, bipedmodel$armpose);
    //         } else {
    //             if (ModList.get().isLoaded("musketmod"))
    //                 bipedmodel$armpose = ModCompat.holdMusketAnim(itemstack, entityIn, bipedmodel$armpose);
    //             boolean flag1 = itemStackMain.getItem() instanceof CrossbowItem;
    //             boolean flag2 = itemStackOff.getItem() instanceof CrossbowItem;
    //             if (flag1 && entityIn.isAggressive()) {
    //                 bipedmodel$armpose = ArmPose.CROSSBOW_HOLD;
    //             }

    //             if (flag2 && itemStackMain.getItem().getUseAnimation(itemStackMain) == UseAnim.NONE
    //                     && entityIn.isAggressive()) {
    //                 bipedmodel$armpose = ArmPose.CROSSBOW_HOLD;
    //             }
    //         }
    //     }
    //     VillagerGuardGunners.LOGGER4J.info("!!!!!ITEMSTACK: " + entityIn.getName().getString() + "   -   " + bipedmodel$armpose.name());
    //     cir.setReturnValue(bipedmodel$armpose);
    // }
}
