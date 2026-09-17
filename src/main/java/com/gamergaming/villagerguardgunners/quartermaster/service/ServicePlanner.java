package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tallestegg.guardvillagers.entities.Guard;

/** Uses the same needs/allocation rules as previews, issuing only their first physical transfer. */
public final class ServicePlanner {
    public static ServicePlan plan(Guard guard, QuartermasterBlockEntity station, List<ServiceSource> sources) {
        var state=GuardEquipmentServiceAccess.get(guard);
        if(!QuartermasterServiceRuntime.assignmentAllows(guard,station))return null;
        var captured=ServiceAssessment.capture(station,List.of(guard),Map.of(guard.getUUID(),state.settings()),sources);
        var result=ServiceNeeds.evaluate(captured.input).guards().get(0);
        if(result.transfers().isEmpty()) {
            state.status=status(result); return null;
        }
        var transfer=result.transfers().get(0); var source=captured.sources.get(transfer.source());
        var before=captured.before.get(guard.getUUID());
        var stock=captured.input.sources().stream().filter(s->s.key().equals(transfer.source())&&s.slot()==transfer.sourceSlot()).findFirst().orElseThrow().stack();
        var original=captured.variants.get(stock.variant()).copy(); original.setCount(stock.count());
        var replacement=original.copy(); replacement.setCount(transfer.amount()+(transfer.destination()>=6?before.get(transfer.destination()).getCount():0));
        state.status=ServiceStatus.SATISFIED;
        return new ServicePlan(UUID.randomUUID(),guard,station,station.dropId(),source,transfer.sourceSlot(),transfer.destination(),transfer.returnSlot(),
                original,replacement,before,captured.profileRevision,state.policy(),state.profile(),state.replaceExisting(),
                source.receipt().revision(),state.receipt.revision(),guard.level().getGameTime()+600,state.settingsRevision());
    }
    private static ServiceStatus status(ServiceNeeds.GuardResult result) {
        if(has(result,ServiceNeeds.Reason.UNAVAILABLE))return ServiceStatus.RECOVERY_REQUIRED;
        if(has(result,ServiceNeeds.Reason.MANUAL))return ServiceStatus.MANUAL;
        if(has(result,ServiceNeeds.Reason.PROFILE_MISSING))return ServiceStatus.PROFILE_MISSING;
        if(has(result,ServiceNeeds.Reason.RETURN_FULL))return ServiceStatus.RETURN_STORAGE_FULL;
        if(result.rows().stream().anyMatch(r->r.missing()>0 || r.reasons().contains(ServiceNeeds.Reason.DEFINITION_UNAVAILABLE)))return ServiceStatus.NO_MATCHING_STOCK;
        return ServiceStatus.SATISFIED;
    }
    private static boolean has(ServiceNeeds.GuardResult result,ServiceNeeds.Reason reason) {
        return result.rows().stream().anyMatch(r->r.reasons().contains(reason));
    }
}
