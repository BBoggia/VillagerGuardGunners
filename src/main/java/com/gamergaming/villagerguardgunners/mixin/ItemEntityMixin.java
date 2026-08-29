package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.ammunition.GuardDroppedAmmoPickup;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoMaintenance;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
    @Shadow
    @Nullable
    private UUID target;

    protected ItemEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void collectNearbyGuardAmmunition(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (level().isClientSide
                || !isAlive()
                || target != null
                || itemEntity.hasPickUpDelay()
                || Math.floorMod(tickCount + getId(), GuardDroppedAmmoPickup.CHECK_INTERVAL_TICKS) != 0) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (GuardAmmoMaintenance.isPotentialSupply(stack)
                && level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            GuardDroppedAmmoPickup.tryPickup(itemEntity);
        }
    }
}
