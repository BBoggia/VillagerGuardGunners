package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerService.OperationResult;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerService.OperationStatus;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Routes deliberate sneak-use into the server-authoritative squad service. */
public final class SquadLedgerInteractions {
    private SquadLedgerInteractions() {
    }

    /**
     * Normal use remains available to Guard Villagers. An offhand ledger is
     * considered only while sneaking and only when the requested main hand is
     * empty, matching the existing Orders Ledger fallback.
     */
    @Nullable
    public static InteractionHand findLedgerHand(
            Player player,
            InteractionHand requestedHand) {
        if (player == null
                || requestedHand == null
                || !player.isSecondaryUseActive()) {
            return null;
        }
        if (isLedger(player.getItemInHand(requestedHand))) {
            return requestedHand;
        }
        if (requestedHand == InteractionHand.MAIN_HAND
                && player.getMainHandItem().isEmpty()
                && isLedger(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public static InteractionResult interact(
            Player player,
            Guard guard,
            InteractionHand ledgerHand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        OperationResult result = SquadLedgerService.interact(
                player,
                guard,
                ledgerHand);
        player.displayClientMessage(message(result, guard), true);
        guard.playSound(sound(result.status()), 0.45F, pitch(result.status()));
        return InteractionResult.CONSUME;
    }

    private static boolean isLedger(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.SQUAD_COMMAND_LEDGER.get());
    }

    private static Component message(OperationResult result, Guard guard) {
        return switch (result.status()) {
            case CREATED_AND_ASSIGNED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.created",
                            result.squadName(),
                            guard.getDisplayName())
                    .withStyle(ChatFormatting.GREEN);
            case BOUND_TO_EXISTING -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.bound",
                            result.squadName())
                    .withStyle(ChatFormatting.GREEN);
            case RECOVERED_AND_BOUND -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.recovered",
                            result.squadName())
                    .withStyle(ChatFormatting.GREEN);
            case ASSIGNED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.assigned",
                            guard.getDisplayName(),
                            result.squadName())
                    .withStyle(ChatFormatting.GREEN);
            case REMOVED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.removed",
                            guard.getDisplayName(),
                            result.squadName())
                    .withStyle(ChatFormatting.YELLOW);
            case CONFIRM_REASSIGNMENT -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.confirm_reassignment",
                            guard.getDisplayName(),
                            result.squadName())
                    .withStyle(ChatFormatting.GOLD);
            case REASSIGNED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.reassigned",
                            guard.getDisplayName(),
                            result.squadName())
                    .withStyle(ChatFormatting.GREEN);
            case CORRUPT_LEDGER -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.corrupt")
                    .withStyle(ChatFormatting.RED);
            case UNSUPPORTED_VERSION -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.unsupported",
                            result.storedVersion())
                    .withStyle(ChatFormatting.RED);
            case REGISTRY_DISABLED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.registry_disabled")
                    .withStyle(ChatFormatting.RED);
            case REGISTRY_FULL -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.registry_full")
                    .withStyle(ChatFormatting.RED);
            case IDENTITY_GENERATION_FAILED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.identity_failed")
                    .withStyle(ChatFormatting.RED);
            case UNAUTHORIZED -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.unauthorized")
                    .withStyle(ChatFormatting.RED);
            case OUT_OF_RANGE -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.out_of_range")
                    .withStyle(ChatFormatting.YELLOW);
            case NOT_SNEAKING -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.sneak_required")
                    .withStyle(ChatFormatting.YELLOW);
            case NOT_LEDGER, INVALID_GUARD, INVALID_REQUEST -> Component.translatable(
                            "message.villagerguardgunners.squad_ledger.failed")
                    .withStyle(ChatFormatting.RED);
        };
    }

    private static SoundEvent sound(OperationStatus status) {
        return switch (status) {
            case CREATED_AND_ASSIGNED, BOUND_TO_EXISTING, RECOVERED_AND_BOUND ->
                    SoundEvents.BOOK_PAGE_TURN;
            case ASSIGNED, REASSIGNED -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case REMOVED, CONFIRM_REASSIGNMENT -> SoundEvents.UI_BUTTON_CLICK.get();
            default -> SoundEvents.VILLAGER_NO;
        };
    }

    private static float pitch(OperationStatus status) {
        return switch (status) {
            case CREATED_AND_ASSIGNED -> 1.0F;
            case BOUND_TO_EXISTING, RECOVERED_AND_BOUND -> 1.1F;
            case ASSIGNED, REASSIGNED -> 1.2F;
            case REMOVED, CONFIRM_REASSIGNMENT -> 0.9F;
            default -> 0.8F;
        };
    }
}
