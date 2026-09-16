package accuracyprobe;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Packaged-runtime regressions for shot approval, native scripts, and refusal display. */
@Mod.EventBusSubscriber(modid = "vggaccuracyprobe")
public final class ShotRegressionProbe {
    private static final List<String> CASES = List.of("glock", "spas", "spas-blocked", "glock-blocked",
            "spas-cancel", "spas-resume", "spas-reload", "event-ally", "event-team", "event-block",
            "dynamic", "dynamic-explosion", "dynamic-explosion-values", "dynamic-pellets", "unsupported", "oversized");
    private static final Set<String> BLOCKED = Set.of("spas-blocked", "glock-blocked", "spas-cancel",
            "event-ally", "event-team", "event-block", "unsupported", "oversized");
    private static int index = -1, ticks, fired, events, initialAmmo, propertyChecks;
    private static boolean finished, changed, reloaded;
    private static String failure;
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

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END || System.getProperty("vgg.regression.output") == null) return;
        try {
            var level = event.getServer().overworld();
            if (guard == null) {
                if (++index == CASES.size()) { finish(event, ""); return; }
                begin(level);
            }
            String name = CASES.get(index);
            ticks++;
            guard.setPos(.5, 100, .5); guard.xOld = .5; guard.yOld = 100; guard.zOld = .5;
            guard.setDeltaMovement(Vec3.ZERO);
            target.setPos(16.5, 100, .5); target.setDeltaMovement(Vec3.ZERO);
            if (name.equals("spas-resume") && ticks == 50) {
                require(ammo() == initialAmmo && fired == 0, "Blocked SPAS consumed ammo before resuming");
                ally.discard();
            }
            controller.tick(target, true, () -> true, () -> true);
            reloaded |= com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard)
                    .getSynReloadState().getStateType().isReloading();
            if (failure != null) throw new AssertionError(failure);
            if (ticks >= (name.equals("spas-reload") ? 200 : 100)) {
                int used = initialAmmo - ammo();
                if (name.equals("spas-reload")) used += 32 - GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0).getCount();
                if (BLOCKED.contains(name)) require(fired == 0 && used == 0, "Refused shot emitted/spent ammo: " + fired + "/" + used);
                else require(fired > 0 && used == controller.successfulAimRounds(), "Normal ammo/round accounting failed");
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
                RESULTS.add(Map.of("case", name, "projectiles", fired, "rounds", controller.successfulAimRounds(),
                        "ammoUsed", used, "fireEvents", events, "propertyChecks", propertyChecks,
                        "safety", controller.lastShotSafety().status().name(), "display", display));
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
        ticks = fired = events = propertyChecks = 0; changed = reloaded = false; ally = null; failure = null;
        GRAVITIES.clear(); BLASTS.clear();
        var aiming = ModConfigs.GUARD_GUNNERS.combat.aiming;
        aiming.errorDegrees.accept(0.0); aiming.overridePlayers.accept(false);
        aiming.aimPoint.accept(name.equals("event-block") ? GuardAimPoint.CENTER_MASS : GuardAimPoint.EYE_LEVEL);
        target = EntityType.HUSK.create(level); ((Mob) target).setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
        target.setPos(16.5, 100, .5); level.addFreshEntity(target);
        guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        guard.setNoAi(true); guard.setNoGravity(true); guard.setInvulnerable(true); guard.setOnGround(true); guard.setPos(.5, 100, .5);
        String id = name.startsWith("spas") ? "tacz:spas_12" : name.equals("event-block") ? "tacz:rpg7"
                : name.startsWith("dynamic") || name.equals("unsupported") || name.equals("oversized") ? "vgg_aim_qa:" + name : "tacz:glock_17";
        gun = GunItemBuilder.create().setId(ResourceLocation.parse(id))
                .setFireMode(name.startsWith("spas") ? FireMode.BURST : FireMode.SEMI)
                .setAmmoCount(name.equals("spas-reload") ? 1 : 100).setAmmoInBarrel(true).build();
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun); level.addFreshEntity(guard); guard.setTarget(target);
        if (name.endsWith("blocked") || name.equals("spas-resume")) addAlly(level, false, 24.5, .5);
        if (name.equals("event-ally")) addAlly(level, false, 8.5, 10.5);
        if (name.equals("event-team")) {
            addAlly(level, true, 8.5, .5);
            var team = level.getScoreboard().addPlayerTeam("vgg_shot_qa");
            level.getScoreboard().addPlayerToTeam(guard.getStringUUID(), team);
            require(!GuardFriendlyFireSafety.isProtected(guard, ally), "Team fixture was already protected");
        }
        if (name.equals("spas-reload")) GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,
                AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:12g")).setCount(32).build());
        initialAmmo = ammo();
        goal = new GuardGunCombatGoal(guard);
        controller = (NativeGunController) get(goal, "gunController"); controller.startAiming();
    }

    private static void addAlly(ServerLevel level, boolean hostile, double x, double z) {
        ally = hostile ? EntityType.HUSK.create(level) : EntityType.VILLAGER.create(level);
        ally.setNoAi(true); ally.setNoGravity(true); ally.setInvulnerable(true); ally.setPos(x, 100, z); level.addFreshEntity(ally);
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void fire(GunFireEvent event) {
        if (guard == null || event.getShooter() != guard || event.getLogicalSide().isClient()) return;
        events++;
        String name = CASES.get(index);
        if (name.equals("spas-cancel")) event.setCanceled(true);
        if (event.isCanceled() || changed) return;
        if (name.equals("event-ally")) { ally.setPos(8.5, 100, .5); changed = true; }
        if (name.equals("event-team")) {
            var level = (ServerLevel) guard.level();
            level.getScoreboard().addPlayerToTeam(ally.getStringUUID(), level.getScoreboard().getPlayerTeam("vgg_shot_qa"));
            changed = true;
        }
        if (name.equals("event-block")) { guard.level().setBlockAndUpdate(IMPACT_BLOCK, Blocks.STONE_SLAB.defaultBlockState()); changed = true; }
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
        gun.getOrCreateTag().putString("GunFireMode", "invalid_mode");
        require(controller.currentShotSafety(target).status() == GuardShotSafety.Status.INVALID_WORLD,
                "Malformed mode should yield an unavailable status without throwing");
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
    private static void finish(TickEvent.ServerTickEvent event, String error) {
        finished = true;
        try { Files.writeString(Path.of(System.getProperty("vgg.regression.output")), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("passed", error.isEmpty(), "cases", RESULTS, "expectedCases", CASES.size(), "failure", error))); }
        catch (Exception writeError) { throw new RuntimeException(writeError); }
        event.getServer().halt(false);
    }
}
