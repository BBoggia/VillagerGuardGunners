package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoMaintenance;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.ArmoryItemHandler;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.menu.ArmoryMenu;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Exercises item ownership and menu persistence with the actual Forge handlers. */
@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class InventoryPersistenceGameTests {
    private static final String TEMPLATE = "villagerguardgunners/gametest_arena";
    private static final ResourceLocation AMMO = ResourceLocation.fromNamespaceAndPath("tacz", "9mm");

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void sparseArmoryInventoriesRetainTheirSlotsAcrossNativeSaveLoad(GameTestHelper helper) {
        ArmoryItemHandler before = new ArmoryItemHandler(() -> {});
        before.setStackInSlot(2, ammo(7));
        before.setStackInSlot(8, ammo(17));
        before.setStackInSlot(5, emptyBox());
        var saved = before.serializeNBT();
        helper.assertTrue(saved.getList("Items", Tag.TAG_COMPOUND).getCompound(0)
                .contains("Slot", Tag.TAG_INT), "Expected the native integer slot format");
        int[] changes = {0};
        ArmoryItemHandler after = new ArmoryItemHandler(() -> changes[0]++);
        helper.assertTrue(after.loadSafely(saved).isEmpty(), "Valid saved items were rejected");
        assertSameInventory(helper, before, after);
        helper.assertTrue(changes[0] == 0, "Loading emitted inventory-change notifications");

        // Retain compatibility with byte-valued older/custom saves.
        for (var tag : saved.getList("Items", Tag.TAG_COMPOUND)) {
            var item = (net.minecraft.nbt.CompoundTag) tag;
            item.putByte("Slot", (byte) item.getInt("Slot"));
        }
        helper.assertTrue(after.loadSafely(saved).isEmpty(), "Legacy slots were rejected");
        assertSameInventory(helper, before, after);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void partialArmoryWithdrawalMarksDirtyAndUpdatesComparator(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(position, ModBlocks.ARMORY.get().defaultBlockState(), 3);
        ArmoryBlockEntity armory = (ArmoryBlockEntity) level.getBlockEntity(position);
        ItemStack stack = ammo(1);
        int maximum = stack.getMaxStackSize();
        int initialCount = Math.min(armory.getInventory().getSlotLimit(0), maximum);
        stack.setCount(initialCount);
        BlockPos comparatorPos = position.east();
        level.setBlock(comparatorPos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState()
                .setValue(ComparatorBlock.FACING, Direction.WEST), 3);
        armory.getInventory().setStackInSlot(0, stack);
        helper.runAfterDelay(4, () -> {
            var comparator = (ComparatorBlockEntity) level.getBlockEntity(comparatorPos);
            helper.assertTrue(comparator.getOutputSignal() == armory.getInventory().comparatorOutput(),
                    "Comparator fixture did not read the full stack");
            int initialSignal = comparator.getOutputSignal();
            var player = helper.makeMockPlayer();
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            ItemStack playerAmmo = ammo(maximum - (initialCount - 1));
            player.getInventory().setItem(0, playerAmmo);
            var menu = new ArmoryMenu(1, player.getInventory(), armory);
            var chunk = level.getChunkAt(position);
            chunk.setUnsaved(false);
            menu.quickMoveStack(player, 0);
            helper.assertTrue(armory.getInventory().getStackInSlot(0).getCount() == 1
                            && player.getInventory().getItem(0).getCount() == maximum,
                    "Partial withdrawal did not conserve ammo");
            helper.assertTrue(chunk.isUnsaved(), "Partial withdrawal was not marked for saving");
            ArmoryBlockEntity restored = new ArmoryBlockEntity(position, armory.getBlockState());
            restored.load(armory.saveWithoutMetadata());
            helper.assertTrue(restored.getInventory().getStackInSlot(0).getCount() == 1,
                    "The remaining stack did not persist");
            helper.runAfterDelay(4, () -> {
                helper.assertTrue(comparator.getOutputSignal() < initialSignal
                                && comparator.getOutputSignal() == armory.getInventory().comparatorOutput(),
                        "Partial withdrawal did not update the comparator output");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void mergedArmoryDepositPersistsAndDoesNotConsumeExtraAmmo(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(position, ModBlocks.ARMORY.get().defaultBlockState(), 3);
        ArmoryBlockEntity armory = (ArmoryBlockEntity) level.getBlockEntity(position);
        armory.getInventory().setStackInSlot(8, ammo(17));
        var player = helper.makeMockPlayer();
        player.getInventory().setItem(0, ammo(11));
        var menu = new ArmoryMenu(1, player.getInventory(), armory);
        var chunk = level.getChunkAt(position);
        chunk.setUnsaved(false);
        menu.quickMoveStack(player, 36);
        helper.assertTrue(chunk.isUnsaved(), "Merged deposit was not marked for saving");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty()
                        && armory.getInventory().getStackInSlot(8).getCount() == 28,
                "Merged deposit did not conserve ammo in its existing slot");
        ArmoryBlockEntity restored = new ArmoryBlockEntity(position, armory.getBlockState());
        restored.load(armory.saveWithoutMetadata());
        assertSameInventory(helper, armory.getInventory(), restored.getInventory());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void canceledEmptyBoxDropRestoresTheExactItem(GameTestHelper helper) {
        Guard guard = guard(helper);
        var inventory = GuardAmmoAccess.getAmmoHandler(guard);
        ItemStack original = emptyBox();
        original.getOrCreateTag().putString("OwnershipFixture", "preserve-me");
        inventory.setStackInSlot(0, original.copy());
        int[] canceled = {0};
        Consumer<EntityJoinLevelEvent> cancelDrop = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                    && item.getItem().getItem() instanceof IAmmoBox) {
                canceled[0]++;
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(cancelDrop);
        try {
            helper.assertTrue(GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard) == 0
                            && canceled[0] == 1
                            && ItemStack.matches(original, inventory.getStackInSlot(0)),
                    "A canceled spawn consumed or changed the empty box");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelDrop);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void capturedAndSuccessfulBoxDropsTransferOwnershipOnce(GameTestHelper helper) {
        Guard guard = guard(helper);
        var inventory = GuardAmmoAccess.getAmmoHandler(guard);
        var captured = new ArrayList<ItemEntity>();
        var previous = guard.captureDrops(captured);
        try {
            inventory.setStackInSlot(0, emptyBox());
            helper.assertTrue(GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard) == 1
                            && captured.size() == 1 && inventory.getStackInSlot(0).isEmpty(),
                    "Drop capture did not receive ownership exactly once");
            helper.assertTrue(GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard) == 0
                            && captured.size() == 1,
                    "A captured box was duplicated");
        } finally {
            guard.captureDrops(previous);
        }
        inventory.setStackInSlot(0, emptyBox());
        var spawned = new ArrayList<ItemEntity>();
        Consumer<EntityJoinLevelEvent> observe = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                    && item.getItem().getItem() instanceof IAmmoBox) {
                spawned.add(item);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(observe);
        try {
            helper.assertTrue(GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard) == 1
                            && inventory.getStackInSlot(0).isEmpty()
                            && spawned.size() == 1 && spawned.get(0).isAddedToWorld(),
                    "A successful spawn did not transfer the box into the world");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(observe);
            spawned.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    private static ItemStack ammo(int count) {
        return AmmoItemBuilder.create().setId(AMMO).setCount(count).build();
    }

    private static ItemStack emptyBox() {
        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
        IAmmoBox box = (IAmmoBox) stack.getItem();
        box.setAmmoId(stack, AMMO);
        box.setAmmoCount(stack, 0);
        return stack;
    }

    private static Guard guard(GameTestHelper helper) {
        Guard guard = GuardEntityType.GUARD.get().create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        guard.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return guard;
    }

    private static void assertSameInventory(GameTestHelper helper, ArmoryItemHandler before,
                                          ArmoryItemHandler after) {
        for (int slot = 0; slot < ArmoryItemHandler.SLOT_COUNT; slot++) {
            helper.assertTrue(ItemStack.matches(before.getStackInSlot(slot), after.getStackInSlot(slot)),
                    "Armory slot " + slot + " changed during save/load");
        }
    }
}
