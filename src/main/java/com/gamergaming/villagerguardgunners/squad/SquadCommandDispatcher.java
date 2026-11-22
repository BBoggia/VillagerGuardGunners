package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import tallestegg.guardvillagers.entities.Guard;

/** One-shot, bounded dispatch of temporary commands to loaded squad members. */
public final class SquadCommandDispatcher {
    public static final int ABSOLUTE_MAXIMUM_AFFECTED_GUARDS = 64;
    public static final int ABSOLUTE_MAXIMUM_QUERIED_GUARDS = 128;
    public static final double ABSOLUTE_MAXIMUM_DISPATCH_RADIUS = 128.0D;

    private static final double MAXIMUM_TARGET_INTERACTION_DISTANCE_SQUARED = 64.0D;

    private SquadCommandDispatcher() {
    }

    public static DispatchResult dispatch(
            Player player,
            ItemStack ledger,
            SquadCommandType type,
            @Nullable BlockPos position,
            @Nullable LivingEntity target) {
        DispatchResult validation = validate(player, ledger, type, position, target);
        if (validation != null) {
            return validation;
        }
        if (!type.isDispatchMode()) {
            return DispatchResult.failed(DispatchStatus.INVALID_REQUEST, type);
        }

        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();
        SquadLedgerBindingCodec.DecodeResult decoded =
                SquadLedgerBindingCodec.readFrom(ledger.getTag());
        if (decoded.status() == SquadLedgerBindingCodec.DecodeStatus.BLANK) {
            return DispatchResult.failed(DispatchStatus.BLANK_LEDGER, type);
        }
        if (decoded.status() == SquadLedgerBindingCodec.DecodeStatus.CORRUPT) {
            return DispatchResult.failed(DispatchStatus.CORRUPT_LEDGER, type);
        }
        if (decoded.status()
                == SquadLedgerBindingCodec.DecodeStatus.UNSUPPORTED_VERSION) {
            return DispatchResult.unsupported(type, decoded.storedVersion());
        }
        SquadLedgerBinding binding = decoded.binding();
        if (binding == null) {
            return DispatchResult.failed(DispatchStatus.CORRUPT_LEDGER, type);
        }

        SquadRegistrySavedData registry = SquadRegistrySavedData.get(server);
        if (!registry.enabled()) {
            return DispatchResult.failed(DispatchStatus.REGISTRY_DISABLED, type);
        }
        SquadMetadata metadata = registry.find(binding.squadId()).orElse(null);
        if (metadata == null) {
            return DispatchResult.failed(DispatchStatus.SQUAD_NOT_REGISTERED, type);
        }

        double dispatchRadius = Math.min(
                ABSOLUTE_MAXIMUM_DISPATCH_RADIUS,
                ModConfigs.GUARD_GUNNERS.squads.commandDispatchRadius.get());
        int maximumAffected = Math.min(
                ABSOLUTE_MAXIMUM_AFFECTED_GUARDS,
                ModConfigs.GUARD_GUNNERS.squads.maximumAffectedGuards.get());
        List<Guard> members = loadedMembers(
                level,
                player,
                binding.squadId(),
                dispatchRadius);

        int applied = 0;
        int unauthorized = 0;
        int capped = 0;
        long issuedGameTime = level.getGameTime();
        long expiresGameTime = expiration(type, issuedGameTime);
        ResourceLocation dimension = level.dimension().location();
        for (Guard guard : members) {
            if (guard.getTarget() == player
                    || !GuardOrderPermissions.mayManage(player, guard)) {
                unauthorized++;
                continue;
            }
            if (applied >= maximumAffected) {
                capped++;
                continue;
            }

            SquadCommand command = commandFor(
                    type,
                    player.getUUID(),
                    issuedGameTime,
                    expiresGameTime,
                    dimension,
                    position,
                    target,
                    guard);
            if (type == SquadCommandType.RESUME_ORDERS) {
                SquadCommandRuntime.replaceCommand(guard, null);
            } else if (command != null) {
                SquadCommandRuntime.replaceCommand(guard, command);
            } else {
                return DispatchResult.failed(DispatchStatus.INVALID_REQUEST, type);
            }
            applied++;
        }

        return DispatchResult.success(
                type,
                metadata.displayName(),
                applied,
                unauthorized,
                capped);
    }

    private static List<Guard> loadedMembers(
            ServerLevel level,
            Player player,
            UUID squadId,
            double radius) {
        double radiusSquared = radius * radius;
        AABB bounds = player.getBoundingBox().inflate(radius);
        List<Guard> members = new ArrayList<>(ABSOLUTE_MAXIMUM_QUERIED_GUARDS);
        level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(Guard.class),
                bounds,
                guard -> guard.isAlive()
                        && !guard.isRemoved()
                        && GuardSquadAccess.isMemberOf(guard, squadId)
                        && guard.distanceToSqr(player) <= radiusSquared,
                members,
                ABSOLUTE_MAXIMUM_QUERIED_GUARDS);
        members.sort(Comparator
                .comparingDouble((Guard guard) -> guard.distanceToSqr(player))
                .thenComparing(Guard::getUUID));
        return members;
    }

    private static long expiration(SquadCommandType type, long issuedGameTime) {
        int duration = type == SquadCommandType.FOCUS_TARGET
                ? ModConfigs.GUARD_GUNNERS.squads.focusCommandDurationTicks.get()
                : ModConfigs.GUARD_GUNNERS.squads
                        .positionalCommandDurationTicks.get();
        if (type == SquadCommandType.RESUME_ORDERS) {
            return issuedGameTime + 1L;
        }
        return issuedGameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : issuedGameTime + duration;
    }

    @Nullable
    private static SquadCommand commandFor(
            SquadCommandType type,
            UUID issuedBy,
            long issuedGameTime,
            long expiresGameTime,
            ResourceLocation dimension,
            @Nullable BlockPos requestedPosition,
            @Nullable LivingEntity target,
            Guard guard) {
        if (type == SquadCommandType.RESUME_ORDERS) {
            return null;
        }
        if (type == SquadCommandType.FOCUS_TARGET) {
            return target == null
                    ? null
                    : SquadCommand.focusTarget(
                            issuedBy,
                            issuedGameTime,
                            expiresGameTime,
                            dimension,
                            target.getUUID());
        }
        BlockPos commandPosition = type == SquadCommandType.HOLD
                ? guard.blockPosition()
                : requestedPosition;
        if (commandPosition == null) {
            return null;
        }
        double radius = type == SquadCommandType.DEFEND
                ? ModConfigs.GUARD_GUNNERS.squads.defendRadius.get()
                : 0.0D;
        return SquadCommand.positional(
                type,
                issuedBy,
                issuedGameTime,
                expiresGameTime,
                dimension,
                commandPosition,
                radius);
    }

    @Nullable
    private static DispatchResult validate(
            Player player,
            ItemStack ledger,
            SquadCommandType type,
            @Nullable BlockPos position,
            @Nullable LivingEntity target) {
        if (player == null
                || ledger == null
                || type == null
                || !(player.level() instanceof ServerLevel)
                || !player.isAlive()
                || player.isRemoved()
                || player.isSpectator()) {
            return DispatchResult.failed(DispatchStatus.INVALID_PLAYER, type);
        }
        if (ledger.isEmpty()
                || ledger.getCount() != 1
                || !ledger.is(ModItems.SQUAD_COMMAND_LEDGER.get())) {
            return DispatchResult.failed(DispatchStatus.NOT_LEDGER, type);
        }
        if (type.requiresTarget()) {
            if (target == null) {
                return DispatchResult.failed(DispatchStatus.TARGET_REQUIRED, type);
            }
            if (!target.isAlive()
                    || target.isRemoved()
                    || target == player
                    || target.level() != player.level()) {
                return DispatchResult.failed(DispatchStatus.INVALID_TARGET, type);
            }
            if (player.distanceToSqr(target)
                    >= MAXIMUM_TARGET_INTERACTION_DISTANCE_SQUARED) {
                return DispatchResult.failed(DispatchStatus.OUT_OF_RANGE, type);
            }
        } else if (target != null) {
            return DispatchResult.failed(DispatchStatus.INVALID_REQUEST, type);
        }
        if (type.requiresPosition()) {
            if (position == null) {
                return DispatchResult.failed(DispatchStatus.POSITION_REQUIRED, type);
            }
            double maximumPositionDistance = Math.min(
                    64.0D,
                    ModConfigs.GUARD_GUNNERS.squads.airCommandRange.get()) + 2.0D;
            if (player.distanceToSqr(
                            position.getX() + 0.5D,
                            position.getY() + 0.5D,
                            position.getZ() + 0.5D)
                    > maximumPositionDistance * maximumPositionDistance) {
                return DispatchResult.failed(DispatchStatus.OUT_OF_RANGE, type);
            }
        } else if (position != null) {
            return DispatchResult.failed(DispatchStatus.INVALID_REQUEST, type);
        }
        return null;
    }

    public enum DispatchStatus {
        SUCCESS,
        BLANK_LEDGER,
        CORRUPT_LEDGER,
        UNSUPPORTED_VERSION,
        REGISTRY_DISABLED,
        SQUAD_NOT_REGISTERED,
        POSITION_REQUIRED,
        TARGET_REQUIRED,
        INVALID_TARGET,
        OUT_OF_RANGE,
        NOT_LEDGER,
        INVALID_PLAYER,
        INVALID_REQUEST
    }

    public record DispatchResult(
            DispatchStatus status,
            @Nullable
            SquadCommandType type,
            String squadName,
            int applied,
            int unauthorized,
            int capped,
            int storedVersion) {
        public DispatchResult {
            Objects.requireNonNull(status, "status");
            squadName = Objects.requireNonNull(squadName, "squadName");
            if (applied < 0 || unauthorized < 0 || capped < 0) {
                throw new IllegalArgumentException("Dispatch counts may not be negative");
            }
            if (status != DispatchStatus.SUCCESS
                    && (applied != 0 || unauthorized != 0 || capped != 0)) {
                throw new IllegalArgumentException("A failed dispatch may not report changes");
            }
            if (status != DispatchStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported ledger may report a stored version");
            }
            if ((status == DispatchStatus.SUCCESS
                            || status == DispatchStatus.UNSUPPORTED_VERSION)
                    && type == null) {
                throw new IllegalArgumentException(
                        "A completed or version-rejected dispatch must identify its command type");
            }
        }

        public int matched() {
            return applied + unauthorized + capped;
        }

        public boolean completed() {
            return status == DispatchStatus.SUCCESS;
        }

        private static DispatchResult success(
                SquadCommandType type,
                String squadName,
                int applied,
                int unauthorized,
                int capped) {
            return new DispatchResult(
                    DispatchStatus.SUCCESS,
                    type,
                    squadName,
                    applied,
                    unauthorized,
                    capped,
                    -1);
        }

        private static DispatchResult failed(
                DispatchStatus status,
                @Nullable SquadCommandType type) {
            return new DispatchResult(status, type, "", 0, 0, 0, -1);
        }

        private static DispatchResult unsupported(
                SquadCommandType type,
                int storedVersion) {
            return new DispatchResult(
                    DispatchStatus.UNSUPPORTED_VERSION,
                    type,
                    "",
                    0,
                    0,
                    0,
                    storedVersion);
        }
    }
}
