package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import com.gamergaming.villagerguardgunners.medical.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(Guard.class)
public abstract class GuardLifeMixin implements GuardLifeAccess, com.gamergaming.villagerguardgunners.api.GuardTransportAccess {
    @Unique private static final EntityDataAccessor<Integer> VGG_LIFE = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<java.util.Optional<net.minecraft.core.BlockPos>> VGG_COT = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    @Unique private static final EntityDataAccessor<Integer> VGG_CARE = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.INT);
    @Unique private final CotRestState vgg$cot = new CotRestState();
    @Override public CotRestState villagerGuardGunners$getCotRest() { return vgg$cot; }
    @Override public java.util.Optional<net.minecraft.core.BlockPos> villagerGuardGunners$getCotVisual() { return ((Guard)(Object)this).getEntityData().get(VGG_COT); }
    @Override public void villagerGuardGunners$setCotVisual(java.util.Optional<net.minecraft.core.BlockPos> head) { ((Guard)(Object)this).getEntityData().set(VGG_COT, head); }
    @Override public int villagerGuardGunners$getCareVisual() { return ((Guard)(Object)this).getEntityData().get(VGG_CARE); }
    @Override public void villagerGuardGunners$setCareVisual(int state) { ((Guard)(Object)this).getEntityData().set(VGG_CARE, state); }
    @Unique private final TransportState vgg$transport = new TransportState();
    @Override public TransportState villagerGuardGunners$getTransportState() { return vgg$transport; }
    @Unique private final GuardLifeState vgg$life = new GuardLifeState();
    @Override public GuardLifeState villagerGuardGunners$getLifeState() { return vgg$life; }
    @Override public int villagerGuardGunners$getLifeVisual() { return ((Guard)(Object)this).getEntityData().get(VGG_LIFE); }
    @Override public void villagerGuardGunners$setLifeVisual(int state) { ((Guard)(Object)this).getEntityData().set(VGG_LIFE, state); }
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void vgg$defineLife(CallbackInfo ci) { var data = ((Guard)(Object)this).getEntityData(); data.define(VGG_LIFE, 0); data.define(VGG_COT, java.util.Optional.empty()); data.define(VGG_CARE, 0); }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void vgg$readLife(CompoundTag tag, CallbackInfo ci) { vgg$life.load(tag); vgg$transport.load(tag); vgg$cot.load(tag); GuardLifeRuntime.synchronize((Guard)(Object)this); }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void vgg$saveLife(CompoundTag tag, CallbackInfo ci) { vgg$life.save(tag); vgg$transport.save(tag); vgg$cot.save(tag); }
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void vgg$downBeforeConversion(DamageSource source, CallbackInfo ci) { if (GuardLifeRuntime.interceptDeath((Guard)(Object)this, source)) ci.cancel(); }
    @Inject(method = "die", at = @At("RETURN"))
    private void vgg$deathReturned(DamageSource source, CallbackInfo ci) { GuardLifeRuntime.deathReturned((Guard)(Object)this); }
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void vgg$noCasualtyMelee(net.minecraft.world.entity.Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (GuardLifeRuntime.blocked((Guard)(Object)this) || TransportRuntime.busy((Guard)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void vgg$tickLife(CallbackInfo ci) { TransportRuntime.tickGuard((Guard)(Object)this); GuardLifeRuntime.tick((Guard)(Object)this); }
    @Inject(method = "tick", at = @At("TAIL"))
    private void vgg$alignClientCot(CallbackInfo ci) { CotRestRuntime.alignClient((Guard)(Object)this); }
}
