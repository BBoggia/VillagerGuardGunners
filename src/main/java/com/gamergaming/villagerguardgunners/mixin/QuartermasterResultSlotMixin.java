package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.quartermaster.trade.QuartermasterTrades;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantResultSlot.class)
public abstract class QuartermasterResultSlotMixin extends Slot {
    @Shadow @Final private MerchantContainer slots;
    protected QuartermasterResultSlotMixin(Container c,int index,int x,int y){super(c,index,x,y);}
    @Override public boolean mayPickup(Player player){return super.mayPickup(player)&&QuartermasterTrades.canTake(slots);}
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void villagerGuardGunners$beforeRemove(int amount,CallbackInfoReturnable<ItemStack> ci) {
        if(!QuartermasterTrades.canTake(slots))ci.setReturnValue(ItemStack.EMPTY);
    }
}
