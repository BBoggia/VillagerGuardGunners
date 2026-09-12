package com.gamergaming.villagerguardgunners.mixin;

import java.util.Set;
import javax.annotation.Nullable;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ai.navigation.GuardDetourPathPolicy;
import com.gamergaming.villagerguardgunners.medical.TransportRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.entities.Guard;

/** A nearby downstairs destination can require a route longer than the mob's follow range. */
@Mixin(PathNavigation.class)
public abstract class GuardDetourPathNavigationMixin {
    @Shadow @Final protected Mob mob;
    @Shadow @Nullable protected Path path;
    @Shadow @Nullable protected abstract Path createPath(
            Set<BlockPos> targets, int padding, boolean startAbove, int accuracy, float range);
    @Unique private GuardDetourPathPolicy villagerguardgunners$detourBudget;

    @Inject(method = "createPath(Ljava/util/Set;IZI)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("RETURN"), cancellable = true)
    private void villagerguardgunners$retryVerticalDetour(
            Set<BlockPos> targets, int padding, boolean startAbove, int accuracy,
            CallbackInfoReturnable<Path> callback) {
        Path original = callback.getReturnValue();
        if (original != null && original.canReach()) return;
        if (!((Object) this instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation)
                || !(mob instanceof Guard guard) || guard.level().isClientSide
                || !guard.isAlive() || !guard.onGround() || guard.isPassenger() || guard.isVehicle()
                || guard.getTarget() != null || targets.size() != 1) return;
        BlockPos destination = targets.iterator().next();
        double range = (float) guard.getAttributeValue(Attributes.FOLLOW_RANGE);
        long vertical = Math.abs((long) destination.getY() - guard.getBlockY());
        if (!GuardDetourPathPolicy.eligible(range, guard.blockPosition().distSqr(destination), vertical)
                || !guard.level().hasChunkAt(destination) || TransportRuntime.busy(guard)) return;
        // Screened combat and transport routes retain their own planning budgets.
        for (var wrapped : guard.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GuardGunCombatGoal goal
                    && (wrapped.isRunning() || goal.ownsScreenedCombatApproach()
                    || goal.tacticalNavigation().ownsPath())) return;
        }
        if (villagerguardgunners$detourBudget == null) {
            villagerguardgunners$detourBudget = new GuardDetourPathPolicy();
        }
        if (!villagerguardgunners$detourBudget.tryAcquire(guard.level().getGameTime())) return;

        Path activePath = path;
        Path retry;
        try {
            // Bypass the native cache of an in-progress partial path for this search.
            // Planning must not replace a caller's currently followed path.
            path = null;
            retry = createPath(targets, GuardDetourPathPolicy.retryPadding(range, padding),
                    startAbove, accuracy, GuardDetourPathPolicy.retryRange(range));
        } finally {
            path = activePath;
        }
        // Keep the original result if the bounded retry also cannot reach the destination.
        if (retry != null && retry.canReach()) callback.setReturnValue(retry);
    }
}
