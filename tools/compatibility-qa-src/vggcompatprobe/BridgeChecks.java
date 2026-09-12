package vggcompatprobe;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.gamergaming.villagerguardgunners.village.coverage.live.CoverageTerrain;

/** Restored artificial footing fixture inside an already loaded production-test chunk. */
final class BridgeChecks {
    static Map<String,Object> run() {
        var level=Probe.level;
        var chunk=Probe.start.getChunkPos();
        BlockPos a=new BlockPos(chunk.getMinBlockX()+15,Math.min(280,level.getMaxBuildHeight()-5),chunk.getMinBlockZ()+6);
        BlockPos b=a.east();Map<BlockPos,BlockState> original=new HashMap<>();
        for(BlockPos p:BlockPos.betweenClosed(a.offset(-1,-1,-1),b.offset(1,3,1))) {
            require(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null,"Fixture chunk missing");
            original.put(p.immutable(),level.getBlockState(p));
        }
        try {
            original.keySet().forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),2));
            level.setBlock(a.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(b.below(),Blocks.STONE.defaultBlockState(),2);
            require(CoverageTerrain.safeStep(level,a,b),"Supported bridge step rejected");
            require(!CoverageTerrain.safeStep(level,a,b.east()),"Gap leap admitted");
            require(!CoverageTerrain.safeStep(level,a,b.south()),"Diagonal shortcut admitted");
            level.setBlock(b.below(),Blocks.AIR.defaultBlockState(),2);
            require(!CoverageTerrain.safeStep(level,a,b),"Destroyed support admitted");
            level.setBlock(b.below(),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(b,Blocks.STONE.defaultBlockState(),2);
            require(CoverageTerrain.safeStep(level,a,b.above()),"Supported step up rejected");
            level.setBlock(a.above(2),Blocks.STONE.defaultBlockState(),2);
            require(!CoverageTerrain.safeStep(level,a,b.above()),"Step headroom ignored");
            BlockPos absent=new BlockPos(640000,80,640000);
            require(level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,"Absent fixture already loaded");
            require(!CoverageTerrain.safeStep(level,absent,absent.east()),"Missing chunk admitted");
            require(level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,"Step observation loaded a chunk");
            return Map.of("kind","artificial_restored_footing_in_production", "support",true,"gap",true,
                    "diagonal",true,"destroyedSupport",true,"headroom",true,"missingChunkRemainedAbsent",true);
        } finally { original.forEach((p,s)->level.setBlock(p,s,2)); }
    }
    static void require(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
