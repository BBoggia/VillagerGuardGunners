package com.gamergaming.villagerguardgunners.qa;

import static com.gamergaming.villagerguardgunners.qa.SupplyMedicalAcceptance.*;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.medical.*;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

final class WorkflowMedicalChecks {
    static void run(SupplyMedicalAcceptance q) {
        ConfigPersistenceAcceptance.restartAnnotations();
        passed("config_restart_annotations");
        damage(q);
        NativeCommandChecks.run(q);
        var g=q.guard(ORIGIN,false); g.setHealth(5); q.owner.setPos(.5,160,1.5);
        var pouch=GuardMedicalAccess.get(g);
        require(!TreatmentProfiles.snapshot().isEmpty(),"Medical profiles missing");
        q.owner.setItemInHand(InteractionHand.MAIN_HAND,bandages(3));
        require(MedicalTreatment.fromHand(q.owner,g,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.HEALED
                &&g.getHealth()>5&&q.owner.getMainHandItem().getCount()==2,"Bandage did not heal for one dose");
        require(MedicalTreatment.fromHand(q.owner,g,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.COOLDOWN
                &&q.owner.getMainHandItem().getCount()==2,"Cooldown spent another dose");
        pouch.readyAt(0); g.setHealth(5);
        Consumer<LivingHealEvent> cancel=event->{if(event.getEntity()==g)event.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(cancel);
        try { require(MedicalTreatment.fromHand(q.owner,g,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.CANCELED
                &&g.getHealth()==5&&q.owner.getMainHandItem().getCount()==2,"Canceled heal spent dose"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        ItemStack potion=PotionContents.createItemStack(Items.POTION,Potions.HEALING);
        require(TreatmentProfiles.resolve(potion)!=null,"Drinkable healing potion unsupported");
        require(TreatmentProfiles.resolve(PotionContents.createItemStack(Items.SPLASH_POTION,Potions.HEALING))==null
                &&TreatmentProfiles.resolve(PotionContents.createItemStack(Items.POTION,Potions.POISON))==null,"Unsafe potion accepted");
        ItemStack custom=potion.copy(); custom.set(DataComponents.POTION_CONTENTS,custom.get(DataComponents.POTION_CONTENTS)
                .withEffectAdded(new MobEffectInstance(MobEffects.POISON,100)));
        require(TreatmentProfiles.resolve(custom)==null,"Custom potion effects accepted");
        q.owner.setItemInHand(InteractionHand.MAIN_HAND,potion);
        require(MedicalTreatment.fromHand(q.owner,g,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.HEALED
                &&q.owner.getMainHandItem().is(Items.GLASS_BOTTLE),"Healing potion did not return bottle");
        passed("medical_treatment_and_cancellation");

        pouch.inventory().setStackInSlot(2,bandages(23)); pouch.readyAt(q.level.getGameTime()+1000);
        var original=pouch.save();
        var future=original.copy(); future.getCompound(MedicalStorage.KEY).putInt("Version",99);
        pouch.load(future); require(!pouch.readable()&&pouch.save().equals(future),"Unknown medical payload lost"); pouch.load(original);
        require(pouch.readable()&&pouch.inventory().getStackInSlot(0).isEmpty()&&pouch.inventory().getStackInSlot(2).getCount()==23,"Sparse slots lost");
        for(int i=0;i<36;i++)q.owner.getInventory().setItem(i,ItemStack.EMPTY);
        require(MedicalTreatment.pack(q.owner,g)==MedicalTreatment.Result.PACKED,"Pouch packing failed");
        var packed=q.owner.getInventory().items.stream().filter(s->s.is(com.gamergaming.villagerguardgunners.init.ModItems.MEDICAL_POUCH.get())).findFirst().orElseThrow().copy();
        require(pouch.empty()&&pouch.restore(packed)&&pouch.inventory().getStackInSlot(2).getCount()==23
                &&pouch.readyAt()==original.getCompound(MedicalStorage.KEY).getLong("ReadyAt"),"Pouch restore lost supplies/cooldown");
        // Restoration uses a fresh drop identity; a second restore must not duplicate it.
        require(!pouch.restore(packed),"Packed pouch restored twice");
        passed("medical_storage_and_packing");

        var cap=g.getCapability(MedicalCapabilities.SUPPLIES);
        require(cap!=null&&cap.insertItem(0,bandages(7),true).isEmpty()&&pouch.inventory().getStackInSlot(0).isEmpty(),"Medical simulation mutated pouch");
        var input=bandages(7); require(cap.insertItem(0,input,false).isEmpty(),"Medical capability rejected supplies"); input.setCount(1); cap.getStackInSlot(0).setCount(2);
        require(pouch.inventory().getStackInSlot(0).getCount()==7&&cap.extractItem(0,7,false).isEmpty(),"Medical capability leaked inventory");
        require(g.getCapability(Capabilities.ItemHandler.ENTITY).getSlots()==9,"Pouch leaked into native ammo capability");
        g.discard(); require(cap.insertItem(1,bandages(2),false).getCount()==2&&g.getCapability(MedicalCapabilities.SUPPLIES)==null,"Removed guard accepted supplies");
        q.clearActors(); passed("medical_capability_lifecycle");
    }
    private static void damage(SupplyMedicalAcceptance q) {
        for (boolean casualty : new boolean[]{false, true}) {
            var g = q.guard(ORIGIN, false);
            g.setHealth(10);
            if (casualty) {
                g.hurt(q.level.damageSources().generic(), 1000);
                require(GuardLifeRuntime.casualty(g), "Damage fixture failed to down guard");
            }
            g.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            var life = GuardLifeAccess.get(g);
            int remaining = life.remaining(); float health = g.getHealth();
            long revision = GuardMedicAccess.get(g).damageRevision;
            g.invulnerableTime = 0; life.lastDamageTick = Long.MIN_VALUE;
            g.hurt(q.level.damageSources().generic(), 2);
            require(g.getHealth() == health && g.getAbsorptionAmount() == 6
                    && life.remaining() == remaining && GuardMedicAccess.get(g).damageRevision == revision,
                    "Fully absorbed hit changed medical state: casualty=" + casualty);
            passed(casualty ? "downed_full_absorption" : "active_full_absorption");

            // A Pre subscriber may cancel damage. It must not spend absorption or interrupt care.
            Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre> cancel = e -> {
                if (e.getEntity() == g) e.setNewDamage(0);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                g.invulnerableTime = 0; life.lastDamageTick = Long.MIN_VALUE;
                g.hurt(q.level.damageSources().generic(), 4);
                require(g.getAbsorptionAmount() == 6 && g.getHealth() == health
                        && life.remaining() == remaining && GuardMedicAccess.get(g).damageRevision == revision,
                        "Canceled hit changed medical state");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            passed(casualty ? "downed_canceled_damage" : "active_canceled_damage");

            // Respect another mod's absorption reduction callback, not a Pre-event estimate.
            Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre> reduce = e -> {
                if (e.getEntity() == g) e.getContainer().addModifier(
                        net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION,
                        (damage, amount) -> amount / 2);
            };
            NeoForge.EVENT_BUS.addListener(reduce);
            try {
                g.invulnerableTime = 0; life.lastDamageTick = Long.MIN_VALUE;
                g.hurt(q.level.damageSources().generic(), 4);
                require(g.getAbsorptionAmount() == 4 && g.getHealth() == (casualty ? health : health - 2)
                        && life.remaining() == (casualty ? remaining - 40 : remaining)
                        && GuardMedicAccess.get(g).damageRevision == revision + 1,
                        "Absorption callback/remaining health damage was not respected");
            } finally { NeoForge.EVENT_BUS.unregister(reduce); }
            passed(casualty ? "downed_modified_absorption" : "active_modified_absorption");
            health = g.getHealth(); remaining = life.remaining(); revision = GuardMedicAccess.get(g).damageRevision;
            g.invulnerableTime = 0; life.lastDamageTick = Long.MIN_VALUE;
            g.hurt(q.level.damageSources().generic(), 6);
            require(g.getAbsorptionAmount() == 0 && g.getHealth() == (casualty ? health : health - 2)
                    && life.remaining() == (casualty ? remaining - 40 : remaining)
                    && GuardMedicAccess.get(g).damageRevision == revision + 1,
                    "Partial absorption counted absorbed damage or interrupted twice");
            passed(casualty ? "downed_partial_absorption" : "active_partial_absorption");
            q.clearActors();
        }
    }

}
