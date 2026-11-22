package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.quartermaster.trade.QuartermasterTrades;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantMenu.class)
public abstract class QuartermasterMerchantMenuMixin extends AbstractContainerMenu {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;
    @Unique private List<Boolean> villagerGuardGunners$availability=List.of();
    protected QuartermasterMerchantMenuMixin(MenuType<?> type,int id){super(type,id);}
    @Unique private void villagerGuardGunners$refresh() {
        if(trader.isClientSide())return;
        var offers=trader.getOffers();
        if(offers.stream().noneMatch(QuartermasterTrades::owned))return;
        List<Boolean> availability=offers.stream().map(QuartermasterTrades::allowed).toList();
        if(!availability.equals(villagerGuardGunners$availability)) {
            villagerGuardGunners$availability=availability;tradeContainer.updateSellItem();
            if(trader.getTradingPlayer() instanceof ServerPlayer player&&player.containerMenu==(Object)this) {
                var menu=(MerchantMenu)(Object)this;
                player.sendMerchantOffers(containerId,offers,menu.getTraderLevel(),trader.getVillagerXp(),trader.showProgressBar(),trader.canRestock());
            }
        }
    }
    @Override public void broadcastChanges(){villagerGuardGunners$refresh();super.broadcastChanges();}
    @Override public void clicked(int slot,int button,ClickType type,Player player){villagerGuardGunners$refresh();super.clicked(slot,button,type,player);}
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void villagerGuardGunners$beforeQuickMove(Player player,int slot,CallbackInfoReturnable<ItemStack> ci) {
        if(slot==2&&!QuartermasterTrades.canTake(tradeContainer)) {tradeContainer.updateSellItem();ci.setReturnValue(ItemStack.EMPTY);}
    }
}
