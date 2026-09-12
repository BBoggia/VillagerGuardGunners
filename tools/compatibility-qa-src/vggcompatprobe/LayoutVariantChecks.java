package vggcompatprobe;

import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayoutCodec;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayouts;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitions;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Isolated native template/definition fixture, not a finished building or natural-placement trial. */
final class LayoutVariantChecks {
    static Map<String, Object> run() {
        var level = Probe.level;
        var manager = level.getStructureManager();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        List<String> names = List.of("barracks/plains/qa_compact", "barracks/plains/qa_courtyard",
                "barracks/taiga/qa_compact");
        List<BlockPos> sizes = List.of(new BlockPos(7,5,7), new BlockPos(9,6,8), new BlockPos(7,5,7));
        List<BlockState> floors = List.of(Blocks.BIRCH_PLANKS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        BlockPos origin = Probe.start.getChunkPos().getWorldPosition()
                .offset(8, level.getMaxBuildHeight() - 20, 8);
        BoundingBox bounds = new BoundingBox(origin.getX()-10, origin.getY()-1, origin.getZ()-10,
                origin.getX()+10, origin.getY()+8, origin.getZ()+10);
        Map<BlockPos, BlockState> restore = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            require(level.hasChunkAt(p), "Fixture area must already be loaded");
            require(level.getBlockEntity(p) == null && level.getBlockState(p).isAir(),
                    "Fixture area must be empty");
            restore.put(p.immutable(), level.getBlockState(p));
        }
        List<String> checked = new ArrayList<>();
        Set<UUID> sameAnchorIds = new HashSet<>();
        try {
            for (int index=0; index<names.size(); index++) {
                var id = ResourceLocation.fromNamespaceAndPath("villagerguardgunners", names.get(index));
                var entry = VillageBuildingDefinitions.get(id);
                require(entry != null && entry.buildingType() == VillageBuildingType.BARRACKS,
                        "Fixture definition not loaded: " + id);
                var raw = ((StructureTemplatePoolAccessor)(Object)java.util.Objects.requireNonNull(pools.get(entry.targetPool())))
                        .villagerGuardGunners$getRawTemplates();
                require(raw.stream().filter(pair -> pair.getFirst() instanceof VillageBuildingPoolElement element
                        && element.entryId().equals(id) && pair.getSecond() == entry.weight()).count() == 1,
                        "Variant must have exactly one native pool entry");
                var template = manager.get(entry.template()).orElseThrow();
                require(template.getSize().equals(sizes.get(index)), "Variant geometry was aliased");
                sameAnchorIds.add(VillageBuildingIdentity.anchored(level.getSeed(), level.dimension().location(),
                        id, entry.buildingType(), origin).buildingId());
                for (Rotation rotation : Rotation.values()) {
                    var settings = new StructurePlaceSettings().setRotation(rotation)
                            .setBoundingBox(bounds).setKnownShape(false);
                    var markers = template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK);
                    var connectors = template.filterBlocks(origin, settings, Blocks.JIGSAW);
                    require(markers.size()==1 && connectors.size()==1, "Invalid marker contract");
                    require(template.placeInWorld(level, origin, BlockPos.ZERO, settings, level.random, 2),
                            "Native template placement failed");
                    BlockPos anchor = markers.get(0).pos();
                    // Raw template placement leaves metadata blocks; finalize these two marker cells explicitly.
                    level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(connectors.get(0).pos(), Blocks.AIR.defaultBlockState(), 2);
                    require(level.getBlockState(anchor.below()).equals(floors.get(index)),
                            "Palette leaked between variants");
                    for (int member=0; member<4; member++)
                        require(GuardPostSpawner.isSafeSpawnCell(level, bounds,
                                GuardPostSpawner.spawnPosition(anchor, rotation, member)),
                                "Unsafe rotated initial cell");
                    var identity = VillageBuildingIdentity.anchored(level.getSeed(), level.dimension().location(),
                            id, entry.buildingType(), anchor);
                    var layout = VillageDefenseDutyLayouts.forBuilding(identity, rotation, entry.dutySlots().orElseThrow());
                    require(layout.slots().size()==4 && layout.equals(VillageDefenseDutyLayoutCodec.decode(
                            VillageDefenseDutyLayoutCodec.encode(layout))), "Duty layout persistence changed");
                    var sentry = layout.slots().stream().filter(slot -> slot.slotKey().equals("entry_sentry")).findFirst().orElseThrow();
                    BlockPos offset = index==1 ? new BlockPos(1,0,-3) : new BlockPos(0,0,-2);
                    require(sentry.position().equals(anchor.offset(offset.rotate(rotation)))
                            && sentry.facing()==rotation.rotate(Direction.NORTH), "Sentry transform mismatch");
                    for (var slot : layout.slots())
                        require(GuardPostSpawner.isSafeSpawnCell(level, bounds, slot.position()),
                                "Unsafe rotated duty cell");
                    checked.add(names.get(index) + ":" + rotation.name());
                    restore.forEach((p,state) -> level.setBlock(p,state,2));
                }
            }
            require(sameAnchorIds.size()==3, "Variant entry identities collided");
            return Map.of("kind","controlled_native_template_fixture", "variants",3,
                    "placements",checked, "distinctIdentities",true, "paletteIsolation",true,
                    "rotatedStandingCells",true, "dutyCodecRoundTrip",true, "poolEntries",true);
        } finally { restore.forEach((p,state) -> level.setBlock(p,state,2)); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
