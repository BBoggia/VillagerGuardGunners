package shotqa;

import com.google.gson.JsonParser;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Uses TaCZ's client operator, which sends normal firing/reload packets. */
@EventBusSubscriber(modid = "vgg_shot_qa", value = Dist.CLIENT)
public final class Client {
    private static boolean connecting, done;
    private static int tick, lastIndex = -1;
    private static String lastPhase = "";
    private static final Set<String> PICTURES = new HashSet<>();
    private static final Map<String, Integer> ACTIONS = new TreeMap<>();

    private static void screenshot(Minecraft client, String name) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(Probe.out().resolve(name + ".png"));
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty("vgg.packqa.dir") == null || done) return;
        var client = Minecraft.getInstance();
        tick++;
        try {
            if (Files.exists(Probe.out().resolve("complete.json"))) {
                done = true;
                Probe.write("client-actions.json", ACTIONS);
                client.stop();
                return;
            }
            if (!connecting && client.player == null && client.screen instanceof TitleScreen) {
                client.options.pauseOnLostFocus = false;
                String address = System.getProperty("vgg.packqa.address");
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                        new ServerData("Shot wrapper test", address, ServerData.Type.OTHER), false, null);
                connecting = true;
                return;
            }
            if (connecting && client.screen instanceof DisconnectedScreen)
                throw new IllegalStateException("Client disconnected: " + client.screen.getTitle().getString());
            if (client.player == null || tick % 3 != 0 || !Files.exists(Probe.out().resolve("control.json"))) return;
            var control = JsonParser.parseString(Files.readString(Probe.out().resolve("control.json"))).getAsJsonObject();
            int index = control.get("index").getAsInt(), age = control.get("tick").getAsInt();
            String phase = control.get("phase").getAsString(), gun = control.get("gun").getAsString();
            var operator = IClientPlayerGunOperator.fromLocalPlayer(client.player);
            if (index != lastIndex || !phase.equals(lastPhase)) {
                lastIndex = index;
                lastPhase = phase;
                operator.aim(false);
            }
            if (phase.equals("player") && age > 30) {
                client.player.setYRot(-90);
                client.player.setXRot(0);
                operator.aim(true);
                String result = operator.shoot().name();
                ACTIONS.merge(gun + ":" + result, 1, Integer::sum);
                if (result.equals("NEED_BOLT")) operator.bolt();
                if (result.equals("NO_AMMO")) operator.reload();
            }
            if ((phase.equals("player") && age >= 100) || (phase.equals("guard") && age >= 260)) {
                String name = index + "-" + phase;
                if (PICTURES.add(name)) {
                    screenshot(client, name);
                    Probe.write("client-progress.json", Map.of("index", index, "gun", gun, "phase", phase, "age", age));
                }
            }
        } catch (Throwable error) {
            error.printStackTrace();
            done = true;
            try {
                Probe.write("client-error.json", Map.of("error", error.toString(), "index", lastIndex, "phase", lastPhase));
                screenshot(client, "client-failure");
            } catch (Exception writeError) { writeError.printStackTrace(); }
            client.stop();
        }
    }
}
