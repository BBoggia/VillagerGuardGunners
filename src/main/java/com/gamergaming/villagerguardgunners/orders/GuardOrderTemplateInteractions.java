package com.gamergaming.villagerguardgunners.orders;

import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.DecodeStatus;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplateService.OperationResult;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplateService.OperationStatus;
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

/** Routes deliberate ledger use into the server-authoritative template service. */
public final class GuardOrderTemplateInteractions {
    private GuardOrderTemplateInteractions() {
    }

    /**
     * Resolves the ledger intended for this interaction. An offhand ledger may
     * stand in for an empty requested main hand so Guard Villagers cannot open
     * its inventory before Minecraft reaches the offhand interaction. A
     * non-empty main-hand item is never overridden by this fallback.
     */
    @Nullable
    public static InteractionHand findLedgerHand(
            Player player,
            InteractionHand requestedHand) {
        if (player == null || requestedHand == null) {
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

        DecodeStatus templateStatus = GuardOrderTemplate.readFrom(
                player.getItemInHand(ledgerHand).getTag()).status();
        boolean overwrite = player.isSecondaryUseActive();
        OperationResult result = overwrite || templateStatus == DecodeStatus.BLANK
                ? GuardOrderTemplateService.capture(
                        player,
                        guard,
                        ledgerHand,
                        overwrite)
                : GuardOrderTemplateService.apply(player, guard, ledgerHand);

        player.displayClientMessage(message(result, guard), true);
        guard.playSound(sound(result.status()), 0.45F, pitch(result.status()));
        return InteractionResult.CONSUME;
    }

    private static boolean isLedger(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.ORDERS_LEDGER.get());
    }

    private static Component message(OperationResult result, Guard guard) {
        return switch (result.status()) {
            case CAPTURED -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.captured",
                            guard.getDisplayName())
                    .withStyle(ChatFormatting.GREEN);
            case OVERWRITTEN -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.overwritten",
                            guard.getDisplayName())
                    .withStyle(ChatFormatting.GOLD);
            case APPLIED -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.applied",
                            guard.getDisplayName())
                    .withStyle(ChatFormatting.GREEN);
            case ALREADY_MATCHES -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.already_matches",
                            guard.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW);
            case TEMPLATE_ALREADY_WRITTEN -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.already_written")
                    .withStyle(ChatFormatting.YELLOW);
            case BLANK_TEMPLATE -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.blank")
                    .withStyle(ChatFormatting.YELLOW);
            case CORRUPT_TEMPLATE -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.corrupt")
                    .withStyle(ChatFormatting.RED);
            case UNSUPPORTED_VERSION -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.unsupported",
                            result.storedVersion())
                    .withStyle(ChatFormatting.RED);
            case UNAUTHORIZED -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.unauthorized")
                    .withStyle(ChatFormatting.RED);
            case OUT_OF_RANGE -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.out_of_range")
                    .withStyle(ChatFormatting.YELLOW);
            case INVALID_GUARD -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.invalid_guard")
                    .withStyle(ChatFormatting.RED);
            case NOT_LEDGER, INVALID_REQUEST -> Component.translatable(
                            "message.villagerguardgunners.orders_ledger.failed")
                    .withStyle(ChatFormatting.RED);
        };
    }

    private static SoundEvent sound(OperationStatus status) {
        return switch (status) {
            case CAPTURED, OVERWRITTEN -> SoundEvents.BOOK_PAGE_TURN;
            case APPLIED -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case ALREADY_MATCHES -> SoundEvents.UI_BUTTON_CLICK.get();
            default -> SoundEvents.VILLAGER_NO;
        };
    }

    private static float pitch(OperationStatus status) {
        return switch (status) {
            case CAPTURED -> 1.0F;
            case OVERWRITTEN -> 0.9F;
            case APPLIED -> 1.2F;
            case ALREADY_MATCHES -> 1.0F;
            default -> 0.8F;
        };
    }
}
