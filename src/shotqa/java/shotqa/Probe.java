package shotqa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/** Real client packets followed by ordinary guard AI; observes native bullets and reload. */
@EventBusSubscriber(modid = "vgg_shot_qa")
public final class Probe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> CASES = List.of("tacz:glock_17", "tacz:ak47", "tacz:m16a4", "tacz:spas_12");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static int index = -1, ticks, wait, guardBullets, playerBullets, fireEvents;
    private static boolean initialized, finished, reload, postReload;
    private static Guard guard;
    private static LivingEntity target;
    private static ServerPlayer player;
    private static String phase = "waiting";
    private static Map<String, Long> hooksBefore;
    private static Map<String, Object> row;

    public static Path out() { return Path.of(System.getProperty("vgg.packqa.dir")); }

    public static void write(String name, Object value) throws Exception {
        Path destination = out().resolve(name), temporary = out().resolve(name + ".tmp");
        Files.writeString(temporary, JSON.toJson(value));
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static ResourceLocation id() { return ResourceLocation.parse(CASES.get(index)); }

    private static ItemStack gun() {
        var data = TimelessAPI.getCommonGunIndex(id()).orElseThrow().getGunData();
        var mode = id().getPath().equals("m16a4") ? FireMode.BURST : data.getFireModeSet().getFirst();
        return GunItemBuilder.create().setId(id()).setFireMode(mode)
                .setAmmoCount(Math.min(2, data.getAmmoAmount())).setAmmoInBarrel(true).build(player.registryAccess());
    }

    private static ItemStack ammo() {
        return AmmoItemBuilder.create().setId(TimelessAPI.getCommonGunIndex(id()).orElseThrow()
                .getGunData().getAmmoId()).setCount(64).build();
    }

    private static void control() throws Exception {
        write("control.json", Map.of("index", index, "gun", CASES.get(index), "phase", phase, "tick", ticks));
    }

    private static void begin(ServerLevel level) throws Exception {
        if (guard != null) guard.discard();
        if (target != null) target.discard();
        // No previous projectile can be attributed to the next gun's player phase.
        var bullets = new ArrayList<Entity>();
        for (var entity : level.getAllEntities()) if (entity instanceof EntityKineticBullet) bullets.add(entity);
        bullets.forEach(Entity::discard);
        if (++index == CASES.size()) {
            write("complete.json", Map.of("completed", true, "results", RESULTS));
            finished = true;
            return;
        }
        hooksBefore = HookAssertions.snapshot();
        ticks = guardBullets = playerBullets = fireEvents = 0;
        reload = postReload = false;
        phase = "player";
        row = new LinkedHashMap<>();
        row.put("gun", CASES.get(index));
        var husk = EntityType.HUSK.create(level);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        husk.setHealth(10000);
        husk.setPos(16.5, 100, .5);
        level.addFreshEntity(husk);
        target = husk;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION, 24000, 0, false, false));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, gun());
        player.getInventory().setItem(1, ammo());
        player.teleportTo(level, .5, 100, .5, -90, 0);
        player.containerMenu.broadcastChanges();
        guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        guard.setPos(.5, 100, 5.5);
        guard.setNoAi(true);
        guard.setInvulnerable(true);
        guard.setPersistenceRequired();
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun());
        level.addFreshEntity(guard);
        control();
        System.out.println("SHOT_QA_BEGIN " + id());
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (System.getProperty("vgg.packqa.dir") == null || finished) return;
        try {
            var server = event.getServer();
            var level = server.overworld();
            if (server.getPlayerList().getPlayers().isEmpty()) {
                if (++wait > 6000) throw new IllegalStateException("Client did not connect");
                return;
            }
            player = server.getPlayerList().getPlayers().getFirst();
            if (!initialized) {
                initialized = true;
                server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
                level.setDayTime(6000);
                for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
                for (int x = -8; x <= 40; x++) for (int z = -9; z <= 12; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                write("selected-guns.json", CASES);
                begin(level);
                return;
            }
            ticks++;
            target.setPos(16.5, 100, .5);
            target.setDeltaMovement(Vec3.ZERO);
            target.setHealth(target.getMaxHealth());
            if (ticks == 180) {
                row.put("playerProjectiles", playerBullets);
                phase = "guard";
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, -3, 102, -5, -40, 12);
                guard.setPos(.5, 100, .5);
                guard.setNoAi(false);
                AddonControls.setup(guard, ammo());
                guard.setTarget(target);
                control();
            }
            if (ticks > 180) {
                guard.setTarget(target);
                reload |= IGunOperator.fromLivingEntity(guard).getSynReloadState().getStateType().isReloading();
            }
            if (ticks % 20 == 0) control();
            if (ticks >= 580) {
                row.put("guardProjectiles", guardBullets);
                row.put("reloadObserved", reload);
                row.put("postReloadProjectile", postReload);
                row.put("fireEvents", fireEvents);
                row.put("diagnostics", AddonControls.diagnostics(guard));
                HookAssertions.check(hooksBefore, fireEvents, guardBullets + playerBullets, guardBullets + playerBullets);
                if (playerBullets < 3 || guardBullets < 3 || !reload || !postReload)
                    throw new AssertionError("Missing player fire, guard fire or reload: " + row);
                row.put("hookCounters", HookAssertions.snapshot());
                RESULTS.add(row);
                write("results.json", RESULTS);
                System.out.println("SHOT_QA_PASS " + id());
                begin(level);
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finished = true;
            try {
                write("complete.json", Map.of("completed", false, "failure", error.toString(),
                        "index", index, "phase", phase, "tick", ticks));
            } catch (Exception writeError) { throw new RuntimeException(writeError); }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void nativeFire(GunFireEvent event) {
        if (!event.getLogicalSide().isClient() && (event.getShooter() == guard || event.getShooter() == player)) fireEvents++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bullet(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.isCanceled() || !(event.getEntity() instanceof EntityKineticBullet bullet)) return;
        if (bullet.getOwner() == guard) {
            guardBullets++;
            if (reload && !IGunOperator.fromLivingEntity(guard).getSynReloadState().getStateType().isReloading()) postReload = true;
        } else if (bullet.getOwner() == player) playerBullets++;
    }
}
