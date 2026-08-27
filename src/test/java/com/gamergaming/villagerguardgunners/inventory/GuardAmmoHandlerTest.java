package com.gamergaming.villagerguardgunners.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

final class GuardAmmoHandlerTest {
    private static final ResourceLocation TEST_AMMO_ID =
            ResourceLocation.fromNamespaceAndPath("villagerguardgunners", "test_ammo");

    @Test
    void hasExactlyThreeDedicatedSlots() {
        assertEquals(3, GuardAmmoHandler.SLOT_COUNT);
    }

    @Test
    void acceptsLooseAmmoAndAmmoBoxesButRejectsUnrelatedItems() {
        assertTrue(GuardAmmoHandler.isAmmoType(TestAmmoItem.class));
        assertTrue(GuardAmmoHandler.isAmmoType(TestAmmoBoxItem.class));
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

    private static final class TestAmmoBoxItem extends Item implements IAmmoBox {
        private TestAmmoBoxItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public ResourceLocation getAmmoId(ItemStack stack) {
            return TEST_AMMO_ID;
        }

        @Override
        public int getAmmoCount(ItemStack stack) {
            return 30;
        }

        @Override
        public void setAmmoId(ItemStack stack, ResourceLocation ammoId) {
        }

        @Override
        public void setAmmoCount(ItemStack stack, int count) {
        }

        @Override
        public boolean isAmmoBoxOfGun(ItemStack gun, ItemStack ammoBox) {
            return true;
        }

        @Override
        public ItemStack setAmmoLevel(ItemStack stack, int level) {
            return stack;
        }

        @Override
        public int getAmmoLevel(ItemStack stack) {
            return 0;
        }

        @Override
        public boolean isCreative(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isAllTypeCreative(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack setCreative(ItemStack stack, boolean creative) {
            return stack;
        }
    }
}
