package shotconflict.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.util.CycleTaskHelper;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shotconflict.HookCounters;

/** Higher priority makes the old required redirects lose, as in the reported conflict. */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false, priority = 1100)
public abstract class ConflictingShotMixin {
    @Redirect(method = "shootOnce", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/CycleTaskHelper;addCycleTask(Ljava/util/function/BooleanSupplier;JI)V"))
    private void competingSchedule(BooleanSupplier task, long interval, int count) {
        HookCounters.SCHEDULED.incrementAndGet();
        CycleTaskHelper.addCycleTask(() -> {
            HookCounters.CALLBACKS.incrementAndGet();
            return task.getAsBoolean();
        }, interval, count);
    }

    @Redirect(method = "/^lambda\\$shootOnce\\$[0-9]+$/", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean competingInsertion(Level level, Entity projectile) {
        HookCounters.INSERTIONS.incrementAndGet();
        NeoForge.EVENT_BUS.post(new HookCounters.ProjectileEvent((EntityKineticBullet) projectile));
        boolean added = level.addFreshEntity(projectile);
        if (added) HookCounters.ACCEPTED.incrementAndGet();
        return added;
    }
}
