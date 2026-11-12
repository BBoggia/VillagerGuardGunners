package shotqa;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ammunition.*;
import com.gamergaming.villagerguardgunners.spawn.*;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.combat.*;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import tallestegg.guardvillagers.common.entities.Guard;

/** Packaged-runtime regressions for shot approval, native scripts, and refusal display. */
@EventBusSubscriber(modid = "vgg_shot_qa")
public final class ShotRegressionProbe {
    private static final List<String> ALL_CASES = List.of("glock", "auto", "burst", "burst-crossing", "spawn-cancel", "spas", "spas-blocked", "glock-blocked",
            "spas-cancel", "spas-resume", "spas-reload", "event-ally", "event-team", "event-block",
            "dynamic", "dynamic-explosion", "dynamic-explosion-values", "dynamic-pellets", "unsupported", "oversized",
            "ammo-reload", "ammo-no-reload", "ammo-spas", "ammo-burst", "ammo-manual",
            "ammo-blocked", "ammo-cancel", "ammo-enable", "ammo-disable", "ammo-box", "ammo-box-remove", "ammo-inventory-reload", "ammo-inventory-no-reload");
    private static final List<String> CASES = selectedCases();

    private static List<String> selectedCases() {
        String selection = System.getProperty("vgg.regression.cases", "");
        if (selection.isEmpty()) return ALL_CASES;
        var cases = List.of(selection.split(","));
        if (!ALL_CASES.containsAll(cases)) throw new IllegalArgumentException("Unknown case: " + selection);
        return cases;
    }
    private static final Set<String> BLOCKED = Set.of("spas-blocked", "glock-blocked", "spas-cancel",
            "event-ally", "event-team", "event-block", "unsupported", "oversized");
    private static int index = -1, ticks, fired, events, initialAmmo, propertyChecks, spawnEvents, rejectedSpawns;
    private static int checkpointRounds;
    private static ItemStack initialReserve, initialEmptyBox;
    private static boolean finished, changed, reloaded, spawnListenerRegistered;
    private static String failure;
    private static Map<String, Long> hooksBefore;
    private static Guard guard;
    private static LivingEntity target;
    private static Mob ally;
    private static NativeGunController controller;
    private static GuardGunCombatGoal goal;
    private static ItemStack gun;
    private static final BlockPos IMPACT_BLOCK = new BlockPos(3, 101, 0);
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Set<Double> GRAVITIES = new HashSet<>();
    private static final Set<Double> BLASTS = new HashSet<>();

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || System.getProperty("vgg.regression.output") == null) return;
        try {
            var level = event.getServer().overworld();
            if (guard == null) {
                if (++index == CASES.size()) { finish(event, ""); return; }
                if (!spawnListenerRegistered) {
                    String eventClass = System.getProperty("vgg.regression.spawnEvent", "");
                    if (!eventClass.isEmpty()) observeSpawnEvent(Class.forName(eventClass).asSubclass(Event.class));
                    spawnListenerRegistered = true;
                }
                begin(level);
            }
            String name = CASES.get(index);
            ticks++;
            guard.setPos(.5, 100, .5); guard.xOld = .5; guard.yOld = 100; guard.zOld = .5;
            guard.setDeltaMovement(Vec3.ZERO);
            target.setPos(16.5, 100, .5); target.setDeltaMovement(Vec3.ZERO);
            stationary(target);
            if (name.equals("spas-resume") && ticks == 50) {
                require(ammo() == initialAmmo && fired == 0, "Blocked SPAS consumed ammo before resuming");
                ally.discard();
            }
            if (name.equals("ammo-enable") && ticks == 60) {
                require(fired == 0 && controller.captureReadiness() == WeaponReadiness.DRY,
                        "Existing guard did not become dry before enabling infinite ammo");
                ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.WITH_RELOADING);
            }
            if (name.equals("ammo-box-remove") && ticks == 100) {
                ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.OFF);
                require(GuardAmmoSummary.captureForGuard(guard, gun, GuardAmmoAccess.getTaCZInventoryHandler(guard)).infiniteReserve(),
                        "Switching modes disabled an issued physical box");
            }
            if ((name.equals("ammo-disable") || name.equals("ammo-box-remove")) && ticks == 140) {
                require(fired > 0, "No fire before disabling ammo source");
                ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.OFF);
                GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0, ItemStack.EMPTY);
                ((IGun) gun.getItem()).setCurrentAmmoCount(gun, 0);
                ((IGun) gun.getItem()).setBulletInBarrel(gun, false);
                checkpointRounds = (int) controller.successfulAimRounds();
            }
            controller.tick(target, true, () -> true, () -> true);
            reloaded |= com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard)
                    .getSynReloadState().getStateType().isReloading();
            if (failure != null) throw new AssertionError(failure);
            if (ticks >= (name.startsWith("ammo-") ? 240 : name.equals("spas-reload") ? 200 : 100)) {
                int used = initialAmmo - ammo();
                if (name.equals("spas-reload")) used += 32 - GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0).getCount();
                if (name.startsWith("ammo-")) {
                    var mode = ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.get();
                    var reserve = GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0);
                    boolean removed = name.equals("ammo-disable") || name.equals("ammo-box-remove");
                    if (removed) {
                        require(controller.successfulAimRounds() == checkpointRounds, "Fired after ammo source was removed");
                        require(controller.captureReadiness() == WeaponReadiness.DRY, "Guard failed to return to finite ammo");
                    } else {
                        if (name.startsWith("ammo-box")) {
                            require(reserve.getCount() == initialReserve.getCount()
                                    && reserve.getItem() instanceof com.tacz.guns.api.item.IAmmoBox box && box.isCreative(reserve)
                                    && box.getAmmoId(reserve).equals(box.getAmmoId(initialReserve)), "Creative box was consumed or replaced");
                        } else require(ItemStack.matches(initialReserve, reserve), "Infinite mode consumed or replaced physical supplies");
                        require(ItemStack.matches(initialEmptyBox, GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(2)),
                                "Virtual ammo moved an empty finite box");
                        if (name.equals("ammo-blocked") || name.equals("ammo-cancel")) {
                            require(fired == 0 && ammo() == initialAmmo, "Refused infinite shot changed ammo or emitted a projectile");
                        } else {
                            require(fired >= (name.equals("ammo-enable") ? 1 : 3), "Infinite ammo did not sustain fire: targetCurrent=" + (guard.getTarget() == target) + ", ownsController=" + (com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(guard) == controller) + ", events=" + events + ", projectiles=" + fired + ", safety=" + controller.lastShotSafety() + ", hooks=" + HookAssertions.snapshot() + ", controller=" + controller.captureDiagnostics());
                            if (mode == InfiniteAmmoMode.WITHOUT_RELOADING || name.startsWith("ammo-inventory")) require(!reloaded, "No-reload mode started a reload");
                            else require(reloaded, "Normal reload delay was bypassed");
                        }
                        var summary = GuardAmmoSummary.captureForGuard(guard, gun, GuardAmmoAccess.getTaCZInventoryHandler(guard));
                        require(summary.infiniteReserve(), "Ammo diagnostics lost unlimited reserve");
                    }
                } else if (BLOCKED.contains(name)) require(fired == 0 && used == 0, "Refused shot emitted/spent ammo: " + fired + "/" + used);
                else if (name.equals("spawn-cancel")) require(rejectedSpawns > 0 && fired == 0 && used > 0
                        && controller.successfulAimRounds() == 0, "Rejected insertion counted as a successful round");
                else require(fired > 0 && used == controller.successfulAimRounds(), "Normal ammo/round accounting failed: fired="
                        + fired + ", used=" + used + ", rounds=" + controller.successfulAimRounds()
                        + ", safety=" + controller.lastShotSafety() + ", controller=" + controller.captureDiagnostics());
                if (name.equals("burst-crossing")) require(changed && fired == 1 && used == 1,
                        "Queued burst continued after an ally entered its path");
                if (name.equals("burst")) require(fired >= 3, "Native burst did not complete");
                if (!System.getProperty("vgg.regression.spawnEvent", "").isEmpty())
                    require(spawnEvents == fired + rejectedSpawns, "External spawn hook skipped or duplicated: " + spawnEvents);
                if (name.equals("spas-reload")) require(reloaded && controller.successfulAimRounds() > initialAmmo, "SPAS reload did not resume");
                if (name.startsWith("dynamic")) require(propertyChecks == fired && GRAVITIES.size() == 2, "Dynamic property coverage incomplete");
                if (name.startsWith("dynamic-explosion")) require(BLASTS.size() == 2, "Explosion enable/disable was not exercised");
                if (name.equals("dynamic-pellets")) require(fired > controller.successfulAimRounds(), "No pellet grouping");
                if (name.startsWith("event-")) require(changed && events > 0, "Event-time mutation was not exercised");
                String display = "not sampled";
                if (name.equals("unsupported") || name.equals("oversized") || name.equals("spas-blocked")) {
                    set(goal, "target", target); set(goal, "canSeeTarget", true);
                    set(goal, "visibleTicks", 100); set(goal, "aimRequirementMet", true);
                    var expected = name.equals("unsupported") ? GuardCombatStatus.UNSUPPORTED_GUN
                            : name.equals("oversized") ? GuardCombatStatus.SAFETY_CHECK_LIMIT : GuardCombatStatus.UNSAFE_SHOT;
                    require(goal.getPlayerFacingStatus() == expected, "Incorrect display: " + goal.getPlayerFacingStatus());
                    display = expected.name();
                    verifyHistory(level);
                }
                HookAssertions.check(hooksBefore, events, fired + rejectedSpawns, fired);
                RESULTS.add(Map.of("case", name, "projectiles", fired, "rounds", controller.successfulAimRounds(),
                        "ammoUsed", used, "fireEvents", events, "propertyChecks", propertyChecks,
                        "safety", controller.lastShotSafety().status().name(), "display", display,
                        "externalSpawnEvents", spawnEvents, "rejectedSpawns", rejectedSpawns));
                Files.writeString(Path.of(System.getProperty("vgg.regression.output")).resolveSibling("progress.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
                controller.stopAiming();
                require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.NOT_EVALUATED, "Stopped context leaked refusal");
                guard.discard(); target.discard(); if (ally != null) ally.discard();
                level.setBlockAndUpdate(IMPACT_BLOCK, Blocks.AIR.defaultBlockState());
                var team = level.getScoreboard().getPlayerTeam("vgg_shot_qa");
                if (team != null) level.getScoreboard().removePlayerTeam(team);
                var cleanup = new ArrayList<Entity>();
                for (var entity : level.getAllEntities()) if (entity instanceof EntityKineticBullet) cleanup.add(entity);
                cleanup.forEach(Entity::discard); guard = null;
            }
        } catch (Throwable error) { error.printStackTrace(); finish(event, error.toString()); }
    }

    private static void begin(ServerLevel level) throws Exception {
        String name = CASES.get(index);
        level.setChunkForced(0, 0, true);
        for (int x = -2; x <= 3; x++) for (int z = -2; z <= 2; z++) level.getChunk(x, z);
        ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(
                (name.equals("ammo-reload") || name.equals("ammo-inventory-reload")) ? InfiniteAmmoMode.WITH_RELOADING
                : name.startsWith("ammo-box") ? InfiniteAmmoMode.CREATIVE_AMMO_BOX
                : name.startsWith("ammo-") && !name.equals("ammo-enable") ? InfiniteAmmoMode.WITHOUT_RELOADING
                : InfiniteAmmoMode.OFF);
        ticks = fired = events = propertyChecks = spawnEvents = rejectedSpawns = 0; changed = reloaded = false; ally = null; failure = null;
        GRAVITIES.clear(); BLASTS.clear();
        var aiming = ModConfigs.GUARD_GUNNERS.combat.aiming;
        aiming.errorDegrees.accept(0.0); aiming.overridePlayers.accept(false);
        aiming.aimPoint.accept(name.equals("event-block") ? GuardAimPoint.CENTER_MASS : GuardAimPoint.EYE_LEVEL);
        target = EntityType.HUSK.create(level); ((Mob) target).setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
        target.setPos(16.5, 100, .5); level.addFreshEntity(target);
        stationary(target);
        guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        guard.setNoAi(true); guard.setNoGravity(true); guard.setInvulnerable(true); guard.setOnGround(true); guard.setPos(.5, 100, .5);
        stationary(guard);
        String id = name.startsWith("ammo-inventory") ? "vgg_aim_qa:inventory" : name.equals("ammo-spas") ? "tacz:spas_12" : name.equals("ammo-burst") ? "tacz:m16a4" : name.equals("ammo-manual") ? "tacz:ai_awp" : name.equals("auto") ? "tacz:ak47" : name.startsWith("burst") ? "tacz:m16a4" : name.startsWith("spas") ? "tacz:spas_12" : name.equals("event-block") ? "tacz:rpg7"
                : name.startsWith("dynamic") || name.equals("unsupported") || name.equals("oversized") ? "vgg_aim_qa:" + name : "tacz:glock_17";
        gun = GunItemBuilder.create().setId(ResourceLocation.parse(id))
                .setFireMode(name.equals("auto") ? FireMode.AUTO : name.startsWith("spas") || name.startsWith("burst") || name.equals("ammo-burst") || name.equals("ammo-spas") ? FireMode.BURST : FireMode.SEMI)
                .setAmmoCount(name.equals("spas-reload") ? 1 : 100).setAmmoInBarrel(true).build(level.registryAccess());
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun); level.addFreshEntity(guard); guard.setTarget(target);
        if (name.endsWith("blocked") || name.equals("spas-resume")) addAlly(level, false, 24.5, .5);
        if (name.equals("event-ally") || name.equals("burst-crossing")) addAlly(level, false, 8.5, 10.5);
        if (name.equals("event-team")) {
            addAlly(level, true, 8.5, .5);
            var team = level.getScoreboard().addPlayerTeam("vgg_shot_qa");
            level.getScoreboard().addPlayerToTeam(guard.getStringUUID(), team);
            require(!GuardFriendlyFireSafety.isProtected(guard, ally), "Team fixture was already protected");
        }
        if (name.equals("spas-reload")) GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,
                AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:12g")).setCount(32).build());
        if (name.startsWith("ammo-")) {
            var data = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(ResourceLocation.parse(id)).orElseThrow().getGunData();
            var nativeGun = (IGun) gun.getItem();
            nativeGun.setCurrentAmmoCount(gun, 0);
            nativeGun.setBulletInBarrel(gun, false);
            if (name.equals("ammo-blocked") || name.equals("ammo-cancel")) {
                com.gamergaming.villagerguardgunners.compat.tacz.provider.TaczInfiniteAmmo.replenishMagazine(guard, gun, data);
                nativeGun.setBulletInBarrel(gun, true);
            }
            if (name.startsWith("ammo-box")) {
                guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                var candidate = new NaturalGuardLoadoutCandidate(ResourceLocation.parse("vgg_shot_qa:box"),
                        ResourceLocation.parse(id), ResourceLocation.parse("tacz:m1911"), NaturalGuardDifficulty.NORMAL, NaturalGuardSpawnSource.STRUCTURE);
                var result = NaturalGuardLoadoutIssuance.issue(guard, candidate, false);
                require(result.issued(), "Creative box loadout failed: " + result);
                gun = guard.getMainHandItem(); nativeGun = (IGun) gun.getItem();
                nativeGun.setCurrentAmmoCount(gun, 0); nativeGun.setBulletInBarrel(gun, false);
                var box = GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0);
                require(box.getItem() instanceof com.tacz.guns.api.item.IAmmoBox ammoBox && ammoBox.isCreative(box),
                        "Spawn loadout did not issue a creative box");
                var sidearm = com.gamergaming.villagerguardgunners.api.GuardSidearmAccess.getConfiguredSidearmInventory(guard).getStackInSlot(0);
                var sidearmBox = GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(1);
                require(sidearmBox.getItem() instanceof com.tacz.guns.api.item.IAmmoBox second
                        && second.isCreative(sidearmBox) && second.isAmmoBoxOfGun(sidearm, sidearmBox),
                        "Sidearm did not receive its matching creative box");
            } else if (!name.equals("ammo-enable")) {
                GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,
                        AmmoItemBuilder.create().setId(data.getAmmoId()).setCount(32).build());
            }
            if (!name.startsWith("ammo-box") && !name.equals("ammo-enable")) {
                GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(2, new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get()));
            }
            initialEmptyBox = GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(2).copy();
            initialReserve = GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0).copy();
            // Non-guard operators must retain the original survival ammo policy.
            require(com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(target).needCheckAmmo(), "Virtual ammo leaked to another entity");
        }
        initialAmmo = ammo();
        hooksBefore = HookAssertions.snapshot();
        goal = new GuardGunCombatGoal(guard);
        controller = (NativeGunController) get(goal, "gunController"); controller.startAiming();
    }

    private static void addAlly(ServerLevel level, boolean hostile, double x, double z) {
        ally = hostile ? EntityType.HUSK.create(level) : EntityType.VILLAGER.create(level);
        ally.setNoAi(true); ally.setNoGravity(true); ally.setInvulnerable(true); ally.setPos(x, 100, z); level.addFreshEntity(ally);
        stationary(ally);
    }

    private static void stationary(Entity entity) {
        // This observer manually places entities outside the usual ticking radius.
        // TaCZ derives hitbox velocity from previous positions, even with NoAI.
        entity.xOld = entity.getX(); entity.yOld = entity.getY(); entity.zOld = entity.getZ();
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void fire(GunFireEvent event) {
        if (guard == null || event.getShooter() != guard || event.getLogicalSide().isClient()) return;
        events++;
        String name = CASES.get(index);
        if (name.equals("spas-cancel") || name.equals("ammo-cancel")) event.setCanceled(true);
        if (event.isCanceled() || changed) return;
        if (name.equals("event-ally")) { ally.setPos(8.5, 100, .5); changed = true; }
        if (name.equals("event-team")) {
            var level = (ServerLevel) guard.level();
            level.getScoreboard().addPlayerToTeam(ally.getStringUUID(), level.getScoreboard().getPlayerTeam("vgg_shot_qa"));
            changed = true;
        }
        if (name.equals("event-block")) { guard.level().setBlockAndUpdate(IMPACT_BLOCK, Blocks.STONE_SLAB.defaultBlockState()); changed = true; }
    }

    /** Optional external hook, supplied by class name so the observer has no Arcana dependency. */
    private static <T extends Event> void observeSpawnEvent(Class<T> eventType) throws Exception {
        var fields = Arrays.stream(eventType.getDeclaredFields())
                .filter(field -> field.getType() == EntityKineticBullet.class).toList();
        if (fields.size() != 1) throw new IllegalArgumentException("Expected one projectile field: " + eventType);
        var field = fields.get(0);
        field.setAccessible(true);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, eventType, event -> {
            try {
                var bullet = (EntityKineticBullet) field.get(event);
                if (guard != null && bullet.getOwner() == guard) spawnEvents++;
            } catch (ReflectiveOperationException error) { failure = error.toString(); }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rejectInsertion(EntityJoinLevelEvent event) {
        if (guard != null && CASES.get(index).equals("spawn-cancel")
                && event.getEntity() instanceof EntityKineticBullet bullet && bullet.getOwner() == guard) {
            rejectedSpawns++;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void projectile(EntityJoinLevelEvent event) {
        if (guard == null || event.isCanceled() || !(event.getEntity() instanceof EntityKineticBullet bullet) || bullet.getOwner() != guard) return;
        try {
            fired++;
            var approved = controller.lastShotSafety();
            require(approved.clear() && controller.approvedShotAim() == approved.envelope().aim(), "Unapproved projectile");
            var profile = approved.envelope().ballistics();
            require(GuardFriendlyFireSafety.diagnoseShot(guard, target, controller.approvedShotAim(), profile,
                    approved.envelope().clearance()).clear(), "Scene changed before insertion");
            require(Math.abs(((Number) get(bullet, "gravity")).doubleValue() - profile.gravity()) < 1e-6, "Gravity mismatch");
            require(Math.abs(((Number) get(bullet, "friction")).doubleValue() - profile.friction()) < 1e-6, "Friction mismatch");
            require(((Number) get(bullet, "life")).intValue() == profile.lifeTicks(), "Lifetime mismatch");
            boolean explosive = (boolean) get(bullet, "explosion");
            require(explosive == (profile.blastReach() > 0), "Explosion enable mismatch");
            if (explosive) {
                require(Math.abs(((Number) get(bullet, "explosionRadius")).doubleValue() * 2 - profile.blastReach()) < 1e-6, "Radius mismatch");
                require(((Number) get(bullet, "explosionDelayCount")).intValue() == profile.fuseTicks(), "Fuse mismatch");
            }
            propertyChecks++; GRAVITIES.add(profile.gravity()); BLASTS.add(profile.blastReach());
            if (CASES.get(index).equals("burst-crossing") && !changed) {
                ally.setPos(8.5, 100, .5);
                changed = true;
            }
        } catch (Throwable error) { failure = error.toString(); }
    }

    private static void verifyHistory(ServerLevel level) throws Exception {
        var status = controller.currentShotSafety(target).status();
        require(status != GuardShotSafety.Status.NOT_EVALUATED && status != GuardShotSafety.Status.CLEAR, "Missing applicable refusal");
        var other = EntityType.HUSK.create(level);
        guard.setTarget(other);
        require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.NOT_EVALUATED, "Detached target refusal leaked");
        guard.setTarget(target);
        require(controller.currentShotSafety(other).status() == GuardShotSafety.Status.NOT_EVALUATED, "Old target refusal leaked");
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun.copy());
        require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.NOT_EVALUATED, "Old stack refusal leaked");
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
        var item = (IGun) gun.getItem();
        var mode = item.getFireMode(gun);
        item.setFireMode(gun, FireMode.AUTO);
        require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.NOT_EVALUATED, "Old fire-mode refusal leaked");
        item.setFireMode(gun, mode);
        ModConfigs.GUARD_GUNNERS.combat.aiming.errorDegrees.accept(2.0);
        require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.NOT_EVALUATED, "Old settings refusal leaked");
        ModConfigs.GUARD_GUNNERS.combat.aiming.errorDegrees.accept(0.0);
        require(controller.currentShotSafety(target).status() == status, "Context restoration lost history");
        long checkedTick = (long) get(controller, "lastShotGameTick");
        set(controller, "lastShotGameTick", level.getGameTime() - 21);
        require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.NOT_EVALUATED, "Expired refusal leaked");
        set(controller, "lastShotGameTick", checkedTick);
    }

    private static Object get(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static int ammo() { var item = (IGun) gun.getItem(); return item.getCurrentAmmoCount(gun) + (item.hasBulletInBarrel(gun) ? 1 : 0); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(CASES.get(index) + ": " + message); }
    private static void finish(ServerTickEvent.Post event, String error) {
        finished = true;
        try { Files.writeString(Path.of(System.getProperty("vgg.regression.output")), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("passed", error.isEmpty(), "cases", RESULTS, "expectedCases", CASES.size(), "failure", error))); }
        catch (Exception writeError) { throw new RuntimeException(writeError); }
        event.getServer().halt(false);
    }
}
