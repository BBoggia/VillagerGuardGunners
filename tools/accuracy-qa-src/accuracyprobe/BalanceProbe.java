package accuracyprobe;

import com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.google.gson.Gson;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Packaged gameplay observation with ordinary AI and real connected players. */
@Mod.EventBusSubscriber(modid="vggaccuracyprobe")
public final class BalanceProbe {
    public static final class Scenario {
        String id, gun="tacz:ak47", target="player", motion="stationary", point="CENTER_MASS", attachment="";
        String mode="AUTO";
        int distance=16, guards=1, seed=1, duration=160, range=32;
        double error=0;
        boolean override=true;
    }
    private static final Gson JSON = new Gson();
    private static Scenario[] scenarios;
    private static Scenario scenario;
    private static final List<Guard> guards = new ArrayList<>();
    private static final List<Entity> extras = new ArrayList<>();
    private static final List<BlockPos> blocks = new ArrayList<>();
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static final Set<UUID> hitBullets = new HashSet<>();
    private static final Set<Object> roundAims = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String,Integer> safety = new TreeMap<>();
    private static final List<Double> tickMillis = new ArrayList<>();
    private static LivingEntity target;
    private static Villager ally;
    private static ServerPlayer player;
    private static int index=-1, ticks, cooldown=80, waiting, shots, hitEvents, headshots, canceled, firstShot;
    private static double damage, allyDamage, maximumError;
    private static int invalidAim, forbiddenShots;
    private static boolean finished, initialized;
    private static long tickStart;

    public static boolean enabled() { return System.getProperty("vgg.balance.dir") != null; }
    public static Path output() { return Path.of(System.getProperty("vgg.balance.dir")); }
    private static void write(String name,Object value) {
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve(name),JSON.toJson(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static void event(String name,Map<String,Object> fields) {
        Map<String,Object> row=new LinkedHashMap<>(fields);
        row.put("event",name); row.put("scenario",scenario.id); row.put("tick",ticks);
        write("events.jsonl",row);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent e) {
        if(!enabled() || finished) return;
        if(e.phase==TickEvent.Phase.START) {tickStart=System.nanoTime(); return;}
        try {
            ServerLevel level=e.getServer().overworld();
            if(!initialized) {
                scenarios=JSON.fromJson(Files.readString(Path.of(System.getProperty("vgg.balance.scenarios"))),Scenario[].class);
                e.getServer().setDifficulty(Difficulty.NORMAL,true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,e.getServer());
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,e.getServer());
                level.setDayTime(6000);
                for(int x=-1;x<=5;x++) for(int z=-1;z<=1;z++) level.setChunkForced(x,z,true);
                for(int x=-12;x<=72;x++) for(int z=-14;z<=14;z++) level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
                initialized=true;
            }
            if(target!=null) tickMillis.add((System.nanoTime()-tickStart)/1e6);
            var players=e.getServer().getPlayerList().getPlayers();
            if(players.isEmpty() || players.get(0).isDeadOrDying()) {
                if(target!=null && !target.isAlive()) {ticks++;finish(level);}
                if(++waiting>1800) throw new IllegalStateException("No living connected player");
                return;
            }
            player=players.get(0); waiting=0;
            if(target==null) {
                if(cooldown-- >0) return;
                if(++index==scenarios.length) {finished=true;write("complete.json",Map.of("completed",true,"encounters",results.size()));return;}
                scenario=scenarios[index]; begin(level); return;
            }
            ticks++;
            if(!target.isAlive() || ticks>=scenario.duration) {finish(level);return;}
            update(level);
        } catch(Throwable failure) {
            finished=true; failure.printStackTrace();
            write("complete.json",Map.of("completed",false,"failure",failure.toString(),"scenario",index));
        }
    }
    private static void block(ServerLevel level,int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);blocks.add(pos);
    }
    private static void clearBlocks(ServerLevel level) {
        for(BlockPos p:blocks) level.setBlock(p,Blocks.AIR.defaultBlockState(),3);blocks.clear();
    }
    private static void begin(ServerLevel level) {
        var config=ModConfigs.GUARD_GUNNERS;
        config.enabled.accept(true);config.combat.maximumEngagementRange.accept((double)scenario.range);
        config.combat.aiming.errorDegrees.accept(scenario.override?0:scenario.error);
        config.combat.aiming.aimPoint.accept(GuardAimPoint.valueOf(scenario.override?"EYE_LEVEL":scenario.point));
        config.combat.aiming.overridePlayers.accept(scenario.override);
        config.combat.aiming.playerErrorDegrees.accept(scenario.error);
        config.combat.aiming.playerAimPoint.accept(GuardAimPoint.valueOf(scenario.point));
        if(scenario.motion.equals("live-config")) config.combat.aiming.playerErrorDegrees.accept(0.0);
        player.setGameMode(scenario.target.equals("husk")?GameType.SPECTATOR:
                scenario.motion.equals("creative")?GameType.CREATIVE:
                scenario.motion.equals("spectator")?GameType.SPECTATOR:GameType.SURVIVAL);
        player.setForcedPose(scenario.motion.equals("crouch")?Pose.CROUCHING:null);
        player.getInventory().clearContent();player.removeAllEffects();player.setHealth(20);player.setAbsorptionAmount(0);
        player.getFoodData().setFoodLevel(20);player.invulnerableTime=0;
        if(scenario.target.equals("husk")) {
            var husk=EntityType.HUSK.create(level);husk.setNoAi(true);husk.setPersistenceRequired();
            husk.setPos(scenario.distance+.5,80,.5);level.addFreshEntity(husk);target=husk;
            player.teleportTo(level,-8,85,-12,0,0);
        } else target=player;
        double gy=scenario.motion.equals("guard-high")?84:80;
        if(gy>80) for(int z=-12;z<=12;z++) block(level,0,83,z);
        if(scenario.motion.equals("elevated")) for(int x=scenario.distance-2;x<=scenario.distance+2;x++) for(int z=-2;z<=2;z++) block(level,x,83,z);
        if(scenario.motion.equals("partial-cover")) for(int z=-2;z<=2;z++) block(level,scenario.distance-2,80,z);
        if(scenario.motion.equals("occlusion")) for(int y=80;y<=83;y++) for(int z=-4;z<=4;z++) block(level,8,y,z);
        if(scenario.motion.equals("near-wall")) {
            // Eye-level visibility clears this slab; the lower center-mass rocket path hits it.
            BlockPos pos=new BlockPos(5,81,0);
            level.setBlock(pos,Blocks.STONE_SLAB.defaultBlockState(),3);blocks.add(pos);
        }
        for(int n=0;n<scenario.guards;n++) {
            Guard g=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
            // Pair the same per-guard seed across error policies; never reuse a UUID within a live group.
            g.setUUID(UUID.nameUUIDFromBytes(("balance-"+scenario.seed+"-"+n).getBytes(StandardCharsets.UTF_8)));
            g.setPos(.5,gy,.5+(n-(scenario.guards-1)/2.0)*3);
            g.setInvulnerable(true);g.setPersistenceRequired();g.getRandom().setSeed(scenario.seed*31L+n);
            for(var slot:EquipmentSlot.values()) g.setItemSlot(slot,ItemStack.EMPTY);
            GuardOrdersAccess.setOrders(g,GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION)
                    .withEngagement(GuardOrders.EngagementOrder.GUARD_TARGETS).withExplosives(GuardOrders.ToggleOrder.ENABLED));
            if(scenario.motion.equals("owner")) g.setOwnerId(player.getUUID());
            ItemStack gun=GunItemBuilder.create().setId(ResourceLocation.parse(scenario.gun))
                    .setFireMode(FireMode.valueOf(scenario.mode)).setAmmoCount(scenario.gun.endsWith("glock_17")?17:30).setAmmoInBarrel(true).build();
            if(!scenario.attachment.isEmpty()) {
                ItemStack attachment=AttachmentItemBuilder.create().setId(ResourceLocation.parse(scenario.attachment)).build();
                if(!((IGun)gun.getItem()).allowAttachment(gun,attachment)) throw new IllegalStateException("Invalid fixture attachment");
                ((IGun)gun.getItem()).installAttachment(gun,attachment);
            }
            g.setItemSlot(EquipmentSlot.MAINHAND,gun);level.addFreshEntity(g);guards.add(g);g.setTarget(target);
        }
        if(scenario.motion.startsWith("ally-")) {
            ally=EntityType.VILLAGER.create(level);ally.setNoAi(true);ally.setPersistenceRequired();
            double x=scenario.motion.equals("ally-behind")?scenario.distance+12:8;
            double z=scenario.motion.equals("ally-beside")?3:scenario.motion.equals("ally-crossing")?5:.5;
            ally.setPos(x+.5,80,z);level.addFreshEntity(ally);
        }
        shots=hitEvents=headshots=canceled=invalidAim=forbiddenShots=0;firstShot=-1;ticks=0;
        damage=allyDamage=maximumError=0;hitBullets.clear();roundAims.clear();safety.clear();tickMillis.clear();
        update(level);
        event("begin",Map.of("settings",scenario,"playerClass",player.getClass().getName(),"dedicated",eDedicated(level)));
    }
    private static boolean eDedicated(ServerLevel level) {return level.getServer().isDedicatedServer();}
    private static void update(ServerLevel level) {
        double x=scenario.distance+.5,z=.5,y=scenario.motion.equals("elevated")?84:80;
        if(scenario.motion.equals("strafe")) z+=2*Math.sin(ticks*.12);
        if(scenario.motion.equals("approach")) x=Math.max(4.5,x-ticks*.18);
        if(target instanceof ServerPlayer p) p.teleportTo(level,x,y,z,90,0); else target.setPos(x,y,z);
        target.setDeltaMovement(Vec3.ZERO);
        boolean transition=scenario.motion.equals("live-config")||scenario.motion.equals("hold-fire")||scenario.motion.equals("disable")||scenario.motion.equals("target-switch");
        if(transition) target.setInvulnerable(ticks<(scenario.motion.equals("live-config")?20:60));
        if(scenario.motion.equals("occlusion")&&ticks==40) clearBlocks(level);
        if(scenario.motion.equals("live-config")&&ticks==20) ModConfigs.GUARD_GUNNERS.combat.aiming.playerErrorDegrees.accept(scenario.error);
        if((scenario.motion.equals("hold-fire")||scenario.motion.equals("disable"))&&(ticks==20||ticks==60)) {
            if(scenario.motion.equals("disable")) ModConfigs.GUARD_GUNNERS.enabled.accept(ticks==60);
            else for(Guard g:guards) GuardOrdersAccess.setOrders(g,GuardOrdersAccess.getOrders(g).withEngagement(
                    ticks==20?GuardOrders.EngagementOrder.HOLD_FIRE:GuardOrders.EngagementOrder.GUARD_TARGETS));
        }
        if(scenario.motion.equals("target-switch")&&ticks==60) {
            LivingEntity old=target;var next=EntityType.HUSK.create(level);next.setNoAi(true);next.setPos(x,y,z+2);
            level.addFreshEntity(next);target=next;extras.add(old);
        }
        if(ally!=null) {
            double ax=scenario.motion.equals("ally-behind")?scenario.distance+12:8;
            double az=scenario.motion.equals("ally-beside")?3:scenario.motion.equals("ally-crossing")?5*Math.cos((ticks-4)*.25):.5;
            ally.setPos(ax+.5,80,az);ally.setDeltaMovement(Vec3.ZERO);
        }
        for(Guard g:guards) g.setTarget(target);
    }
    private static void finish(ServerLevel level) {
        Map<String,Object> r=new LinkedHashMap<>();r.put("settings",scenario);r.put("killed",!target.isAlive());r.put("ticks",ticks);
        r.put("firstShotTick",firstShot);r.put("projectiles",shots);r.put("rounds",roundAims.size());r.put("hitProjectiles",hitBullets.size());
        r.put("hitEvents",hitEvents);r.put("headshots",headshots);r.put("damage",damage);r.put("allyDamage",allyDamage);
        r.put("canceledRounds",canceled);r.put("safety",new TreeMap<>(safety));r.put("maxAimError",maximumError);
        r.put("invalidAim",invalidAim);r.put("forbiddenShots",forbiddenShots);r.put("tickMillis",List.copyOf(tickMillis));
        int ammo=0;for(Guard g:guards) {ItemStack stack=g.getMainHandItem();IGun gun=(IGun)stack.getItem();ammo+=gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0);}
        r.put("ammoRemaining",ammo);results.add(r);write("encounters.jsonl",r);
        for(Guard g:guards) g.discard();guards.clear();
        if(!(target instanceof ServerPlayer)) target.discard();target=null;
        for(Entity extra:extras) extra.discard();extras.clear();
        if(ally!=null) {ally.discard();ally=null;}
        player.setInvulnerable(false);player.setForcedPose(null);
        List<Entity> cleanup=new ArrayList<>();for(Entity entity:level.getAllEntities()) if(entity instanceof EntityKineticBullet) cleanup.add(entity);
        cleanup.forEach(Entity::discard);clearBlocks(level);cooldown=80;
    }
    /** Called by the separate observer mixin after the full native callback, including late refusals. */
    public static void recordFinalRound(Guard guard, boolean accepted) {
        if(!enabled() || !guards.contains(guard)) return;
        var controller=GuardNativeShotAccess.controller(guard);
        var result=controller.currentShotSafety(guard.getTarget());
        safety.merge(result.status().name(),1,Integer::sum);
        // TaCZ can continue a burst after a canceled event; callback true alone is not approval.
        if(!accepted || !result.clear()) canceled++;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void projectile(EntityJoinLevelEvent e) {
        if(!enabled()||target==null||e.getLevel().isClientSide||e.isCanceled()||!(e.getEntity() instanceof EntityKineticBullet b)||!guards.contains(b.getOwner())) return;
        Guard g=(Guard)b.getOwner();var c=GuardNativeShotAccess.controller(g);var aim=c.approvedShotAim();shots++;
        if(firstShot<0) firstShot=ticks;
        if(aim==null||!c.lastShotSafety().clear()||c.lastShotSafety().envelope().aim()!=aim) {invalidAim++;return;}
        roundAims.add(aim);
        Vec3 direction=aim.targetPoint().subtract(aim.aimOrigin()).normalize();
        double offset=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,direction.dot(aim.direction())))));
        var policy=ModConfigs.GUARD_GUNNERS.combat.aiming.resolve(target instanceof ServerPlayer);
        maximumError=Math.max(maximumError,offset);
        if(offset>policy.errorDegrees()+.002||aim.projectileOrigin().distanceToSqr(b.position())>1e-10) invalidAim++;
        if((scenario.motion.equals("hold-fire")||scenario.motion.equals("disable"))&&ticks>=20&&ticks<60) forbiddenShots++;
        if(scenario.motion.equals("occlusion")&&ticks<40) forbiddenShots++;
        if(scenario.motion.endsWith("-switch") && !scenario.motion.equals("target-switch")) {
            if(shots>1) forbiddenShots++;
            // Change eligibility after round one, while the native burst still has queued rounds.
            if(shots==1) player.setGameMode(scenario.motion.equals("creative-switch")?GameType.CREATIVE:GameType.SPECTATOR);
        }
        event("projectile",Map.of("id",b.getUUID(),"guard",g.getUUID(),"offset",offset,"configuredError",policy.errorDegrees(),
                "point",policy.aimPoint().name(),"shotIndex",aim.shotIndex()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void hit(EntityHurtByGunEvent.Pre e) {
        if(!enabled()||target==null||e.getLogicalSide().isClient()||!guards.contains(e.getAttacker())) return;
        if(e.getHurtEntity()==target&&!e.isCanceled()) {hitEvents++;if(e.isHeadShot())headshots++;hitBullets.add(e.getBullet().getUUID());}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent e) {
        if(!enabled()||target==null||e.getEntity().level().isClientSide||!guards.contains(e.getSource().getEntity())) return;
        if(e.getEntity()==target) damage+=e.getAmount();if(e.getEntity()==ally) allyDamage+=e.getAmount();
    }
}
