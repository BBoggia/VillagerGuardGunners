package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.quartermaster.trade.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MerchantOffer.class)
public abstract class QuartermasterOfferMixin implements TradeOfferAccess {
    @Unique private Tag villagerGuardGunners$tradeIdentity;
    public Tag villagerGuardGunners$getTradeIdentity(){return villagerGuardGunners$tradeIdentity;}
    public void villagerGuardGunners$setTradeIdentity(Tag value){villagerGuardGunners$tradeIdentity=value==null?null:value.copy();}
    @Shadow @Final @Mutable private static com.mojang.serialization.Codec<MerchantOffer> CODEC;
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void villagerGuardGunners$persistIdentity(CallbackInfo ci) {
        CODEC = com.gamergaming.villagerguardgunners.quartermaster.trade.TradeIdentityCodec.wrap(CODEC);
    }
    @Inject(method = "copy", at = @At("RETURN"))
    private void villagerGuardGunners$copyIdentity(CallbackInfoReturnable<MerchantOffer> ci) {
        ((TradeOfferAccess) ci.getReturnValue()).villagerGuardGunners$setTradeIdentity(villagerGuardGunners$tradeIdentity);
    }
    @Inject(method="isOutOfStock",at=@At("RETURN"),cancellable=true)
    private void villagerGuardGunners$disabled(CallbackInfoReturnable<Boolean> ci) {
        if(!ci.getReturnValue()&&!QuartermasterTrades.allowed((MerchantOffer)(Object)this))ci.setReturnValue(true);
    }
}
