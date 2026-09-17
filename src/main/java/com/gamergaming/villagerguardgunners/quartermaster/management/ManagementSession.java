package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.ServiceManagementPermissions;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/** One live menu context. Inventory authority never follows the NPC away from the station. */
public final class ManagementSession {
    private final UUID id=UUID.randomUUID();
    private final ServerPlayer viewer;
    private final QuartermasterBlockEntity station;
    private final ServiceManagementPermissions.Context context;
    private final UUID npc;
    private final boolean stock,manage;
    public ManagementSession(ServerPlayer viewer,QuartermasterBlockEntity station,Villager npc) {
        this.viewer=viewer;this.station=station;this.context=ServiceManagementPermissions.Context.of(station);
        this.npc=npc==null?null:npc.getUUID();
        var access=ServiceManagementPermissions.station(viewer,station,context);stock=access.viewStock();manage=access.manage();
    }
    public UUID id(){return id;}
    public QuartermasterBlockEntity station(){return station;}
    public boolean stock(){return stock;}
    public boolean manage(){return manage;}
    public boolean npcOrigin(){return npc!=null;}
    public Villager operator() {
        if(npc==null)return QuartermasterManagement.operator(station);
        return station.getLevel() instanceof ServerLevel level&&level.getEntity(npc) instanceof Villager v?v:null;
    }
    public boolean valid(Player player) {
        if(player!=viewer||!player.isAlive()||player.isRemoved()||player.isSpectator()||player.level()!=station.getLevel()
                ||station.isRemoved()||!station.stationId().equals(context.station())||!station.dropId().equals(context.placement())
                ||!station.getBlockPos().equals(context.position())||!player.level().dimension().location().equals(context.dimension())
                ||!player.level().hasChunkAt(context.position())||player.level().getBlockEntity(context.position())!=station)return false;
        var access=ServiceManagementPermissions.station(player,station,context);
        if(access.viewStock()!=stock||access.manage()!=manage)return false;
        if(npc==null)return stock;
        var villager=operator();
        return villager!=null&&QuartermasterManagement.station(villager)==station&&player.distanceToSqr(villager)<=16
                &&player.hasLineOfSight(villager)&&!villager.isSleeping()&&!villager.isTrading();
    }
}
