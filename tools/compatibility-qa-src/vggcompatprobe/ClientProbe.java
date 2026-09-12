package vggcompatprobe;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="vggcompatprobe",value=Dist.CLIENT)
public class ClientProbe {
 static int ticks;static boolean capture;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
  if(Boolean.getBoolean("vgg.compat.barracksDesignPreview"))BarracksDesignPreviewClient.tick();
  if(Files.exists(Probe.DIR.resolve("result.json"))){mc.stop();return;}
  if(++ticks%100==0){capture=true;
   if(mc.screen!=null)Probe.write("client-screen.json",java.util.Map.of("screen",mc.screen.getClass().getName(),"title",mc.screen.getTitle().getString(),"buttons",mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).toList()));
  }
  if(mc.level!=null&&mc.player!=null)mc.options.hideGui=true;
 }
 @SubscribeEvent public static void render(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.END&&Boolean.getBoolean("vgg.compat.barracksDesignPreview"))BarracksDesignPreviewClient.render();if(e.phase!=TickEvent.Phase.END||!capture)return;capture=false;try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Probe.DIR.resolve("village.png"));}catch(Exception failure){throw new RuntimeException(failure);}}
}
