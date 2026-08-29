package com.gamergaming.villagerguardgunners.orders;

import java.util.UUID;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;

/** Shared authorization for changing a guard's persistent behavior orders. */
public final class GuardOrderPermissions {
    private GuardOrderPermissions() {
    }

    /**
     * Mirrors the owner, village-reputation, and Hero of the Village access
     * decision made by Guard Villagers before it opens a guard inventory.
     * This deliberately does not use the Guard Villagers item-supply helper:
     * that helper controls a separate, optionally broader permission and is
     * therefore unsuitable for behavior-policy changes.
     */
    public static boolean mayManage(Player player, Guard guard) {
        if (player == null || guard == null) {
            return false;
        }

        UUID ownerId = guard.getOwnerId();
        return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
                || guard.getPlayerReputation(player)
                        >= GuardConfig.reputationRequirement
                || (ownerId != null && ownerId.equals(player.getUUID()));
    }
}
