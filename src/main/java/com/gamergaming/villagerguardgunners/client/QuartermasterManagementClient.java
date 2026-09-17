package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.management.ManagementProtocol;
import net.minecraft.client.Minecraft;

public final class QuartermasterManagementClient {
    public static void receive(com.gamergaming.villagerguardgunners.quartermaster.management.BulkProtocol.Navigation value) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||!(player.containerMenu instanceof QuartermasterManagementMenu menu)||!menu.receive(value)
                ||!(mc.screen instanceof QuartermasterManagementScreen||mc.screen instanceof QuartermasterBulkScreen))return;
        var title=com.gamergaming.villagerguardgunners.quartermaster.management.QuartermasterManagement.text("title");
        if(value.active()&&!(mc.screen instanceof QuartermasterBulkScreen))mc.setScreen(new QuartermasterBulkScreen(menu,player.getInventory(),title));
        else if(!value.active()&&!(mc.screen instanceof QuartermasterManagementScreen))mc.setScreen(new QuartermasterManagementScreen(menu,player.getInventory(),title));
    }
    public static void receive(ManagementProtocol.Summary summary) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof QuartermasterManagementMenu menu)menu.receive(summary);
    }
    public static void receive(com.gamergaming.villagerguardgunners.quartermaster.management.OverviewProtocol.Page page) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof QuartermasterManagementMenu menu)menu.receive(page);
    }
    public static void receive(com.gamergaming.villagerguardgunners.quartermaster.management.BulkProtocol.State state) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof QuartermasterManagementMenu menu)menu.receive(state);
    }
    private QuartermasterManagementClient() {}
}
