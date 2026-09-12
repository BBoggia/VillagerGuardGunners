package vggcompatprobe;

import com.gamergaming.villagerguardgunners.village.compat.*;
import com.gamergaming.villagerguardgunners.village.population.VillagePopulationSavedData;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.LevelResource;

/** Controlled post-observation checks in the production process; never included in the release. */
final class CompatibilityChecks {
    private static int stage;
    private static CompletableFuture<Void> reload;
    private static SettlementCatalog original;
    private static CompoundTag population;
    private static Path profile, changedTemplate;
    private static ResourceLocation changedTemplateId;
    private static final Map<String,Object> checks = new LinkedHashMap<>();
    static boolean beforeFinish() {
        try {
            if (stage == 0) {
                original = SettlementDefinitions.snapshot();
                var id = ResourceLocation.parse((String) Probe.result.get("structure"));
                var decision = SettlementDefinitions.lookup(id);
                var context = StructurePieceSerializationContext.fromLevel(Probe.level);
                var saved = Probe.start.createTag(context, Probe.start.getChunkPos());
                var restored = StructureStart.loadStaticStart(context, saved, Probe.level.getSeed());
                var before = Probe.start.getPieces().stream().map(p -> PieceReaders.read(p, decision.reader())).toList();
                List<PieceReader.Result> after = restored == null ? List.of() : restored.getPieces().stream().map(p -> PieceReaders.read(p, decision.reader())).toList();
                // Native loading recalculates boxes from templates; generation can expand them.
                // Stable source identity uses placement metadata, while restored geometry must
                // be stable on the next native save/load cycle.
                var restoredAgain = restored == null ? null : StructureStart.loadStaticStart(context,
                        restored.createTag(context, restored.getChunkPos()), Probe.level.getSeed());
                List<PieceReader.Result> again = restoredAgain == null ? List.of() : restoredAgain.getPieces().stream()
                        .map(p -> PieceReaders.read(p, decision.reader())).toList();
                boolean identityMatches = before.size() == after.size();
                List<Object> changedBoxes = new ArrayList<>();
                for (int i=0; i<Math.min(before.size(), after.size()); i++) {
                    var a=before.get(i); var b=after.get(i);
                    identityMatches &= a.geometry().position().equals(b.geometry().position())
                            && a.geometry().rotation().equals(b.geometry().rotation())
                            && a.geometry().template().equals(b.geometry().template())
                            && a.reader().equals(b.reader()) && a.supported()==b.supported();
                    if (!a.geometry().bounds().equals(b.geometry().bounds())) changedBoxes.add(Map.of(
                            "piece",i,"template",a.geometry().template().toString(),
                            "generatedBounds",a.geometry().bounds(),"restoredBounds",b.geometry().bounds()));
                }
                checks.put("nativeSerializationMatches", identityMatches && after.equals(again));
                checks.put("nativeBoundsRecalculated", changedBoxes);
                checks.put("supportedPieces", before.stream().filter(PieceReader.Result::supported).count());
                checks.put("integratedPieces", before.stream().filter(r -> r.supported() && r.reader().equals(SettlementProfile.INTEGRATED)).count());
                checks.put("initial", diagnostics());
                if (!Boolean.getBoolean("vgg.compat.definitionReloadChecks")) { stage=3; return complete(); }
                population = ((net.minecraft.world.level.saveddata.SavedData) VillagePopulationSavedData.get(Probe.level)).save(new CompoundTag());
                profile=Probe.level.getServer().getWorldPath(LevelResource.DATAPACK_DIR)
                        .resolve("vgg-compat-contribution/data/vgg_compat_fixture/village_compatibility/reload.json");
                if (!Files.isRegularFile(profile.getParent().getParent().getParent().getParent().resolve("pack.mcmeta")))
                    throw new IllegalStateException("Reload checks require the independent contribution fixture pack");
                var pack = profile.getParent().getParent().getParent().getParent();
                var marker = pack.resolve("mutate-template-on-rejection.txt");
                if (Files.isRegularFile(marker)) {
                    changedTemplateId = ResourceLocation.parse(Files.readString(marker).trim());
                    var resourceId = ResourceLocation.parse(changedTemplateId.getNamespace() + ":structures/" + changedTemplateId.getPath() + ".nbt");
                    byte[] bytes;
                    try (var input = Probe.level.getServer().getResourceManager().getResourceOrThrow(resourceId).open()) { bytes = input.readNBytes(2_097_153); }
                    if (bytes.length < 10 || bytes.length > 2_097_152 || (bytes[0]&255)!=31 || (bytes[1]&255)!=139 || (bytes[3]&2)!=0)
                        throw new IllegalStateException("Expected bounded gzip template without header CRC");
                    bytes[4] ^= 1; // Change only the ignored gzip timestamp, retaining the NBT payload.
                    changedTemplate = pack.resolve("data/" + changedTemplateId.getNamespace() + "/structures/" + changedTemplateId.getPath() + ".nbt");
                    Files.createDirectories(changedTemplate.getParent()); Files.write(changedTemplate, bytes);
                }
                Files.createDirectories(profile.getParent());
                Files.writeString(profile,"{\"format\":999,\"intent\":\"friendly\",\"structures\":[\"minecraft:village_plains\"]}");
                reload=Probe.level.getServer().reloadResources(Probe.level.getServer().getPackRepository().getSelectedIds());
                stage=1; Probe.progress("testing rejected compatibility reload"); return false;
            }
            if(stage==1 || stage==2) {
                if(!reload.isDone()) return false;
                reload.join();
                if(stage==1) {
                    checks.put("rejectedReloadRetainsSnapshot", original==SettlementDefinitions.snapshot());
                    if (changedTemplateId != null) {
                        var structure = (String) Probe.result.get("structure");
                        var previous = original.get(structure); var current = SettlementDefinitions.snapshot().get(structure);
                        var meaning = current.meaning(changedTemplateId.toString());
                        checks.put("rejectedReloadRetainsProfileChoices", previous.profiles().equals(current.profiles())
                                && previous.intent()==current.intent() && previous.reader().equals(current.reader()));
                        checks.put("rejectedReloadRevokesChangedHints", meaning.kind()==SettlementProfile.Kind.UNSUPPORTED
                                && meaning.beds().isEmpty() && meaning.entrances().isEmpty()
                                && SettlementDefinitions.snapshot().generation()>original.generation());
                        Files.delete(changedTemplate);
                    }
                    checks.put("rejectedReloadReportsError", !SettlementDefinitions.errors().isEmpty());
                    checks.put("afterRejectedReload", diagnostics());
                    Files.writeString(profile,"{\"format\":1,\"priority\":50,\"intent\":\"abandoned\",\"structures\":[\"vgg_compat_fixture:contributed_village\"]}");
                    reload=Probe.level.getServer().reloadResources(Probe.level.getServer().getPackRepository().getSelectedIds());
                    stage=2; Probe.progress("testing accepted compatibility reload"); return false;
                }
                checks.put("acceptedReloadAdvancesGeneration", SettlementDefinitions.snapshot().generation()>original.generation());
                if (changedTemplateId != null) checks.put("restoredTemplateVerified", SettlementDefinitions.snapshot()
                        .get((String) Probe.result.get("structure")).meaning(changedTemplateId.toString()).supported());
                checks.put("acceptedReloadClearsErrors", SettlementDefinitions.errors().isEmpty());
                checks.put("explicitExclusionApplied", !SettlementDefinitions.lookup(ResourceLocation.parse("vgg_compat_fixture:contributed_village")).friendly());
                var current=((net.minecraft.world.level.saveddata.SavedData) VillagePopulationSavedData.get(Probe.level)).save(new CompoundTag());
                checks.put("populationPlansUnchanged", Objects.equals(population.get("Plans"), current.get("Plans")));
                checks.put("populationCreditsUnchanged", Objects.equals(population.get("Credits"), current.get("Credits")));
                checks.put("afterAcceptedReload", diagnostics()); stage=3;
            }
            return complete();
        } catch (Exception failure) { throw new IllegalStateException("Compatibility checks failed",failure); }
    }
    private static Map<String,Object> diagnostics() {
        var registry=Probe.level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var snapshot=SettlementDefinitions.snapshot();
        List<String> villages=new ArrayList<>(), dependent=new ArrayList<>();
        registry.getTagOrEmpty(StructureTags.VILLAGE).forEach(h->villages.add(h.unwrapKey().orElseThrow().location().toString()));
        registry.getTagOrEmpty(TagKey.create(Registries.STRUCTURE,ResourceLocation.parse("vgg_compat_fixture:dependent")))
                .forEach(h->dependent.add(h.unwrapKey().orElseThrow().location().toString()));
        var supported=registry.keySet().stream().filter(id->id.getNamespace().equals("ctov")&&snapshot.get(id.toString()).friendly()).count();
        return Map.of("generation",snapshot.generation(),"profiles",snapshot.profiles(),"inactive",snapshot.inactive(),
                "errors",SettlementDefinitions.errors(),"tagCorrection",IntegratedVillageTagCorrection.status(),
                "villageMembers",villages,"dependentMembers",dependent,"supportedCtovTypes",supported,"supportedVillageTypes",villages.stream().filter(id -> snapshot.get(id).friendly()).count());
    }
    private static boolean complete() { Probe.result.put("compatibilityChecks",checks); return true; }
    private CompatibilityChecks() { }
}
