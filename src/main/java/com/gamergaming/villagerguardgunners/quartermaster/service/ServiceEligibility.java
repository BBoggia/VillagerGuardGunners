package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.squad.SquadCommandRuntime;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.entities.Guard;

public final class ServiceEligibility {
    public static ServiceStatus guard(Guard guard) {
        var state = GuardEquipmentServiceAccess.get(guard);
        if (!(guard.level() instanceof ServerLevel level) || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) || !guard.isAlive() || guard.isRemoved() || !state.readable()
                || !ServiceJournal.get(level.getServer()).verify(state.receipt)) return ServiceStatus.RECOVERY_REQUIRED;
        if (state.policy() == GuardServiceState.Policy.MANUAL) return ServiceStatus.MANUAL;
        if (guard.getTarget() != null && guard.getTarget().isAlive()) return ServiceStatus.FIGHTING;
        var order = com.gamergaming.villagerguardgunners.role.GuardRoleResolver.effectiveOrders(guard).movement();
        if (com.gamergaming.villagerguardgunners.medical.MedicRuntime.busy(guard) || guard.interacting || guard.isPassenger() || guard.isSleeping() || ((GuardCoverageAccess) guard).villagerGuardGunners$hasFollowOrder()
                || guard.isPatrolling() || order.holdsPosition() || order == GuardOrders.MovementOrder.PATROL_BOUND
                || SquadCommandRuntime.activeCommand(guard) != null
                || com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver.hasLoadedWaypoint(guard)) return ServiceStatus.PLAYER_ORDER;
        if (GuardSidearmAccess.getState(guard) != SidearmState.PRIMARY_ACTIVE || GuardMedicalAccess.get(guard).busy()
                || !((GuardCoverageAccess) guard).villagerGuardGunners$nativeEquipmentServiceSafe()) return ServiceStatus.NATIVE_ACTION;
        return ServiceStatus.READY;
    }
    public static ServiceStatus station(QuartermasterBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level) || station.isRemoved() || !station.readable()
                || !ServiceJournal.get(level.getServer()).verify(station.serviceReceipt())) return ServiceStatus.RECOVERY_REQUIRED;
        if (!level.hasChunkAt(station.getBlockPos()) || level.getBlockEntity(station.getBlockPos()) != station
                || !level.isPositionEntityTicking(station.getBlockPos())) return ServiceStatus.OPERATOR_UNAVAILABLE;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time < 2000 || time >= 9000) return ServiceStatus.OUTSIDE_WORK_HOURS;
        if (level.getRaidAt(station.getBlockPos()) != null) return ServiceStatus.RAID;
        if (!claimedStation(level, station.getBlockPos())) return ServiceStatus.OPERATOR_UNAVAILABLE;
        if (station.operator == null || !(level.getEntity(station.operator) instanceof Villager villager)
                || !villager.isAlive() || villager.isRemoved() || villager.isBaby() || villager.isNoAi() || villager.isTrading() || villager.isSleeping()
                || villager.getVillagerData().getProfession() != ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)
                || villager.distanceToSqr(station.getBlockPos().getX() + .5, station.getBlockPos().getY() + .5, station.getBlockPos().getZ() + .5) > 16
                || villager.getBrain().isActive(Activity.PANIC) || villager.getBrain().isActive(Activity.RAID)
                || villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site -> site.dimension().equals(level.dimension()) && site.pos().equals(station.getBlockPos())).isEmpty()) return ServiceStatus.OPERATOR_UNAVAILABLE;
        return ServiceStatus.READY;
    }
    /** POIs store ticket counts, not owner UUIDs; the live villager checks above supply identity evidence. */
    private static boolean claimedStation(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var cached = ((com.gamergaming.villagerguardgunners.mixin.accessor.VillagePoiStorageAccessor) level.getPoiManager())
                .villagerGuardGunners$cachedSection(net.minecraft.core.SectionPos.of(pos).asLong());
        if (cached == null || cached.isEmpty() || !(cached.get() instanceof net.minecraft.world.entity.ai.village.poi.PoiSection section)) return false;
        var type = ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID);
        if (type == null) return false;
        return section.getRecords(holder -> holder.value() == type,
                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.IS_OCCUPIED)
                .anyMatch(record -> record.getPos().equals(pos));
    }
}
