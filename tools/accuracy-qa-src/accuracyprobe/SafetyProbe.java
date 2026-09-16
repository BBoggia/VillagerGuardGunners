package accuracyprobe;

import com.gamergaming.villagerguardgunners.combat.*;
import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Calls the production safety API in an ordinary packaged dedicated server. Does not fire guns. */
@Mod.EventBusSubscriber(modid = "vggaccuracyprobe")
public final class SafetyProbe {
    private static boolean finished;
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static Guard guard;
    private static LivingEntity target;
    private static ServerLevel level;

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        String output = System.getProperty("vgg.safety.output");
        if (output == null || finished || event.phase != TickEvent.Phase.END) return;
        finished = true;
        long start = System.nanoTime();
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            level = event.getServer().overworld();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(x, z);
            guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
            guard.setNoAi(true); guard.setPos(.5, 100, .5); guard.setOnGround(true);
            level.addFreshEntity(guard);
            var husk = EntityType.HUSK.create(level);
            husk.setNoAi(true); husk.setPos(16.5, 100, .5); level.addFreshEntity(husk); target = husk;
            Vec3 straight = new Vec3(1, 0, 0);
            expect("open", shot(straight), profile(0), GuardShotSafety.Status.CLEAR);
            Villager ally = EntityType.VILLAGER.create(level);
            ally.setNoAi(true); ally.setPos(10.5, 100, 2.5); level.addFreshEntity(ally);
            expect("ally-outside-straight", shot(straight), profile(0), GuardShotSafety.Status.CLEAR);
            expect("ally-in-offset", shot(new Vec3(1, 0, .2)), profile(0), GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH);
            ally.setPos(38.5, 100, .5);
            expect("ally-behind-target", shot(straight), profile(0), GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH);
            ally.setPos(10.5, 100, .5);
            expect("ally-crossed-same-tick", shot(straight), profile(0), GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH);
            ally.setPos(-1.5, 100, 2.5);
            expect("rocket-owner-behind", shot(straight), profile(3), GuardShotSafety.Status.CLEAR);
            BlockPos wall = new BlockPos(2, 101, 0);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            expect("early-wall", shot(straight), profile(3), GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT);
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            ally.discard();
            target.setPos(2.5, 100, .5);
            expect("early-entity", shot(straight), profile(3), GuardShotSafety.Status.EXPLOSIVE_ENTITY_IMPACT);
            target.setPos(16.5, 100, .5);
            GuardShotAim stale = shot(straight);
            guard.setPos(.6, 100, .5);
            expect("moved-origin", stale, profile(0), GuardShotSafety.Status.STALE_AIM);
            guard.setPos(.5, 100, .5);
            var priorTick = new GuardShotAim(stale.gameTick() - 1, stale.shotIndex(), stale.aimOrigin(),
                    stale.projectileOrigin(), stale.targetPoint(), stale.direction(), stale.yaw(), stale.pitch());
            expect("previous-tick", priorTick, profile(0), GuardShotSafety.Status.STALE_AIM);
            for (String gun : List.of("glock_17", "ak47", "m16a4", "rpg7")) {
                guard.setItemSlot(EquipmentSlot.MAINHAND, GunItemBuilder.create()
                        .setId(ResourceLocation.fromNamespaceAndPath("tacz", gun)).setFireMode(FireMode.SEMI)
                        .setAmmoCount(10).setAmmoInBarrel(true).build());
                NativeGunController controller = new NativeGunController(guard);
                controller.startAiming();
                // Explicit native stance fixtures; this test does not simulate ADS timing or fire rounds.
                com.tacz.guns.entity.sync.ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(guard, 0.0F);
                var hip = controller.captureShotBallistics(1).orElseThrow();
                expect("native-hip-" + gun, shot(straight), hip, gun.equals("glock_17")
                        ? GuardShotSafety.Status.CLEAR : GuardShotSafety.Status.EXCESSIVE_ENVELOPE);
                com.tacz.guns.entity.sync.ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(guard, 1.0F);
                var resolved = controller.captureShotBallistics(1).orElseThrow();
                expect("native-aimed-" + gun, shot(straight), resolved, GuardShotSafety.Status.CLEAR);
                if (gun.equals("rpg7") && resolved.blastReach() != 6) throw new AssertionError("Native blast reach");
                controller.stopAiming();
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            failure.printStackTrace();
        } finally {
            report.put("cases", results);
            report.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000.0);
            report.put("runtime", "Packaged production JAR and separate test fixture; no dev runtime; no live firing integration");
            try {
                Path path = Path.of(output);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
            event.getServer().halt(false);
        }
    }

    private static void expect(String name, GuardShotAim aim, GuardShotEnvelope.Ballistics b, GuardShotSafety.Status expected) {
        // These are teleported stationary fixtures, not high-speed movement since the previous tick.
        for (Entity entity : level.getAllEntities()) {
            if (entity != guard) { entity.xOld = entity.getX(); entity.yOld = entity.getY(); entity.zOld = entity.getZ(); }
        }
        var result = GuardFriendlyFireSafety.diagnoseShot(guard, target, aim, b, .1);
        results.add(Map.of("name", name, "status", result.status().name(), "expected", expected.name(),
                "ballistics", b.toString()));
        if (result.status() != expected) throw new AssertionError(name + ": " + result.status() + " != " + expected);
    }
    private static GuardShotEnvelope.Ballistics profile(double blast) {
        return new GuardShotEnvelope.Ballistics(5, 10, 0, 0, 0, Vec3.ZERO, blast, Integer.MAX_VALUE);
    }
    private static GuardShotAim shot(Vec3 direction) {
        Vec3 unit = direction.normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-unit.x, unit.z));
        float pitch = (float)-Math.toDegrees(Math.asin(unit.y));
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        guard.xOld = guard.getX(); guard.yOld = guard.getY(); guard.zOld = guard.getZ();
        Vec3 origin = GuardFriendlyFireSafety.nativeProjectileOrigin(guard);
        return new GuardShotAim(level.getGameTime(), 0, guard.getEyePosition(), origin, target.getEyePosition(),
                new Vec3(-Math.sin(y)*Math.cos(p), -Math.sin(p), Math.cos(y)*Math.cos(p)), yaw, pitch);
    }
}
