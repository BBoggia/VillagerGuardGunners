package accuracyprobe;

import com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Native firing mechanics fixture; ordinary server JAR, real projectiles, no replacement shooter. */
@Mod.EventBusSubscriber(modid = "vggaccuracyprobe")
public final class FiringProbe {
    private record Case(String name, String gun, FireMode mode, double error, String change) {}
    private static final List<Case> CASES = List.of(
        new Case("single-zero", "glock_17", FireMode.SEMI, 0, ""),
        new Case("single-error", "glock_17", FireMode.SEMI, 6, ""),
        new Case("automatic", "ak47", FireMode.AUTO, 6, ""),
        new Case("subtick", "vgg_aim_qa:fast", FireMode.AUTO, 6, ""),
        new Case("moving-origin", "glock_17", FireMode.SEMI, 3, "motion"),
        new Case("charge", "vgg_aim_qa:charge", FireMode.SEMI, 3, ""),
        new Case("burst", "m16a4", FireMode.BURST, 6, ""),
        new Case("shotgun", "aa12", FireMode.AUTO, 6, ""),
        new Case("bolt", "m700", FireMode.SEMI, 3, ""),
        new Case("cancel-event", "glock_17", FireMode.SEMI, 6, "cancel"),
        new Case("cancel-spawn", "glock_17", FireMode.SEMI, 6, "spawn"),
        new Case("burst-target-change", "m16a4", FireMode.BURST, 6, "target"),
        new Case("burst-policy-change", "m16a4", FireMode.BURST, 6, "policy"),
        new Case("burst-stop", "m16a4", FireMode.BURST, 6, "stop"),
        new Case("burst-ally-crossing", "m16a4", FireMode.BURST, 0, "ally"),
        new Case("burst-fire-mode-change", "m16a4", FireMode.BURST, 6, "mode"),
        new Case("burst-weapon-change", "m16a4", FireMode.BURST, 6, "weapon"),
        new Case("dry-resupply", "glock_17", FireMode.SEMI, 3, "resupply"),
        new Case("stop-restart", "glock_17", FireMode.SEMI, 3, "restart"),
        new Case("sidearm-swap", "m16a4", FireMode.BURST, 3, "swap"),
        new Case("center-mass", "glock_17", FireMode.SEMI, 0, "center")
    );
    private static int index=Integer.getInteger("vgg.firing.startIndex", -1), ticks, fired, canceled, rejectedSpawns, initialAmmo;
    private static Guard guard;
    private static LivingEntity target;
    private static Villager ally;
    private static NativeGunController controller;
    private static ItemStack original;
    private static boolean finished, changed, dryObserved, reloadObserved;
    private static int pausedShots;
    private static ItemStack replacement;
    private static String failure;
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static final Set<Object> aimObjects = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Long> samples = new ArrayList<>();
    private static final Set<Long> rounds = new LinkedHashSet<>();
    private static final Map<Long, Set<Long>> tickRounds = new HashMap<>();
    private static final List<Double> offsets = new ArrayList<>();

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (System.getProperty("vgg.firing.output") == null || finished || event.phase != TickEvent.Phase.END) return;
        ServerLevel level=event.getServer().overworld();
        try {
            if (guard == null) {
                if (++index == CASES.size()) { complete(event, true); return; }
                begin(level);
            }
            ticks++;
            var c=CASES.get(index);
            guard.setPos(.5,100,.5); guard.setDeltaMovement(Vec3.ZERO);
            target.setPos(16.5,100,.5); target.setDeltaMovement(Vec3.ZERO);
            if (c.change.equals("motion")) {
                guard.xOld = guard.getX() - .4; guard.yOld = guard.getY() - .2; guard.zOld = guard.getZ() + .3;
                guard.setDeltaMovement(new Vec3(.4,.2,-.3));
            }
            if (!changed) guard.setTarget(target);
            // Allow normal native draw/aim timers; all rounds use production controller and native scripts.
            boolean restarting = c.change.equals("restart") && ticks >= 35 && ticks < 65;
            if (c.change.equals("restart") && ticks == 35) { controller.stopAiming(); pausedShots=fired; }
            if (restarting && fired != pausedShots) throw new AssertionError("Shot during goal stop");
            if (c.change.equals("restart") && ticks == 65) controller.startAiming();
            if (c.change.equals("swap") && ticks == 40) {
                controller.prepareForSidearmTransition();
                replacement=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17"))
                        .setFireMode(FireMode.SEMI).setAmmoCount(100).setAmmoInBarrel(true).build();
                guard.setItemSlot(EquipmentSlot.MAINHAND,replacement); controller.refreshAfterSidearmTransaction();
                pausedShots=fired;
            }
            if (c.change.equals("resupply") && ticks == 80) {
                com.gamergaming.villagerguardgunners.api.GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,
                        com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:9mm")).setCount(64).build());
            }
            if (!changed && !restarting) controller.tick(target,true,()->true,()->true);
            dryObserved |= controller.isCurrentWeaponDryConfirmed();
            reloadObserved |= com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).getSynReloadState().getStateType().isReloading();
            if (fired > 0 && !changed) {
                switch(c.change) {
                    case "target" -> { guard.setTarget(null); changed=true; }
                    case "policy" -> { ModConfigs.GUARD_GUNNERS.combat.aiming.errorDegrees.accept(1.0); changed=true; }
                    case "stop" -> { controller.stopAiming(); changed=true; }
                    case "mode" -> { ((IGun)original.getItem()).setFireMode(original,FireMode.SEMI); changed=true; }
                    case "weapon" -> { guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); changed=true; }
                    case "ally" -> { ally=EntityType.VILLAGER.create(level); ally.setNoAi(true); ally.setNoGravity(true);
                        ally.setPos(8.5,100,.5); level.addFreshEntity(ally); changed=true; }
                    default -> {}
                }
            }
            if (failure != null) throw new AssertionError(failure);
            if (ticks >= (c.change.equals("resupply") ? 180 : 100)) {
                long committed=controller.successfulAimRounds();
                int used=initialAmmo-ammo(original);
                if (c.change.equals("resupply")) {
                    used += 64 - com.gamergaming.villagerguardgunners.api.GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0).getCount();
                    if (!dryObserved || !reloadObserved || fired <= initialAmmo) throw new AssertionError("Dry/resupply/reload did not complete");
                }
                if (replacement != null) { used += 101-ammo(replacement); if(fired <= pausedShots) throw new AssertionError("Sidearm did not resume firing"); }
                if(c.change.equals("restart") && fired <= pausedShots) throw new AssertionError("Goal did not resume firing");
                if (fired==0) throw new AssertionError(c.name+" did not fire: "+controller.lastShotSafety()+" profile="+controller.captureShotBallistics(1)+" native="+controller.captureDiagnostics());
                if (committed != aimObjects.size()) throw new AssertionError("Round commits != distinct aim samples");
                if (used != committed + rejectedSpawns) throw new AssertionError("Ammo/round mismatch: "+used+"/"+committed);
                if (samples.get(0)!=0) throw new AssertionError("First emitted sample advanced before emission");
                long expected=0;
                for (long sample:rounds) if (!c.change.equals("restart") && !c.change.equals("swap") && sample!=expected++) throw new AssertionError("Skipped/reused round index");
                if (!changed && committed < 2) throw new AssertionError("Native cycling stopped after first round");
                if (changed && committed!=1) throw new AssertionError("Burst survived invalidation");
                if (c.change.equals("cancel") && canceled!=1) throw new AssertionError("Missing cancellation");
                if (c.change.equals("spawn") && rejectedSpawns!=1) throw new AssertionError("Missing rejected insertion");
                if (c.name.equals("subtick") && tickRounds.values().stream().noneMatch(set -> set.size() > 1)) throw new AssertionError("No multiple-round tick observed: "+committed+" rounds, "+controller.captureDiagnostics());
                if (c.name.equals("shotgun") && fired<=committed) throw new AssertionError("No native pellet grouping");
                if (c.error==0 && offsets.stream().anyMatch(x->x>.002)) throw new AssertionError("Zero error changed intended aim");
                if (c.error>0 && offsets.stream().allMatch(x->x<.01)) throw new AssertionError("Aim error inactive");
                results.add(Map.of("case",c.name,"projectiles",fired,"rounds",committed,"ammoUsed",used,
                    "canceledEvents",canceled,"rejectedSpawns",rejectedSpawns,"offsetDegrees",List.copyOf(offsets)));
                Files.writeString(Path.of(System.getProperty("vgg.firing.output")).resolveSibling("progress.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(results));
                controller.stopAiming(); guard.discard(); target.discard(); if(ally!=null) ally.discard();
                List<Entity> cleanup=new ArrayList<>();
                for(Entity entity:level.getAllEntities()) if(entity instanceof EntityKineticBullet) cleanup.add(entity);
                cleanup.forEach(Entity::discard);
                guard=null;
            }
        } catch(Throwable error) { failure=error.toString(); error.printStackTrace(); complete(event,false); }
    }

    private static void begin(ServerLevel level) {
        var c=CASES.get(index);
        level.setChunkForced(0,0,true);
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.getChunk(x,z);
        ticks=fired=canceled=rejectedSpawns=0; dryObserved=reloadObserved=false; replacement=null; aimObjects.clear(); changed=false; ally=null; samples.clear();rounds.clear();offsets.clear();tickRounds.clear();
        var aiming=ModConfigs.GUARD_GUNNERS.combat.aiming;
        aiming.errorDegrees.accept(c.error); aiming.overridePlayers.accept(false);
        aiming.aimPoint.accept(c.change.equals("center")?GuardAimPoint.CENTER_MASS:GuardAimPoint.EYE_LEVEL);
        var husk=EntityType.HUSK.create(level); husk.setNoAi(true); husk.setNoGravity(true); husk.setInvulnerable(true);
        husk.setPos(16.5,100,.5); level.addFreshEntity(husk); target=husk;
        guard=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        guard.setNoAi(true); guard.setNoGravity(true); guard.setInvulnerable(true); guard.setPos(.5,100,.5); guard.setOnGround(true);
        original=GunItemBuilder.create().setId(c.gun.contains(":") ? ResourceLocation.parse(c.gun) : ResourceLocation.fromNamespaceAndPath("tacz",c.gun))
            .setFireMode(c.mode).setAmmoCount(c.change.equals("resupply") ? 1 : 100).setAmmoInBarrel(true).build();
        guard.setItemSlot(EquipmentSlot.MAINHAND,original); level.addFreshEntity(guard); guard.setTarget(target);
        initialAmmo=ammo(original);
        controller=new NativeGunController(guard); controller.startAiming();
    }

    @SubscribeEvent(priority=EventPriority.HIGH) public static void fire(GunFireEvent event) {
        if(guard==null || event.getShooter()!=guard || event.getLogicalSide().isClient() || event.isCanceled()) return;
        if(CASES.get(index).change.equals("cancel") && canceled==0) { event.setCanceled(true); canceled++; }
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void rejectSpawn(EntityJoinLevelEvent event) {
        if(guard==null || !(event.getEntity() instanceof EntityKineticBullet b) || b.getOwner()!=guard) return;
        if(CASES.get(index).change.equals("spawn") && rejectedSpawns==0) { event.setCanceled(true); rejectedSpawns++; }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void projectile(EntityJoinLevelEvent event) {
        if(guard==null || event.isCanceled() || !(event.getEntity() instanceof EntityKineticBullet b) || b.getOwner()!=guard) return;
        var aim=controller.approvedShotAim();
        if(aim==null) {failure="Native projectile had no approved aim";return;}
        aimObjects.add(aim);
        fired++; samples.add(aim.shotIndex()); rounds.add(aim.shotIndex());
        tickRounds.computeIfAbsent(aim.gameTick(), ignored -> new HashSet<>()).add(aim.shotIndex());
        if (aim.projectileOrigin().distanceToSqr(b.position()) > 1.0E-10) failure="Native projectile origin differs from approved origin";
        Vec3 expected=controller.intentionalAimPoint(target).subtract(guard.getEyePosition()).normalize();
        double offset=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,expected.dot(aim.direction())))));
        offsets.add(offset);
        if(offset>CASES.get(index).error+.002) failure="Guard error exceeded configured cone";
        if(!controller.lastShotSafety().clear() || controller.lastShotSafety().envelope().aim()!=aim)
            failure="Projectile did not consume the approved safety geometry";
        if(!controller.lastShotSafety().envelope().intersects(net.minecraft.world.phys.AABB.ofSize(
                b.position().add(b.getDeltaMovement().scale(.5)),.001,.001,.001),0)) failure="Native velocity escaped approved envelope: case="+CASES.get(index)+" aim="+aim+" position="+b.position()+" velocity="+b.getDeltaMovement()+" profile="+controller.lastShotSafety().envelope().ballistics();
    }
    private static int ammo(ItemStack stack) { IGun gun=(IGun)stack.getItem(); return gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0); }
    private static void complete(TickEvent.ServerTickEvent event, boolean passed) {
        finished=true;
        try {
            Path path=Path.of(System.getProperty("vgg.firing.output")); Files.createDirectories(path.getParent());
            Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",passed,
                "cases",results,"failure",failure==null?"":failure,"expectedCases",CASES.size(),
                "scope","Packaged native mechanics; scripted no-AI entities; not a PvP balance test")));
        } catch(Exception error){throw new RuntimeException(error);}
        event.getServer().halt(false);
    }
}
