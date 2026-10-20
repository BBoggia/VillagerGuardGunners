package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.gamergaming.villagerguardgunners.worldgen.village.*;
import com.gamergaming.villagerguardgunners.worldgen.structure.VillageWatchtowerPiece;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/** Packaged-resource and worldgen acceptance in an ordinary installed server. */
final class WorldgenAcceptance {
    final String scenario = System.getProperty("vgg.qa.worldgenScenario", "builtin");
    final Path expectedPath = Path.of("worldgen-" + scenario + "-qa-expected.nbt");
    ServerLevel level;
    CompoundTag expected;
    boolean done, restoring;
    int ticks, index;
    long generation;
    CompletableFuture<Void> reload;
    final List<Placement> placements = new ArrayList<>();
    final List<VillageBuildingIdentity> placed = new ArrayList<>();
    final Map<ResourceLocation, Integer> poolSizes = new HashMap<>();
    final List<CompoundTag> towerPieces = new ArrayList<>();
    final List<BlockPos> towerArmories = new ArrayList<>();
    record Placement(VillageBuildingPoolEntry entry, Rotation rotation) {}

    WorldgenAcceptance() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::configure);
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    static void pass(String name) { LogUtils.getLogger().info("VGG_WORLDGEN_QA_CASE_PASS case={}", name); }
    void configure(ServerAboutToStartEvent event) {
        ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.accept(true);
        ModConfigs.GUARD_GUNNERS.villageBuildings.stockArmories.accept(true);
        ModConfigs.GUARD_GUNNERS.villageBuildings.placeArmoryBlocks.accept(true);
    }
    void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            level.setDayTime(3000);
            if (Files.exists(expectedPath)) {
                expected = NbtIo.readCompressed(expectedPath, NbtAccounter.create(4_194_304));
                restoring = true;
                for(Tag value:expected.getList("TowerArmories",Tag.TAG_LONG)) loadArea(BlockPos.of(((LongTag)value).getAsLong()));
                for (Tag value : expected.getList("Buildings", Tag.TAG_COMPOUND)) {
                    var pos = BlockPos.of(((CompoundTag)value).getLong("Anchor"));
                    loadArea(pos);
                }
                return;
            }
            var definitions = VillageBuildingDefinitions.snapshot();
            require(definitions.size() == (scenario.equals("builtin") ? 25 : 175), "Missing built-in or companion definitions: " + definitions.size());
            require(VillageBuildingDefinitions.reloadSnapshot().lastErrors().isEmpty(), "Definition errors");
            var report = VillageBuildingPoolIntegration.installationSnapshot().report();
            require(report.enabled() && !report.hasWarnings(), "Pool injection rejected resources: " + report);
            int expectedActive = switch (scenario) { case "builtin" -> 25; case "universal" -> 75; default -> 175; };
            require(report.injected() == expectedActive, "Optional provider activation mismatch: " + report);
            var registry = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
            for (var entry : definitions.values()) {
                var template = level.getStructureManager().get(entry.template()).orElseThrow(() -> new AssertionError("Missing " + entry.template()));
                for (Rotation rotation : Rotation.values()) {
                    var markers = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings().setRotation(rotation), Blocks.STRUCTURE_BLOCK);
                    require(markers.size() == 1 && markers.getFirst().nbt().getString("metadata").equals("village_anchor:" + entry.buildingType().serializedName()), "Missing/rotated marker: " + entry.entryId());
                }
                if (VillageBuildingPoolIntegration.installationSnapshot().requestedEntry(entry.entryId()) == null) continue;
                var pool = registry.get(entry.targetPool());
                var raw = ((StructureTemplatePoolAccessor)pool).villagerGuardGunners$getRawTemplates();
                var matches = raw.stream().filter(p -> p.getFirst() instanceof VillageBuildingPoolElement e && e.entryId().equals(entry.entryId())).toList();
                require(matches.size() == 1 && matches.getFirst().getSecond() == entry.weight(), "Pool weights/duplicates: " + entry.entryId());
                var element = matches.getFirst().getFirst();
                var encoded = StructurePoolElement.CODEC.encodeStart(ops, element).getOrThrow();
                require(StructurePoolElement.CODEC.parse(ops, encoded).getOrThrow() instanceof VillageBuildingPoolElement e && e.entryId().equals(entry.entryId()), "Pool element codec");
                poolSizes.put(entry.targetPool(), pool.size());
            }
            require(level.getStructureManager().get(VillageWatchtowerPiece.TEMPLATE_LOCATION).isPresent(), "Standalone Watchtower missing");
            require(level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("villagerguardgunners:watchtower")) != null, "Standalone structure missing");
            require(com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions.errors().isEmpty(), "Village profile reload failed");
            var settlements = scenario.equals("providers") ? List.of("minecraft:village_plains", "ctov:large/village_plains", "mmv:village_jungle", "joshie:village_ocean", "skyvillages:skyvillage") : List.of("minecraft:village_plains");
            for (String structure : settlements) {
                var decision = com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions.lookup(id(structure));
                require(decision.friendly() && decision.meanings().values().stream().anyMatch(m -> m.supported() && !m.beds().isEmpty()),"No verified residential template evidence: "+structure);
                require(decision.meanings().values().stream().allMatch(m -> m.supported()),"Stale template fingerprints: "+structure);
            }
            pass("verified_village_discovery_profiles");
            pass("templates_markers_codecs_and_optional_providers_" + scenario);
            VillageBuildingPoolIntegration.onServerAboutToStart(new ServerAboutToStartEvent(level.getServer()));
            require(VillageBuildingPoolIntegration.installationSnapshot().report().alreadyPresent() == expectedActive, "Repeated pool installation added duplicates");
            pass("pool_injection_idempotence");
            for (String name : List.of("village_armory", "watchtower_armory")) {
                var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("villagerguardgunners:chests/" + name)));
                var items = table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST));
                require(items.size() == 7 && items.stream().allMatch(s -> s.getCount() == 32 && s.getItem() instanceof IAmmo a && TimelessAPI.getCommonAmmoIndex(a.getAmmoId(s)).isPresent()), "Invalid ammo loot " + name);
            }
            pass("component_ammo_loot");
            standaloneTowers();
            // Each family is placed in all four rotations through its production pool element.
            for (var entry : definitions.values()) if (entry.entryId().getNamespace().equals("villagerguardgunners") && entry.entryId().getPath().endsWith("/plains"))
                for (Rotation rotation : Rotation.values()) placements.add(new Placement(entry, rotation));
            if (!scenario.equals("builtin")) {
                // One of each family per regional/provider pool, including both layout sizes across the list.
                Set<String> keys = new HashSet<>();
                int rotation = 0;
                for (var entry : definitions.values().stream().sorted(Comparator.comparing(e -> e.entryId().toString())).toList()) {
                    if (entry.entryId().getNamespace().equals("villagerguardgunners")) continue;
                    if (keys.add(entry.targetPool() + "/" + entry.buildingType())) placements.add(new Placement(entry, Rotation.values()[rotation++ % 4]));
                }
            }
        } catch (Throwable e) { fail(e); }
    }
    void standaloneTowers() {
        int index=0;
        for(Rotation rotation:Rotation.values()) {
            var origin=new BlockPos(1056,200,index++*96);
            loadArea(origin);
            var piece=new VillageWatchtowerPiece(level.getStructureManager(),origin,rotation,42L);
            piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(42),BoundingBox.infinite(),new ChunkPos(origin),origin);
            var encoded=piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
            var restored=new VillageWatchtowerPiece(level.getStructureManager(),encoded);
            var roundtrip=restored.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
            require(encoded.getCompound("VggPost").equals(roundtrip.getCompound("VggPost")),"Watchtower marker claims did not serialize");
            towerPieces.add(encoded);
            var markers=level.getStructureManager().getOrCreate(VillageWatchtowerPiece.TEMPLATE_LOCATION).filterBlocks(origin,new StructurePlaceSettings().setRotation(rotation).setRotationPivot(new BlockPos(4,0,4)),Blocks.STRUCTURE_BLOCK);
            for(var marker:markers) if(marker.nbt().getString("metadata").startsWith("armory:")) {
                require(level.getBlockEntity(marker.pos()) instanceof ArmoryBlockEntity,"Standalone Armory marker failed");towerArmories.add(marker.pos());
            }
        }
        require(towerArmories.size()==4,"Standalone tower Armories missing");
        pass("standalone_watchtower_placement_and_piece_serialization");
    }
    void loadArea(BlockPos center) {
        for (int x = (center.getX() - 24) >> 4; x <= (center.getX() + 24) >> 4; x++)
            for (int z = (center.getZ() - 24) >> 4; z <= (center.getZ() + 24) >> 4; z++) {
                level.getChunk(x,z); level.setChunkForced(x,z,true);
            }
    }
    void place(Placement placement) {
        var entry = placement.entry();
        BlockPos origin = new BlockPos((index % 10) * 96, 200, (index / 10) * 96);
        loadArea(origin);
        var processors = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, entry.processors()));
        var element = VillageBuildingPoolElement.create(entry.entryId(), entry.buildingType(), entry.template(), processors, entry.projection(), 1.0, entry.terrainSafety());
        var template = level.getStructureManager().getOrCreate(entry.template());
        var bounds = element.getBoundingBox(level.getStructureManager(), origin, placement.rotation());
        // Prepared support makes this a placement/activation check, independent of natural terrain selection.
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
            level.setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),2);
        require(element.place(level.getStructureManager(),level,level.structureManager(),level.getChunkSource().getGenerator(),origin,BlockPos.ZERO,placement.rotation(),BoundingBox.infinite(),RandomSource.create(42),LiquidSettings.APPLY_WATERLOGGING,false), "Placement failed: " + entry.entryId());
        var marker = template.filterBlocks(origin,new StructurePlaceSettings().setRotation(placement.rotation()),Blocks.STRUCTURE_BLOCK).getFirst().pos();
        var record = VillageBuildingSavedData.get(level).getAt(level.dimension().location(),marker);
        require(record != null && record.identity().entryId().equals(entry.entryId()), "Placement did not register anchor: " + entry.entryId());
        require(level.getBlockState(marker).isAir(), "Marker was not removed");
        placed.add(record.identity());
    }
    void tick(ServerTickEvent.Post event) {
        if (done || level == null) return;
        try {
            ticks++;
            if (restoring) { if (ticks == 40) restore(); return; }
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join();
                require(VillageBuildingDefinitions.generation() > generation, "Reload did not publish definitions");
                var pools=level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
                poolSizes.forEach((id,size) -> require(pools.get(id).size()==size,"Reload duplicated pool entries"));
                pass("resource_reload_preserves_live_pools");
                save(); return;
            }
            if (ticks % 3 != 0) return;
            if (index < placements.size()) { place(placements.get(index)); index++; return; }
            if (ticks < placements.size()*3+60) return;
            for (var identity : placed) {
                var record = VillageBuildingSavedData.get(level).get(identity.buildingId());
                require(record.pendingActivation().isEmpty(), "Activation still pending: " + identity.entryId());
                require(record.hasClaimed(identity.buildingType()==VillageBuildingType.ARMORY ? VillageBuildingAction.INITIAL_STOCK : VillageBuildingAction.GARRISON), "Activation not claimed: " + identity.entryId());
            }
            for (var identity : placed) if (identity.buildingType()==VillageBuildingType.ARMORY) {
                require(level.getBlockEntity(identity.anchorPosition()) instanceof ArmoryBlockEntity,"Armory block missing");
                var armory=(ArmoryBlockEntity)level.getBlockEntity(identity.anchorPosition());
                int total=0;
                for(int slot=0;slot<armory.getInventory().getSlots();slot++) total+=armory.getInventory().getStackInSlot(slot).getCount();
                require(total==224,"Generated Armory stock not populated: "+total);
                armory.getInventory().extractItem(0,1,false);
                require(VillageBuildingArmoryLifecycle.activate(level,identity,Rotation.NONE).status()==VillageBuildingArmoryLifecycle.Status.ALREADY_CLAIMED,"Armory restocked on replay");
            }
            pass("generated_armory_stock_and_no_refill");
            pass("native_placement_and_activation_" + placements.size());
            nativeVillages();
            generation=VillageBuildingDefinitions.generation();
            reload=level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
        } catch (Throwable e) { fail(e); }
    }
    void nativeVillages() throws Exception {
        var structures = scenario.equals("providers") ? List.of("minecraft:village_plains", "ctov:large/village_plains", "mmv:village_jungle", "joshie:village_ocean", "skyvillages:skyvillage") : List.of("minecraft:village_plains");
        int sample=0;
        for(String structure:structures) {
            int before=VillageBuildingSavedData.get(level).size();
            int attempts=0;
            while(VillageBuildingSavedData.get(level).size()==before && attempts++<4) {
                int x=2048+(sample++)*512;
                var generator=level.getChunkSource().getGenerator();
                var type=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id(structure));
                var start=type.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),new ChunkPos(x>>4,0),0,level,biome -> true);
                require(start.isValid(),"Native village generation failed: "+structure);
                var box=start.getBoundingBox();
                var chunks=ChunkPos.rangeClosed(new ChunkPos(box.minX()>>4,box.minZ()>>4),new ChunkPos(box.maxX()>>4,box.maxZ()>>4)).toList();
                require(chunks.size()<=625,"Native sample exceeds QA chunk budget");
                for(var chunk:chunks) level.getChunk(chunk.x,chunk.z);
                // Same bounded per-chunk placement path as /place structure, after
                // loading the selected start's exact bounds rather than a guessed radius.
                for(var chunk:chunks) start.placeInChunk(level,level.structureManager(),generator,RandomSource.create(42),new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxBuildHeight(),chunk.getMaxBlockZ()),chunk);
            }
            require(VillageBuildingSavedData.get(level).size()>before,"Native jigsaw starts did not include a defense building: "+structure);
            pass("native_jigsaw_"+structure+"_attempts_"+attempts);
        }
    }
    void save() throws Exception {
        expected=new CompoundTag(); ListTag buildings=new ListTag();
        for(var identity:placed) {
            CompoundTag tag=new CompoundTag(); tag.putUUID("Id",identity.buildingId());tag.putLong("Anchor",identity.anchorPosition().asLong());
            tag.putInt("Claims",VillageBuildingSavedData.get(level).get(identity.buildingId()).claimedActions());
            if(identity.buildingType()==VillageBuildingType.ARMORY) tag.putBoolean("Armory",true);
            buildings.add(tag);
        }
        expected.put("Buildings",buildings);
        ListTag towers=new ListTag(); towerPieces.forEach(towers::add); expected.put("TowerPieces",towers);
        ListTag armories=new ListTag();
        for(var pos:towerArmories) {
            var armory=(ArmoryBlockEntity)level.getBlockEntity(pos);int total=0;
            for(int slot=0;slot<armory.getInventory().getSlots();slot++) total+=armory.getInventory().getStackInSlot(slot).getCount();
            require(total==224,"Standalone tower stock missing");armories.add(LongTag.valueOf(pos.asLong()));
        }
        expected.put("TowerArmories",armories);
        ListTag guards=new ListTag();
        for(var entity:level.getAllEntities()) if(entity instanceof Guard guard && guard.getX()<1500) {
            guard.setNoAi(true); CompoundTag tag=new CompoundTag();tag.putUUID("Id",guard.getUUID());guards.add(tag);
        }
        require(!guards.isEmpty(),"Garrisons created no guards"); expected.put("Guards",guards);
        NbtIo.writeCompressed(expected,expectedPath);done=true;
        LogUtils.getLogger().info("VGG_WORLDGEN_QA_READY_TO_RESTART buildings={} guards={}",placed.size(),guards.size());
    }
    void restore() {
        for(Tag value:expected.getList("TowerPieces",Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)value;
            var piece=new VillageWatchtowerPiece(level.getStructureManager(),tag);
            require(piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level)).getCompound("VggPost").equals(tag.getCompound("VggPost")),"Standalone marker claims changed on restart");
        }
        for(Tag value:expected.getList("TowerArmories",Tag.TAG_LONG)) {
            var pos=BlockPos.of(((LongTag)value).getAsLong());require(level.getBlockEntity(pos) instanceof ArmoryBlockEntity,"Standalone Armory lost");
            var armory=(ArmoryBlockEntity)level.getBlockEntity(pos);int total=0;
            for(int slot=0;slot<armory.getInventory().getSlots();slot++) total+=armory.getInventory().getStackInSlot(slot).getCount();
            require(total==224,"Standalone stock changed on restart");
        }
        for(Tag value:expected.getList("Buildings",Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)value;var record=VillageBuildingSavedData.get(level).get(tag.getUUID("Id"));
            require(record!=null && record.claimedActions()==tag.getInt("Claims") && record.pendingActivation().isEmpty(),"Saved activation claims changed");
            if(tag.getBoolean("Armory")) {
                require(level.getBlockEntity(record.identity().anchorPosition()) instanceof ArmoryBlockEntity,"Saved Armory missing");
                var armory=(ArmoryBlockEntity)level.getBlockEntity(record.identity().anchorPosition());
                int total=0; for(int slot=0;slot<armory.getInventory().getSlots();slot++) total+=armory.getInventory().getStackInSlot(slot).getCount();
                require(total==223,"Consumed generated stock was refilled or lost on restart");
            }
        }
        for(Tag value:expected.getList("Guards",Tag.TAG_COMPOUND)) require(level.getEntity(((CompoundTag)value).getUUID("Id")) instanceof Guard,"Saved garrison lost");
        pass("restart_preserves_buildings_and_garrisons");done=true;LogUtils.getLogger().info("VGG_WORLDGEN_QA_RESTART_PASS");
    }
    void fail(Throwable e) { done=true;LogUtils.getLogger().error("VGG_WORLDGEN_QA_FAIL scenario="+scenario+" placement="+index,e); }
}
