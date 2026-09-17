package com.gamergaming.villagerguardgunners.quartermaster.trade;

import net.minecraft.nbt.Tag;

/** An offer-local saved identity; never attached to purchased or deposited items. */
public interface TradeOfferAccess {
    Tag villagerGuardGunners$getTradeIdentity();
    void villagerGuardGunners$setTradeIdentity(Tag identity);
}
