package vggcompatprobe;

import java.util.*;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.live.CoverageTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import tallestegg.guardvillagers.entities.Guard;

/** Controlled real entities in the naturally generated Sky village; no direct affiliation calls. */
final class MembershipChecks {
    static Guard deck, ground;
    static BlockPos deckPos, groundPos;
    static UUID village;
    static int started, stage;
    static CompoundTag savedGuard;
    static boolean beforeFinish() {
        if (!Boolean.getBoolean("vgg.compat.membershipChecks")) return true;
        var level=Probe.level;
        var data=VillageRegistrySavedData.get(level);
        if(stage==0) {
            var identity=new VillageIdentity(level.dimension().location(),
                    net.minecraft.resources.ResourceLocation.parse((String)Probe.result.get("structure")),Probe.start.getChunkPos().toLong());
            village=identity.id();var record=data.get(village);
            if(record==null)throw new AssertionError("Natural village was not discovered");
            search:for(var piece:record.footprint().pieces()) {
                var b=piece.bounds();
                for(int x=b.minX();x<=b.maxX();x+=2)for(int z=b.minZ();z<=b.maxZ();z+=2) {
                    for(int y=b.maxY()+1;y>=b.minY();y--) {
                        var p=new BlockPos(x,y,z);
                        if(!village.equals(data.membershipAt(p).unambiguous()) || !CoverageTerrain.safeStandingPosition(level,p))continue;
                        for(int gy=y-30;gy>level.getMinBuildHeight()+1;gy--) {
                            var g=new BlockPos(x,gy,z);
                            if(CoverageTerrain.safeStandingPosition(level,g) && data.membershipAt(g).complete()
                                    && data.membershipAt(g).unambiguous()==null) {
                                deckPos=p;groundPos=g;break search;
                            }
                        }
                    }
                }
            }
            if(deckPos==null)throw new AssertionError("No qualified natural deck/ground pair");
            deck=create(deckPos);ground=create(groundPos);started=Probe.ticks;stage=1;return false;
        }
        if(Probe.ticks-started<400)return false;
        if(stage==1) {
            require(village.equals(data.guardVillage(deck.getUUID())),"Deck guard never affiliated through discovery");
            require(data.guardVillage(ground.getUUID())==null,"Ground guard affiliated upstairs");
            orders(deck,deckPos);orders(ground,groundPos);
            savedGuard=((Entity)deck).saveWithoutId(new CompoundTag());
            var persisted=VillageRegistrySavedData.load(((SavedData)data).save(new CompoundTag()),level.dimension().location());
            require(village.equals(persisted.guardVillage(deck.getUUID())),"Registry save lost affiliation");
            deck.discard();started=Probe.ticks;stage=2;return false;
        }
        if(stage==2) {
            deck=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
            ((Entity)deck).load(savedGuard);
            deck.moveTo(groundPos.getX()+.5,groundPos.getY(),groundPos.getZ()+.5);
            require(level.addFreshEntity(deck),"Saved guard rejoin failed");
            started=Probe.ticks;stage=3;return false;
        }
        orders(deck,deckPos);
        require(village.equals(data.guardVillage(deck.getUUID())),"Travel/rejoin changed durable affiliation");
        require(data.guardVillage(ground.getUUID())==null,"Ground guard acquired upstairs affiliation later");
        Probe.result.put("membershipLifecycle",Map.of("kind","supplied_guards_on_natural_sky_and_ground",
                "deck",List.of(deckPos.getX(),deckPos.getY(),deckPos.getZ()),
                "ground",List.of(groundPos.getX(),groundPos.getY(),groundPos.getZ()),
                "deckAffiliated",true,"groundUnaffiliated",true,"ordersPreserved",true,"savedGuardRejoined",true));
        return true;
    }
    static Guard create(BlockPos p) {
        var g=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(Probe.level);
        g.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);g.setPersistenceRequired();g.setNoAi(true);
        g.setPatrolPos(p);g.setPatrolling(true);
        require(Probe.level.addFreshEntity(g),"Fixture guard insertion failed");return g;
    }
    static void orders(Guard g,BlockPos p) { require(g.isNoAi()&&g.isPatrolling()&&p.equals(g.getPatrolPos()),"Discovery rewrote fixture orders"); }
    static void require(boolean ok,String text) { if(!ok)throw new AssertionError(text); }
}
