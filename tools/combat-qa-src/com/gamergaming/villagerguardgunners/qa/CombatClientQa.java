package com.gamergaming.villagerguardgunners.qa;
import com.google.gson.*;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import java.nio.file.*;
import java.util.Map;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;
@Mod.EventBusSubscriber(modid="villagerguardgunners",value=Dist.CLIENT)
public final class CombatClientQa {
 static final Path DIR=Path.of(System.getProperty("combat.qa.dir"));static final Gson JSON=new Gson();
 static int ticks;static String scene="",capture;static int id;static boolean stopped;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END||stopped)return;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;ticks++;
  try{
   if(Files.exists(DIR.resolve("server-result.txt"))||ticks>6000){stopped=true;mc.stop();return;}
   if(ticks%10==0&&Files.exists(DIR.resolve("scene.json"))){var data=JsonParser.parseString(Files.readString(DIR.resolve("scene.json"))).getAsJsonObject();var next=data.get("scene").getAsString();if(!scene.equals(next)){scene=next;id=data.get("id").getAsInt();}}
   if(mc.level==null||mc.player==null||scene.isEmpty())return;
   mc.options.hideGui=true;
   if(mc.level.getEntity(id) instanceof Guard guard&&guard.getMainHandItem().getItem() instanceof IGun gun){
    mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,guard.getEyePosition());
    String name=mc.getUser().getName();var stack=guard.getMainHandItem();
    var row=Map.of("scene",scene,"tick",mc.level.getGameTime(),"id",id,"x",guard.getX(),"y",guard.getY(),"z",guard.getZ(),"gun",gun.getGunId(stack).toString(),"ammo",gun.getCurrentAmmoCount(stack),"aim",IGunOperator.fromLivingEntity(guard).getSynIsAiming());
    Files.writeString(DIR.resolve(name+".jsonl"),JSON.toJson(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    if(ticks%80==0)capture=name+"-"+scene+".png";
   }
  }catch(Exception failure){try{Files.writeString(DIR.resolve("client-error.txt"),failure.toString());}catch(Exception ignored){}stopped=true;mc.stop();}
 }
 @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){if(event.phase!=TickEvent.Phase.END||capture==null)return;try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(DIR.resolve(capture));capture=null;}catch(Exception failure){throw new RuntimeException(failure);}}
}
