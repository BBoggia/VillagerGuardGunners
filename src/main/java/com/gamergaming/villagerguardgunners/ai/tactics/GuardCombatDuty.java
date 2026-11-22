package com.gamergaming.villagerguardgunners.ai.tactics;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Transient engagement/return anchors. Values never retain entities or worlds. */
public final class GuardCombatDuty {
    private static final Map<Guard, Duty> DUTIES = new WeakHashMap<>();
    private GuardCombatDuty() { }
    public enum Source { VILLAGE, DEFENSE_POST, PATROL_ROUTE }
    public record Duty(ResourceLocation dimension, UUID owner, Source source, long sourceRevision, int ordersRevision,
            BlockPos anchor, double radius, long expiresAt) {
        public boolean post() { return source == Source.DEFENSE_POST; }
        public TacticalAssignmentBounds bounds() {
            return new TacticalAssignmentBounds(Vec3.atBottomCenterOf(anchor), radius);
        }
    }

    public static void capture(Guard guard) {
        try { captureSafely(guard); }
        catch (RuntimeException ignored) { forget(guard); }
    }

    private static void captureSafely(Guard guard) {
        Duty existing = current(guard);
        if (existing != null) {
            if (existing.expiresAt() - guard.level().getGameTime() > 1180) return;
            DUTIES.put(guard, new Duty(existing.dimension(), existing.owner(), existing.source(), existing.sourceRevision(),
                    existing.ordersRevision(), existing.anchor(), existing.radius(), guard.level().getGameTime() + 1200));
            return;
        }
        if (!(guard.level() instanceof ServerLevel level) || !automatic(guard)) return;
        var route = com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver.resolve(guard).orElse(null);
        if (route != null && com.gamergaming.villagerguardgunners.squad.SquadPatrolRuntimePolicy.executionEnabled()) {
            if (new TacticalPositionPlanner(guard).safeStanding(guard.position())) {
                DUTIES.put(guard, new Duty(level.dimension().location(), route.squadId(), Source.PATROL_ROUTE, route.revision(),
                        GuardRoleResolver.effectiveRevision(guard), com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.navigationNode(level, guard.position()), 24, level.getGameTime() + 1200));
            }
            return;
        }
        var post = VillageDefenseStaffingRuntime.assignment(guard);
        if (post != null) {
            DUTIES.put(guard, new Duty(level.dimension().location(), post.slotId(), Source.DEFENSE_POST, 0,
                    GuardRoleResolver.effectiveRevision(guard), post.slot().position().immutable(), 16, level.getGameTime() + 1200));
            return;
        }
        if (GuardSquadAccess.getSquadId(guard) != null) return;
        var registry = VillageRegistrySavedData.get(level);
        UUID village = registry.guardVillage(guard.getUUID());
        var record = village == null || !registry.enabled() ? null : registry.get(village);
        if (record == null || !SettlementDefinitions.lookup(record.identity().structure()).friendly()
                || !record.footprint().bounds().contains(guard.blockPosition())) return;
        if (!new TacticalPositionPlanner(guard).safeStanding(guard.position())) return;
        // Preserve a local village duty origin across target replacements and short combat gaps.
        DUTIES.put(guard, new Duty(level.dimension().location(), village, Source.VILLAGE, 0,
                GuardRoleResolver.effectiveRevision(guard), com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.navigationNode(level, guard.position()), 24, level.getGameTime() + 1200));
    }

    public static Duty current(Guard guard) {
        try { return currentSafely(guard); }
        catch (RuntimeException ignored) { forget(guard); return null; }
    }

    private static Duty currentSafely(Guard guard) {
        Duty duty = DUTIES.get(guard);
        if (duty == null) return null;
        boolean valid = guard.level() instanceof ServerLevel && automatic(guard)
                && duty.dimension().equals(guard.level().dimension().location())
                && duty.ordersRevision() == GuardRoleResolver.effectiveRevision(guard)
                && guard.level().getGameTime() < duty.expiresAt();
        if (valid && duty.post()) {
            var post = VillageDefenseStaffingRuntime.assignment(guard);
            valid = post != null && post.slotId().equals(duty.owner()) && post.slot().position().equals(duty.anchor());
        } else if (valid && duty.source() == Source.PATROL_ROUTE) {
            var route = com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver.resolve(guard).orElse(null);
            valid = route != null && route.squadId().equals(duty.owner()) && route.revision() == duty.sourceRevision()
                    && com.gamergaming.villagerguardgunners.squad.SquadPatrolRuntimePolicy.executionEnabled();
        } else if (valid) {
            var registry = VillageRegistrySavedData.get((ServerLevel) guard.level());
            var record = registry.get(duty.owner());
            valid = registry.enabled() && GuardSquadAccess.getSquadId(guard) == null
                    && duty.owner().equals(registry.guardVillage(guard.getUUID())) && record != null
                    && SettlementDefinitions.lookup(record.identity().structure()).friendly()
                    && record.footprint().bounds().contains(duty.anchor());
        }
        if (!valid) { forget(guard); return null; }
        return duty;
    }

    private static boolean automatic(Guard guard) {
        var movement = GuardRoleResolver.effectiveOrders(guard).movement();
        return ModConfigs.GUARD_GUNNERS.enabled.get() && guard.isAlive() && !guard.isRemoved()
                && !guard.isNoAi() && GuardSquadCommandAccess.getCommand(guard) == null
                && !guard.isFollowing() && (!(guard instanceof GuardCoverageAccess access) || !access.villagerGuardGunners$hasFollowOrder())
                && !movement.holdsPosition() && !movement.treatAsPatrolling(guard.isPatrolling());
    }
    public static TacticalAssignmentBounds bounds(Guard guard) {
        Duty duty = current(guard);
        return duty == null ? TacticalAssignmentBounds.UNBOUNDED : duty.bounds();
    }
    public static void forget(Guard guard) { DUTIES.remove(guard); }
}
