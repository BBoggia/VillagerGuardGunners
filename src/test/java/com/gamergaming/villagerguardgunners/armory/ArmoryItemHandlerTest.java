package com.gamergaming.villagerguardgunners.armory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.tacz.guns.api.item.IAmmo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

final class ArmoryItemHandlerTest {
    private static final ResourceLocation TEST_AMMO_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "villagerguardgunners", "armory_test_ammo");

    @Test
    void storageHasNineSlotsAndReusesTheReviewedTaCZAmmoTypeBoundary() {
        // ItemStackHandler requires Minecraft's runtime bootstrap, so actual
        // insertion, safe loading, capability access, and comparator behavior
        // are exercised by the live Forge GameTest instead of a partial mock.
        assertEquals(9, ArmoryItemHandler.SLOT_COUNT);
        assertTrue(GuardAmmoHandler.isAmmoType(TestAmmoItem.class));
        assertFalse(GuardAmmoHandler.isAmmoType(Item.class));
    }

    private static final class TestAmmoItem extends Item implements IAmmo {
        private TestAmmoItem() {
            super(new Item.Properties());
        }

        @Override
        public ResourceLocation getAmmoId(ItemStack stack) {
            return TEST_AMMO_ID;
        }

        @Override
        public void setAmmoId(ItemStack stack, ResourceLocation ammoId) {
        }

        @Override
        public boolean isAmmoOfGun(ItemStack gun, ItemStack ammo) {
            return true;
        }
    }
}
