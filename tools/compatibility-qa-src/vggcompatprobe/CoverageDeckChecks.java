package vggcompatprobe;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.live.*;
import com.gamergaming.villagerguardgunners.worldgen.village.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Controlled deck in a packaged client, using the guard's ordinary automatic goals. */
final class CoverageDeckChecks {
    private static int started = -1;
    private static BlockPos origin;
    private static Guard guard;
    private static VillageRegistrySavedData original;
    private static VillageIdentity identity;
    private static boolean enabled, duties, left;
    private static long visits;

    static boolean tick() {
        var level = Probe.level;
        if (started == -1) {
            var start = Probe.start.getChunkPos().getWorldPosition();
            origin = new BlockPos((start.getX() >> 5) * 32 + 4, 200, (start.getZ() >> 5) * 32 + 4);
            // The observer loads its small fixture. Production coverage must use cached chunks.
            for (int x = 0; x < 24; x++) for (int z = 0; z < 8; z++) {
                level.setBlock(origin.offset(x, -3, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                level.setBlock(origin.offset(x, -2, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                var floor = Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
                if (x == 8) floor = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF,
                        net.minecraft.world.level.block.state.properties.Half.TOP).setValue(BlockStateProperties.WATERLOGGED, true);
                if (x == 16) floor = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP).setValue(BlockStateProperties.WATERLOGGED, true);
                if (x == 8 || x == 16) {
                    // Like the natural road: the wet block is below the
                    // neighboring campfire, so its water cannot flood that top.
                    level.setBlock(origin.offset(x, -2, z), floor, 3);
                    level.setBlock(origin.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
                } else level.setBlock(origin.offset(x, -1, z), floor, 3);
                for (int y = 0; y < 5; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            surfaceChecks();
            selectionChecks();
            original = VillageRegistrySavedData.get(level);
            var data = new VillageRegistrySavedData(level.dimension().location());
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, data);
            VillageCoverageRuntime.clear(level);
            enabled = ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.get();
            duties = ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get();
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(true);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(false);
            identity = new VillageIdentity(level.dimension().location(), ResourceLocation.parse("minecraft:village_plains"),
                    net.minecraft.world.level.ChunkPos.asLong(origin));
            var bounds = new VillageFootprint.Bounds(origin.getX(), 198, origin.getZ(), origin.getX()+23, 204, origin.getZ()+7);
            data.observe(identity, VillageFootprint.of(List.of(new VillageFootprint.Piece(origin, "NONE", ResourceLocation.parse("test:deck"), bounds))));
            var homes = List.of(origin.offset(4, 0, 5), origin.offset(20, 0, 5));
            Map<Long, List<BlockPos>> sections = new HashMap<>();
            for (var home : homes) {
                level.setBlock(home.below(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                level.setBlock(home.north().below(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                var bed = Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH);
                level.setBlock(home.north(), bed.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
                level.setBlock(home, bed.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
                sections.computeIfAbsent(SectionPos.asLong(home), k -> new ArrayList<>()).add(home);
            }
            sections.forEach(data::observeHomes);
            guard = GuardEntityType.GUARD.get().create(level);
            guard.setPersistenceRequired();
            guard.moveTo(origin.getX()+22.5, origin.getY()-9.0/16.0, origin.getZ()+3.5);
            guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            level.addFreshEntity(guard);
            data.affiliateGuard(guard.getUUID(), identity.id());
            VillageCoverageRuntime.synchronizeGuard(guard);
            started = Probe.ticks;
            Probe.progress("checking automatic guard coverage on unlit campfire decks");
        }
        var snapshot = VillageCoverageRuntime.snapshot(level, identity.id());
        if (snapshot.plan() != null) visits = snapshot.plan().areas().stream().filter(a -> a.lastSuccessfulVisit() >= 0).count();
        left |= guard.getX() <= origin.getX()+6;
        require(guard.isAlive() && !guard.isInWater() && guard.getY() > origin.getY()-1, "Guard fell off the deck or died");
        // Both homes occupy one 32-block coverage cell. Verify a real journey
        // across it and a credited arrival, not a nonexistent second area.
        if (left && visits >= 1) {
            Probe.result.put("coverageDeckChecks", Map.of("passed",true,"ordinaryGoals",true,"visitedAreas",visits,
                    "crossedDeck",true,"minimumHorizontalTravel",16.5,"elapsedTicks",Probe.ticks-started,"surfaceChecks",14,"candidateSelectionChecks",true));
            guard.discard();
            VillageCoverageRuntime.clear(level);
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, original);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(enabled);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(duties);
            return true;
        }
        if (Probe.ticks-started > 1200) throw new AssertionError("Deck coverage timed out: left="+left+", pos="+guard.position()+", visits="+visits+", snapshot="+snapshot);
        return false;
    }

    private static void surfaceChecks() {
        var level = Probe.level;
        var p = origin.offset(10,0,3);
        var unlit = Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false);
        require(CoverageTerrain.safeStandingPosition(level,p), "Unlit deck was rejected");
        require(CoverageTerrain.safeStep(level,p,p.east()), "Level campfire deck disconnected");
        level.setBlock(p.below(),unlit.setValue(BlockStateProperties.LIT,true),3);
        require(!CoverageTerrain.safeStandingPosition(level,p), "Lit campfire accepted");
        level.setBlock(p.below(),unlit.setValue(BlockStateProperties.WATERLOGGED,true),3);
        require(!CoverageTerrain.safeStandingPosition(level,p), "Flooded deck accepted");
        var topSlab = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.TOP).setValue(BlockStateProperties.WATERLOGGED,true);
        level.setBlock(p.below(),topSlab,3);
        require(CoverageTerrain.safeStandingPosition(level,p), "Dry waterlogged top slab rejected");
        level.setBlock(p.below(),topSlab.setValue(BlockStateProperties.SLAB_TYPE,net.minecraft.world.level.block.state.properties.SlabType.BOTTOM),3);
        require(!CoverageTerrain.safeStandingPosition(level,p), "Submerged bottom slab accepted");
        var topTrapdoor = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF,
                net.minecraft.world.level.block.state.properties.Half.TOP).setValue(BlockStateProperties.WATERLOGGED,true);
        level.setBlock(p.below(),topTrapdoor,3);
        require(CoverageTerrain.safeStandingPosition(level,p), "Dry waterlogged top trapdoor rejected");
        level.setBlock(p.below(),topTrapdoor.setValue(BlockStateProperties.OPEN,true),3);
        require(!CoverageTerrain.safeStandingPosition(level,p), "Open waterlogged trapdoor accepted");
        level.setBlock(p.below(),Blocks.WATER.defaultBlockState(),3);
        require(!CoverageTerrain.safeStandingPosition(level,p), "Open water accepted");
        level.setBlock(p.below(),Blocks.OAK_FENCE.defaultBlockState(),3);
        require(!CoverageTerrain.safeStandingPosition(level,p), "Fence accepted as deck");
        level.setBlock(p.below(),unlit,3);
        level.setBlock(p.east().below(),Blocks.OAK_PLANKS.defaultBlockState(),3);
        require(CoverageTerrain.safeStep(level,p,p.east()), "Deck-to-plank step rejected");
        level.setBlock(p.east(),Blocks.OAK_PLANKS.defaultBlockState(),3);
        require(!CoverageTerrain.safeStep(level,p,p.east().above()), "1.5625-block climb accepted");
        require(!CoverageTerrain.safeStep(level,p.east().above(),p), "Oversized drop accepted");
        level.setBlock(p.east(),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(p.east().below(),unlit,3);
        level.setBlock(p.below(),Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false),3);
        require(CoverageTerrain.safeStandingPosition(level,p), "Unlit soul campfire rejected");
        level.setBlock(p.below(),unlit,3);
    }

    private static void selectionChecks() {
        var processors = net.minecraft.core.Holder.direct(new StructureProcessorList(List.of()));
        var no = VillageBuildingPoolElement.create(ResourceLocation.parse("test:no"), VillageBuildingType.BARRACKS,
                ResourceLocation.parse("test:template"), processors, StructureTemplatePool.Projection.RIGID, 0.0);
        var yes = VillageBuildingPoolElement.create(ResourceLocation.parse("test:yes"), VillageBuildingType.BARRACKS,
                ResourceLocation.parse("test:template"), processors, StructureTemplatePool.Projection.RIGID, 1.0);
        var empty = net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement.INSTANCE;
        var fallback = net.minecraft.core.Holder.direct(new StructureTemplatePool(null, List.<com.mojang.datafixers.util.Pair<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement,Integer>>of()));
        var pool = new StructureTemplatePool(fallback, List.of(com.mojang.datafixers.util.Pair.of(no,3),
                com.mojang.datafixers.util.Pair.of(yes,2),com.mojang.datafixers.util.Pair.of(empty,1)));
        var admitted = pool.getShuffledTemplates(RandomSource.create(52));
        require(admitted.size()==3 && admitted.stream().noneMatch(e -> e==no) && admitted.contains(empty), "Production selection mixin failed");
        var encoded = VillageBuildingPoolElement.CODEC.encodeStart(JsonOps.INSTANCE,no).result().orElseThrow();
        var decoded = VillageBuildingPoolElement.CODEC.parse(JsonOps.INSTANCE,encoded).result().orElseThrow();
        require(decoded.selectionChance()==0.0,"Selection chance lost in codec round trip");
        encoded.getAsJsonObject().remove("selection_chance");
        require(VillageBuildingPoolElement.CODEC.parse(JsonOps.INSTANCE,encoded).result().orElseThrow().selectionChance()==1.0,"Legacy element lost default chance");
    }

    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
