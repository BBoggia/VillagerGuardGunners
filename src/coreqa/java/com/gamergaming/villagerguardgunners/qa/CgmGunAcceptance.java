package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.guns.GuardWeaponControl;
import com.gamergaming.villagerguardgunners.guns.GuardWeaponControlAccess;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Drives the real guard goals with MrCrayfish's Gun Mod guns in an installed,
 * packaged server. Written against item identifiers and the neutral gun seam
 * only, so it compiles without the gun mod.
 */
final class CgmGunAcceptance {
    private static final Path EXPECTED = Path.of("cgm-qa-expected.nbt");
    private static final String PROVIDER = "cgm";
    private static final Case[] CASES = {
        new Case("reload_then_fire", "pistol", "basic_bullet", 32, 1, false, false),
        new Case("automatic_fire", "assault_rifle", "basic_bullet", 64, 1, false, false),
        new Case("shotgun_pellets", "shotgun", "shell", 8, 5, false, false),
        new Case("dry", "assault_rifle", "basic_bullet", 0, 1, false, false),
        new Case("wrong_ammo", "rifle", "basic_bullet", 32, 1, true, false),
        new Case("ally_in_lane", "pistol", "basic_bullet", 32, 1, false, true)
    };
    private record Case(String name, String gun, String ammo, int reserve, int pellets, boolean wrongAmmo, boolean ally) {}

    private ServerLevel level;
    private BlockPos origin;
    private Guard guard;
    private Husk target;
    private Villager ally;
    private int index = -1, ticks, projectiles, initialTotal;
    private boolean reloaded, dry, done, restoring;
    private CompoundTag expected;

    CgmGunAcceptance() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::joined);
    }

    private void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            require(GunProviders.byId(PROVIDER).isPresent(), "The CGM provider was not registered");
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
            next();
        } catch (Exception failure) { fail(failure); }
    }

    private void forceChunks() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
    }

    private void joined(EntityJoinLevelEvent event) {
        if (done || restoring || guard == null || event.getEntity() instanceof ItemEntity) return;
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (PROVIDER.equals(type.getNamespace())) projectiles++;
    }

    private void tick(ServerTickEvent.Post event) {
        if (level == null || done) return;
        try {
            ticks++;
            if (restoring) {
                var loaded = level.getEntity(expected.getUUID("Guard"));
                if (loaded instanceof Guard restored) {
                    guard = restored;
                    require(ItemStack.matches(guard.getMainHandItem(),
                            ItemStack.parseOptional(level.registryAccess(), expected.getCompound("Gun"))),
                            "Gun changed across server restart");
                    require(GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess())
                            .equals(expected.getCompound("Ammo")), "Reserve ammo changed across server restart");
                    require(GunProviders.find(guard.getMainHandItem()).map(p -> p.id().equals(PROVIDER)).orElse(false),
                            "Restored gun is not owned by the CGM provider");
                    done = true;
                    LogUtils.getLogger().info("VGG_CGM_QA_RESTART_PASS uuid={} ammo={}", guard.getUUID(), total());
                } else require(ticks < 400, "Saved guard did not load after a real server restart");
                return;
            }
            GuardWeaponControl control = GuardWeaponControlAccess.control(guard).orElse(null);
            if (control != null) {
                WeaponReadiness readiness = control.captureReadiness();
                reloaded |= readiness == WeaponReadiness.RELOADING;
                dry |= control.isCurrentWeaponDryConfirmed();
            }
            Case test = CASES[index];
            if (ticks % 100 == 0) LogUtils.getLogger().info(
                    "VGG_CGM_QA_PROGRESS case={} tick={} projectiles={} total={} reloaded={} dry={} readiness={}",
                    test.name, ticks, projectiles, total(), reloaded, dry,
                    control == null ? "no-control" : control.captureReadiness());
            if (ticks < 300) return;

            require(control != null, test.name + ": the combat goal never created a weapon control");
            int remaining = total();
            if (test.reserve == 0 || test.wrongAmmo) {
                require(projectiles == 0, test.name + ": fired without usable ammo");
                require(dry, test.name + ": the gun was never confirmed dry");
                require(remaining == initialTotal, test.name + ": ammo changed without firing");
            } else if (test.ally) {
                require(ally.getHealth() >= ally.getMaxHealth(), test.name + ": the villager in the lane was hurt");
                require((initialTotal - remaining) * test.pellets == projectiles,
                        test.name + ": ammo was lost or duplicated: spent=" + (initialTotal - remaining) + " projectiles=" + projectiles);
            } else {
                require(reloaded, test.name + ": the empty gun never reloaded");
                require(projectiles > 0, test.name + ": no projectile was fired");
                require(target.getHealth() < target.getMaxHealth(), test.name + ": projectiles never damaged the target");
                require((initialTotal - remaining) * test.pellets == projectiles,
                        test.name + ": ammo was lost or duplicated: spent=" + (initialTotal - remaining) + " projectiles=" + projectiles);
            }
            roundTrip();
            LogUtils.getLogger().info("VGG_CGM_QA_CASE_PASS case={} projectiles={} remaining={} reloaded={} dry={} targetHealth={}",
                    test.name, projectiles, remaining, reloaded, dry, target.getHealth());
            next();
        } catch (Exception failure) { fail(failure); }
    }

    private void next() throws Exception {
        if (guard != null) guard.discard();
        if (target != null) target.discard();
        if (ally != null) ally.discard();
        guard = null; ally = null;
        index++; ticks = projectiles = 0; reloaded = dry = false;
        if (index == CASES.length) {
            guard = newGuard("pistol", "basic_bullet", 23);
            guard.setNoAi(true);
            expected = new CompoundTag();
            expected.putUUID("Guard", guard.getUUID()); expected.putLong("Origin", origin.asLong());
            expected.put("Gun", guard.getMainHandItem().save(level.registryAccess()));
            expected.put("Ammo", GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess()));
            NbtIo.writeCompressed(expected, EXPECTED);
            done = true;
            LogUtils.getLogger().info("VGG_CGM_QA_READY_TO_RESTART uuid={}", guard.getUUID());
            return;
        }
        Case test = CASES[index];
        target = new Husk(EntityType.HUSK, level);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        target.setHealth(1024); target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(origin.getX() + 12.5, origin.getY(), origin.getZ() + .5, 90, 0);
        require(level.addFreshEntity(target), "Target spawn rejected");
        if (test.ally) {
            ally = new Villager(EntityType.VILLAGER, level);
            ally.setNoAi(true); ally.setPersistenceRequired();
            ally.moveTo(origin.getX() + 6.5, origin.getY(), origin.getZ() + .5, 90, 0);
            require(level.addFreshEntity(ally), "Villager spawn rejected");
        }
        guard = newGuard(test.gun, test.ammo, test.reserve);
        guard.setTarget(target);
        initialTotal = total();
    }

    private Guard newGuard(String gunId, String ammoId, int reserve) {
        Guard result = GuardEntityType.GUARD.get().create(level);
        require(result != null, "Guard creation failed");
        result.setPersistenceRequired();
        result.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, -90, 0);
        ItemStack weapon = new ItemStack(item(gunId));
        require(GunProviders.find(weapon).map(p -> p.id().equals(PROVIDER)).orElse(false),
                "CGM provider does not own " + gunId);
        result.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        if (reserve > 0) {
            ItemStack ammo = new ItemStack(item(ammoId), reserve);
            require(GunProviders.isAmmo(ammo), "CGM ammo is not recognized: " + ammoId);
            GuardAmmoAccess.getAmmoHandler(result).setStackInSlot(0, ammo);
        }
        GuardOrdersAccess.setOrders(result, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
        require(level.addFreshEntity(result), "Guard spawn rejected");
        return result;
    }

    private static Item item(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PROVIDER, path);
        require(BuiltInRegistries.ITEM.containsKey(id), "Missing CGM item: " + id);
        return BuiltInRegistries.ITEM.get(id);
    }

    /** Rounds in the magazine plus every item in the ammo slots, compatible or not. */
    private int total() {
        GuardAmmoSummary.Snapshot summary = GuardAmmoSummary.capture(guard.getMainHandItem(), null);
        require(summary.isReady(), "Gun ammo is unreadable: " + summary.status());
        int total = summary.loadedAmmo();
        var inventory = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < inventory.getSlots(); slot++) total += inventory.getStackInSlot(slot).getCount();
        return total;
    }

    private void roundTrip() {
        CompoundTag saved = guard.saveWithoutId(new CompoundTag());
        Guard copy = GuardEntityType.GUARD.get().create(level); copy.load(saved);
        require(ItemStack.matches(guard.getMainHandItem(), copy.getMainHandItem()), "Gun components changed on entity round trip");
        require(GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess()).equals(
                GuardAmmoAccess.getAmmoHandler(copy).serializeNBT(level.registryAccess())), "Reserve ammo changed on entity round trip");
    }

    private void fail(Exception failure) {
        done = true;
        LogUtils.getLogger().error("VGG_CGM_QA_FAIL case={} tick={}", index, ticks, failure);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
