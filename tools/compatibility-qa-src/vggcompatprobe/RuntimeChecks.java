package vggcompatprobe;

import com.gamergaming.villagerguardgunners.village.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraftforge.event.level.LevelEvent;

/** Explicit synthetic lifecycle checks, separate from natural-world observations. */
final class RuntimeChecks {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Map<String,Object> run(ServerLevel level,StructureStart natural){
        var left=level.getChunkSource().getChunkNow(natural.getChunkPos().x,natural.getChunkPos().z);
        var right=level.getChunkSource().getChunkNow(natural.getChunkPos().x+1,natural.getChunkPos().z);
        require(left!=null&&right!=null,"Check chunks are not already loaded");
        var startsA=new HashMap<>(left.getAllStarts());var startsB=new HashMap<>(right.getAllStarts());
        var refsA=new HashMap<>(left.getAllReferences());var refsB=new HashMap<>(right.getAllReferences());
        var original=VillageRegistrySavedData.get(level);
        var structure=natural.getStructure();
        var id=new VillageIdentity(level.dimension().location(),level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(structure),left.getPos().toLong());
        int maximum=0;
        var expansionPiece=new VillageFootprint.Piece(new BlockPos(left.getPos().getMinBlockX(),64,left.getPos().getMinBlockZ()),
                "NONE",net.minecraft.resources.ResourceLocation.parse("test:expanded_source"),
                new VillageFootprint.Bounds(left.getPos().getMinBlockX(),64,left.getPos().getMinBlockZ(),left.getPos().getMinBlockX()+1,66,left.getPos().getMinBlockZ()+1));
        var expansionSource=new com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan.Source(
                com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan.sourceId(expansionPiece),expansionPiece,false,false,64,10);
        var expansion=com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan.create(id,List.of(expansionSource),
                new com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlan.Settings(64,64,3,2,2),0);
        var expansionNbt=com.gamergaming.villagerguardgunners.village.population.VillagePopulationCodec.encode(expansion);
        require(expansionNbt.getInt("Version")==3 && expansion.allowance()==64
                && expansion.equals(com.gamergaming.villagerguardgunners.village.population.VillagePopulationCodec.decode(expansionNbt,level.dimension().location())),
                "Expanded plan did not round-trip in production");
        var membership = new VillageRegistrySavedData(level.dimension().location());
        var sky = VillageFootprint.of(List.of(new VillageFootprint.Piece(new BlockPos(0, 160, 0), "NONE",
                net.minecraft.resources.ResourceLocation.parse("test:sky_house"),
                new VillageFootprint.Bounds(0, 158, 0, 31, 180, 31))));
        membership.observe(id, sky);
        BlockPos ground = new BlockPos(5, 64, 5), deck = new BlockPos(5, 160, 5);
        require(membership.membershipAt(ground).unambiguous() == null, "Ground member affiliated upstairs");
        require(id.id().equals(membership.membershipAt(deck).unambiguous()), "Deck membership rejected");
        UUID member = UUID.randomUUID();
        membership.affiliateGuard(member, id.id());
        ((net.minecraft.world.level.saveddata.SavedData)membership).setDirty(false);
        require(membership.membershipOutsideObservedRegion(id.id(), ground), "Suspect membership not reported");
        require(id.id().equals(membership.guardVillage(member)) && !((net.minecraft.world.level.saveddata.SavedData)membership).isDirty(), "Diagnostic changed durable affiliation");
        membership.observeHomes(net.minecraft.core.SectionPos.asLong(ground), List.of(ground));
        require(membership.get(id.id()).homes().isEmpty(), "Ground home associated upstairs");
        try {
            var data=new VillageRegistrySavedData(level.dimension().location());level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME,data);
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
            var synthetic=new StructureStart(structure,left.getPos(),0,new PiecesContainer(Collections.nCopies(128,natural.getPieces().get(0))));
            left.setAllStarts(Map.of(structure,synthetic));right.setAllStarts(Map.of());left.setAllReferences(Map.of());
            right.setAllReferences(Map.of(structure,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(new long[]{left.getPos().toLong()})));
            VillageDiscoveryRuntime.trackChunk(level,left.getPos().toLong());VillageDiscoveryRuntime.trackChunk(level,right.getPos().toLong());
            for(int tick=1;tick<=9;tick++){
                VillageDiscoveryRuntime.tick(level);maximum=Math.max(maximum,VillageDiscoveryRuntime.diagnostics(level).lastWorkUnits());
                require(maximum<=32,"Shared scan exceeded 32 units");
                if(tick<9)require(data.size()==0,"Partial layout published");
            }
            require(data.get(id.id())!=null&&data.get(id.id()).footprint().pieces().size()==128,"Shared layout failed to finish by ninth tick");
            // A replacement start invalidates an unfinished cursor rather than publishing stale geometry.
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
            data=new VillageRegistrySavedData(level.dimension().location());level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME,data);
            VillageDiscoveryRuntime.trackChunk(level,right.getPos().toLong());VillageDiscoveryRuntime.tick(level);
            left.setStartForStructure(structure,new StructureStart(structure,left.getPos(),0,new PiecesContainer(List.of(natural.getPieces().get(0)))));
            for(int i=0;i<40;i++)VillageDiscoveryRuntime.tick(level);
            require(data.size()==0,"Replaced native start published stale layout");
            require(VillageDiscoveryRuntime.chunkStatus(level,right.getPos().toLong()).unresolvedStarts()==1,"Changed layout was not deferred");
            VillageDiscoveryRuntime.onTagsUpdated(null);
            require(!VillageDiscoveryRuntime.chunkStatus(level,right.getPos().toLong()).complete(),"Tag reload retained stale completed status");
            for(int i=0;i<40;i++)VillageDiscoveryRuntime.tick(level);
            require(data.get(id.id())!=null&&data.get(id.id()).footprint().pieces().size()==1,"Reload failed to observe replacement");
            // Explicit unload invalidates every reference to the shared scan, even before native cache removal.
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
            data=new VillageRegistrySavedData(level.dimension().location());level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME,data);
            left.setStartForStructure(structure,synthetic);
            VillageDiscoveryRuntime.trackChunk(level,right.getPos().toLong());VillageDiscoveryRuntime.tick(level);
            VillageDiscoveryRuntime.onChunkUnload(new net.minecraftforge.event.level.ChunkEvent.Unload(left));
            for(int i=0;i<40;i++)VillageDiscoveryRuntime.tick(level);
            require(data.size()==0 && VillageDiscoveryRuntime.chunkStatus(level,right.getPos().toLong()).unresolvedStarts()==1,"Unloaded origin retained an active shared cursor");
            // Missing references must remain unresolved without requesting absent chunks.
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
            long absent=ChunkPos.asLong(40000,40000);
            require(level.getChunkSource().getChunkNow(40000,40000)==null,"Missing-chunk fixture was already loaded");
            right.setAllReferences(Map.of(structure,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(new long[]{absent})));
            VillageDiscoveryRuntime.trackChunk(level,right.getPos().toLong());
            for(int i=0;i<40;i++)VillageDiscoveryRuntime.tick(level);
            require(VillageDiscoveryRuntime.chunkStatus(level,right.getPos().toLong()).unresolvedStarts()==1,"Missing reference treated as absence");
            require(level.getChunkSource().getChunkNow(40000,40000)==null,"Discovery loaded absent chunk");
            return Map.of("shared128PiecesTicks",9,"maximumWorkUnits",maximum,"partialPublicationBlocked",true,"replacementDeferred",true,"tagReloadRecovered",true,"missingReferenceDeferred",true,"originUnloadInvalidated",true,"staleReloadStatusBlocked",true,"elevationMembershipChecks",true,"expandedPlanRoundTrip",true);
        } finally {
            left.setAllStarts(startsA);right.setAllStarts(startsB);left.setAllReferences(refsA);right.setAllReferences(refsB);
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME,original);
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
        }
    }
}
