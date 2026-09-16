package accuracyprobe;

import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.google.gson.Gson;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Test-only arena setup and event observation; never included in the mod JAR. */
@Mod("vggaccuracyprobe")
@Mod.EventBusSubscriber(modid = "vggaccuracyprobe")
public final class Probe {
    private static final Gson JSON = new Gson();
    private static final List<Scenario> SCENARIOS = scenarios();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Set<UUID> HIT_BULLETS = new HashSet<>();
    private static Guard guard;
    private static LivingEntity target;
    private static Villager ally;
    private static int index = -1, ticks, waiting, cooldown = 80;
    private static int fireEvents, canceledFireEvents, projectiles, hits, headshots;
    private static int firstFireTick, firstHitTick;
    private static final Map<String, Integer> DECISIONS = new TreeMap<>();
    private static float damage, allyDamage;
    private static long started;
    private static boolean finished;
    private record Scenario(String targetKind, String gun, FireMode mode, int distance,
                            boolean strafe, boolean blockAlly, int trial) {}

    public static boolean enabled() { return System.getProperty("vgg.accuracy.dir") != null; }
    public static Path output() { return Path.of(System.getProperty("vgg.accuracy.dir")); }

    private static List<Scenario> scenarios() {
        List<Scenario> result = new ArrayList<>();
        int trials = Integer.getInteger("vgg.accuracy.trials", 3);
        for (String kind : List.of("player", "husk"))
            for (String gun : List.of("glock_17", "ak47", "m16a4"))
                for (int distance : new int[]{8, 16, 32})
                    for (int trial = 0; trial < trials; trial++)
                        result.add(new Scenario(kind, gun, mode(gun), distance, false, false, trial));
        for (int trial = 0; trial < trials; trial++) {
            result.add(new Scenario("player", "ak47", FireMode.AUTO, 16, true, false, trial));
            result.add(new Scenario("husk", "ak47", FireMode.AUTO, 16, false, true, trial));
        }
        return result;
    }

    private static FireMode mode(String gun) {
        return gun.equals("m16a4") ? FireMode.BURST : gun.equals("ak47") ? FireMode.AUTO : FireMode.SEMI;
    }

    private static void write(String name, Object value) {
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve(name), JSON.toJson(value) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static void event(String kind, Map<String, Object> details) {
        Map<String, Object> row = new LinkedHashMap<>(details);
        row.put("event", kind); row.put("scenario", index); row.put("tick", ticks);
        write("events.jsonl", row);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (!enabled() || finished || event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        try {
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty() || players.get(0).isDeadOrDying()) {
                if (++waiting > 1200) throw new IllegalStateException("No live client player available");
                return;
            }
            waiting = 0;
            ServerPlayer player = players.get(0);
            var level = server.overworld();
            if (guard == null) {
                if (cooldown-- > 0) return;
                if (++index >= SCENARIOS.size()) {
                    finished = true;
                    write("complete.json", Map.of("completed", true, "encounters", RESULTS.size(),
                            "results", RESULTS));
                    return;
                }
                begin(level, player);
                return;
            }
            ticks++;
            Scenario scenario = SCENARIOS.get(index);
            if (!target.isAlive() || ticks >= (scenario.blockAlly ? 100 : 160)) {
                finish(level);
                return;
            }
            // Controlled fixed-range encounters: remove target AI and displacement as confounders.
            // Strafing is a scripted trajectory, not a claim of human-input movement coverage.
            double z = scenario.strafe ? 0.5 + 2.0 * Math.sin(ticks * 0.12) : 0.5;
            if (target instanceof ServerPlayer p) p.teleportTo(level, scenario.distance + 0.5, 61, z, 90, 0);
            else target.setPos(scenario.distance + 0.5, 61, z);
            target.setDeltaMovement(Vec3.ZERO);
            guard.setTarget(target);
            if (ticks % 20 == 0) {
                var sample = new LinkedHashMap<String, Object>();
                sample.put("health", target.getHealth()); sample.put("ammo", ammo());
                sample.put("guard", List.of(guard.getX(), guard.getY(), guard.getZ()));
                for (var wrapped : guard.goalSelector.getAvailableGoals()) {
                    if (wrapped.getGoal() instanceof GuardGunCombatGoal gunGoal) {
                        var diagnostics = gunGoal.captureDiagnostics();
                        sample.put("diagnostics", diagnostics.toString());
                        DECISIONS.merge(diagnostics.decision().name(), 1, Integer::sum);
                    }
                }
                event("sample", sample);
            }
        } catch (Throwable failure) {
            finished = true;
            write("complete.json", Map.of("completed", false, "failure", failure.toString()));
        }
    }

    private static void begin(ServerLevel level, ServerPlayer player) {
        Scenario s = SCENARIOS.get(index);
        var aiming = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.combat.aiming;
        aiming.errorDegrees.accept(Double.parseDouble(System.getProperty("vgg.accuracy.error", "0")));
        aiming.aimPoint.accept(com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint.valueOf(
                System.getProperty("vgg.accuracy.point", "EYE_LEVEL")));
        aiming.overridePlayers.accept(System.getProperty("vgg.accuracy.playerError") != null);
        if (aiming.overridePlayers.get()) aiming.playerErrorDegrees.accept(Double.parseDouble(System.getProperty("vgg.accuracy.playerError")));
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, level.getServer());
        level.setDayTime(6000);
        player.setGameMode(s.targetKind.equals("player") ? GameType.SURVIVAL : GameType.SPECTATOR);
        player.getInventory().clearContent(); player.removeAllEffects();
        player.setHealth(player.getMaxHealth()); player.setAbsorptionAmount(0);
        player.getFoodData().setFoodLevel(20); player.invulnerableTime = 0;
        player.teleportTo(level, s.distance + .5, 61, .5, 90, 0);
        if (s.targetKind.equals("player")) target = player;
        else {
            var husk = EntityType.HUSK.create(level);
            husk.setNoAi(true); husk.setBaby(false); husk.setPersistenceRequired();
            husk.setPos(s.distance + .5, 61, .5); level.addFreshEntity(husk); target = husk;
            player.teleportTo(level, 0, 66, -12, 0, 20);
        }
        guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        guard.setPos(.5, 61, .5); guard.setInvulnerable(true); guard.setPersistenceRequired();
        guard.getRandom().setSeed(71000L + index);
        for (var slot : EquipmentSlot.values()) guard.setItemSlot(slot, ItemStack.EMPTY);
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT
                .withMovement(GuardOrders.MovementOrder.HOLD_POSITION)
                .withEngagement(GuardOrders.EngagementOrder.GUARD_TARGETS));
        guard.setItemSlot(EquipmentSlot.MAINHAND, GunItemBuilder.create()
                .setId(ResourceLocation.fromNamespaceAndPath("tacz", s.gun)).setFireMode(s.mode)
                .setAmmoCount(s.gun.equals("glock_17") ? 17 : 30).setAmmoInBarrel(true).build());
        level.addFreshEntity(guard); guard.setTarget(target);
        if (s.blockAlly) {
            ally = EntityType.VILLAGER.create(level); ally.setNoAi(true);
            ally.setPos(s.distance / 2.0 + .5, 61, .5); level.addFreshEntity(ally);
        }
        ticks = fireEvents = canceledFireEvents = projectiles = hits = headshots = 0;
        damage = allyDamage = 0; firstFireTick = firstHitTick = -1;
        HIT_BULLETS.clear(); DECISIONS.clear(); started = System.nanoTime();
        event("begin", Map.of("settings", s, "targetMaxHealth", target.getMaxHealth(),
                "targetClass", target.getClass().getName(), "initialAmmo", ammo(),
                "rules", "Normal; no armor/effects; hold position; guard targets; default combat config"));
    }

    private static int ammo() {
        ItemStack stack = guard.getMainHandItem();
        IGun gun = (IGun) stack.getItem();
        return gun.getCurrentAmmoCount(stack) + (gun.hasBulletInBarrel(stack) ? 1 : 0);
    }

    private static void finish(ServerLevel level) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", index); row.put("settings", SCENARIOS.get(index));
        row.put("killed", !target.isAlive()); row.put("ticks", ticks);
        row.put("wallSeconds", (System.nanoTime() - started) / 1e9);
        row.put("firstFireTick", firstFireTick); row.put("firstHitTick", firstHitTick);
        row.put("fireEvents", fireEvents); row.put("canceledFireEvents", canceledFireEvents);
        row.put("projectiles", projectiles); row.put("hitProjectiles", HIT_BULLETS.size());
        row.put("hitEvents", hits); row.put("headshotEvents", headshots);
        row.put("damageEventAmount", damage); row.put("healthRemaining", target.getHealth());
        row.put("ammoRemaining", ammo()); row.put("allyDamageEventAmount", allyDamage);
        row.put("allyHealthRemaining", ally == null ? -1 : ally.getHealth());
        row.put("sampledFireDecisions", new TreeMap<>(DECISIONS));
        RESULTS.add(row); write("encounters.jsonl", row);
        guard.discard(); guard = null;
        if (!(target instanceof ServerPlayer)) target.discard();
        target = null;
        if (ally != null) { ally.discard(); ally = null; }
        List<Entity> cleanup = new ArrayList<>();
        for (var entity : level.getAllEntities()) if (entity instanceof EntityKineticBullet) cleanup.add(entity);
        cleanup.forEach(Entity::discard);
        // Let the real player's native spawn/respawn damage protection expire.
        cooldown = 80;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fire(GunFireEvent e) {
        if (!enabled() || guard == null || e.getLogicalSide().isClient() || e.getShooter() != guard) return;
        if (e.isCanceled()) canceledFireEvents++; else fireEvents++;
        if (!e.isCanceled() && firstFireTick < 0) firstFireTick = ticks;
        event("fire", Map.of("canceled", e.isCanceled(), "ammoBefore", ammo()));
    }

    @SubscribeEvent
    public static void projectile(EntityJoinLevelEvent e) {
        if (!enabled() || guard == null || e.getLevel().isClientSide()
                || !(e.getEntity() instanceof EntityKineticBullet bullet) || bullet.getOwner() != guard) return;
        projectiles++;
        Vec3 pos = bullet.position(), v = bullet.getDeltaMovement();
        var row = new LinkedHashMap<String,Object>();
        row.put("id", bullet.getUUID().toString()); row.put("origin", List.of(pos.x,pos.y,pos.z));
        row.put("velocity", List.of(v.x,v.y,v.z));
        var controller = com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(guard);
        var aim = controller == null ? null : controller.approvedShotAim();
        row.put("approved", aim != null && controller.lastShotSafety().clear()
                && controller.lastShotSafety().envelope().aim() == aim);
        if (aim != null) {
            row.put("shotIndex", aim.shotIndex()); row.put("yaw", aim.yaw()); row.put("pitch", aim.pitch());
            row.put("aimPoint", List.of(aim.targetPoint().x, aim.targetPoint().y, aim.targetPoint().z));
            Vec3 intended = aim.targetPoint().subtract(aim.aimOrigin()).normalize();
            row.put("errorDegrees", Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,intended.dot(aim.direction()))))));
            row.put("configuredError", com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.combat.aiming
                    .resolve(target instanceof ServerPlayer).errorDegrees());
        }
        event("projectile", row);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void hit(EntityHurtByGunEvent.Pre e) {
        if (!enabled() || guard == null || e.getLogicalSide().isClient() || e.getAttacker() != guard) return;
        boolean intended = e.getHurtEntity() == target;
        if (intended && !e.isCanceled()) {
            hits++; if (e.isHeadShot()) headshots++;
            HIT_BULLETS.add(e.getBullet().getUUID());
            if (firstHitTick < 0) firstHitTick = ticks;
        }
        event("hit", Map.of("intended", intended, "canceled", e.isCanceled(),
                "headshot", e.isHeadShot(), "baseAmount", e.getBaseAmount(),
                "bullet", e.getBullet().getUUID().toString()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void damage(LivingDamageEvent e) {
        if (!enabled() || guard == null || e.getEntity().level().isClientSide || e.isCanceled()
                || e.getSource().getEntity() != guard) return;
        if (e.getEntity() == target) damage += e.getAmount();
        if (e.getEntity() == ally) allyDamage += e.getAmount();
    }
}
