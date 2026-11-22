package accuracyprobe;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "vggaccuracyprobe", value = Dist.CLIENT)
public final class ClientProbe {
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Probe.enabled() && !BalanceProbe.enabled()) return;
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        if (client.player != null && client.player.isDeadOrDying()) client.player.respawn();
        if (Files.exists((BalanceProbe.enabled()?BalanceProbe.output():Probe.output()).resolve("complete.json"))) client.stop();
    }
}
