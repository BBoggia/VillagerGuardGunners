package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.menu.QuartermasterMenu;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlock;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterRecovery;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBinding;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class QuartermasterGameTests {
    private static final String TEMPLATE = "villagerguardgunners/gametest_arena";
    private static QuartermasterBlockEntity station(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(8, 2, 8));
        h.getLevel().setBlock(pos, ModBlocks.QUARTERMASTER.get().defaultBlockState(), 3);
        return (QuartermasterBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static Player player(GameTestHelper h, QuartermasterBlockEntity station) {
        Player p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "QuartermasterTest")) {
            @Override public boolean hasPermissions(int level) { return false; }
        };
        p.setPos(Vec3.atCenterOf(station.getBlockPos()));
        return p;
    }
    private static ItemStack ammo(int count) {
        ItemStack stack = AmmoItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz", "9mm"))
                .setCount(count).build();
        stack.getOrCreateTag().putString("TestOwner", "exact item payload");
        return stack;
    }
    private static void equal(GameTestHelper h, ItemStack a, ItemStack b, String message) {
        h.assertTrue(ItemStack.matches(a, b), message);
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void sparseStockAndReturnsPreserveGunAndAmmoNbt(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        ItemStack gun = GunItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz", "glock_17")).build();
        gun.getOrCreateTag().putString("CustomGunState", "preserve");
        s.inventory().setStackInSlot(4, gun);
        s.inventory().setStackInSlot(26, ammo(17));
        s.inventory().setStackInSlot(35, new ItemStack(Items.IRON_HELMET));
        s.changeSetting(owner, 1);
        var loaded = new QuartermasterBlockEntity(s.getBlockPos(), s.getBlockState());
        loaded.load(s.saveWithoutMetadata()); loaded.setLevel(h.getLevel());
        h.assertTrue(loaded.readable() && loaded.stationId().equals(s.stationId()) && loaded.canManage(owner) && loaded.automationEnabled(), "Settings did not survive load");
        for (int i = 0; i < 36; i++) equal(h, s.inventory().getStackInSlot(i), loaded.inventory().getStackInSlot(i), "Slot " + i + " changed");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void automationRechecksPolicyAndConservesSimulatedTransfers(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        var pipe = s.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(AssertionError::new);
        ItemStack input = ammo(31);
        equal(h, pipe.insertItem(0, input, false), input, "Disabled pipe accepted items");
        s.changeSetting(owner, 1);
        h.assertTrue(pipe.insertItem(0, input, true).isEmpty() && s.inventory().getStackInSlot(0).isEmpty(), "Simulation changed storage");
        h.assertTrue(pipe.insertItem(0, input, false).isEmpty(), "Stock insertion failed");
        pipe.getStackInSlot(0).setCount(1);
        equal(h, s.inventory().getStackInSlot(0), input, "Capability exposed mutable inventory");
        h.assertTrue(pipe.extractItem(0, 31, false).isEmpty(), "Pipe extracted reserved stock");
        equal(h, pipe.insertItem(27, input, false), input, "Pipe inserted into returns");
        s.inventory().setStackInSlot(35, input.copy());
        h.assertTrue(pipe.extractItem(35, 4, true).getCount() == 4 && s.inventory().getStackInSlot(35).getCount() == 31, "Extraction simulation mutated returns");
        h.assertTrue(pipe.extractItem(35, 4, false).getCount() == 4 && s.inventory().getStackInSlot(35).getCount() == 27, "Return extraction lost items");
        s.changeSetting(owner, 1);
        h.assertTrue(pipe.extractItem(35, 4, false).isEmpty(), "Cached pipe ignored revocation");
        s.changeSetting(owner, 1); s.setRemoved();
        h.assertTrue(pipe.extractItem(35, 4, false).isEmpty(), "Removed capability still extracted");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void publicAccessRevocationRejectsOpenMenuClicks(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); var guest = player(h, s); s.placedBy(owner);
        h.assertTrue(!s.canUse(guest), "Private station admitted stranger");
        s.changeSetting(owner, 0); s.changeSetting(owner, 0);
        var menu = new QuartermasterMenu(1, guest.getInventory(), s);
        h.assertTrue(menu.stillValid(guest) && !menu.clickMenuButton(guest, 1), "Guest management boundary failed");
        s.inventory().setStackInSlot(0, ammo(17));
        s.changeSetting(owner, 0);
        menu.clicked(0, 0, ClickType.PICKUP, guest);
        menu.clicked(0, 0, ClickType.SWAP, guest);
        menu.quickMoveStack(guest, 0);
        h.assertTrue(menu.getCarried().isEmpty() && s.inventory().getStackInSlot(0).getCount() == 17
                && !menu.stillValid(guest), "Revoked menu moved items");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void squadAccessUsesCreatorRatherThanLedgerPossession(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); var creator = player(h, s); var stranger = player(h, s);
        s.placedBy(owner);
        UUID squad = UUID.randomUUID();
        var registry = SquadRegistrySavedData.get(h.getLevel().getServer());
        h.assertTrue(registry.register(squad, "Quartermaster test", creator.getUUID(), 0).isPresent(), "Squad fixture failed");
        ItemStack ledger = new ItemStack(ModItems.SQUAD_COMMAND_LEDGER.get());
        SquadLedgerBindingCodec.writeTo(ledger.getOrCreateTag(), SquadLedgerBinding.bound(squad, "Quartermaster test"));
        owner.setItemInHand(InteractionHand.MAIN_HAND, ledger);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ledger.copy());
        h.assertTrue(s.changeSetting(owner, 2), "Squad bind failed");
        s.changeSetting(owner, 0);
        h.assertTrue(s.canUse(creator) && !s.canManage(creator) && !s.canUse(stranger), "Squad access was transferable by ledger");
        s.changeSetting(owner, 2);
        h.assertTrue(!s.canUse(creator), "Unbound squad still authorized creator");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void shiftClickMergesAndFullDestinationsConserveItems(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        var menu = new QuartermasterMenu(1, owner.getInventory(), s);
        s.inventory().setStackInSlot(26, ammo(17)); owner.getInventory().setItem(0, ammo(11));
        var chunk = h.getLevel().getChunkAt(s.getBlockPos()); chunk.setUnsaved(false);
        menu.quickMoveStack(owner, 63);
        equal(h, s.inventory().getStackInSlot(26), ammo(28), "Shift-click deposit changed ammo");
        h.assertTrue(chunk.isUnsaved() && owner.getInventory().getItem(0).isEmpty(), "Merge did not mark persistence");
        for (int i = 0; i < 36; i++) owner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        menu.quickMoveStack(owner, 26);
        equal(h, s.inventory().getStackInSlot(26), ammo(28), "Full destination lost stock");
        int maximum = ammo(1).getMaxStackSize();
        owner.getInventory().setItem(0, ammo(maximum - 4));
        menu.quickMoveStack(owner, 26);
        h.assertTrue(owner.getInventory().getItem(0).getCount() == maximum && s.inventory().getStackInSlot(26).getCount() == 24, "Partial merge lost ammo");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void stationsCannotNestAndReturnsAcceptOrdinaryEquipment(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        var menu = new QuartermasterMenu(1, owner.getInventory(), s);
        ItemStack packed = s.packedStack();
        for (int i = 0; i < 36; i++) h.assertTrue(!menu.getSlot(i).mayPlace(packed), "Nested station accepted in slot " + i);
        h.assertTrue(!ModItems.QUARTERMASTER.get().canFitInsideContainerItems(), "Station can nest inside shulker boxes");
        h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.SHULKER_BOX)), "Station accepted container nesting");
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.setDamageValue(81);
        menu.setCarried(helmet.copy()); menu.clicked(35, 0, ClickType.PICKUP, owner);
        equal(h, s.inventory().getStackInSlot(35), helmet, "Manual return changed armor");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void unsupportedOrDuplicateSlotPayloadStaysPackedAndUnavailable(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner); s.inventory().setStackInSlot(3, ammo(19));
        for (int mode = 0; mode < 4; mode++) {
            CompoundTag saved = s.saveWithoutMetadata();
            CompoundTag data = saved.getCompound(QuartermasterBlockEntity.DATA_TAG);
            if (mode == 0) data.putInt("Version", 500);
            else if (mode == 1) data.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND)
                    .add(data.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND).getCompound(0).copy());
            else if (mode == 2) data.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putInt("Slot", 40);
            else data.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putString("id", "absent:removed_item");
            var restored = new QuartermasterBlockEntity(s.getBlockPos(), s.getBlockState()); restored.load(saved);
            h.assertTrue(!restored.readable() && !restored.canUse(owner), "Invalid payload allowed mutation");
            h.assertTrue(restored.packedStack().getTag().getCompound("BlockEntityTag").get(QuartermasterBlockEntity.DATA_TAG).equals(data), "Invalid payload was not preserved");
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void packedBlockItemPlacementRetainsStockAndOwnership(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); var placer = player(h, s); s.placedBy(owner);
        s.inventory().setStackInSlot(8, ammo(23)); s.inventory().setStackInSlot(29, new ItemStack(Items.GOLDEN_APPLE, 3));
        ItemStack packed = s.packedStack(); UUID oldId = s.dropId();
        BlockPos target = s.getBlockPos().east(3);
        h.getLevel().setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        placer.setItemInHand(InteractionHand.MAIN_HAND, packed.copy());
        var hit = new BlockHitResult(Vec3.atCenterOf(target.below()).add(0, .5, 0), Direction.UP, target.below(), false);
        placer.getMainHandItem().useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(h.getLevel().getBlockEntity(target) instanceof QuartermasterBlockEntity, "Packed BlockItem did not place");
        var restored = (QuartermasterBlockEntity) h.getLevel().getBlockEntity(target);
        equal(h, restored.inventory().getStackInSlot(8), ammo(23), "Packed placement lost ammo");
        h.assertTrue(restored.inventory().getStackInSlot(29).getCount() == 3 && restored.canManage(owner)
                && !restored.canManage(placer) && restored.stationId().equals(s.stationId()) && !restored.dropId().equals(oldId), "Placement changed owner or reused removal identity");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void onlyOwnerOrOperatorCanBreakEvenPublicStation(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); var guest = player(h, s); s.placedBy(owner);
        s.changeSetting(owner, 0); s.changeSetting(owner, 0);
        var denied = new BlockEvent.BreakEvent(h.getLevel(), s.getBlockPos(), s.getBlockState(), guest);
        QuartermasterBlock.onBreak(denied);
        var allowed = new BlockEvent.BreakEvent(h.getLevel(), s.getBlockPos(), s.getBlockState(), owner);
        QuartermasterBlock.onBreak(allowed);
        h.assertTrue(denied.isCanceled() && !allowed.isCanceled(), "Break permission did not match ownership");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void canceledBreakDropSurvivesRetrySaveLoadAndRepeatedRemoval(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner); s.inventory().setStackInSlot(2, ammo(13));
        ItemStack expected = s.packedStack(); UUID id = s.dropId();
        int[] canceled = {0};
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity().getUUID().equals(id)) { canceled[0]++; event.setCanceled(true); }
        };
        var recovery = QuartermasterRecovery.get(h.getLevel()); int initial = recovery.pendingCount();
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            h.getLevel().setBlock(s.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            s.releaseContents();
            h.assertTrue(recovery.pendingCount() == initial + 1, "Repeated removal duplicated pending ownership");
            h.assertTrue(canceled[0] == 1 && h.getLevel().getEntity(id) == null, "Spawn cancellation fixture failed");
            var loaded = QuartermasterRecovery.load(recovery.save(new CompoundTag()));
            loaded.retry(h.getLevel());
            h.assertTrue(canceled[0] == 2, "Saved outbox lost canceled drop");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        recovery.retry(h.getLevel());
        h.assertTrue(h.getLevel().getEntity(id) instanceof ItemEntity, "Canceled drop did not recover");
        equal(h, ((ItemEntity) h.getLevel().getEntity(id)).getItem(), expected, "Recovery changed packed contents");
        h.getLevel().getEntity(id).discard(); recovery.retry(h.getLevel());
        h.assertTrue(h.getLevel().getEntity(id) == null, "Accepted drop was emitted twice");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void retryBudgetAndUnloadedChunksDoNotDiscardPendingStock(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        var queue = new QuartermasterRecovery();
        for (int i = 0; i < 5; i++) queue.enqueue(UUID.randomUUID(), s.getBlockPos().above(), s.packedStack());
        queue.retry(h.getLevel());
        h.assertTrue(queue.pendingCount() == 3, "Retry exceeded two-entry budget");
        var unloaded = new QuartermasterRecovery(); BlockPos far = new BlockPos(25000000, 80, 25000000);
        h.assertTrue(!h.getLevel().hasChunkAt(far), "Unloaded chunk fixture unexpectedly loaded");
        unloaded.enqueue(UUID.randomUUID(), far, s.packedStack()); unloaded.retry(h.getLevel());
        h.assertTrue(unloaded.pendingCount() == 1 && !h.getLevel().hasChunkAt(far), "Retry loaded a chunk or lost items");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void futureOutboxPayloadSurvivesTwoSaveLoadCycles(GameTestHelper h) {
        CompoundTag future = new CompoundTag(); future.putInt("Version", 900); future.putString("FutureStock", "untouched");
        var queue = QuartermasterRecovery.load(future);
        var saved = QuartermasterRecovery.load(queue.save(new CompoundTag())).save(new CompoundTag());
        h.assertTrue(saved.get("Preserved").equals(future), "Future recovery payload was discarded");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void conflictingRemovalIdentitiesPreserveBothPayloads(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        var queue = new QuartermasterRecovery(); UUID id = UUID.randomUUID();
        ItemStack first = s.packedStack();
        s.inventory().setStackInSlot(0, ammo(21)); ItemStack second = s.packedStack();
        queue.enqueue(id, s.getBlockPos(), first);
        queue.enqueue(id, s.getBlockPos(), first);
        queue.enqueue(id, s.getBlockPos().east(), second);
        CompoundTag saved = QuartermasterRecovery.load(queue.save(new CompoundTag())).save(new CompoundTag());
        h.assertTrue(saved.getList("Pending", Tag.TAG_COMPOUND).size() == 1
                && saved.getList("Quarantined", Tag.TAG_COMPOUND).size() == 1, "Conflicting identity lost or duplicated an owner");
        equal(h, ItemStack.of(saved.getList("Quarantined", Tag.TAG_COMPOUND).getCompound(0).getCompound("Stack")), second,
                "Quarantine did not preserve conflicting stock");
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void distanceAndReplacementInvalidateMenusWithoutMovingItems(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner);
        s.inventory().setStackInSlot(0, ammo(9));
        var menu = new QuartermasterMenu(1, owner.getInventory(), s);
        owner.setPos(owner.position().add(20, 0, 0));
        h.assertTrue(!menu.stillValid(owner) && menu.quickMoveStack(owner, 0).isEmpty(), "Distant menu still moved stock");
        owner.setPos(Vec3.atCenterOf(s.getBlockPos()));
        h.getLevel().setBlock(s.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(s.getBlockPos(), ModBlocks.QUARTERMASTER.get().defaultBlockState(), 3);
        h.assertTrue(!menu.stillValid(owner) && menu.quickMoveStack(owner, 0).isEmpty(), "Replaced station reused old menu");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void transportNeverRetainsCallerOwnedStackReferences(GameTestHelper h) {
        var s = station(h); var owner = player(h, s); s.placedBy(owner); s.changeSetting(owner, 1);
        var pipe = s.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
        ItemStack input = ammo(7);
        h.assertTrue(pipe.insertItem(0, input, false).isEmpty() && pipe.insertItem(1, input, false).isEmpty(), "Insertion fixture failed");
        pipe.insertItem(0, ammo(3), false);
        equal(h, input, ammo(7), "Later insertion mutated the caller's stack");
        input.setCount(1); input.getOrCreateTag().putString("TestOwner", "changed by caller");
        equal(h, pipe.getStackInSlot(0), ammo(10), "Caller changed stored contents");
        equal(h, pipe.getStackInSlot(1), ammo(7), "Two slots shared a mutable stack");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void staleStationAccessNeverLoadsAnUnloadedChunk(GameTestHelper h) {
        BlockPos far = new BlockPos(25000000, 80, 25000000);
        var stale = new QuartermasterBlockEntity(far, ModBlocks.QUARTERMASTER.get().defaultBlockState());
        var owner = player(h, stale); stale.placedBy(owner); stale.setLevel(h.getLevel());
        h.assertTrue(!h.getLevel().hasChunkAt(far), "Unloaded access fixture unexpectedly loaded");
        h.assertTrue(!stale.canInteract(owner) && !h.getLevel().hasChunkAt(far), "Stale station menu loaded a chunk");
        h.succeed();
    }

}
