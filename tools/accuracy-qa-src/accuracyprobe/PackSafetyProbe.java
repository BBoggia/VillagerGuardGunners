package accuracyprobe;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.combat.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Native firing regression fixture; install the published gun packs with the normal mod JARs. */
@Mod.EventBusSubscriber(modid = "vggaccuracyprobe")
public final class PackSafetyProbe {
    private static final List<String> GUNS = List.of("tacz:glock_17", "tacz:spas_12", "elitex:ak47",
            "elitex:inter_m200", "elitex:m249x", "elitex:dual_hr", "elitex:lee", "elitex:trs_bull",
            "maxstuff:db_50", "daffas_arsenal:m79");
    private static final List<String> SCENES = List.of("open", "front", "behind", "event-ally", "resume");
    private static final BlockPos WALL = new BlockPos(6,101,0);
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static int index=Integer.getInteger("vgg.packSafety.start",0)-1, ticks, fired, events, initialAmmo;
    private static Guard guard;
    private static Mob target, ally;
    private static NativeGunController controller;
    private static boolean finished, moved, arenaReady;
    private static String failure;
    private static final Map<String,Integer> statuses = new TreeMap<>();
    private static double distance;
    private static long checkNanos;
    private static int checks;

    private static String gunId() { return GUNS.get(Math.min(index/SCENES.size(), GUNS.size()-1)); }
    private static String scene() { return index<GUNS.size()*SCENES.size() ? SCENES.get(index%SCENES.size()) : "wall"; }
    private static Path output() { return Path.of(System.getProperty("vgg.packSafety.output")); }
    private static int ammo() {
        var stack=guard.getMainHandItem(); var item=(IGun)stack.getItem();
        return item.getCurrentAmmoCount(stack)+(item.hasBulletInBarrel(stack)?1:0);
    }
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(gunId()+"/"+scene()+": "+message);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(System.getProperty("vgg.packSafety.output")==null || finished || event.phase!=TickEvent.Phase.END) return;
        try {
            var level=event.getServer().overworld();
            if(guard==null) {
                if(++index>=GUNS.size()*SCENES.size()+1) { finish(event, ""); return; }
                begin(level);
            }
            ticks++;
            guard.setPos(.5,100,.5); guard.xOld=.5; guard.yOld=100; guard.zOld=.5;
            guard.setDeltaMovement(Vec3.ZERO); guard.setOnGround(true);
            target.setPos(.5+distance,100,.5); target.setDeltaMovement(Vec3.ZERO);
            if(scene().equals("resume") && ticks==100) {
                require(events>0 && fired==0 && ammo()==initialAmmo,"Blocked stage fired or consumed ammo");
                ally.discard();
            }
            controller.tick(target,true,()->true,()->true);
            statuses.merge(controller.lastShotSafety().status().name(),1,Integer::sum);
            if(failure!=null) throw new AssertionError(failure);
            if(ticks>=220) {
                boolean blocked=Set.of("front","behind","event-ally","wall").contains(scene());
                require(events>0,"No native firing attempt");
                require(blocked ? fired==0 && ammo()==initialAmmo : fired>0,
                        "Unexpected firing/ammo: "+fired+"/"+(initialAmmo-ammo())+" "+statuses);
                if(blocked) require(statuses.containsKey(scene().equals("wall")?"EXPLOSIVE_BLOCK_IMPACT":"PROTECTED_ENTITY_IN_PATH"),"Wrong refusal: "+statuses);
                if(scene().equals("event-ally")) require(moved,"Fire-event mutation not exercised");
                if(ally!=null) require(ally.getHealth()==ally.getMaxHealth(),"Ally lost health");
                var row=new LinkedHashMap<String,Object>();
                row.put("gun",gunId()); row.put("scene",scene()); row.put("projectiles",fired);
                row.put("fireEvents",events); row.put("ammoUsed",initialAmmo-ammo());
                row.put("statuses",new TreeMap<>(statuses)); row.put("recheckMeanMicros",checks==0?0:checkNanos/1000.0/checks);
                results.add(row);
                Files.writeString(output().resolveSibling("progress.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
                controller.stopAiming(); guard.discard(); target.discard(); if(ally!=null) ally.discard();
                level.setBlockAndUpdate(WALL,Blocks.AIR.defaultBlockState());
                var bullets=new ArrayList<Entity>(); for(var entity:level.getAllEntities()) if(entity instanceof EntityKineticBullet) bullets.add(entity);
                bullets.forEach(Entity::discard); guard=null;
            }
        } catch(Throwable error) { error.printStackTrace(); finish(event,error.toString()); }
    }
    private static void begin(ServerLevel level) throws Exception {
        if(!arenaReady) {
            arenaReady=true;
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
            for(int x=-2;x<=4;x++) for(int z=-2;z<=2;z++) level.setChunkForced(x,z,true);
            for(int x=-16;x<=64;x++) for(int z=-16;z<=16;z++) level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
        }
        ticks=fired=events=checks=0; checkNanos=0; moved=false; failure=null; ally=null; statuses.clear();
        var settings=ModConfigs.GUARD_GUNNERS.combat.aiming;
        settings.errorDegrees.accept(0.0); settings.overridePlayers.accept(false); settings.aimPoint.accept(scene().equals("wall")?GuardAimPoint.CENTER_MASS:GuardAimPoint.EYE_LEVEL);
        distance=gunId().equals("maxstuff:db_50")?6.5:gunId().equals("daffas_arsenal:m79")?31.5:25;
        target=EntityType.HUSK.create(level); target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
        target.setPos(.5+distance,100,.5); level.addFreshEntity(target);
        guard=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        guard.setNoAi(true); guard.setNoGravity(true); guard.setInvulnerable(true); guard.setOnGround(true); guard.setPos(.5,100,.5);
        var id=ResourceLocation.parse(gunId()); var data=TimelessAPI.getCommonGunIndex(id).orElseThrow().getGunData();
        guard.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(id).setFireMode(data.getFireModeSet().get(0))
                .setAmmoCount(100).setAmmoInBarrel(true).build());
        level.addFreshEntity(guard); guard.setTarget(target);
        if(!scene().equals("open") && !scene().equals("wall")) {
            ally=EntityType.VILLAGER.create(level); ally.setNoAi(true); ally.setNoGravity(true);
            ally.setPos(scene().equals("behind")?distance+8.5:distance/2+.5,100,scene().equals("event-ally")?20.5:.5);
            level.addFreshEntity(ally);
        }
        // A low wall leaves eye-to-eye visibility clear while threatening the shot envelope.
        if(scene().equals("wall")) level.setBlockAndUpdate(WALL,Blocks.STONE_SLAB.defaultBlockState());
        controller = (NativeGunController) com.gamergaming.villagerguardgunners.guns.GuardWeaponControlAccess
                .control(guard).orElseThrow().active();
        controller.startAiming();
        initialAmmo = ammo();
    }
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void fire(GunFireEvent event) {
        if(guard==null || event.getLogicalSide().isClient() || event.getShooter()!=guard) return;
        events++;
        if(!event.isCanceled() && scene().equals("event-ally") && !moved) {
            ally.setPos(distance/2+.5,100,.5); moved=true;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void projectile(EntityJoinLevelEvent event) {
        if(guard==null || event.isCanceled() || !(event.getEntity() instanceof EntityKineticBullet bullet) || bullet.getOwner()!=guard) return;
        try {
            fired++;
            var approved=controller.lastShotSafety(); require(approved.clear(),"Unapproved native projectile");
            long started=System.nanoTime();
            var again=GuardFriendlyFireSafety.diagnoseShot(guard,target,controller.approvedShotAim(),
                    approved.envelope().ballistics(),approved.envelope().clearance());
            checkNanos+=System.nanoTime()-started; checks++;
            require(again.clear(),"Insertion scene no longer clear");
        } catch(Throwable error) { failure=error.toString(); }
    }
    private static void finish(TickEvent.ServerTickEvent event,String error) {
        finished=true;
        try { Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed",error.isEmpty(),"cases",results,"expectedCases",GUNS.size()*SCENES.size()+1-Integer.getInteger("vgg.packSafety.start",0),"failure",error))); }
        catch(Exception exception) { throw new RuntimeException(exception); }
        event.getServer().halt(false);
    }
}
