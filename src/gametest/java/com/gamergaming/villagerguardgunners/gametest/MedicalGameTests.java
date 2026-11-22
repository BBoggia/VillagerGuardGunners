package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.medical.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class MedicalGameTests {
    private static final String TEMPLATE = "villagerguardgunners/gametest_arena";
    private record Fixture(Guard guard, FakePlayer player, MedicalStorage pouch) {}
    private static Fixture fixture(GameTestHelper h) {
        Guard guard = GuardEntityType.GUARD.get().create(h.getLevel());
        BlockPos pos = h.absolutePos(new BlockPos(8, 2, 8));
        guard.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        guard.setNoAi(true); guard.setHealth(8);
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "MedicalTest"));
        player.setPos(guard.position().add(1, 0, 0));
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1000));
        guard.setOwnerId(player.getUUID());
        return new Fixture(guard, player, GuardMedicalAccess.get(guard));
    }
    private static ItemStack bandages(int count) { return new ItemStack(ModItems.BANDAGE.get(), count); }
    private static void supply(Fixture f, int count) { f.pouch.inventory().setStackInSlot(0, bandages(count)); }
    private static void result(GameTestHelper h, Fixture f, MedicalTreatment.Result expected) {
        var actual = MedicalTreatment.fromPouch(f.player, f.guard);
        h.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }
    private static void healListener(Consumer<LivingHealEvent> listener, Runnable action) {
        MinecraftForge.EVENT_BUS.addListener(listener);
        try { action.run(); } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void oneBandageHealsOnceAndCooldownPreventsRepeat(GameTestHelper h) {
        var f = fixture(h); supply(f, 3);
        result(h, f, MedicalTreatment.Result.HEALED);
        h.assertTrue(f.guard.getHealth() == 12 && f.pouch.inventory().getStackInSlot(0).getCount() == 2, "Wrong dose or healing");
        result(h, f, MedicalTreatment.Result.COOLDOWN);
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).getCount() == 2, "Cooldown consumed medicine"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void fullHealthAndPartialHealingConserveDoses(GameTestHelper h) {
        var f = fixture(h); supply(f, 3); f.guard.setHealth(f.guard.getMaxHealth());
        result(h, f, MedicalTreatment.Result.FULL_HEALTH);
        h.assertTrue(f.pouch.readyAt() == 0 && f.pouch.inventory().getStackInSlot(0).getCount() == 3, "Full health cost medicine");
        f.guard.setHealth(f.guard.getMaxHealth() - 1); result(h, f, MedicalTreatment.Result.HEALED);
        h.assertTrue(f.guard.getHealth() == f.guard.getMaxHealth() && f.pouch.inventory().getStackInSlot(0).getCount() == 2, "Partial treatment failed"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void canceledAndNonFiniteHealingCostNothing(GameTestHelper h) {
        var f = fixture(h); supply(f, 3);
        healListener(e -> { if (e.getEntity() == f.guard) e.setCanceled(true); }, () -> result(h, f, MedicalTreatment.Result.CANCELED));
        for (float amount : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            healListener(e -> { if (e.getEntity() == f.guard) e.setAmount(amount); }, () -> result(h, f, MedicalTreatment.Result.CANCELED));
        }
        h.assertTrue(f.guard.getHealth() == 8 && f.pouch.readyAt() == 0 && f.pouch.inventory().getStackInSlot(0).getCount() == 3, "Rejected healing mutated state"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void eventModifiedHealingUsesOneDoseAndOneEvent(GameTestHelper h) {
        var f = fixture(h); supply(f, 3); int[] events = {0};
        healListener(e -> { if (e.getEntity() == f.guard) { events[0]++; e.setAmount(2); } }, () -> result(h, f, MedicalTreatment.Result.HEALED));
        h.assertTrue(events[0] == 1 && f.guard.getHealth() == 10 && f.pouch.inventory().getStackInSlot(0).getCount() == 2, "Forge healing boundary mismatch"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void independentCallbackHealingDoesNotChargeOurDose(GameTestHelper h) {
        var f = fixture(h); supply(f, 3);
        healListener(e -> { if (e.getEntity() == f.guard) f.guard.setHealth(f.guard.getMaxHealth()); }, () -> result(h, f, MedicalTreatment.Result.FULL_HEALTH));
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).getCount() == 3 && f.pouch.readyAt() == 0, "Unrelated healing consumed medicine"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void reentryCannotChargeSamePlayerOrPatientTwice(GameTestHelper h) {
        var f = fixture(h); var other = fixture(h); supply(f, 3); supply(other, 3);
        healListener(e -> { if (e.getEntity() == f.guard) {
            result(h, f, MedicalTreatment.Result.BUSY);
            other.guard.setOwnerId(f.player.getUUID());
            h.assertTrue(MedicalTreatment.fromPouch(f.player, other.guard) == MedicalTreatment.Result.BUSY, "Reentrant cross-patient use succeeded");
        } }, () -> result(h, f, MedicalTreatment.Result.HEALED));
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).getCount() == 2 && other.pouch.inventory().getStackInSlot(0).getCount() == 3, "Reentry charged extra dose"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void sourceMutationInsideEventAbortsTreatment(GameTestHelper h) {
        var f = fixture(h); supply(f, 3);
        healListener(e -> { if (e.getEntity() == f.guard) f.pouch.inventory().setStackInSlot(0, bandages(7)); }, () -> result(h, f, MedicalTreatment.Result.CANCELED));
        h.assertTrue(f.guard.getHealth() == 8 && f.pouch.inventory().getStackInSlot(0).getCount() == 7, "Treatment overwrote event mutation"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void removalInsideEventDoesNotHealOrCharge(GameTestHelper h) {
        var f = fixture(h); supply(f, 3);
        healListener(e -> { if (e.getEntity() == f.guard) f.guard.discard(); }, () -> result(h, f, MedicalTreatment.Result.DENIED));
        h.assertTrue(f.guard.getHealth() == 8 && f.pouch.inventory().getStackInSlot(0).getCount() == 3, "Removed patient was treated"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void potionBottleUsesFreedSlotEvenWhenInventoriesAreFull(GameTestHelper h) {
        var f = fixture(h); for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        f.pouch.inventory().setStackInSlot(0, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING));
        f.pouch.inventory().setStackInSlot(1, bandages(64)); f.pouch.inventory().setStackInSlot(2, bandages(64));
        result(h, f, MedicalTreatment.Result.HEALED);
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).is(Items.GLASS_BOTTLE), "Missing pouch bottle");
        f.pouch.readyAt(0); f.guard.setHealth(8);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRONG_HEALING));
        h.assertTrue(MedicalTreatment.fromHand(f.player, f.guard, InteractionHand.MAIN_HAND) == MedicalTreatment.Result.HEALED, "Hand potion failed");
        h.assertTrue(f.player.getMainHandItem().is(Items.GLASS_BOTTLE) && f.guard.getHealth() == 16, "Hand bottle or stronger healing lost"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void unsupportedPotionVariantsAreNeverTreatment(GameTestHelper h) {
        for (var item : new net.minecraft.world.item.Item[]{Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
            var stack = PotionUtils.setPotion(new ItemStack(item), Potions.POISON);
            h.assertTrue(TreatmentProfiles.resolve(stack) == null, "Poison accepted");
            PotionUtils.setPotion(stack, Potions.HEALING);
            if (item != Items.POTION) h.assertTrue(TreatmentProfiles.resolve(stack) == null, "Thrown potion accepted");
            stack.getOrCreateTag().put("CustomPotionEffects", new net.minecraft.nbt.ListTag());
            h.assertTrue(TreatmentProfiles.resolve(stack) == null, "Custom potion accepted");
        } h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void sparseSuppliesAndCooldownSurviveEntitySaveLoad(GameTestHelper h) {
        var f = fixture(h); f.pouch.inventory().setStackInSlot(2, bandages(23)); f.pouch.readyAt(12345);
        CompoundTag saved = new CompoundTag(); f.guard.saveWithoutId(saved);
        var loaded = fixture(h); loaded.guard.load(saved);
        h.assertTrue(loaded.pouch.save().equals(f.pouch.save()), "Medical state changed across guard NBT round trip");
        h.assertTrue(loaded.pouch.inventory().getStackInSlot(0).isEmpty() && loaded.pouch.inventory().getStackInSlot(2).getCount() == 23, "Sparse slots shifted"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void unknownAndMalformedStorageRoundTripVerbatim(GameTestHelper h) {
        var f = fixture(h); supply(f, 3); var original = f.pouch.save();
        var future = original.copy(); future.getCompound(MedicalStorage.KEY).putInt("Version", 99);
        f.pouch.load(future); h.assertTrue(!f.pouch.readable() && future.equals(f.pouch.save()), "Future payload was changed");
        var invalid = original.copy(); invalid.getCompound(MedicalStorage.KEY).getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND).getCompound(0).putInt("Slot", 9);
        f.pouch.load(invalid); h.assertTrue(!f.pouch.readable() && invalid.equals(f.pouch.save()), "Invalid slot was lost"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void dedicatedCapabilityCopiesSimulatesAndRevokes(GameTestHelper h) {
        var f = fixture(h); var optional = f.guard.getCapability(MedicalCapabilities.SUPPLIES);
        var cap = optional.orElseThrow(AssertionError::new); ItemStack input = bandages(20);
        h.assertTrue(cap.insertItem(0, input, true).isEmpty() && f.pouch.empty(), "Simulation changed pouch");
        h.assertTrue(cap.insertItem(0, input, false).isEmpty(), "Capability rejected medical supplies"); input.setCount(1); cap.getStackInSlot(0).setCount(2);
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).getCount() == 20 && cap.extractItem(0, 20, false).isEmpty(), "Capability exposed or extracted contents");
        h.assertTrue(f.guard.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new).getSlots() == 9, "Medical slots leaked into TaCZ capability");
        f.guard.invalidateCaps(); f.guard.reviveCaps();
        h.assertTrue(!optional.isPresent() && cap.insertItem(1, bandages(5), false).getCount() == 5, "Old capability revived");
        h.assertTrue(f.guard.getCapability(MedicalCapabilities.SUPPLIES).orElseThrow(AssertionError::new).insertItem(1, bandages(5), false).isEmpty(), "New capability unavailable"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void packingNeedsSpaceAndCannotResetCooldown(GameTestHelper h) {
        var f = fixture(h); supply(f, 20); f.pouch.readyAt(h.getLevel().getGameTime() + 100);
        for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var before = f.pouch.save();
        h.assertTrue(MedicalTreatment.pack(f.player, f.guard) == MedicalTreatment.Result.FULL_INVENTORY && before.equals(f.pouch.save()), "Full inventory lost pouch");
        f.player.getInventory().setItem(5, ItemStack.EMPTY);
        h.assertTrue(MedicalTreatment.pack(f.player, f.guard) == MedicalTreatment.Result.PACKED, "Packing failed");
        var packed = f.player.getInventory().getItem(5).copy();
        h.assertTrue(f.pouch.empty() && f.pouch.restore(packed), "Restore failed"); result(h, f, MedicalTreatment.Result.COOLDOWN);
        h.assertTrue(!f.pouch.restore(packed) && f.pouch.inventory().getStackInSlot(0).getCount() == 20, "Nonempty restore duplicated stock"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void unreadablePouchCanBePackedWithoutDeletingPayload(GameTestHelper h) {
        var f = fixture(h); supply(f, 20); var future = f.pouch.save(); future.getCompound(MedicalStorage.KEY).putString("FutureField", "preserve"); f.pouch.load(future);
        h.assertTrue(MedicalTreatment.pack(f.player, f.guard) == MedicalTreatment.Result.PACKED, "Could not recover unreadable pouch");
        var packed = f.player.getInventory().getItem(0); h.assertTrue(future.equals(packed.getTag()), "Packing changed unknown data");
        h.assertTrue(f.pouch.restore(packed) && !f.pouch.readable() && future.equals(f.pouch.save()), "Restoring changed unknown data"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void medicalMenuQuickMoveAndStaleClicksConserveItems(GameTestHelper h) {
        var f = fixture(h); var menu = new GuardGunnerContainer(1, f.player.getInventory(), f.guard.guardInventory, GuardAmmoAccess.getAmmoHandler(f.guard), f.guard);
        f.player.getInventory().setItem(9, bandages(21));
        menu.quickMoveStack(f.player, GuardGunnerContainer.ADDON_SLOT_COUNT);
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).getCount() == 21 && f.player.getInventory().getItem(9).isEmpty(), "Shift stock routed incorrectly");
        f.player.setPos(f.player.position().add(100, 0, 0));
        menu.clicked(GuardGunnerContainer.MEDICAL_SLOT_START, 0, ClickType.PICKUP, f.player);
        menu.quickMoveStack(f.player, GuardGunnerContainer.MEDICAL_SLOT_START);
        h.assertTrue(f.pouch.inventory().getStackInSlot(0).getCount() == 21 && menu.getCarried().isEmpty(), "Stale menu withdrew supplies"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void canceledRecoverySpawnSurvivesSaveAndRetriesExactlyOnce(GameTestHelper h) {
        var f = fixture(h); supply(f, 17); var queue = new MedicalRecovery(); UUID id = UUID.randomUUID();
        queue.enqueue(id, f.guard.blockPosition(), f.pouch.packed());
        Consumer<EntityJoinLevelEvent> cancel = e -> { if (e.getEntity().getUUID().equals(id)) e.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(cancel);
        try { queue.retry(h.getLevel()); h.assertTrue(queue.pendingCount() == 1 && h.getLevel().getEntity(id) == null, "Canceled spawn removed saved supplies"); }
        finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        var restored = MedicalRecovery.load(queue.save(new CompoundTag())); restored.retry(h.getLevel());
        h.assertTrue(restored.pendingCount() == 0 && h.getLevel().getEntity(id) instanceof ItemEntity, "Recovery did not retry");
        var item = (ItemEntity) h.getLevel().getEntity(id); var contents = new MedicalStorage(f.guard); contents.load(item.getItem().getTag());
        h.assertTrue(contents.inventory().getStackInSlot(0).getCount() == 17, "Recovered stock changed");
        queue.retry(h.getLevel()); h.assertTrue(queue.pendingCount() == 0, "Existing recovery item was duplicated"); item.discard(); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void releaseIsIdempotentAndSavedGuardCannotDropTwice(GameTestHelper h) {
        var f = fixture(h); supply(f, 17); var queue = MedicalRecovery.get(h.getLevel()); int before = queue.pendingCount();
        f.pouch.release(); f.pouch.release();
        h.assertTrue(queue.pendingCount() == before + 1 && f.pouch.released(), "Release duplicated recovery");
        var restored = fixture(h); restored.pouch.load(f.pouch.save()); restored.pouch.release();
        h.assertTrue(queue.pendingCount() == before + 1, "Restart duplicated recovery"); h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void actualDeathRecoversSuppliesWithMobLootDisabled(GameTestHelper h) {
        var f = fixture(h); supply(f, 13); h.getLevel().addFreshEntity(f.guard);
        var rule = h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT); boolean old = rule.get();
        var queue = MedicalRecovery.get(h.getLevel()); int before = queue.pendingCount();
        try {
            rule.set(false, h.getLevel().getServer());
            f.guard.hurt(h.getLevel().damageSources().fellOutOfWorld(), 1000);
            h.assertTrue(f.pouch.released() && queue.pendingCount() == before + 1, "Actual death lost medical stock when mob loot disabled");
        } finally { rule.set(old, h.getLevel().getServer()); f.guard.discard(); }
        h.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void lightningConversionPreservesPouchOnce(GameTestHelper h) {
        var f = fixture(h); supply(f, 13); h.getLevel().addFreshEntity(f.guard);
        var queue = MedicalRecovery.get(h.getLevel()); int before = queue.pendingCount();
        var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(h.getLevel());
        f.guard.thunderHit(h.getLevel(), bolt);
        h.assertTrue(f.guard.isRemoved() && f.pouch.released() && queue.pendingCount() == before + 1, "Lightning conversion lost supplies");
        f.pouch.release(); h.assertTrue(queue.pendingCount() == before + 1, "Conversion released twice"); h.succeed();
    }

}
