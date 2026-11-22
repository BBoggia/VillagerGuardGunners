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
    @Inject(method="<init>(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("RETURN"))
    private void villagerGuardGunners$read(CompoundTag tag,CallbackInfo ci){villagerGuardGunners$setTradeIdentity(tag.get(QuartermasterTrades.KEY));}
    @Inject(method="createTag",at=@At("RETURN"))
    private void villagerGuardGunners$save(CallbackInfoReturnable<CompoundTag> ci) {
        if(villagerGuardGunners$tradeIdentity!=null)ci.getReturnValue().put(QuartermasterTrades.KEY,villagerGuardGunners$tradeIdentity.copy());
    }
    @Inject(method="isOutOfStock",at=@At("RETURN"),cancellable=true)
    private void villagerGuardGunners$disabled(CallbackInfoReturnable<Boolean> ci) {
        if(!ci.getReturnValue()&&!QuartermasterTrades.allowed((MerchantOffer)(Object)this))ci.setReturnValue(true);
    }
}
