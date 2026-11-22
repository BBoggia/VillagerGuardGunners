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
 * Drives the real guard goals with Point Blank guns in an installed, packaged
 * server. Runs unchanged against Vic's Point Blank and Point Blank: Jelly:
 * it uses item identifiers and the neutral gun seam only.
 */
final class PointBlankGunAcceptance {
    // Named after the suite, as the runner expects: both builds share one server installation.
    private static final Path EXPECTED = Path.of(System.getProperty("vgg.qa.suite") + "-qa-expected.nbt");
    private static final String PROVIDER = "pointblank";
    private static final Case[] CASES = {
        new Case("reload_then_fire", "glock17", "ammo9mm", 34, Kind.FIRES),
        new Case("automatic_fire", "ak47", "ammo762", 60, Kind.FIRES),
        new Case("shotgun", "m870", "ammo12gauge", 12, Kind.FIRES),
        new Case("dry", "ak47", "ammo762", 0, Kind.NEVER_LOADS),
        new Case("wrong_ammo", "ak47", "ammo9mm", 32, Kind.NEVER_LOADS),
        new Case("ally_in_lane", "glock17", "ammo9mm", 34, Kind.HOLDS_FIRE),
        new Case("launcher_refused", "at4", "at4_rocket", 2, Kind.REFUSED)
    };
    private enum Kind { FIRES, NEVER_LOADS, HOLDS_FIRE, REFUSED }
    private record Case(String name, String gun, String ammo, int reserve, Kind kind) {}

    private ServerLevel level;
    private BlockPos origin;
    private Guard guard;
    private Husk target;
    private Villager ally;
    private int index = -1, ticks, spawnedEntities, initialTotal;
    private boolean reloaded, dry, credited, done, restoring;
    private CompoundTag expected;

    PointBlankGunAcceptance() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::joined);
    }

    private void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            require(GunProviders.byId(PROVIDER).isPresent(), "The Point Blank provider was not registered");
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
            checkSelectedMode();
            LogUtils.getLogger().info("VGG_PB_QA_CASE_PASS case=shipped_data {}", OptionalGunDataChecks.run(level, PROVIDER));
            next();
        } catch (Exception failure) { fail(failure); }
    }

    private void checkSelectedMode() throws ReflectiveOperationException {
        var provider = GunProviders.byId(PROVIDER).orElseThrow();
        ItemStack stack = provider.createGun(ResourceLocation.parse("pointblank:glock18"), level.registryAccess(), true).orElseThrow();
        String adapterPackage = "com.gamergaming.villagerguardgunners.compat.pointblank.";
        String adapterName = com.gamergaming.villagerguardgunners.compat.pointblank.PointBlankApi.INSTALLED
                == com.gamergaming.villagerguardgunners.compat.pointblank.PointBlankApi.VPB ? "vpb.VpbAccess" : "pbj.PbjAccess";
        var adapter = Class.forName(adapterPackage + adapterName).getConstructor().newInstance();
        var ctor = Class.forName(adapterPackage + "PointBlankGunBackend")
                .getDeclaredConstructor(com.gamergaming.villagerguardgunners.compat.pointblank.PointBlankAccess.class);
        ctor.setAccessible(true);
        var backend = (com.gamergaming.villagerguardgunners.guns.managed.ManagedGunBackend) ctor.newInstance(adapter);
        String category = provider.describe(stack).orElseThrow().type();
        var observed = new java.util.HashSet<Boolean>();
        for (Object mode : (java.util.List<?>) stack.getItem().getClass().getMethod("getMainFireModes").invoke(stack.getItem())) {
            var setter = Class.forName(mode.getClass().getPackageName() + ".GunItem")
                    .getDeclaredMethod("setFireModeInstance", ItemStack.class, mode.getClass());
            setter.setAccessible(true);
            setter.invoke(null, stack, mode);
            boolean automatic = ((Enum<?>) mode.getClass().getMethod("getType").invoke(mode)).name().equals("AUTOMATIC");
            require(backend.stats(stack).orElseThrow().automatic() == automatic, "Wrong cadence for selected fire mode");
            require(java.util.Objects.equals(category, provider.describe(stack).orElseThrow().type()),
                    "Gun category changed when switching modes");
            observed.add(automatic);
        }
        require(observed.size() == 2, "Fixture must provide both single and automatic fire");
    }

    private void forceChunks() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
    }

    /** Hit-scan guns spawn nothing. Any entity from the mod here means a projectile path was taken. */
    private void joined(EntityJoinLevelEvent event) {
        if (done || restoring || guard == null) return;
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (PROVIDER.equals(type.getNamespace())) spawnedEntities++;
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
                            "Restored gun is not owned by the Point Blank provider");
                    done = true;
                    LogUtils.getLogger().info("VGG_PB_QA_RESTART_PASS uuid={} ammo={}", guard.getUUID(), total());
                } else require(ticks < 400, "Saved guard did not load after a real server restart");
                return;
            }
            GuardWeaponControl control = GuardWeaponControlAccess.control(guard).orElse(null);
            WeaponReadiness readiness = control == null ? null : control.captureReadiness();
            if (control != null) {
                reloaded |= readiness == WeaponReadiness.RELOADING;
                dry |= control.isCurrentWeaponDryConfirmed();
            }
            // Vanilla forgets an attacker after 100 ticks, so sample it while the guard is firing.
            credited |= target.getLastHurtByMob() == guard;
            Case test = CASES[index];
            if (ticks % 100 == 0) LogUtils.getLogger().info(
                    "VGG_PB_QA_PROGRESS case={} tick={} spent={} total={} reloaded={} dry={} readiness={} targetHealth={}",
                    test.name, ticks, initialTotal - total(), total(), reloaded, dry, readiness, target.getHealth());
            if (ticks < 300) return;

            require(control != null, test.name + ": the combat goal never created a weapon control");
            int spent = initialTotal - total();
            require(spawnedEntities == 0, test.name + ": the mod spawned an entity for a hit-scan gun");
            switch (test.kind) {
                case FIRES -> {
                    require(reloaded, test.name + ": the empty gun never reloaded");
                    require(spent > 0, test.name + ": no round was fired");
                    require(target.getHealth() < target.getMaxHealth(), test.name + ": rounds never damaged the target");
                    require(credited, test.name + ": damage was not credited to the guard");
                }
                case NEVER_LOADS -> {
                    require(spent == 0, test.name + ": ammo changed without usable ammo");
                    require(dry, test.name + ": the gun was never confirmed dry");
                    require(target.getHealth() >= target.getMaxHealth(), test.name + ": the target was hurt");
                }
                case HOLDS_FIRE -> {
                    require(ally.getHealth() >= ally.getMaxHealth(), test.name + ": the villager in the lane was hurt");
                    require(spent == 0, test.name + ": rounds were fired past a villager");
                    require(target.getHealth() >= target.getMaxHealth(), test.name + ": the target was hurt");
                }
                case REFUSED -> {
                    require(spent == 0, test.name + ": a launcher was fired or loaded");
                    require(target.getHealth() >= target.getMaxHealth(), test.name + ": the target was hurt");
                    require(readiness == WeaponReadiness.SUSPENDED, test.name + ": launcher readiness was " + readiness);
                }
            }
            roundTrip();
            LogUtils.getLogger().info("VGG_PB_QA_CASE_PASS case={} spent={} remaining={} reloaded={} dry={} readiness={} targetHealth={}",
                    test.name, spent, total(), reloaded, dry, readiness, target.getHealth());
            next();
        } catch (Exception failure) { fail(failure); }
    }

    private void next() throws Exception {
        if (guard != null) guard.discard();
        if (target != null) target.discard();
        if (ally != null) ally.discard();
        guard = null; ally = null;
        index++; ticks = spawnedEntities = 0; reloaded = dry = credited = false;
        if (index == CASES.length) {
            guard = newGuard("glock17", "ammo9mm", 23);
            guard.setNoAi(true);
            expected = new CompoundTag();
            expected.putUUID("Guard", guard.getUUID()); expected.putLong("Origin", origin.asLong());
            expected.put("Gun", guard.getMainHandItem().save(level.registryAccess()));
            expected.put("Ammo", GuardAmmoAccess.getAmmoHandler(guard).serializeNBT(level.registryAccess()));
            NbtIo.writeCompressed(expected, EXPECTED);
            done = true;
            LogUtils.getLogger().info("VGG_PB_QA_READY_TO_RESTART uuid={}", guard.getUUID());
            return;
        }
        Case test = CASES[index];
        target = new Husk(EntityType.HUSK, level);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        target.setHealth(1024); target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(origin.getX() + 12.5, origin.getY(), origin.getZ() + .5, 90, 0);
        require(level.addFreshEntity(target), "Target spawn rejected");
        if (test.kind == Kind.HOLDS_FIRE) {
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
                "Point Blank provider does not own " + gunId);
        result.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        if (reserve > 0) {
            ItemStack ammo = new ItemStack(item(ammoId), reserve);
            require(GunProviders.isAmmo(ammo), "Point Blank ammo is not recognized: " + ammoId);
            GuardAmmoAccess.getAmmoHandler(result).setStackInSlot(0, ammo);
        }
        GuardOrdersAccess.setOrders(result, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
        require(level.addFreshEntity(result), "Guard spawn rejected");
        return result;
    }

    private static Item item(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PROVIDER, path);
        require(BuiltInRegistries.ITEM.containsKey(id), "Missing Point Blank item: " + id);
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
        LogUtils.getLogger().error("VGG_PB_QA_FAIL case={} tick={}", index, ticks, failure);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
