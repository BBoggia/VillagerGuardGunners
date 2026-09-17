package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import tallestegg.guardvillagers.entities.Guard;

/** One synchronous physical slot exchange. Destination and source snapshots are rechecked before the write-ahead checkpoint. */
public final class ServiceTransactions {
    private static final Set<Object> ACTIVE = new HashSet<>();
    public static boolean mutating(Object owner) { return ACTIVE.contains(owner); }
    public static boolean valid(ServicePlan plan) {
        try { return plan != null && validate(plan,true); } catch (RuntimeException exception) { return false; }
    }
    /** Same live checks without quarantining receipts or changing service state. */
    public static boolean incoming(ServicePlan plan) {
        try { return plan!=null && QuartermasterServiceRuntime.plan(plan.guard())==plan && validate(plan,false); }
        catch(RuntimeException unavailable) { return false; }
    }
    private static boolean validate(ServicePlan plan, boolean verify) {
        Guard guard = plan.guard(); var state = GuardEquipmentServiceAccess.get(guard); var source = plan.source();
        return !mutating(guard) && !mutating(source.block()) && !mutating(plan.station())
                && state.settingsRevision() == plan.settingsRevision()
                && QuartermasterServiceRuntime.assignmentAllows(guard, plan.station()) && (verify?ServiceEligibility.guard(guard):ServiceEligibility.inspectGuard(guard)) == ServiceStatus.READY
                && (verify?ServiceEligibility.station(plan.station()):ServiceEligibility.inspectStation(plan.station())) == ServiceStatus.READY && plan.station().mayService(guard)
                && plan.station().dropId().equals(plan.placement()) && source.live(guard)
                && (source.block() == plan.station() || plan.station().linkedArmories().contains(source.block().getBlockPos()) && source.block().getBlockPos().distSqr(plan.station().getBlockPos()) <= 256)
                && guard.level().getGameTime() < plan.expiresAt() && EquipmentProfiles.revision() == plan.profileRevision()
                && state.policy() == plan.policy() && Objects.equals(state.profile(), plan.profile()) && state.replaceExisting() == plan.replaceExisting()
                && state.receipt.revision() == plan.guardRevision() && source.receipt().revision() == plan.sourceRevision()
                && (verify?ServiceJournal.get(((ServerLevel) guard.level()).getServer()).verify(source.receipt()):ServiceJournal.get(((ServerLevel) guard.level()).getServer()).matches(source.receipt()))
                && ItemStack.matches(source.inventory().getStackInSlot(plan.sourceSlot()), plan.stockBefore())
                && ServiceEquipment.same(guard, plan.guardBefore()) && ServiceEquipment.valid(guard, plan.targetSlot(), plan.replacement())
                && (plan.returnSlot() < 0 || plan.station().inventory().getStackInSlot(plan.returnSlot()).isEmpty()
                    && plan.station().inventory().isItemValid(plan.returnSlot(), plan.guardBefore().get(plan.targetSlot())));
    }
    public static ServiceStatus transfer(ServicePlan plan) {
        if (!valid(plan)) return ServiceStatus.RETRY;
        var guard = plan.guard(); var station = plan.station(); var source = plan.source();
        if (guard.distanceToSqr(station.getBlockPos().getX() + .5, station.getBlockPos().getY(), station.getBlockPos().getZ() + .5) > 9) return ServiceStatus.TRAVELLING;
        var state = GuardEquipmentServiceAccess.get(guard);
        ItemStack old = plan.guardBefore().get(plan.targetSlot());
        int amount = plan.replacement().getCount() - (plan.targetSlot() >= 6 ? old.getCount() : 0);
        ItemStack remaining = plan.stockBefore().copy(); remaining.shrink(amount);
        if (!ServiceJournal.get(((ServerLevel) guard.level()).getServer()).begin(source.receipt(), state.receipt, plan.operation())) return ServiceStatus.RECOVERY_REQUIRED;
        ACTIVE.add(guard); ACTIVE.add(source.block());
        try {
            if (plan.returnSlot() >= 0) station.inventory().setStackInSlot(plan.returnSlot(), old.copy());
            source.inventory().setStackInSlot(plan.sourceSlot(), remaining.copy());
            ServiceEquipment.set(guard, plan.targetSlot(), plan.replacement());
            if (!ItemStack.matches(ServiceEquipment.get(guard, plan.targetSlot()), plan.replacement())
                    || !ItemStack.matches(source.inventory().getStackInSlot(plan.sourceSlot()), remaining)) throw new IllegalStateException("Transfer did not retain its result");
            source.receipt().revision(source.receipt().revision() + 1); state.receipt.revision(state.receipt.revision() + 1);
            source.block().setChanged(); ServiceHistory.completed(plan,amount); return ServiceStatus.SUPPLIED;
        } catch (RuntimeException exception) {
            // Keep exact rollback snapshots. Never synthesize a replacement from a later journal replay.
            try {
                // Restore the destination first: if it refuses rollback, do not duplicate the issued stock.
                ServiceEquipment.set(guard, plan.targetSlot(), old);
                if (ItemStack.matches(ServiceEquipment.get(guard, plan.targetSlot()), old)) {
                    if (plan.returnSlot() >= 0) station.inventory().setStackInSlot(plan.returnSlot(), ItemStack.EMPTY);
                    source.inventory().setStackInSlot(plan.sourceSlot(), plan.stockBefore().copy());
                }
            } catch (RuntimeException rollbackFailure) {
                // Preserve the physical remainder for inspection; quarantine both owners below.
            } finally { state.receipt.quarantine(); source.receipt().quarantine(); source.block().setChanged(); }
            return ServiceStatus.RECOVERY_REQUIRED;
        } finally { ACTIVE.remove(guard); ACTIVE.remove(source.block()); }
    }
}
