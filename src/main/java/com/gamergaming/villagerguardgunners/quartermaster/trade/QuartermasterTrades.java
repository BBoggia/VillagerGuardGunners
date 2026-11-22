package com.gamergaming.villagerguardgunners.quartermaster.trade;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession;
import com.mojang.logging.LogUtils;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.guns.OptionalGunMods;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.core.registries.BuiltInRegistries;

/** Ordinary merchant offers with saved identities and no connection to station inventories. */
public final class QuartermasterTrades {
    public static final String KEY="VillagerGuardGunnersTrade", LEVELS="VillagerGuardGunnersTradeLevels";
    private static final Set<ResourceLocation> WARNED=new HashSet<>();
    public static void clearWarnings(){WARNED.clear();}
    private static boolean enabled(boolean gun){return gun?ModConfigs.GUARD_GUNNERS.quartermasterTrades.gunSalesEnabled.get():ModConfigs.GUARD_GUNNERS.quartermasterTrades.suppliesEnabled.get();}
    private static void unavailable(ResourceLocation id,String reason) {
        if(WARNED.add(id))LogUtils.getLogger().warn("Quartermaster trade {} omitted: {}",id,reason);
    }
    /** A trade for a supported gun mod that is not installed is expected, not a mistake to warn about. */
    static boolean forAbsentGunMod(TradeDefinition definition) {
        if(OptionalGunMods.isAbsent(definition.output().id().getNamespace()))return true;
        for(var input:definition.inputs())if(OptionalGunMods.isAbsent(input.id().getNamespace()))return true;
        return false;
    }
    public static ItemStack stack(TradeDefinition.Stack spec, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack stack;
        switch(spec.type()) {
            // The gun mod that defines the ID builds the stack. Guns are sold unloaded.
            case AMMO -> stack=GunProviders.forAmmoId(spec.id()).flatMap(provider->provider.createAmmo(spec.id(),spec.count())).orElse(ItemStack.EMPTY);
            case GUN -> stack=GunProviders.forGunId(spec.id()).flatMap(provider->provider.createGun(spec.id(),registries,false)).orElse(ItemStack.EMPTY);
            default -> {
                if(!BuiltInRegistries.ITEM.containsKey(spec.id()))return ItemStack.EMPTY;
                var item=BuiltInRegistries.ITEM.get(spec.id());
                if(item==null||item==Items.AIR)return ItemStack.EMPTY;
                stack=new ItemStack(item,spec.count());
                // Guns and ammo from any gun mod must be declared as such, so the gun-sales setting applies.
                if(GunProviders.isGun(stack)||GunProviders.isAmmo(stack))return ItemStack.EMPTY;
            }
        }
        return stack.isEmpty()||stack.getCount()>stack.getMaxStackSize()?ItemStack.EMPTY:stack;
    }
    private static ItemCost cost(ItemStack stack) {
        return new ItemCost(stack.getItemHolder(), stack.getCount(), net.minecraft.core.component.DataComponentPredicate.allOf(stack.getComponents()));
    }
    private static MerchantOffer create(ResourceLocation id,TradeDefinition definition, net.minecraft.core.HolderLookup.Provider registries) {
        if(!definition.enabled()||!enabled(definition.output().type()==TradeDefinition.Kind.GUN))return null;
        try {
            var a=stack(definition.inputs().get(0), registries);var b=definition.inputs().size()==2?stack(definition.inputs().get(1), registries):ItemStack.EMPTY;var output=stack(definition.output(), registries);
            if(a.isEmpty()||output.isEmpty()||definition.inputs().size()==2&&b.isEmpty()){
                if(!forAbsentGunMod(definition))unavailable(id,"missing item/gun/ammo definition, gun or ammo declared as a plain item, or invalid stack size");
                return null;
            }
            var offer=new MerchantOffer(cost(a), b.isEmpty() ? Optional.empty() : Optional.of(cost(b)),output,definition.maxUses(),definition.xp(),definition.multiplier());
            var tag=new CompoundTag();tag.putInt("Version",1);tag.putString("Id",id.toString());tag.putInt("Inputs",definition.inputs().size());
            ((TradeOfferAccess)offer).villagerGuardGunners$setTradeIdentity(tag);return offer;
        } catch(RuntimeException ex){unavailable(id,ex.toString());return null;}
    }
    /** Once per profession level, including old already-leveled villagers. Existing offers are never cleared. */
    public static void populate(Villager villager,MerchantOffers offers) {
        if(villager.level().isClientSide||offers==null||!TradeDefinitions.ready()||(!enabled(false)&&!enabled(true))
                ||villager.getVillagerData().getProfession()!=BuiltInRegistries.VILLAGER_PROFESSION.get(QuartermasterProfession.ID))return;
        var data=villager.getPersistentData();int mask=0;
        if(data.contains(LEVELS)) {
            if(!(data.get(LEVELS) instanceof CompoundTag stored)||stored.size()!=2||!stored.contains("Version",Tag.TAG_INT)||stored.getInt("Version")!=1
                    ||!stored.contains("Mask",Tag.TAG_INT)||stored.getInt("Mask")<0||stored.getInt("Mask")>31)return;
            mask=stored.getInt("Mask");
        }
        int level=Math.max(1,Math.min(5,villager.getVillagerData().getLevel()));
        int unlockedMask=(1<<level)-1;if((mask&unlockedMask)==unlockedMask)return;
        Set<ResourceLocation> existing=new HashSet<>();for(var offer:offers){var id=id(offer);if(id!=null)existing.add(id);}
        for(int l=1;l<=level;l++) {
            int bit=1<<(l-1);if((mask&bit)!=0)continue;
            final int unlocked=l;
            for(var entry:TradeDefinitions.snapshot().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if(!entry.getValue().enabled()||entry.getValue().level()!=unlocked||existing.contains(entry.getKey()))continue;
                if(offers.size()>=256){unavailable(entry.getKey(),"villager already has 256 offers");continue;}
                var offer=create(entry.getKey(),entry.getValue(), villager.registryAccess());if(offer!=null){offers.add(offer);existing.add(entry.getKey());}
            }
            mask|=bit;
        }
        var saved=new CompoundTag();saved.putInt("Version",1);saved.putInt("Mask",mask);data.put(LEVELS,saved);
    }
    public static boolean owned(MerchantOffer offer){return offer instanceof TradeOfferAccess access&&access.villagerGuardGunners$getTradeIdentity()!=null;}
    public static ResourceLocation id(MerchantOffer offer) {
        if(!(offer instanceof TradeOfferAccess access)||!(access.villagerGuardGunners$getTradeIdentity() instanceof CompoundTag tag)
                ||tag.size()!=3||!tag.contains("Inputs",Tag.TAG_INT)||tag.getInt("Inputs")<1||tag.getInt("Inputs")>2||!tag.contains("Version",Tag.TAG_INT)||tag.getInt("Version")!=1||!tag.contains("Id",Tag.TAG_STRING)||tag.getString("Id").length()>96)return null;
        return ResourceLocation.tryParse(tag.getString("Id"));
    }
    private static boolean nativePresent(ItemStack stack) {
        if(stack.isEmpty())return true;
        var gunOwner=GunProviders.find(stack);
        if(gunOwner.isPresent())return gunOwner.get().isDefined(stack);
        return GunProviders.findAmmoOwner(stack).map(provider->provider.isDefined(stack)).orElse(true);
    }
    /** Called before taking the result, never as a substitute for paying after items moved. */
    public static boolean allowed(MerchantOffer offer) {
        if(!owned(offer))return true;
        var id=id(offer);var current=id==null?null:TradeDefinitions.snapshot().get(id);
        if(!TradeDefinitions.ready()||current==null||!current.enabled()||offer.getResult().isEmpty()||offer.getBaseCostA().isEmpty())return false;
        var identity=(CompoundTag)((TradeOfferAccess)offer).villagerGuardGunners$getTradeIdentity();
        if(identity.getInt("Inputs")==2&&offer.getCostB().isEmpty())return false;
        boolean gun=GunProviders.isGun(offer.getResult())||GunProviders.isGun(offer.getBaseCostA())||GunProviders.isGun(offer.getCostB());
        try{return enabled(gun)&&nativePresent(offer.getResult())&&nativePresent(offer.getBaseCostA())&&nativePresent(offer.getCostB());}
        catch(RuntimeException ex){return false;}
    }
    public static boolean canTake(MerchantContainer container) {
        var offer=container.getActiveOffer();return offer==null||allowed(offer);
    }
    private QuartermasterTrades(){}
}
