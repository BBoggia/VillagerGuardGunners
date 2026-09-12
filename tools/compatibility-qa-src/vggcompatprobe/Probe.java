package vggcompatprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.VillagePoolElementAccessor;
import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlacement;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationSavedData;
import com.tacz.guns.api.item.IGun;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import tallestegg.guardvillagers.entities.Guard;

@Mod("vggcompatprobe")
public class Probe {
    static final Path DIR=Path.of(System.getProperty("vgg.compat.dir"));
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final boolean EXTENDED=Boolean.getBoolean("vgg.compat.extended");
    static int ticks, phase; static boolean done; static ServerLevel level; static StructureStart start;
    static AABB area; static Vec3 camera; static int locatedTick;
    static final Map<String,Object> result=new LinkedHashMap<>();
    static final Map<UUID,Vec3> origins=new HashMap<>();
    public Probe(){MinecraftForge.EVENT_BUS.register(this);}
    static void write(String file,Object value){try{Files.writeString(DIR.resolve(file),JSON.toJson(value));}catch(Exception e){throw new RuntimeException(e);}}
    static void progress(String value){write("progress.json",Map.of("stage",value,"tick",ticks));}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public void about(ServerAboutToStartEvent event){
        var cfg=ModConfigs.GUARD_GUNNERS;
        result.put("production",FMLLoader.isProduction());
        result.put("runtimeKind",Boolean.getBoolean("vgg.compat.dedicated")?"packaged_dedicated_server":"packaged_client");
        result.put("vggCodeSource",VillagerGuardGunners.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.put("mods",ModList.get().getMods().stream().map(m->Map.of("id",m.getModId(),"version",m.getVersion().toString())).toList());
        result.put("defaults",Map.of("managedGeneration",cfg.villagePopulation.managedGeneration.get(),"automaticCoverage",cfg.villageDefense.automaticCoverage.get(),"buildings",cfg.villageBuildings.enabled.get(),"maximumGuards",cfg.villagePopulation.maximumGuards.get()));
        if(System.getProperty("vgg.compat.managed")!=null)cfg.villagePopulation.managedGeneration.accept(Boolean.getBoolean("vgg.compat.managed"));
        if(System.getProperty("vgg.compat.coverage")!=null)cfg.villageDefense.automaticCoverage.accept(Boolean.getBoolean("vgg.compat.coverage"));
        if(System.getProperty("vgg.compat.buildings")!=null)cfg.villageBuildings.enabled.accept(Boolean.getBoolean("vgg.compat.buildings"));
        if(System.getProperty("vgg.compat.maximum")!=null)cfg.villagePopulation.maximumGuards.accept(Integer.getInteger("vgg.compat.maximum"));
        result.put("schema",2);
        result.put("observationKind","natural_generation");
        result.put("effectiveSettings",Map.of("managedGeneration",cfg.villagePopulation.managedGeneration.get(),"automaticCoverage",cfg.villageDefense.automaticCoverage.get(),"buildings",cfg.villageBuildings.enabled.get(),"maximumGuards",cfg.villagePopulation.maximumGuards.get()));
        Observations.verifyCodeSources();
        write("startup.json",result);
        if(!FMLLoader.isProduction()) throw new IllegalStateException("This observer requires a production Forge runtime");
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||done)return;
        ticks++;
        try{
            level=event.getServer().overworld();
            if(event.getServer().getPlayerCount()==0&&!Boolean.getBoolean("vgg.compat.dedicated"))return;
            if(Boolean.getBoolean("vgg.compat.barracksDesignPreview")){BarracksDesignPreview.tick(event);return;}
            if(phase==0){
                progress("locating natural structure");
                var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                var id=ResourceLocation.parse(System.getProperty("vgg.compat.structure","minecraft:village_plains"));
                var structure=registry.get(id);
                if(structure==null)throw new IllegalStateException("Missing requested structure "+id);
                var holder=registry.getHolderOrThrow(registry.getResourceKey(structure).orElseThrow());
                result.put("structure",id.toString());result.put("villageTag",holder.is(StructureTags.VILLAGE));
                result.put("seed",level.getSeed());
                List<String> villageTypes=new ArrayList<>();registry.getTagOrEmpty(StructureTags.VILLAGE).forEach(h->h.unwrapKey().ifPresent(k->villageTypes.add(k.location().toString())));result.put("loadedVillageTypes",villageTypes);
                var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,Integer.getInteger("vgg.compat.locateRadiusChunks",100),false);
                if(located==null)throw new IllegalStateException("No structure found within bounded 100-chunk locate radius");
                var pos=located.getFirst(); result.put("located",List.of(pos.getX(),pos.getY(),pos.getZ()));
                progress("loading natural start");
                for(int x=(pos.getX()>>4)-2;x<=(pos.getX()>>4)+2&&start==null;x++)for(int z=(pos.getZ()>>4)-2;z<=(pos.getZ()>>4)+2&&start==null;z++){
                    var candidate=level.getChunk(x,z).getAllStarts().get(structure);
                    if(candidate!=null&&candidate.isValid())start=candidate;
                }
                if(start==null)throw new IllegalStateException("Located structure start was not present in nearby chunks");
                start=RegionalBarracksNatural.select(structure,holder,start);
                result.put("located",List.of(start.getBoundingBox().getCenter().getX(),start.getBoundingBox().getCenter().getY(),start.getBoundingBox().getCenter().getZ()));
                var b=start.getBoundingBox();area=AABB.of(b).inflate(8);
                result.put("bounds",List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));
                result.put("pieceCount",start.getPieces().size());
                List<Object> pieces=new ArrayList<>();
                for(var piece:start.getPieces()){
                    var classification=com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions.lookup(id);
                    var read=com.gamergaming.villagerguardgunners.village.compat.PieceReaders.read(piece,classification.reader());
                    String template=read.geometry().template().toString();
                    boolean home=classification.kind(template)==com.gamergaming.villagerguardgunners.village.compat.SettlementProfile.Kind.RESIDENCE;
                    var pb=piece.getBoundingBox();pieces.add(Map.of("template",template,"recognizedHouse",home,"reader",read.reader(),"readerSupported",read.supported(),"readerReason",read.reason(),"elementClass",piece instanceof PoolElementStructurePiece pool?pool.getElement().getClass().getName():piece.getClass().getName(),"bounds",List.of(pb.minX(),pb.minY(),pb.minZ(),pb.maxX(),pb.maxY(),pb.maxZ())));
                }
                result.put("pieces",pieces);
                Observations.resources();
                result.put("nativeGuardTemplates",tallestegg.guardvillagers.configuration.GuardConfig.COMMON.structuresThatSpawnGuards.get());
                result.put("nativeGuardSpawnSetting",tallestegg.guardvillagers.configuration.GuardConfig.COMMON.guardSpawnInVillage.get());
                int chunks=((b.maxX()>>4)-(b.minX()>>4)+3)*((b.maxZ()>>4)-(b.minZ()>>4)+3);
                result.put("forcedChunkCount",chunks);
                if(chunks>Integer.getInteger("vgg.compat.maximumForcedChunks",400))throw new IllegalStateException("Village exceeds the bounded 400-chunk observation window");
                progress("loading village footprint");
                for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++)level.setChunkForced(x,z,true);
                camera=new Vec3((b.minX()+b.maxX())*.5,b.maxY()+25,b.minZ()-25);
                for(var player:event.getServer().getPlayerList().getPlayers()){
                    player.setGameMode(GameType.SPECTATOR);player.teleportTo(level,camera.x,camera.y,camera.z,0,35);
                }
                level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                locatedTick=ticks;phase=1;write("structure.json",result);progress("observing natural residents");
            }
            int elapsed=ticks-locatedTick;
            Observations.sample();
            RefreshChecks.sample();
            if(phase==1&&elapsed>=200){census("natural");phase=2;progress("observing guard duties and native paths");}
            if(elapsed%200==0)diagnostics("latest-diagnostics.json");
            if(phase==2&&elapsed>=500){census("settled");if(EXTENDED){Behavior.begin();phase=3;progress("executing checkpoint orders on natural terrain");}else phase=6;}
            if(phase==6&&elapsed>=Integer.getInteger("vgg.compat.observationTicks",500))finish(event);
            if(phase==3){Behavior.sample();if(elapsed>=1100){Behavior.finishPatrol();census("checkpoint");Behavior.combat();phase=4;progress("observing live gun combat");}}
            if(phase==4){Behavior.sampleCombat();if(elapsed>=1500){Behavior.finishCombat();phase=5;progress("allowing village discovery and coverage to settle");}}
            if(phase==5&&elapsed>=Integer.getInteger("vgg.compat.observationTicks",1600)){census("extended");finish(event);}
            if(ticks>Integer.getInteger("vgg.compat.observationTicks",500)+7500)throw new IllegalStateException("Observation tick limit");
        }catch(Throwable failure){result.put("status","failed");result.put("error",failure.toString());write("result.json",result);failure.printStackTrace();done=true;event.getServer().halt(false);}
    }
    static void diagnostics(String file){
        var data=VillageRegistrySavedData.get(level);
        var id=new com.gamergaming.villagerguardgunners.village.VillageIdentity(level.dimension().location(),ResourceLocation.parse((String)result.get("structure")),start.getChunkPos().toLong()).id();
        var record=data.get(id);
        Map<String,Object> d=new LinkedHashMap<>();d.put("elapsed",ticks-locatedTick);d.put("discovery",com.gamergaming.villagerguardgunners.village.VillageDiscoveryRuntime.diagnostics(level));d.put("registryRecords",data.size());d.put("requestedVillageRecord",record==null?"absent":Map.of("pieces",record.footprint().pieces().size(),"homes",record.homes().size()));d.put("coverage",com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.snapshot(level,id));d.put("buildings",com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolIntegration.installationSnapshot().toString());
        Observations.diagnostics(d,id);
        result.put("diagnostics",d);write(file,d);write("diagnostics-"+(ticks-locatedTick)+".json",d);
    }
    static void finish(TickEvent.ServerTickEvent event){if(!RegionalArmoryNatural.beforeFinish())return;if(!RegionalSentryPostNatural.beforeFinish())return;if(!RegionalWatchtowerNatural.beforeFinish())return;if(!RegionalTrainingYardNatural.beforeFinish())return;if(!CustomBarracksNatural.beforeFinish())return;if(Boolean.getBoolean("vgg.compat.coverageDeckChecks")&&!CoverageDeckChecks.tick())return;if(!MembershipChecks.beforeFinish())return;if(!CompatibilityChecks.beforeFinish())return;diagnostics("diagnostics.json");if(Boolean.getBoolean("vgg.compat.quartermasterClaimChecks"))result.put("quartermasterClaimChecks",QuartermasterClaimChecks.run());if(Boolean.getBoolean("vgg.compat.layoutVariantChecks"))result.put("layoutVariantChecks",LayoutVariantChecks.run());if(Boolean.getBoolean("vgg.compat.cotRouteChecks"))result.put("cotRouteChecks",CotRouteChecks.run(level,start.getChunkPos().getWorldPosition()));if(Boolean.getBoolean("vgg.compat.bridgeChecks"))result.put("bridgeChecks",BridgeChecks.run());if(Boolean.getBoolean("vgg.compat.runtimeChecks"))result.put("syntheticLifecycle",Map.of("kind","synthetic_after_natural_observation","checks",RuntimeChecks.run(level,start)));result.put("registry",((net.minecraft.world.level.saveddata.SavedData)VillageRegistrySavedData.get(level)).save(new CompoundTag()).toString());result.put("population",((net.minecraft.world.level.saveddata.SavedData)VillagePopulationSavedData.get(level)).save(new CompoundTag()).toString());RegionalBarracksNatural.finish();Observations.finish();result.put("status","observed");write("result.json",result);done=true;event.getServer().halt(false);}
    @SubscribeEvent public void damage(net.minecraftforge.event.entity.living.LivingDamageEvent event){
        if(event.getEntity()==Behavior.threat){Behavior.damage.add(Map.of("tick",ticks,"amount",event.getAmount(),"attacker",event.getSource().getEntity()==null?"none":event.getSource().getEntity().getUUID().toString(),"type",event.getSource().getMsgId(),"direct",event.getSource().getDirectEntity()==null?"none":event.getSource().getDirectEntity().getClass().getName()));}
    }
    static void census(String label){
        var villagers=level.getEntitiesOfClass(Villager.class,area);var guards=level.getEntitiesOfClass(Guard.class,area);
        List<Object> rows=new ArrayList<>();int guns=0;Map<String,Integer> entities=new TreeMap<>();for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,area))entities.merge(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),1,Integer::sum);result.put(label+"EntityTypes",entities);
        for(var guard:guards){
            var origin=origins.computeIfAbsent(guard.getUUID(),id->guard.position());var stack=guard.getMainHandItem();String gun="none";
            if(stack.getItem() instanceof IGun item){guns++;gun=item.getGunId(stack).toString();}
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",guard.getUUID());row.put("pos",List.of(guard.getX(),guard.getY(),guard.getZ()));row.put("gun",gun);row.put("travel",guard.position().distanceTo(origin));row.put("village",String.valueOf(VillageRegistrySavedData.get(level).guardVillage(guard.getUUID())));row.put("navigationDone",guard.getNavigation().isDone());row.put("water",guard.isInWater());row.put("onGround",guard.onGround());row.put("health",guard.getHealth());row.put("gunDecision",guard.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::isInstance).map(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::cast).map(g->g.captureDiagnostics().decision().toString()).findFirst().orElse("no_gun_goal"));row.put("footBlock",level.getBlockState(guard.blockPosition()).toString());row.put("floorBlock",level.getBlockState(guard.blockPosition().below()).toString());
            // Post-observation path construction samples; these paths are not executed.
            if(label.equals("settled")&&rows.size()<4){
                List<Object> paths=new ArrayList<>();
                for(var villager:villagers.stream().sorted(Comparator.comparingDouble(guard::distanceToSqr)).limit(8).toList()){
                    var path=guard.getNavigation().createPath(villager.blockPosition(),0);
                    paths.add(Map.of("distance",guard.distanceTo(villager),"reachable",path!=null&&path.canReach(),"nodes",path==null?0:path.getNodeCount(),"target",List.of(villager.getX(),villager.getY(),villager.getZ())));
                }
                row.put("nativePaths",paths);
            }
            row.put("movementAndServices",Observations.guard(guard));
            rows.add(row);
        }
        result.put(label,Map.of("villagers",villagers.size(),"guards",guards.size(),"guns",guns,"guardRows",rows));write(label+".json",result.get(label));
    }
}
