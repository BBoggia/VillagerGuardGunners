package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmTransactions;
import com.gamergaming.villagerguardgunners.sidearm.SidearmActivationReason;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.sidearm.SidearmTransitionStatus;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class EquipmentIntegrationGameTests {
    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void equipmentServiceMustWaitForNativeActionsAndRecoverTheStowedPrimary(GameTestHelper helper) {
        var guard = MedicalIntegrationGameTests.guard(helper);
        ItemStack primary = gun("ak47");
        ItemStack sidearm = gun("glock_17");
        primary.getOrCreateTag().putString("CustomOwnership", "primary-original");
        sidearm.getOrCreateTag().putString("CustomOwnership", "sidearm-original");
        guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        helper.assertTrue(GuardSidearmTransactions.configureNaturalSidearm(guard, sidearm).configured(),
                "Could not establish the two-weapon fixture");
        var controller = new NativeGunController(guard);
        controller.startAiming();
        var nativeData = IGunOperator.fromLivingEntity(guard).getDataHolder();
        nativeData.drawTimestamp = -1L;
        nativeData.shootTimestamp = -1L;
        nativeData.meleePrepTickCount = -1;
        helper.assertTrue(controller.isSidearmSwapSafe(), "Idle native gun did not open the swap gate");
        nativeData.isBolting = true;
        helper.assertTrue(!controller.isSidearmSwapSafe(), "Native bolt operation did not close the swap gate");
        var blocked = GuardSidearmTransactions.activate(guard, SidearmActivationReason.PRIMARY_DRY,
                helper.getLevel().getGameTime(), UUID.randomUUID(), controller.isSidearmSwapSafe());
        helper.assertTrue(blocked.status() == SidearmTransitionStatus.ROLLED_BACK
                        && ItemStack.matches(primary, guard.getMainHandItem()),
                "An interrupted/blocked equipment operation changed the primary");
        nativeData.isBolting = false;
        helper.assertTrue(controller.isSidearmSwapSafe(), "Finished bolt did not reopen the swap gate");
        var active = GuardSidearmTransactions.activate(guard, SidearmActivationReason.PRIMARY_DRY,
                helper.getLevel().getGameTime(), UUID.randomUUID(), controller.isSidearmSwapSafe());
        helper.assertTrue(active.status() == SidearmTransitionStatus.ACTIVATED,
                "Could not establish active-sidearm ownership");
        CompoundTag saved = new CompoundTag();
        guard.saveWithoutId(saved);
        var restored = GuardEntityType.GUARD.get().create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(GuardSidearmAccess.getState(restored) == SidearmState.SIDEARM_ACTIVE
                        && ItemStack.matches(primary, GuardSidearmAccess.getEffectivePrimaryCopy(restored)),
                "Active-sidearm serialization lost the stowed primary");
        helper.assertTrue(!GuardSidearmTransactions.configureNaturalSidearm(restored, gun("glock_17")).configured(),
                "Equipment service replaced an occupied active-sidearm layout");
        helper.assertTrue(GuardSidearmTransactions.returnPrimary(restored, true).status() == SidearmTransitionStatus.RETURNED
                        && ItemStack.matches(primary, restored.getMainHandItem())
                        && ItemStack.matches(sidearm, GuardSidearmAccess.getConfiguredSidearmInventory(restored).getStackInSlot(0)),
                "Recovery did not return the exact original two items");
        System.out.println("VGG_INTEGRATION native bolt blocks swap; active-sidearm save/load preserves both owners");
        guard.discard();
        helper.succeed();
    }

    private static ItemStack gun(String id) {
        return GunItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz", id)).build();
    }
}
