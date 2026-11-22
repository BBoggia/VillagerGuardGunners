package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.api.item.gun.FireMode;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

@Mod.EventBusSubscriber(modid="villagerguardgunners")
public final class CombatServerQa {
    static final Path DIR=Path.of(System.getProperty("combat.qa.dir"));
    static final Gson JSON=new Gson();
    static int scene=-1, ticks, waiting; static Guard guard; static final List<Husk> enemies=new ArrayList<>();
    static BlockPos origin; static double startX,startZ,maxTravel; static boolean aim,reload,sidearm; static int firstAmmo,minAmmo,melee;
    static String[] names={"loaded","dry","reload","sidearm"};
    static void write(String file,Object value)throws Exception {Files.writeString(DIR.resolve(file),JSON.toJson(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();
        try {
            if(server.getPlayerList().getPlayerCount()<Integer.getInteger("combat.qa.clients",2)){if(++waiting>3600)throw new AssertionError("Two clients did not join");return;}
            var level=server.overworld();
            if(scene<0){origin=findMeadow(level);write("terrain.json",Map.of("origin",origin.toShortString(),"modifiedBlocks",0,"seed",level.getSeed(),"generator",level.getChunkSource().getGenerator().getClass().getSimpleName()));begin(level);}
            ticks++;
            if(ticks==100&&scene==3){var old=enemies.get(0);var replacement=enemy(level,origin.offset(15,0,3));guard.setTarget(replacement);old.discard();}
            var stack=guard.getMainHandItem();var gun=(IGun)stack.getItem();int ammo=gun.getCurrentAmmoCount(stack);minAmmo=Math.min(minAmmo,ammo);
            var operator=IGunOperator.fromLivingEntity(guard);aim|=operator.getSynIsAiming();reload|=operator.getSynReloadState().getStateType().isReloading();
            sidearm|=gun.getGunId(stack).getPath().equals("glock_17");
            maxTravel=Math.max(maxTravel,Math.hypot(guard.getX()-startX,guard.getZ()-startZ));
            if(guard.goalSelector.getRunningGoals().anyMatch(g->g.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal))melee++;
            write("server-samples.jsonl",Map.of("scene",names[scene],"tick",level.getGameTime(),"id",guard.getId(),"x",guard.getX(),"y",guard.getY(),"z",guard.getZ(),"gun",gun.getGunId(stack).toString(),"ammo",ammo,"aim",operator.getSynIsAiming()));
            if(ticks==240){GuardOrdersAccess.setOrders(guard,GuardOrdersAccess.getOrders(guard).withMovement(GuardOrders.MovementOrder.HOLD_POSITION).withEngagement(GuardOrders.EngagementOrder.HOLD_FIRE));}
            if(ticks>=300){
                require(melee==0,"Melee takeover in "+names[scene]);
                if(scene==0)require(aim&&minAmmo<firstAmmo,"Loaded guard did not aim/fire");
                if(scene==1||scene==2)require(maxTravel>=2,"No natural-terrain withdrawal in "+names[scene]);
                if(scene==2)require(reload,"Native reload missing");
                if(scene==3)require(sidearm,"No usable sidearm activation");
                write("server-results.jsonl",Map.of("scene",names[scene],"passed",true,"travel",maxTravel,"aim",aim,"reload",reload,"sidearm",sidearm));
                if(scene==3){Files.writeString(DIR.resolve("server-result.txt"),"PASS\n");server.halt(false);return;}begin(level);
            }
        } catch(Throwable failure){try{Files.writeString(DIR.resolve("server-result.txt"),"FAIL "+failure+"\n");}catch(Exception ignored){}server.halt(false);}
    }
    static BlockPos findMeadow(ServerLevel level) {
        // Use generated terrain unchanged. Select a gently sloping grass patch; tall grass is not ground.
        for(int x=0;x<512;x+=16)for(int z=0;z<512;z+=16){
            int y=groundHeight(level,x,z);
            boolean okay=true;
            for(int dx=-16;dx<=20&&okay;dx+=2)for(int dz=-16;dz<=16;dz+=2){
                var p=new BlockPos(x+dx,y,z+dz);
                int surface=groundHeight(level,p.getX(),p.getZ());
                if(Math.abs(surface-y)>3 || !level.getBlockState(new BlockPos(p.getX(),surface-1,p.getZ())).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)){okay=false;break;}
            }
            if(okay)return new BlockPos(x,y,z);
        }
        throw new AssertionError("No unchanged meadow found in the bounded search");
    }
    static int groundHeight(ServerLevel level,int x,int z) {
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        while(y>level.getMinBuildHeight() && level.getBlockState(new BlockPos(x,y-1,z)).canBeReplaced())y--;
        return y;
    }
    static void begin(ServerLevel level)throws Exception {
        if(guard!=null)guard.discard();enemies.forEach(Entity::discard);enemies.clear();scene++;ticks=0;maxTravel=0;aim=reload=sidearm=false;melee=0;
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(var player:level.players()){player.setGameMode(GameType.SPECTATOR);player.teleportTo(level,origin.getX()+6,origin.getY()+5,origin.getZ()-12,0,15);}
        guard=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);guard.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
        guard.setCustomName(Component.literal("Combat QA guard"));guard.setCustomNameVisible(true);guard.setPersistenceRequired();guard.setInvulnerable(true);
        for(var slot:EquipmentSlot.values())guard.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
        GuardOrdersAccess.setOrders(guard,GuardOrders.DEFAULT.withMovement(scene==0?GuardOrders.MovementOrder.HOLD_POSITION:GuardOrders.MovementOrder.TACTICAL).withEngagement(GuardOrders.EngagementOrder.HOSTILE_MOBS_ONLY));
        int rounds=scene==0?30:0;
        guard.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz","ak47")).setFireMode(FireMode.AUTO).setAmmoCount(rounds).setAmmoInBarrel(rounds>0).build());
        if(scene==2)GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz","762x39")).setCount(64).build());
        if(scene==3) com.gamergaming.villagerguardgunners.sidearm.CombatQaSidearm.equip(guard,GunItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz","glock_17")).setFireMode(FireMode.SEMI).setAmmoCount(17).setAmmoInBarrel(true).build());
        level.addFreshEntity(guard);guard.setTarget(enemy(level,origin.offset(12,0,0)));
        if(scene==2){enemy(level,origin.offset(12,0,2));enemy(level,origin.offset(12,0,-2));}
        startX=guard.getX();startZ=guard.getZ();firstAmmo=rounds;minAmmo=rounds;
        Files.writeString(DIR.resolve("scene.json"),JSON.toJson(Map.of("scene",names[scene],"id",guard.getId())));
    }
    static Husk enemy(ServerLevel level,BlockPos p){var mob=EntityType.HUSK.create(level);mob.setPos(p.getX()+.5,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()),p.getZ()+.5);mob.setBaby(false);mob.setPersistenceRequired();mob.setInvulnerable(true);mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.20);mob.setTarget(guard);level.addFreshEntity(mob);enemies.add(mob);return mob;}
}
