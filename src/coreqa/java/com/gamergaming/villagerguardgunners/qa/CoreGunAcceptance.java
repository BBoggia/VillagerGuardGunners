package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.inventory.ItemCustomData;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** Drives the real guard goals and TaCZ scripts in an installed, packaged server. */
final class CoreGunAcceptance {
    private static final Path EXPECTED = Path.of("core-qa-expected.nbt");
    private static final Case[] CASES = {
        new Case("loaded_semi", "glock_17", FireMode.SEMI, 8, 0, false),
        new Case("empty_magazine_reload", "ak47", FireMode.AUTO, 0, 64, false),
        new Case("ammo_box_reload", "ak47", FireMode.AUTO, 0, 64, false),
        new Case("burst", "m16a4", FireMode.BURST, 12, 0, false),
        new Case("dry", "ak47", FireMode.AUTO, 0, 0, false),
        new Case("canceled_fire", "ak47", FireMode.AUTO, 12, 0, true)
    };
    private record Case(String name, String gun, FireMode mode, int loaded, int reserve, boolean cancel) {}
    private ServerLevel level;
    private BlockPos origin;
    private Guard guard;
    private Husk target;
    private int index = -1, ticks, bullets, initialTotal;
    private boolean aimed, reloaded, done, restoring;
    private CompoundTag expected;

    CoreGunAcceptance() {
        if ("worldgen".equals(System.getProperty("vgg.qa.suite"))) {
            new WorldgenAcceptance();
            return;
        }
        if ("workflow".equals(System.getProperty("vgg.qa.suite"))) {
            new SupplyMedicalAcceptance();
            return;
        }
        if ("behavior".equals(System.getProperty("vgg.qa.suite"))) {
            new CombatBehaviorAcceptance();
            return;
        }
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::joined);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::fire);
    }
    private void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            if (Files.exists(EXPECTED)) {
                expected = NbtIo.readCompressed(EXPECTED, NbtAccounter.create(1_048_576));
                origin = BlockPos.of(expected.getLong("Origin"));
                restoring = true;
                forceChunks();
                return;
            }
            origin = new BlockPos(0, 160, 0);
            forceChunks();
            for (int x = -4; x <= 20; x++) for (int z = -5; z <= 5; z++) {
                level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 5; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            CorePersistenceCases.run(level);
            next();
        } catch (Exception failure) { fail(failure); }
    }
    private void forceChunks() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
    }
    private void joined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityKineticBullet bullet && bullet.getOwner() == guard) bullets++;
    }
    private void fire(GunFireEvent event) {
        if (!done && !restoring && index >= 0 && index < CASES.length && CASES[index].cancel && event.getShooter() == guard)
            event.setCanceled(true);
    }
    private void tick(ServerTickEvent.Post event) {
        if (level == null || done) return;
        try {
            ticks++;
            if (restoring) {
                var loaded = level.getEntity(expected.getUUID("Guard"));
                if (loaded instanceof Guard restored) {
                    guard = restored;
                    checkPersistence();
                    done = true;
                    LogUtils.getLogger().info("VGG_CORE_QA_RESTART_PASS uuid={} ammo={}", guard.getUUID(), total());
                } else require(ticks < 400, "Saved guard did not load after a real server restart");
                return;
            }
            var operator = IGunOperator.fromLivingEntity(guard);
            aimed |= operator.getSynIsAiming() || operator.getDataHolder().isAiming;
            reloaded |= operator.getSynReloadState().getStateType().isReloading();
            if (ticks % 100 == 0) LogUtils.getLogger().info("VGG_CORE_QA_PROGRESS case={} tick={} bullets={} total={} aim={} reload={}",
                    CASES[index].name, ticks, bullets, total(), aimed, reloaded);
            if (ticks < 300) return;
            Case test = CASES[index];
            if (test.loaded + test.reserve > 0 && !test.cancel) {
                require(aimed, test.name + ": guard never aimed");
                require(bullets > 0, test.name + ": native gun produced no projectile");
                require(target.getHealth() < target.getMaxHealth(), test.name + ": bullets never damaged the target");
            } else require(bullets == 0, test.name + ": unexpected projectile");
            if (test.reserve > 0) require(reloaded, "Empty gun never entered native reload state");
            // All fixtures fire one bullet per round. Count the chamber independently.
            require(total() + bullets == initialTotal, test.name + ": ammo was lost or duplicated: " + total() + "+" + bullets + " != " + initialTotal);
            if (test.name.equals("ammo_box_reload")) {
                // Normal maintenance ejects an empty reusable box beside the guard.
                var reserves = GuardAmmoAccess.getAmmoHandler(guard);
                java.util.List<ItemStack> boxes = new java.util.ArrayList<>();
                for (int slot = 0; slot < reserves.getSlots(); slot++) {
                    var stack = reserves.getStackInSlot(slot);
                    if (stack.getItem() instanceof com.tacz.guns.api.item.IAmmoBox) boxes.add(stack);
                }
                for (var dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        guard.getBoundingBox().inflate(4))) {
                    if (dropped.getItem().getItem() instanceof com.tacz.guns.api.item.IAmmoBox) boxes.add(dropped.getItem());
                }
                require(boxes.size() == 1 && boxes.getFirst().getCount() == 1, "Empty reusable ammo box was lost or duplicated");
                var box = boxes.getFirst(); var api = (com.tacz.guns.api.item.IAmmoBox) box.getItem();
                require(api.getAmmoCount(box) == 0, "Depleted box retained ammo");
            }
            roundTrip();
            LogUtils.getLogger().info("VGG_CORE_QA_CASE_PASS case={} bullets={} remaining={} aimed={} reload={}", test.name, bullets, total(), aimed, reloaded);
            next();
        } catch (Exception failure) { fail(failure); }
    }
    private void next() throws Exception {
        if (guard != null) guard.discard();
        if (target != null) target.discard();
        index++; ticks = bullets = 0; aimed = reloaded = false;
        if (index == CASES.length) {
            guard = newGuard("ak47", FireMode.AUTO, 7, 51);
            guard.setNoAi(true);
            var persistedBox = new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
            var boxApi = (com.tacz.guns.api.item.IAmmoBox) persistedBox.getItem();
            boxApi.setAmmoId(persistedBox, ResourceLocation.parse("tacz:762x39"));
            boxApi.setAmmoCount(persistedBox, 23);
            GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(1, persistedBox);
            ItemCustomData.update(guard.getMainHandItem(), tag -> tag.putString("vgg_qa", "persistent-component"));
            expected = new CompoundTag();
            expected.putUUID("Guard", guard.getUUID()); expected.putLong("Origin", origin.asLong());
            expected.put("Gun", guard.getMainHandItem().save(level.registryAccess()));
            expected.put("Ammo", GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess()));
            NbtIo.writeCompressed(expected, EXPECTED);
            done = true;
            LogUtils.getLogger().info("VGG_CORE_QA_READY_TO_RESTART uuid={}", guard.getUUID());
            return;
        }
        Case test = CASES[index];
        guard = newGuard(test.gun, test.mode, test.loaded, test.reserve);
        if (test.name.equals("ammo_box_reload")) {
            var box = new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
            var api = (com.tacz.guns.api.item.IAmmoBox) box.getItem();
            api.setAmmoId(box, ResourceLocation.parse("tacz:762x39"));
            api.setAmmoCount(box, test.reserve);
            GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0, box);
        }
        target = new Husk(EntityType.HUSK, level);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        target.setHealth(1024); target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(origin.getX() + 12.5, origin.getY(), origin.getZ() + .5, 90, 0);
        require(level.addFreshEntity(target), "Target spawn rejected");
        guard.setTarget(target);
        initialTotal = total();
    }
    private Guard newGuard(String gunId, FireMode mode, int loaded, int reserve) {
        Guard result = GuardEntityType.GUARD.get().create(level);
        require(result != null, "Guard creation failed");
        result.setPersistenceRequired();
        result.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, -90, 0);
        var id = ResourceLocation.fromNamespaceAndPath("tacz", gunId);
        ItemStack weapon = GunItemBuilder.create().setId(id).setFireMode(mode).setCount(1)
                .setAmmoCount(loaded).setAmmoInBarrel(loaded > 0).build(level.registryAccess());
        require(!weapon.isEmpty(), "Gun build failed: " + gunId);
        result.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        if (reserve > 0) {
            var ammoId = TimelessAPI.getCommonGunIndex(id).orElseThrow().getGunData().getAmmoId();
            GuardAmmoAccess.getAmmoHandler(result).setStackInSlot(0, AmmoItemBuilder.create().setId(ammoId).setCount(reserve).build());
        }
        GuardOrdersAccess.setOrders(result, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
        require(level.addFreshEntity(result), "Guard spawn rejected");
        require(GuardAmmoAccess.getTaCZInventoryHandler(result).getSlots() == 9, "TaCZ does not see equipment plus reserve slots");
        return result;
    }
    private int total() {
        ItemStack stack = guard.getMainHandItem();
        require(stack.getItem() instanceof IGun, "Guard lost its gun");
        IGun gun = (IGun) stack.getItem();
        int total = gun.getCurrentAmmoCount(stack) + (gun.hasBulletInBarrel(stack) ? 1 : 0);
        var inventory = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var reserve = inventory.getStackInSlot(slot);
            total += reserve.getItem() instanceof com.tacz.guns.api.item.IAmmoBox box ? box.getAmmoCount(reserve) : reserve.getCount();
        }
        return total;
    }
    private void roundTrip() {
        CompoundTag saved = guard.saveWithoutId(new CompoundTag());
        Guard copy = GuardEntityType.GUARD.get().create(level); copy.load(saved);
        require(ItemStack.matches(guard.getMainHandItem(), copy.getMainHandItem()), "Gun components changed on entity round trip");
        require(GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess()).equals(
                GuardAmmoAccess.getAmmoHandler(copy).serializeNBT(level.registryAccess())), "Reserve ammo changed on entity round trip");
    }
    private void checkPersistence() {
        require(ItemStack.matches(guard.getMainHandItem(), ItemStack.parseOptional(level.registryAccess(), expected.getCompound("Gun"))), "Gun lost components across server restart");
        require(GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess()).equals(expected.getCompound("Ammo")), "Reserve ammo changed across server restart");
    }
    private void fail(Exception failure) {
        done = true;
        LogUtils.getLogger().error("VGG_CORE_QA_FAIL case={} tick={}", index, ticks, failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
