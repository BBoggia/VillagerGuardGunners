package com.gamergaming.villagerguardgunners.defense;

import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import tallestegg.guardvillagers.common.entities.Guard;

/** Allocation-light Forge event adapters for network defense-state signals. */
public final class VillageDefenseEventSources {
    private VillageDefenseEventSources() {
    }

    public static void onGuardTargetChanged(LivingChangeTargetEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof Guard guard)
                || !(guard.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null
                || !target.isAlive()
                || target.isSpectator()
                || GuardAllyProtection.isProtected(guard, target)) {
            return;
        }
        VillageDefenseEventRuntime.signalAt(
                level,
                guard.blockPosition(),
                VillageDefenseSignal.HOSTILE_CONFIRMED,
                VillageDefenseEventSource.GUARD_TARGET);
    }

    public static void onDefendedEntityDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getNewDamage() > 0)
                || !(victim.level() instanceof ServerLevel level)
                || !isDefendedEntity(victim)
                || !hasExternalLivingAttacker(event.getSource(), victim)) {
            return;
        }
        VillageDefenseEventRuntime.signalAt(
                level,
                victim.blockPosition(),
                VillageDefenseSignal.FRIENDLY_DAMAGED,
                VillageDefenseEventSource.FRIENDLY_DAMAGED);
    }

    public static void onDefendedEntityKilled(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled()
                || !(victim.level() instanceof ServerLevel level)
                || !isDefendedEntity(victim)
                || !hasExternalLivingAttacker(event.getSource(), victim)) {
            return;
        }
        VillageDefenseEventRuntime.signalAt(
                level,
                victim.blockPosition(),
                VillageDefenseSignal.FRIENDLY_KILLED,
                VillageDefenseEventSource.FRIENDLY_KILLED);
    }

    static boolean isDefendedEntity(LivingEntity entity) {
        if (entity instanceof Guard) {
            return true;
        }
        VillagerGuardGunnersConfig.AllySettings settings =
                ModConfigs.GUARD_GUNNERS.safety.allies;
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(
                entity.getType());
        if (entityTypeId != null
                && settings.excludedEntities.get().contains(entityTypeId)) {
            return false;
        }
        return entity instanceof AbstractVillager
                || entity instanceof IronGolem
                || entity.getType().is(GuardAllyProtection.GUARD_ALLIES)
                || entityTypeId != null
                        && settings.additionalProtectedEntities.get().contains(entityTypeId);
    }

    private static boolean hasExternalLivingAttacker(
            DamageSource source,
            LivingEntity victim) {
        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }
        return attacker instanceof LivingEntity livingAttacker
                && livingAttacker != victim
                && !isDefendedEntity(livingAttacker);
    }
}
