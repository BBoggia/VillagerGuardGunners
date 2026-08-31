package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec.DecodeResult;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec.DecodeStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Server-authoritative membership operations for deliberate ledger use.
 *
 * <p>Every lookup is caused by one player interaction. Reassignment warnings
 * are transient, bounded by the number of interacting players, and discarded
 * when the server stops; no tick cleanup or entity scan is required.</p>
 */
public final class SquadLedgerService {
    public static final int REASSIGNMENT_CONFIRMATION_TICKS = 100;

    private static final double MAXIMUM_INTERACTION_DISTANCE_SQUARED = 8.0D * 8.0D;
    private static final int MAXIMUM_ID_GENERATION_ATTEMPTS = 16;
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Map<MinecraftServer, Map<UUID, PendingReassignment>>
            PENDING_REASSIGNMENTS = new WeakHashMap<>();

    private SquadLedgerService() {
    }

    public static OperationResult interact(
            Player player,
            Guard guard,
            InteractionHand hand) {
        return interact(player, guard, hand, UUID::randomUUID);
    }

    static OperationResult interact(
            Player player,
            Guard guard,
            InteractionHand hand,
            Supplier<UUID> squadIdSource) {
        OperationResult validation = validate(player, guard, hand);
        if (validation != null) {
            return validation;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return OperationResult.of(OperationStatus.INVALID_REQUEST);
        }
        long gameTime = guard.level().getGameTime();
        ItemStack ledger = player.getItemInHand(hand);
        DecodeResult decoded = SquadLedgerBindingCodec.readFrom(ledger.getTag());
        if (decoded.status() == DecodeStatus.CORRUPT) {
            clearPending(server, player.getUUID());
            return OperationResult.of(OperationStatus.CORRUPT_LEDGER);
        }
        if (decoded.status() == DecodeStatus.UNSUPPORTED_VERSION) {
            clearPending(server, player.getUUID());
            return OperationResult.unsupported(decoded.storedVersion());
        }

        SquadRegistrySavedData registry = SquadRegistrySavedData.get(server);
        SquadMembership currentMembership = GuardSquadAccess.getMembership(guard);
        if (decoded.status() == DecodeStatus.BLANK) {
            clearPending(server, player.getUUID());
            return bindBlankLedger(
                    player,
                    guard,
                    ledger,
                    currentMembership,
                    registry,
                    gameTime,
                    squadIdSource);
        }

        SquadLedgerBinding binding = decoded.binding();
        if (binding == null) {
            clearPending(server, player.getUUID());
            return OperationResult.of(OperationStatus.CORRUPT_LEDGER);
        }

        boolean reassignment = currentMembership != null
                && !currentMembership.squadId().equals(binding.squadId());
        if (reassignment
                && !consumeOrArmConfirmation(
                        server,
                        player.getUUID(),
                        guard.getUUID(),
                        currentMembership.squadId(),
                        binding.squadId(),
                        gameTime)) {
            String destinationName = registry.find(binding.squadId())
                    .map(SquadMetadata::displayName)
                    .orElse(binding.cachedDisplayName());
            return OperationResult.named(
                    OperationStatus.CONFIRM_REASSIGNMENT,
                    destinationName);
        }
        clearPending(server, player.getUUID());

        MetadataResolution resolved = resolveMetadata(
                registry,
                binding.squadId(),
                binding.cachedDisplayName(),
                requestedRename(ledger, binding),
                player.getUUID(),
                gameTime);
        if (resolved.metadata() == null) {
            return OperationResult.of(resolved.failureStatus());
        }

        SquadMetadata metadata = resolved.metadata();
        synchronizeLedger(ledger, metadata, player);
        if (currentMembership == null) {
            GuardSquadAccess.setMembership(
                    guard,
                    SquadMembership.assigned(metadata.squadId()));
            return OperationResult.named(OperationStatus.ASSIGNED, metadata.displayName());
        }
        if (currentMembership.squadId().equals(metadata.squadId())) {
            GuardSquadAccess.setMembership(guard, null);
            return OperationResult.named(OperationStatus.REMOVED, metadata.displayName());
        }

        GuardSquadAccess.setMembership(
                guard,
                SquadMembership.assigned(metadata.squadId()));
        return OperationResult.named(OperationStatus.REASSIGNED, metadata.displayName());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (PENDING_REASSIGNMENTS) {
            PENDING_REASSIGNMENTS.remove(event.getServer());
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().level().getServer();
        if (server != null) {
            clearPending(server, event.getEntity().getUUID());
        }
    }

    private static OperationResult bindBlankLedger(
            Player player,
            Guard guard,
            ItemStack ledger,
            @Nullable SquadMembership currentMembership,
            SquadRegistrySavedData registry,
            long gameTime,
            Supplier<UUID> squadIdSource) {
        String requestedName = ledger.hasCustomHoverName()
                ? ledger.getHoverName().getString()
                : null;
        if (currentMembership != null) {
            boolean registryEntryExisted = registry.find(currentMembership.squadId()).isPresent();
            MetadataResolution resolved = resolveMetadata(
                    registry,
                    currentMembership.squadId(),
                    null,
                    requestedName,
                    player.getUUID(),
                    gameTime);
            if (resolved.metadata() == null) {
                return OperationResult.of(resolved.failureStatus());
            }
            synchronizeLedger(ledger, resolved.metadata(), player);
            return OperationResult.named(
                    registryEntryExisted
                            ? OperationStatus.BOUND_TO_EXISTING
                            : OperationStatus.RECOVERED_AND_BOUND,
                    resolved.metadata().displayName());
        }

        CreationResult creation = createSquad(
                registry,
                requestedName,
                player.getUUID(),
                gameTime,
                squadIdSource);
        if (creation.metadata() == null) {
            return OperationResult.of(creation.failureStatus());
        }

        SquadMetadata metadata = creation.metadata();
        if (!GuardSquadAccess.setMembership(
                guard,
                SquadMembership.assigned(metadata.squadId()))) {
            registry.remove(metadata.squadId());
            return OperationResult.of(OperationStatus.INVALID_GUARD);
        }
        synchronizeLedger(ledger, metadata, player);
        return OperationResult.named(
                OperationStatus.CREATED_AND_ASSIGNED,
                metadata.displayName());
    }

    private static CreationResult createSquad(
            SquadRegistrySavedData registry,
            @Nullable String requestedName,
            UUID createdBy,
            long gameTime,
            Supplier<UUID> squadIdSource) {
        if (registry.size() >= SquadRegistrySavedData.MAX_SQUADS) {
            return CreationResult.failed(OperationStatus.REGISTRY_FULL);
        }
        for (int attempt = 0; attempt < MAXIMUM_ID_GENERATION_ATTEMPTS; attempt++) {
            UUID squadId;
            try {
                squadId = squadIdSource.get();
            } catch (RuntimeException exception) {
                continue;
            }
            if (squadId == null
                    || NIL_UUID.equals(squadId)
                    || registry.find(squadId).isPresent()) {
                continue;
            }
            Optional<SquadMetadata> registered = registry.register(
                    squadId,
                    requestedName,
                    createdBy,
                    gameTime);
            if (registered.isPresent()) {
                return CreationResult.created(registered.get());
            }
            if (registry.size() >= SquadRegistrySavedData.MAX_SQUADS) {
                return CreationResult.failed(OperationStatus.REGISTRY_FULL);
            }
        }
        return CreationResult.failed(OperationStatus.IDENTITY_GENERATION_FAILED);
    }

    private static MetadataResolution resolveMetadata(
            SquadRegistrySavedData registry,
            UUID squadId,
            @Nullable String cachedDisplayName,
            @Nullable String requestedRename,
            UUID createdBy,
            long gameTime) {
        Optional<SquadMetadata> existing = registry.find(squadId);
        if (existing.isPresent()) {
            SquadMetadata metadata = requestedRename == null
                    ? existing.get()
                    : registry.rename(squadId, requestedRename, gameTime).orElseThrow();
            return MetadataResolution.resolved(metadata);
        }
        if (registry.size() >= SquadRegistrySavedData.MAX_SQUADS) {
            return MetadataResolution.failed(OperationStatus.REGISTRY_FULL);
        }

        String initialName = requestedRename != null ? requestedRename : cachedDisplayName;
        return registry.register(squadId, initialName, createdBy, gameTime)
                .map(MetadataResolution::resolved)
                .orElseGet(() -> MetadataResolution.failed(OperationStatus.REGISTRY_FULL));
    }

    @Nullable
    private static String requestedRename(
            ItemStack ledger,
            SquadLedgerBinding binding) {
        if (!ledger.hasCustomHoverName()) {
            return null;
        }
        String hoverName = ledger.getHoverName().getString();
        return hoverName.equals(binding.cachedDisplayName()) ? null : hoverName;
    }

    private static void synchronizeLedger(
            ItemStack ledger,
            SquadMetadata metadata,
            Player player) {
        SquadLedgerBindingCodec.writeTo(
                ledger.getOrCreateTag(),
                SquadLedgerBinding.bound(metadata.squadId(), metadata.displayName()));
        if (ledger.hasCustomHoverName()) {
            ledger.setHoverName(Component.literal(metadata.displayName()));
        }
        player.getInventory().setChanged();
    }

    private static boolean consumeOrArmConfirmation(
            MinecraftServer server,
            UUID playerId,
            UUID guardId,
            UUID sourceSquadId,
            UUID destinationSquadId,
            long gameTime) {
        synchronized (PENDING_REASSIGNMENTS) {
            Map<UUID, PendingReassignment> serverPending =
                    PENDING_REASSIGNMENTS.computeIfAbsent(
                            server,
                            ignored -> new HashMap<>());
            serverPending.values().removeIf(pending -> pending.isExpired(gameTime));
            PendingReassignment pending = serverPending.get(playerId);
            if (pending != null
                    && pending.matches(
                            guardId,
                            sourceSquadId,
                            destinationSquadId,
                            gameTime)) {
                serverPending.remove(playerId);
                if (serverPending.isEmpty()) {
                    PENDING_REASSIGNMENTS.remove(server);
                }
                return true;
            }
            serverPending.put(
                    playerId,
                    new PendingReassignment(
                            guardId,
                            sourceSquadId,
                            destinationSquadId,
                            gameTime + REASSIGNMENT_CONFIRMATION_TICKS));
            return false;
        }
    }

    private static void clearPending(MinecraftServer server, UUID playerId) {
        synchronized (PENDING_REASSIGNMENTS) {
            Map<UUID, PendingReassignment> serverPending =
                    PENDING_REASSIGNMENTS.get(server);
            if (serverPending == null) {
                return;
            }
            serverPending.remove(playerId);
            if (serverPending.isEmpty()) {
                PENDING_REASSIGNMENTS.remove(server);
            }
        }
    }

    @Nullable
    private static OperationResult validate(
            Player player,
            Guard guard,
            InteractionHand hand) {
        if (player == null
                || guard == null
                || hand == null
                || player.level().isClientSide
                || guard.level().isClientSide
                || player.level() != guard.level()
                || player.level().getServer() == null
                || !player.isAlive()
                || player.isRemoved()) {
            return OperationResult.of(OperationStatus.INVALID_REQUEST);
        }
        if (!guard.isAlive()
                || guard.isRemoved()
                || !(guard instanceof GuardSquadAccess)) {
            return OperationResult.of(OperationStatus.INVALID_GUARD);
        }
        if (!player.isSecondaryUseActive()) {
            return OperationResult.of(OperationStatus.NOT_SNEAKING);
        }
        if (player.distanceToSqr(guard) >= MAXIMUM_INTERACTION_DISTANCE_SQUARED) {
            return OperationResult.of(OperationStatus.OUT_OF_RANGE);
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty()
                || heldStack.getCount() != 1
                || !heldStack.is(ModItems.SQUAD_COMMAND_LEDGER.get())) {
            return OperationResult.of(OperationStatus.NOT_LEDGER);
        }
        if (player.isSpectator()
                || guard.getTarget() == player
                || !GuardOrderPermissions.mayManage(player, guard)) {
            return OperationResult.of(OperationStatus.UNAUTHORIZED);
        }
        return null;
    }

    public enum OperationStatus {
        CREATED_AND_ASSIGNED,
        BOUND_TO_EXISTING,
        RECOVERED_AND_BOUND,
        ASSIGNED,
        REMOVED,
        CONFIRM_REASSIGNMENT,
        REASSIGNED,
        CORRUPT_LEDGER,
        UNSUPPORTED_VERSION,
        REGISTRY_FULL,
        IDENTITY_GENERATION_FAILED,
        UNAUTHORIZED,
        OUT_OF_RANGE,
        NOT_SNEAKING,
        NOT_LEDGER,
        INVALID_GUARD,
        INVALID_REQUEST
    }

    public record OperationResult(
            OperationStatus status,
            String squadName,
            int storedVersion) {
        public OperationResult {
            Objects.requireNonNull(status, "status");
            squadName = Objects.requireNonNull(squadName, "squadName");
            if (status != OperationStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported ledger may report a stored version");
            }
        }

        public boolean changedGuard() {
            return status == OperationStatus.CREATED_AND_ASSIGNED
                    || status == OperationStatus.ASSIGNED
                    || status == OperationStatus.REMOVED
                    || status == OperationStatus.REASSIGNED;
        }

        public boolean changedLedger() {
            return status == OperationStatus.CREATED_AND_ASSIGNED
                    || status == OperationStatus.BOUND_TO_EXISTING
                    || status == OperationStatus.RECOVERED_AND_BOUND
                    || status == OperationStatus.ASSIGNED
                    || status == OperationStatus.REMOVED
                    || status == OperationStatus.REASSIGNED;
        }

        public boolean completed() {
            return changedGuard() || changedLedger();
        }

        private static OperationResult of(OperationStatus status) {
            return new OperationResult(status, "", -1);
        }

        private static OperationResult named(OperationStatus status, String squadName) {
            return new OperationResult(status, squadName, -1);
        }

        private static OperationResult unsupported(int storedVersion) {
            return new OperationResult(
                    OperationStatus.UNSUPPORTED_VERSION,
                    "",
                    storedVersion);
        }
    }

    private record PendingReassignment(
            UUID guardId,
            UUID sourceSquadId,
            UUID destinationSquadId,
            long expiresAtGameTime) {
        private boolean matches(
                UUID requestedGuardId,
                UUID requestedSourceSquadId,
                UUID requestedDestinationSquadId,
                long gameTime) {
            return gameTime <= expiresAtGameTime
                    && guardId.equals(requestedGuardId)
                    && sourceSquadId.equals(requestedSourceSquadId)
                    && destinationSquadId.equals(requestedDestinationSquadId);
        }

        private boolean isExpired(long gameTime) {
            return gameTime > expiresAtGameTime;
        }
    }

    private record MetadataResolution(
            @Nullable SquadMetadata metadata,
            @Nullable OperationStatus failureStatus) {
        private static MetadataResolution resolved(SquadMetadata metadata) {
            return new MetadataResolution(metadata, null);
        }

        private static MetadataResolution failed(OperationStatus status) {
            return new MetadataResolution(null, status);
        }
    }

    private record CreationResult(
            @Nullable SquadMetadata metadata,
            @Nullable OperationStatus failureStatus) {
        private static CreationResult created(SquadMetadata metadata) {
            return new CreationResult(metadata, null);
        }

        private static CreationResult failed(OperationStatus status) {
            return new CreationResult(null, status);
        }
    }
}
