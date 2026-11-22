package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import tallestegg.guardvillagers.entities.Guard;

public record ServiceSource(BlockEntity block, ItemStackHandler inventory, ServiceReceipt receipt, int stockSlots) {
    public static ServiceSource of(QuartermasterBlockEntity station) { return new ServiceSource(station, station.inventory(), station.serviceReceipt(), 27); }
    public static ServiceSource of(ArmoryBlockEntity armory) { return new ServiceSource(armory, armory.getInventory(), armory.serviceReceipt(), 9); }
    public boolean live(Guard guard) {
        var level = guard.level();
        if (block.isRemoved() || block.getLevel() != level || !level.hasChunkAt(block.getBlockPos())
                || level.getBlockEntity(block.getBlockPos()) != block || receipt.locked()) return false;
        if (block instanceof QuartermasterBlockEntity station) return station.mayService(guard);
        var member = GuardSquadAccess.getMembership(guard);
        return ((ArmoryBlockEntity) block).maySupply(member == null ? null : member.squadId());
    }
}
