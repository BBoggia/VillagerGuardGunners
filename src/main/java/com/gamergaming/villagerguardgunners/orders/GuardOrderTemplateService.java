package com.gamergaming.villagerguardgunners.orders;

import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.DecodeResult;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.DecodeStatus;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Server-authoritative, interaction-only capture and application of guard
 * order templates. This service never creates, removes, copies, or transfers
 * an item and performs no work from an entity or world tick.
 */
public final class GuardOrderTemplateService {
    private static final double MAXIMUM_INTERACTION_DISTANCE_SQUARED = 8.0D * 8.0D;

    private GuardOrderTemplateService() {
    }

    /**
     * Captures all seven raw orders into the ledger held in {@code hand}.
     * Existing data is changed only when {@code overwriteExisting} is true.
     */
    public static OperationResult capture(
            Player player,
            Guard guard,
            InteractionHand hand,
            boolean overwriteExisting) {
        OperationResult validation = validate(player, guard, hand);
        if (validation != null) {
            return validation;
        }

        ItemStack ledger = player.getItemInHand(hand);
        DecodeResult existing = GuardOrderTemplate.readFrom(ledger.getTag());
        boolean replacingExisting = existing.status() != DecodeStatus.BLANK;
        if (!overwriteExisting) {
            switch (existing.status()) {
                case BLANK -> {
                    // A normal first capture may proceed.
                }
                case VALID -> {
                    return OperationResult.of(OperationStatus.TEMPLATE_ALREADY_WRITTEN);
                }
                case CORRUPT -> {
                    return OperationResult.of(OperationStatus.CORRUPT_TEMPLATE);
                }
                case UNSUPPORTED_VERSION -> {
                    return OperationResult.unsupported(existing.storedVersion());
                }
            }
        }

        GuardOrderTemplate.writeTo(
                ledger.getOrCreateTag(),
                GuardOrdersAccess.getOrders(guard));
        player.getInventory().setChanged();
        return OperationResult.of(replacingExisting
                ? OperationStatus.OVERWRITTEN
                : OperationStatus.CAPTURED);
    }

    /**
     * Atomically replaces all seven guard orders from the held ledger.
     * Matching orders are a mutation-free no-op so the guard's order revision
     * and active combat state are not needlessly invalidated.
     */
    public static OperationResult apply(
            Player player,
            Guard guard,
            InteractionHand hand) {
        OperationResult validation = validate(player, guard, hand);
        if (validation != null) {
            return validation;
        }

        DecodeResult decoded = GuardOrderTemplate.readFrom(
                player.getItemInHand(hand).getTag());
        switch (decoded.status()) {
            case BLANK -> {
                return OperationResult.of(OperationStatus.BLANK_TEMPLATE);
            }
            case CORRUPT -> {
                return OperationResult.of(OperationStatus.CORRUPT_TEMPLATE);
            }
            case UNSUPPORTED_VERSION -> {
                return OperationResult.unsupported(decoded.storedVersion());
            }
            case VALID -> {
                // Continue only after the complete template has decoded.
            }
        }

        GuardOrders templateOrders = decoded.orders();
        if (templateOrders == null) {
            return OperationResult.of(OperationStatus.CORRUPT_TEMPLATE);
        }

        GuardOrders currentOrders = GuardOrdersAccess.getOrders(guard);
        if (templateOrders.equals(currentOrders)) {
            return OperationResult.of(OperationStatus.ALREADY_MATCHES);
        }
        if (!GuardOrdersAccess.setOrders(guard, templateOrders)) {
            return OperationResult.of(OperationStatus.INVALID_GUARD);
        }
        return OperationResult.of(OperationStatus.APPLIED);
    }

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
                || !player.isAlive()
                || player.isRemoved()) {
            return OperationResult.of(OperationStatus.INVALID_REQUEST);
        }
        if (!guard.isAlive() || guard.isRemoved()) {
            return OperationResult.of(OperationStatus.INVALID_GUARD);
        }
        if (player.distanceToSqr(guard) >= MAXIMUM_INTERACTION_DISTANCE_SQUARED) {
            return OperationResult.of(OperationStatus.OUT_OF_RANGE);
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty() || !heldStack.is(ModItems.ORDERS_LEDGER.get())) {
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
        CAPTURED,
        OVERWRITTEN,
        APPLIED,
        ALREADY_MATCHES,
        TEMPLATE_ALREADY_WRITTEN,
        BLANK_TEMPLATE,
        CORRUPT_TEMPLATE,
        UNSUPPORTED_VERSION,
        UNAUTHORIZED,
        OUT_OF_RANGE,
        NOT_LEDGER,
        INVALID_GUARD,
        INVALID_REQUEST
    }

    /**
     * {@code storedVersion} is meaningful only for
     * {@link OperationStatus#UNSUPPORTED_VERSION}; it is {@code -1} otherwise.
     */
    public record OperationResult(OperationStatus status, int storedVersion) {
        public OperationResult {
            Objects.requireNonNull(status, "status");
            if (status != OperationStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException(
                        "Only an unsupported template may report a stored version");
            }
        }

        public boolean changedLedger() {
            return status == OperationStatus.CAPTURED
                    || status == OperationStatus.OVERWRITTEN;
        }

        public boolean changedGuard() {
            return status == OperationStatus.APPLIED;
        }

        public boolean completed() {
            return changedLedger()
                    || changedGuard()
                    || status == OperationStatus.ALREADY_MATCHES;
        }

        private static OperationResult of(OperationStatus status) {
            return new OperationResult(status, -1);
        }

        private static OperationResult unsupported(int storedVersion) {
            return new OperationResult(
                    OperationStatus.UNSUPPORTED_VERSION,
                    storedVersion);
        }
    }
}
