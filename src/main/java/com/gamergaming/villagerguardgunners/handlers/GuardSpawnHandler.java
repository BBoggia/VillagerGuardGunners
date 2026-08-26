package com.gamergaming.villagerguardgunners.handlers;

import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;

@Mod.EventBusSubscriber(modid = VillagerGuardGunners.MODID)
public class GuardSpawnHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Guard guard) {
            // Replace the guard's inventory
            // expandGuardInventory(guard);
        }
    }

    // private static void expandGuardInventory(Guard guard) {
    //     VillagerGuardGunners.LOGGER.info("!! GUARD SPAWNED !!");
    //     guard.guardInventory = new SimpleContainer(9);
    // }
}