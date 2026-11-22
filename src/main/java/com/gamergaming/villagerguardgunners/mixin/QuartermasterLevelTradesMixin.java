package com.gamergaming.villagerguardgunners.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class QuartermasterLevelTradesMixin {
    @Inject(method="setVillagerData",at=@At("HEAD"))
    private void villagerGuardGunners$professionChanged(net.minecraft.world.entity.npc.VillagerData next,CallbackInfo ci) {
        var villager=(Villager)(Object)this;var current=villager.getVillagerData().getProfession();
        if(current!=next.getProfession()&&current==net.minecraftforge.registries.ForgeRegistries.VILLAGER_PROFESSIONS.getValue(
                com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession.ID))
            villager.getPersistentData().remove(com.gamergaming.villagerguardgunners.quartermaster.trade.QuartermasterTrades.LEVELS);
    }
    @Inject(method="updateTrades",at=@At("TAIL"))
    private void villagerGuardGunners$unlock(CallbackInfo ci) {
        // AbstractVillager has allocated offers before calling updateTrades, so this does not recurse.
        var villager=(Villager)(Object)this;
        if(villager.getVillagerData().getProfession()==net.minecraftforge.registries.ForgeRegistries.VILLAGER_PROFESSIONS.getValue(
                com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession.ID))villager.getOffers();
    }
}
