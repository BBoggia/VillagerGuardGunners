package com.gamergaming.villagerguardgunners.village.population;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Small immutable files prevent a saved-data rollback from reopening a spawn slot. */
public final class VillagePopulationJournal {
    private final Path directory;
    public VillagePopulationJournal(Path directory) { this.directory = directory; }
    public static VillagePopulationJournal get(ServerLevel level) {
        return new VillagePopulationJournal(DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data/villagerguardgunners_population_journal"));
    }
    public boolean plan(VillagePopulationPlan plan) throws IOException {
        Path file = path("village", plan.identity().id());
        CompoundTag encoded = VillagePopulationCodec.encode(plan);
        if (Files.exists(file)) {
            // Compare decoded plans so a version-one journal keeps its exact allowance.
            try { return VillagePopulationCodec.decode(read(file), plan.identity().dimension()).equals(plan); }
            catch (IllegalArgumentException exception) { throw new IOException("Invalid frozen population plan", exception); }
        }
        writeNew(file, encoded);
        return true;
    }
    public boolean hasPlan(UUID village) { return !Files.notExists(path("village", village)); }
    public CompoundTag readPlan(UUID village) throws IOException { return read(path("village", village)); }
    public void bind(UUID building, VillagePopulationPlacement.Binding binding) throws IOException {
        if (!plan(binding.plan())) throw new IOException("Conflicting frozen village plan");
        CompoundTag tag = new CompoundTag(); tag.putUUID("Village", binding.plan().identity().id()); tag.putUUID("Source", binding.source());
        Path file = path("building", building);
        if (Files.exists(file)) {
            if (!read(file).equals(tag)) throw new IOException("Conflicting building population binding");
        } else writeNew(file, tag);
    }
    public CompoundTag binding(UUID building) throws IOException {
        Path file = path("building", building);
        return Files.notExists(file) ? null : read(file);
    }
    public boolean attempted(UUID slot) { return Files.exists(path("slot", slot)); }
    public boolean reserve(UUID slot, UUID guard) throws IOException {
        Files.createDirectories(directory);
        Path file = path("slot", slot);
        if (Files.exists(file)) return false;
        // Force the intent before entity insertion. An interrupted attempt stays closed
        // until its exact guard is observed; absence in loaded chunks is never proof of death.
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = ByteBuffer.wrap(guard.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        return true;
    }
    public boolean matches(UUID slot, UUID guard) throws IOException {
        Path file = path("slot", slot);
        return Files.size(file) == 36 && Files.readString(file).equals(guard.toString());
    }
    public void canceled(UUID slot, UUID guard) throws IOException {
        if (!matches(slot, guard)) throw new IOException("Mismatched spawn intent");
        Files.delete(path("slot", slot));
    }
    private Path path(String prefix, UUID id) { return directory.resolve(prefix + "_" + id + ".dat"); }
    private static CompoundTag read(Path file) throws IOException {
        if (Files.size(file) > 262_144) throw new IOException("Population journal file exceeds limit");
        try (var input = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            return NbtIo.read(input, new NbtAccounter(524_288));
        }
    }
    private void writeNew(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(directory);
        // CREATE_NEW seals the identity; a partial/corrupt file blocks issuance instead
        // of being overwritten with a fresh allowance after a crash.
        try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            NbtIo.writeCompressed(tag, output);
        }
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }
}
