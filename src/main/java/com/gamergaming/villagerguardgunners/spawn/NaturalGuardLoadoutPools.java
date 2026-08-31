package com.gamergaming.villagerguardgunners.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Atomically reloads immutable natural guard loadout pools from server datapacks.
 *
 * <p>Files live at {@code data/<namespace>/natural_guard_loadouts/<path>.json}
 * and resolve to pool ID {@code <namespace>:<path>}. TaCZ gun IDs are not
 * resolved during this reload because TaCZ's own gun definitions are reloadable;
 * live issuance performs that compatibility check after every reload has
 * completed.</p>
 */
public final class NaturalGuardLoadoutPools
        extends SimplePreparableReloadListener<NaturalGuardLoadoutPools.PreparedResources> {
    public static final String DIRECTORY = "natural_guard_loadouts";
    public static final int FORMAT_VERSION = NaturalGuardLoadoutFormat.SIDEARM_VERSION;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final NaturalGuardLoadoutPools INSTANCE = new NaturalGuardLoadoutPools();

    private volatile PublishedState publishedState = PublishedState.empty();

    NaturalGuardLoadoutPools() {
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event").addListener(INSTANCE);
    }

    /** Returns the currently published immutable pool, or {@code null} if unavailable. */
    public static @Nullable NaturalGuardLoadoutPool get(ResourceLocation poolId) {
        return INSTANCE.getLoaded(poolId);
    }

    /** Returns the current immutable snapshot for diagnostics and tests. */
    public static Map<ResourceLocation, NaturalGuardLoadoutPool> snapshot() {
        return INSTANCE.loadedSnapshot();
    }

    /** Returns the complete immutable result of the latest datapack publication. */
    public static NaturalGuardLoadoutReloadSnapshot reloadSnapshot() {
        return INSTANCE.currentReloadSnapshot();
    }

    /** Logs one actionable warning per unresolved pool entry per reload. */
    public static void reportUnavailableGun(NaturalGuardLoadoutCandidate candidate) {
        reportUnavailableWeapon(candidate.poolId(), candidate.gunId(), "primary");
    }

    /** Logs one actionable warning per unresolved format-3 sidearm per reload. */
    public static void reportUnavailableSidearm(NaturalGuardLoadoutCandidate candidate) {
        if (candidate.sidearmId() != null) {
            reportUnavailableWeapon(candidate.poolId(), candidate.sidearmId(), "sidearm");
        }
    }

    private static void reportUnavailableWeapon(
            ResourceLocation poolId,
            ResourceLocation gunId,
            String role) {
        UnavailableGun unavailable = new UnavailableGun(
                poolId,
                gunId,
                role);
        PublishedState state = INSTANCE.publishedState;
        if (state.unavailableGunWarnings().add(unavailable)) {
            LOGGER.warn(
                    "Natural guard loadout pool {} selected unavailable TaCZ {} gun {}; "
                            + "this guard's one-time loadout attempt was left unchanged",
                    unavailable.poolId(),
                    unavailable.role(),
                    unavailable.gunId());
        }
    }

    @Override
    protected PreparedResources prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Objects.requireNonNull(resourceManager, "resourceManager");
        FileToIdConverter converter = FileToIdConverter.json(DIRECTORY);
        Map<ResourceLocation, JsonElement> parsed = new LinkedHashMap<>();
        List<NaturalGuardLoadoutReloadSnapshot.RejectedPool> rejected = new ArrayList<>();

        converter.listMatchingResources(resourceManager).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation poolId = converter.fileToId(entry.getKey());
                    try (var reader = entry.getValue().openAsReader()) {
                        JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                        JsonElement previous = parsed.put(
                                poolId,
                                json == null ? JsonNull.INSTANCE : json);
                        if (previous != null) {
                            throw new IllegalStateException(
                                    "Duplicate natural guard loadout pool ID " + poolId);
                        }
                    } catch (IllegalArgumentException | IOException | JsonParseException exception) {
                        parsed.remove(poolId);
                        rejected.add(new NaturalGuardLoadoutReloadSnapshot.RejectedPool(
                                poolId,
                                safeMessage(exception)));
                    }
                });
        return new PreparedResources(parsed, rejected);
    }

    @Override
    protected void apply(
            PreparedResources prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Objects.requireNonNull(prepared, "prepared");
        LoadResult result = parseAll(prepared.parsed());
        List<NaturalGuardLoadoutReloadSnapshot.RejectedPool> rejectedPools =
                new ArrayList<>(prepared.rejected());
        rejectedPools.addAll(result.rejected());
        for (NaturalGuardLoadoutReloadSnapshot.RejectedPool rejected : rejectedPools) {
            LOGGER.error(
                    "Skipping invalid natural guard loadout pool {}: {}",
                    rejected.id(),
                    rejected.reason());
        }

        PublishedState previous = publishedState;
        long generation = nextGeneration(previous.snapshot().generation());
        NaturalGuardLoadoutReloadSnapshot snapshot =
                NaturalGuardLoadoutReloadSnapshot.create(
                        generation,
                        result.pools(),
                        rejectedPools);
        // Pools, rejection metadata, generation, and the warning-deduplication
        // scope become visible together through this single volatile write.
        publishedState = PublishedState.create(snapshot);
        LOGGER.info(
                "Loaded {} natural guard loadout pool(s); rejected {} invalid pool(s); generation {}",
                snapshot.pools().size(),
                snapshot.rejectedPools().size(),
                snapshot.generation());
    }

    @Nullable NaturalGuardLoadoutPool getLoaded(ResourceLocation poolId) {
        return publishedState.snapshot().get(poolId);
    }

    Map<ResourceLocation, NaturalGuardLoadoutPool> loadedSnapshot() {
        return currentReloadSnapshot().pools();
    }

    NaturalGuardLoadoutReloadSnapshot currentReloadSnapshot() {
        return publishedState.snapshot();
    }

    static LoadResult parseAll(Map<ResourceLocation, JsonElement> prepared) {
        Objects.requireNonNull(prepared, "prepared");
        Map<ResourceLocation, NaturalGuardLoadoutPool> loaded = new LinkedHashMap<>();
        List<NaturalGuardLoadoutReloadSnapshot.RejectedPool> rejected = new ArrayList<>();

        prepared.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = Objects.requireNonNull(entry.getKey(), "pool ID");
                    try {
                        loaded.put(id, parsePool(id, entry.getValue()));
                    } catch (JsonParseException | IllegalArgumentException exception) {
                        rejected.add(new NaturalGuardLoadoutReloadSnapshot.RejectedPool(
                                id,
                                safeMessage(exception)));
                    }
                });
        return new LoadResult(Map.copyOf(loaded), List.copyOf(rejected));
    }

    static NaturalGuardLoadoutPool parsePool(ResourceLocation id, JsonElement json) {
        return NaturalGuardLoadoutParser.parse(id, json);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        String safe = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        StringBuilder normalized = new StringBuilder(safe.length());
        safe.codePoints().forEach(codePoint -> normalized.appendCodePoint(
                Character.isISOControl(codePoint) ? ' ' : codePoint));
        safe = normalized.toString().trim();
        if (safe.isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        int maximumLength = 512;
        if (safe.length() <= maximumLength) {
            return safe;
        }
        int end = maximumLength - 3;
        if (Character.isHighSurrogate(safe.charAt(end - 1))) {
            end--;
        }
        return safe.substring(0, end) + "...";
    }

    private static long nextGeneration(long current) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
    }

    record LoadResult(
            Map<ResourceLocation, NaturalGuardLoadoutPool> pools,
            List<NaturalGuardLoadoutReloadSnapshot.RejectedPool> rejected) {
        LoadResult {
            pools = Map.copyOf(Objects.requireNonNull(pools, "pools"));
            rejected = List.copyOf(Objects.requireNonNull(rejected, "rejected"));
        }
    }

    record PreparedResources(
            Map<ResourceLocation, JsonElement> parsed,
            List<NaturalGuardLoadoutReloadSnapshot.RejectedPool> rejected) {
        PreparedResources {
            parsed = Map.copyOf(Objects.requireNonNull(parsed, "parsed"));
            rejected = List.copyOf(Objects.requireNonNull(rejected, "rejected"));
        }
    }

    private record PublishedState(
            NaturalGuardLoadoutReloadSnapshot snapshot,
            Set<UnavailableGun> unavailableGunWarnings) {
        private PublishedState {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            unavailableGunWarnings = Objects.requireNonNull(
                    unavailableGunWarnings,
                    "unavailableGunWarnings");
        }

        private static PublishedState empty() {
            return create(NaturalGuardLoadoutReloadSnapshot.empty());
        }

        private static PublishedState create(NaturalGuardLoadoutReloadSnapshot snapshot) {
            return new PublishedState(snapshot, ConcurrentHashMap.newKeySet());
        }
    }

    private record UnavailableGun(
            ResourceLocation poolId,
            ResourceLocation gunId,
            String role) {
    }
}
