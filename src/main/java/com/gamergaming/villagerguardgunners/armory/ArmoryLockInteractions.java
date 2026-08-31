package com.gamergaming.villagerguardgunners.armory;

import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBinding;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec;
import com.gamergaming.villagerguardgunners.squad.SquadMetadata;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One-shot server validation for assigning or removing an armory squad lock. */
public final class ArmoryLockInteractions {
    private static final double MAXIMUM_INTERACTION_DISTANCE_SQUARED = 8.0D * 8.0D;

    private ArmoryLockInteractions() {
    }

    public static InteractionResult interact(
            Player player,
            ItemStack ledger,
            ArmoryBlockEntity armory) {
        if (player == null
                || armory == null
                || !(player.level() instanceof ServerLevel level)
                || player.level() != armory.getLevel()
                || !player.isAlive()
                || player.isRemoved()
                || player.isSpectator()
                || !player.isSecondaryUseActive()
                || player.distanceToSqr(
                                armory.getBlockPos().getX() + 0.5D,
                                armory.getBlockPos().getY() + 0.5D,
                                armory.getBlockPos().getZ() + 0.5D)
                        >= MAXIMUM_INTERACTION_DISTANCE_SQUARED) {
            return InteractionResult.INVALID_REQUEST;
        }
        if (ledger == null
                || ledger.isEmpty()
                || ledger.getCount() != 1
                || !ledger.is(ModItems.SQUAD_COMMAND_LEDGER.get())) {
            return InteractionResult.NOT_LEDGER;
        }

        SquadLedgerBindingCodec.DecodeResult decoded =
                SquadLedgerBindingCodec.readFrom(ledger.getTag());
        if (decoded.status() == SquadLedgerBindingCodec.DecodeStatus.BLANK) {
            return InteractionResult.BLANK_LEDGER;
        }
        if (decoded.status() == SquadLedgerBindingCodec.DecodeStatus.CORRUPT) {
            return InteractionResult.CORRUPT_LEDGER;
        }
        if (decoded.status()
                == SquadLedgerBindingCodec.DecodeStatus.UNSUPPORTED_VERSION) {
            return InteractionResult.UNSUPPORTED_LEDGER;
        }
        SquadLedgerBinding binding = decoded.binding();
        if (binding == null) {
            return InteractionResult.CORRUPT_LEDGER;
        }
        SquadMetadata metadata = SquadRegistrySavedData.get(level.getServer())
                .find(binding.squadId())
                .orElse(null);
        if (metadata == null) {
            return InteractionResult.SQUAD_NOT_REGISTERED;
        }

        ArmorySquadLock current = armory.getSquadLock();
        if (armory.hasReadableLock()
                && current != null
                && current.squadId().equals(metadata.squadId())) {
            armory.setSquadLock(null);
            return InteractionResult.UNLOCKED;
        }
        armory.setSquadLock(ArmorySquadLock.assigned(
                metadata.squadId(),
                metadata.displayName()));
        return current == null ? InteractionResult.LOCKED : InteractionResult.REASSIGNED;
    }

    public static void report(
            Player player,
            ArmoryBlockEntity armory,
            InteractionResult result) {
        Component message = switch (result) {
            case LOCKED -> Component.translatable(
                    "message.villagerguardgunners.armory.locked",
                    armory.getSquadLock() == null ? "" : armory.getSquadLock().displayName());
            case UNLOCKED -> Component.translatable(
                    "message.villagerguardgunners.armory.unlocked");
            case REASSIGNED -> Component.translatable(
                    "message.villagerguardgunners.armory.reassigned",
                    armory.getSquadLock() == null ? "" : armory.getSquadLock().displayName());
            case BLANK_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.armory.blank_ledger");
            case CORRUPT_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.armory.corrupt_ledger");
            case UNSUPPORTED_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.armory.unsupported_ledger");
            case SQUAD_NOT_REGISTERED -> Component.translatable(
                    "message.villagerguardgunners.armory.missing_squad");
            case NOT_LEDGER, INVALID_REQUEST -> Component.translatable(
                    "message.villagerguardgunners.armory.failed");
        };
        player.displayClientMessage(
                message.copy().withStyle(result.succeeded()
                        ? ChatFormatting.GOLD
                        : ChatFormatting.RED),
                true);
        player.playSound(
                result.succeeded() ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.VILLAGER_NO,
                0.45F,
                result.succeeded() ? 1.1F : 0.8F);
    }

    public enum InteractionResult {
        LOCKED,
        UNLOCKED,
        REASSIGNED,
        BLANK_LEDGER,
        CORRUPT_LEDGER,
        UNSUPPORTED_LEDGER,
        SQUAD_NOT_REGISTERED,
        NOT_LEDGER,
        INVALID_REQUEST;

        public boolean succeeded() {
            return this == LOCKED || this == UNLOCKED || this == REASSIGNED;
        }
    }
}
