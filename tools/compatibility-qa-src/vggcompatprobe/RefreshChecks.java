package vggcompatprobe;

import java.util.*;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime;

/** Observe ordinary periodic refreshes without changing movement precedence. */
final class RefreshChecks {
    static final List<Object> samples=new ArrayList<>();
    static void sample() {
        if(!Boolean.getBoolean("vgg.compat.refreshChecks") || Probe.ticks%20!=0)return;
        var id=new VillageIdentity(Probe.level.dimension().location(),
                net.minecraft.resources.ResourceLocation.parse((String)Probe.result.get("structure")),Probe.start.getChunkPos().toLong()).id();
        var view=VillageCoverageRuntime.snapshot(Probe.level,id);
        if(samples.size()<256)samples.add(Map.of("tick",Probe.ticks,"status",view.status(),
                "unchangedRefreshes",VillageCoverageRuntime.unchangedRefreshes(Probe.level,id),
                "assignments",view.plan()==null?List.of():view.plan().assignments(),
                "areas",view.plan()==null?List.of():view.plan().areas(),
                "guards",Probe.level.getEntitiesOfClass(tallestegg.guardvillagers.entities.Guard.class,Probe.area)
                    .stream().limit(32).map(g->Map.of("id",g.getUUID(),"position",g.position(),
                        "availability",VillageCoverageRuntime.availability(g).name(),
                        "navigationDone",g.getNavigation().isDone(),
                        "goals",g.goalSelector.getAvailableGoals().stream().limit(64).map(goal->Map.of(
                            "class",goal.getGoal().getClass().getName(),"priority",goal.getPriority(),
                            "running",goal.isRunning(),"flags",goal.getFlags().toString())).toList())).toList()));
        Probe.result.put("refreshChecks",Map.of("samples",samples,
                "unchangedRefreshes",VillageCoverageRuntime.unchangedRefreshes(Probe.level,id)));
    }
}
