package quartermasterprobe;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="vggquartermasterprobe",value=Dist.CLIENT)
public final class MultiplayerClient {
    private static int tick,stage=-2,step,last;
    private static boolean connecting,done;
    private static UUID token;
    private static BulkProtocol.Action oldAction;
    private static String role(){return System.getProperty("vgg.multiplayer.role");}
    private static Path shared(){return Path.of(System.getProperty("vgg.multiplayer.shared"));}
    private static void ack(int stage) throws Exception {try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(shared().resolve(role()+"-"+stage+".png"));} Files.writeString(shared().resolve(role()+"-"+stage+".done"),"passed\n");step=99;}
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
    private static void connect(Minecraft mc){String address=System.getProperty("vgg.multiplayer.address");ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Quartermaster acceptance",address,false),false);connecting=true;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(role()==null||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();tick++;
        try {
            if(Files.exists(shared().resolve("multiplayer-result.json"))){done=true;mc.stop();return;}
            if(tick>6000)throw new AssertionError("Multiplayer client timeout");
            if(!connecting&&mc.player==null&&mc.screen instanceof TitleScreen){mc.options.pauseOnLostFocus=false;connect(mc);return;}
            var control=shared().resolve("control.json");if(!Files.exists(control))return;
            var json=JsonParser.parseString(Files.readString(control)).getAsJsonObject();int next=json.get("stage").getAsInt();
            if(next!=stage){stage=next;step=0;last=tick;System.out.println("VGG multiplayer client "+role()+" stage="+stage);}
            if(tick-last<8||step==99)return;last=tick;
            if(stage==7&&role().equals("A")) {
                if(mc.player==null){if(step==0){connect(mc);step=1;}return;}
                ack(7);return;
            }
            if(mc.player==null)return;
            if(stage==6&&role().equals("A")) {
                mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Acceptance reconnect"));mc.clearLevel(new TitleScreen());ack(6);return;
            }
            if(stage==4&&role().equals("A")&&mc.screen instanceof MerchantScreen) {
                var m=(MerchantMenu)mc.player.containerMenu;
                if(step<=1){var buttons=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getWidth()==88).map(c->(Button)c).toList();if(buttons.isEmpty())return;var b=buttons.get(0);b.mouseClicked(b.getX()+5,b.getY()+5,0);step=2;}
                else if(step==2&&!m.getSlot(2).getItem().isEmpty()){mc.gameMode.handleInventoryMouseClick(m.containerId,2,0,ClickType.PICKUP,mc.player);step=3;}
                else if(step==3){check(m.getCarried().getCount()==4,"single-purchase-four-bandages");mc.gameMode.handleInventoryMouseClick(m.containerId,30,0,ClickType.PICKUP,mc.player);step=4;}
                else if(step==4){check(m.getSlot(0).getItem().getCount()==18,"purchase-left-18-payment");ack(4);}return;
            }
            if(!(mc.player.containerMenu instanceof QuartermasterManagementMenu m)||m.summary()==null)return;
            if(stage==1) {
                switch(step) {
                    case 0 -> {check(m.summary().stock()&&m.summary().manage(),"both-authorized");ManagementNetwork.request(m.requestBulk(BulkProtocol.OPEN,0,null,null));step++;}
                    case 1 -> {if(m.bulkState()==null)return;ManagementNetwork.request(m.requestBulk(BulkProtocol.TOGGLE,0,UUID.fromString(json.get("guard").getAsString()),null));step++;}
                    case 2 -> {ManagementNetwork.request(m.requestBulk(BulkProtocol.POLICY,1,null,null));step++;}
                    case 3 -> {ManagementNetwork.request(m.requestBulk(BulkProtocol.ASSIGN,1,null,null));step++;}
                    case 4 -> {ManagementNetwork.request(m.requestBulk(BulkProtocol.PREVIEW,0,null,null));step++;}
                    default -> {if(m.bulkState()==null||m.bulkState().token()==null)return;token=m.bulkState().token();ack(1);}
                }
            }else if(stage==2&&role().equals("A")) {
                if(step==0){oldAction=m.requestBulk(BulkProtocol.APPLY,0,null,token);ManagementNetwork.request(oldAction);step++;}
                else if(m.bulkState().status().equals("applied"))ack(2);
            }else if(stage==3&&role().equals("B")) {
                if(step==0){ManagementNetwork.request(m.requestBulk(BulkProtocol.APPLY,0,null,token));step++;}
                else {check(!m.bulkState().status().equals("applied")&&m.bulkState().token()==null,"stale-preview-refused");ack(3);}
            }else if(stage==4&&role().equals("A")&&step==0){ManagementNetwork.request(m.request(ManagementProtocol.TRADE));step=1;}
            else if(stage==5&&role().equals("B")) {
                if(step==0){ManagementNetwork.request(m.request(ManagementProtocol.TRADE));step++;}
                else {check(m.summary().tradeStatus()==ManagementProtocol.BUSY,"other-trader-busy");ack(5);}
            }else if(stage==8&&role().equals("A")) {
                if(step==0){ManagementNetwork.request(oldAction);step++;}
                else {check(m.bulkState()==null,"old-session-not-reused");ack(8);}
            }
        }catch(Throwable error){error.printStackTrace();done=true;try{Files.writeString(shared().resolve(role()+"-error.txt"),error.toString());}catch(Exception ignored){}mc.stop();}
    }
}
