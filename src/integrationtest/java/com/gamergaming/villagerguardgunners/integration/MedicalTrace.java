package com.gamergaming.villagerguardgunners.integration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/** Not a downed-state implementation: a scoped experiment at the death boundary. */
public final class MedicalTrace implements AutoCloseable {
    private static final ThreadLocal<MedicalTrace> ACTIVE = new ThreadLocal<>();
    public final Guard guard;
    public final List<String> events = new ArrayList<>();
    public boolean cancelHeal;
    public boolean cancelDeath;
    public boolean interceptGuardDeath;
    public int intercepted;

    public MedicalTrace(Guard guard) {
        this.guard = guard;
        if (ACTIVE.get() != null) throw new IllegalStateException("Medical probe scopes cannot nest");
        ACTIVE.set(this);
        NeoForge.EVENT_BUS.register(this);
    }

    public static boolean intercept(Guard guard, DamageSource source) {
        var trace = ACTIVE.get();
        if (trace == null || trace.guard != guard) return false;
        trace.events.add("guard.die");
        if (trace.interceptGuardDeath && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            trace.intercepted++;
            guard.setHealth(1);
            return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void hurt(LivingHurtEvent event) {
        if (event.getEntity() == guard) events.add("hurt");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void damage(LivingDamageEvent event) {
        if (event.getEntity() == guard) events.add("damage");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void heal(LivingHealEvent event) {
        if (event.getEntity() == guard) {
            events.add("heal");
            if (cancelHeal) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void death(LivingDeathEvent event) {
        if (event.getEntity() == guard) {
            events.add("death");
            if (cancelDeath) {
                event.setCanceled(true);
                guard.setHealth(1);
            }
        }
    }

    @SubscribeEvent
    public void conversion(LivingConversionEvent.Pre event) {
        events.add("conversion.pre");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void drops(LivingDropsEvent event) {
        if (event.getEntity() == guard) events.add("drops");
    }

    @Override
    public void close() {
        NeoForge.EVENT_BUS.unregister(this);
        ACTIVE.remove();
    }
}
