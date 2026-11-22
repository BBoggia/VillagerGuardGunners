package accuracyprobe.mixin;

import accuracyprobe.BalanceProbe;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.entities.Guard;

/** Observer-only instrumentation: count the completed callback, after the full fire-event dispatch. */
@Mixin(value = NativeGunController.class, remap = false)
public abstract class RoundObservationMixin {
    @Shadow @Final private Guard guard;

    @Inject(method = "bindNativeRounds", at = @At("RETURN"), cancellable = true)
    private void observe(BooleanSupplier task, ItemStack stack, CallbackInfoReturnable<BooleanSupplier> cir) {
        BooleanSupplier bound = cir.getReturnValue();
        cir.setReturnValue(() -> {
            boolean accepted = bound.getAsBoolean();
            BalanceProbe.recordFinalRound(guard, accepted);
            return accepted;
        });
    }
}
