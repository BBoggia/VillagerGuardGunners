package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.quartermaster.trade.QuartermasterTrades;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
public abstract class QuartermasterOffersMixin {
    @Inject(method="getOffers",at=@At("RETURN"))
    private void villagerGuardGunners$initialize(CallbackInfoReturnable<MerchantOffers> ci) {
        if((Object)this instanceof Villager villager)QuartermasterTrades.populate(villager,ci.getReturnValue());
    }
}
