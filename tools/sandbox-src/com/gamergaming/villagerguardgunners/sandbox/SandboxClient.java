package com.gamergaming.villagerguardgunners.sandbox;

import java.nio.file.Files;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="villagerguardgunners",value=Dist.CLIENT)
public final class SandboxClient {
    private static boolean requested, stopped, overview, care, displacedPose;
    private static int ticks;
    private static String poseView;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;ticks++;
        // Challenge cot synchronization with a stale client position. The next
        // entity tick must restore the server-owned anchor before pose capture.
        if(VillageSandbox.POSE_CHECK&&!displacedPose&&mc.level!=null)for(var entity:mc.level.entitiesForRendering()) {
            if(entity instanceof tallestegg.guardvillagers.entities.Guard guard
                    &&guard.getName().getString().equals("Cot facing east")
                    &&((com.gamergaming.villagerguardgunners.api.GuardLifeAccess)guard).villagerGuardGunners$getCotVisual().isPresent()) {
                guard.setPos(guard.position().add(0,.75,0));
                guard.walkAnimation.update(1,1);guard.walkAnimation.update(1,1);
                displacedPose=true;break;
            }
        }
        if(!requested&&mc.player!=null&&mc.getSingleplayerServer()!=null) {
            requested=true;mc.setScreen(null);var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try { VillageSandbox.setup(mc.getSingleplayerServer().getPlayerList().getPlayer(id));if(VillageSandbox.VERIFY)VillageSandbox.start(); }
                catch(Throwable error) {VillageSandbox.failure=error.toString();VillageSandbox.write("verification.json",Map.of("passed",false,"error",error.toString()));VillageSandbox.complete=true;}
            });
        }
        if(VillageSandbox.VERIFY&&!stopped&&(VillageSandbox.complete||ticks>3000)) {
            if(ticks>3000&&!VillageSandbox.complete)VillageSandbox.write("verification.json",Map.of("passed",false,"error","Client timeout"));
            stopped=true;try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {screenshot.writeToFile(VillageSandbox.DIR.resolve("verified-scene.png"));}catch(Exception ignored){}
            mc.stop();
        }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&VillageSandbox.poseView!=null&&!VillageSandbox.poseView.equals(poseView)) {
            var entity=Minecraft.getInstance().level.getEntity(VillageSandbox.poseEntityId);
            if(entity instanceof tallestegg.guardvillagers.entities.Guard guard)VillageSandbox.write(VillageSandbox.poseView.replace(".png","-client.json"),Map.of("position",entity.position().toString(),"old",java.util.List.of(entity.xOld,entity.yOld,entity.zOld),"pose",entity.getPose().name(),"boundingBox",entity.getBoundingBox().toString(),"displacementChallenge",displacedPose,"walkSpeeds",java.util.List.of(guard.walkAnimation.speed(0),guard.walkAnimation.speed(.5F),guard.walkAnimation.speed(1))));
            try(var screenshot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { screenshot.writeToFile(VillageSandbox.DIR.resolve(VillageSandbox.poseView));poseView=VillageSandbox.poseView; }catch(Exception ignored){}
        }
        if(event.phase==TickEvent.Phase.END&&VillageSandbox.careView&&!care) {
            try(var screenshot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { screenshot.writeToFile(VillageSandbox.DIR.resolve("cot-care.png"));care=true; }catch(Exception ignored){}
        }
        if(event.phase!=TickEvent.Phase.END||overview||!VillageSandbox.ready||ticks<120)return;
        try(var screenshot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            Files.createDirectories(VillageSandbox.DIR);screenshot.writeToFile(VillageSandbox.DIR.resolve("village-overview.png"));overview=true;
        }catch(Exception ignored){}
    }
}
