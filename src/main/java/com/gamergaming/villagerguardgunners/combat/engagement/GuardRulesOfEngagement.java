package com.gamergaming.villagerguardgunners.combat.engagement;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Allocation-free, server-authoritative firing policy for a target already
 * selected by Guard Villagers. This class deliberately performs no entity or
 * world scans.
 */
public final class GuardRulesOfEngagement {
    public static final TagKey<EntityType<?>> ALWAYS_ENGAGE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    VillagerGuardGunners.MODID,
                    "always_engage"));
    public static final TagKey<EntityType<?>> NEVER_ENGAGE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    VillagerGuardGunners.MODID,
                    "never_engage"));

    private GuardRulesOfEngagement() {
    }

    public static boolean mayEngage(Guard shooter, LivingEntity target) {
        return classify(shooter, target).mayEngage();
    }

    /**
     * Classifies the current target using deterministic precedence. Protected
     * allies and hold-fire cannot be bypassed by an allowlist entry.
     */
    public static EngagementReason classify(Guard shooter, LivingEntity target) {
        if (target == null) {
            return EngagementReason.NO_TARGET;
        }
        if (!target.isAlive()) {
            return EngagementReason.TARGET_DEAD;
        }
        if (GuardAllyProtection.isProtected(shooter, target)) {
            return EngagementReason.PROTECTED_ALLY;
        }

        VillagerGuardGunnersConfig.EngagementSettings settings =
                ModConfigs.GUARD_GUNNERS.engagement;
        EngagementMode mode = GuardOrdersAccess.getOrders(shooter)
                .resolveEngagementMode(settings.mode.get());
        ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (entityTypeId != null && settings.excludedEntities.get().contains(entityTypeId)) {
            return EngagementReason.EXPLICIT_EXCLUSION;
        }
        if (target.getType().is(NEVER_ENGAGE)) {
            return EngagementReason.NEVER_ENGAGE_TAG;
        }
        if (mode == EngagementMode.HOLD_FIRE) {
            return EngagementReason.HOLD_FIRE;
        }
        if (entityTypeId != null && settings.additionalTargets.get().contains(entityTypeId)) {
            return EngagementReason.EXPLICIT_ADDITION;
        }
        if (target.getType().is(ALWAYS_ENGAGE)) {
            return EngagementReason.ALWAYS_ENGAGE_TAG;
        }

        return switch (mode) {
            case GUARD_TARGETS -> EngagementReason.GUARD_TARGET;
            case HOSTILE_MOBS_ONLY -> target instanceof Enemy
                    ? EngagementReason.HOSTILE_MOB
                    : EngagementReason.NOT_HOSTILE;
            case DEFENSIVE -> defensiveReason(
                    shooter,
                    target,
                    settings.defensiveMemoryTicks.get());
            case HOLD_FIRE -> EngagementReason.HOLD_FIRE;
        };
    }

    private static EngagementReason defensiveReason(
            Guard shooter,
            LivingEntity target,
            int memoryTicks) {
        if (shooter.getLastHurtByMob() == target
                && EngagementMemory.isRecent(
                        shooter.tickCount,
                        shooter.getLastHurtByMobTimestamp(),
                        memoryTicks)) {
            return EngagementReason.RECENT_GUARD_ATTACKER;
        }

        LivingEntity recentVictim = target.getLastHurtMob();
        if (recentVictim != null
                && EngagementMemory.isRecent(
                        target.tickCount,
                        target.getLastHurtMobTimestamp(),
                        memoryTicks)
                && GuardAllyProtection.isProtected(shooter, recentVictim)) {
            return EngagementReason.RECENT_ALLY_ATTACKER;
        }
        return EngagementReason.NO_RECENT_AGGRESSION;
    }

    public enum EngagementReason {
        NO_TARGET(false, false),
        TARGET_DEAD(false, true),
        PROTECTED_ALLY(false, true),
        EXPLICIT_EXCLUSION(false, true),
        NEVER_ENGAGE_TAG(false, true),
        HOLD_FIRE(false, false),
        NOT_HOSTILE(false, true),
        NO_RECENT_AGGRESSION(false, true),
        EXPLICIT_ADDITION(true, false),
        ALWAYS_ENGAGE_TAG(true, false),
        GUARD_TARGET(true, false),
        HOSTILE_MOB(true, false),
        RECENT_GUARD_ATTACKER(true, false),
        RECENT_ALLY_ATTACKER(true, false);

        private final boolean mayEngage;
        private final boolean releaseTarget;

        EngagementReason(boolean mayEngage, boolean releaseTarget) {
            this.mayEngage = mayEngage;
            this.releaseTarget = releaseTarget;
        }

        public boolean mayEngage() {
            return mayEngage;
        }

        /** Whether the upstream selector should be allowed to choose again. */
        public boolean releaseTarget() {
            return releaseTarget;
        }
    }
}
