package com.gamergaming.villagerguardgunners.item;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.ArmoryLockInteractions;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBinding;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec.DecodeResult;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.squad.SquadCommandDispatcher;
import com.gamergaming.villagerguardgunners.squad.SquadCommandDispatcher.DispatchResult;
import com.gamergaming.villagerguardgunners.squad.SquadCommandDispatcher.DispatchStatus;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerModeCodec;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRoute;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor.EditAction;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor.EditResult;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor.EditStatus;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Reusable stable identity for membership and future temporary squad commands. */
public final class SquadCommandLedgerItem extends Item {
    public SquadCommandLedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Match the Orders Ledger's allocation-free render path. A full strict
        // decode is reserved for tooltips and deliberate interactions.
        CompoundTag itemTag = stack.getTag();
        return (itemTag != null
                        && itemTag.contains(
                                SquadLedgerBindingCodec.BINDING_TAG,
                                Tag.TAG_COMPOUND))
                || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack ledger = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(ledger, true);
        }
        if (player.isSecondaryUseActive()) {
            cycleMode(player, ledger);
            return InteractionResultHolder.consume(ledger);
        }

        SquadCommandType mode = selectedMode(player, ledger);
        if (mode == null) {
            return InteractionResultHolder.consume(ledger);
        }
        if (mode.isPatrolRouteEditor()) {
            reportRouteEdit(
                    player,
                    SquadPatrolRouteEditor.edit(
                            player, ledger, EditAction.INSPECT, null));
            return InteractionResultHolder.consume(ledger);
        }
        BlockPos position = mode.requiresPosition()
                ? projectedPosition(player)
                : null;
        reportDispatch(
                player,
                SquadCommandDispatcher.dispatch(
                        player,
                        ledger,
                        mode,
                        position,
                        null));
        return InteractionResultHolder.consume(ledger);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()
                && context.getLevel().getBlockEntity(context.getClickedPos())
                        instanceof ArmoryBlockEntity armory) {
            // Sneaking with an item normally skips Block#use, so handle the
            // deliberate armory operation here as well as in the block path.
            if (!context.getLevel().isClientSide) {
                ArmoryLockInteractions.InteractionResult result =
                        ArmoryLockInteractions.interact(player, context.getItemInHand(), armory);
                ArmoryLockInteractions.report(player, armory, result);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack ledger = context.getItemInHand();
        SquadCommandType mode = selectedMode(player, ledger);
        if (mode != null) {
            if (mode.isPatrolRouteEditor()) {
                boolean removing = player.isSecondaryUseActive();
                BlockPos waypoint = removing
                        ? null
                        : context.getClickedPos().relative(context.getClickedFace());
                reportRouteEdit(
                        player,
                        SquadPatrolRouteEditor.edit(
                                player,
                                ledger,
                                removing ? EditAction.REMOVE_LAST : EditAction.ADD_WAYPOINT,
                                waypoint));
                return InteractionResult.CONSUME;
            }
            BlockPos position = mode.requiresPosition()
                    ? BlockPos.containing(context.getClickLocation())
                    : null;
            reportDispatch(
                    player,
                    SquadCommandDispatcher.dispatch(
                            player,
                            ledger,
                            mode,
                            position,
                            null));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack ledger,
            Player player,
            LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        SquadCommandType mode = selectedMode(player, ledger);
        if (mode != null) {
            if (mode.isPatrolRouteEditor()) {
                reportRouteEdit(
                        player,
                        SquadPatrolRouteEditor.edit(
                                player, ledger, EditAction.INSPECT, null));
                return InteractionResult.CONSUME;
            }
            reportDispatch(
                    player,
                    SquadCommandDispatcher.dispatch(
                            player,
                            ledger,
                            mode,
                            null,
                            mode.requiresTarget() ? target : null));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        DecodeResult decoded = SquadLedgerBindingCodec.readFrom(stack.getTag());
        switch (decoded.status()) {
            case BLANK -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.blank")
                    .withStyle(ChatFormatting.GRAY));
            case CORRUPT -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.corrupt")
                    .withStyle(ChatFormatting.RED));
            case UNSUPPORTED_VERSION -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.unsupported",
                            decoded.storedVersion())
                    .withStyle(ChatFormatting.RED));
            case VALID -> appendBinding(stack, tooltip, decoded.binding());
        }
    }

    private static void appendBinding(
            ItemStack stack,
            List<Component> tooltip,
            @Nullable SquadLedgerBinding binding) {
        if (binding == null) {
            tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.corrupt")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.bound",
                        binding.cachedDisplayName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.id",
                        binding.squadId().toString().substring(0, 8))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.membership")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.rename")
                .withStyle(ChatFormatting.DARK_GRAY));

        SquadLedgerModeCodec.DecodeResult mode =
                SquadLedgerModeCodec.readFrom(stack.getTag());
        switch (mode.status()) {
            case BLANK -> appendMode(
                    tooltip,
                    SquadLedgerModeCodec.DEFAULT_MODE);
            case VALID -> appendMode(tooltip, mode.mode());
            case CORRUPT -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.mode_corrupt")
                    .withStyle(ChatFormatting.RED));
            case UNSUPPORTED_VERSION -> tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.mode_unsupported",
                            mode.storedVersion())
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void cycleMode(Player player, ItemStack ledger) {
        DecodeResult binding = SquadLedgerBindingCodec.readFrom(ledger.getTag());
        if (!binding.isValid()) {
            player.displayClientMessage(bindingFailure(binding), true);
            player.playSound(SoundEvents.VILLAGER_NO, 0.45F, 0.8F);
            return;
        }

        SquadLedgerModeCodec.DecodeResult decoded =
                SquadLedgerModeCodec.readFrom(ledger.getTag());
        if (decoded.status()
                == SquadLedgerModeCodec.DecodeStatus.UNSUPPORTED_VERSION) {
            player.displayClientMessage(Component.translatable(
                            "message.villagerguardgunners.squad_command.unsupported_mode",
                            decoded.storedVersion())
                    .withStyle(ChatFormatting.RED), true);
            player.playSound(SoundEvents.VILLAGER_NO, 0.45F, 0.8F);
            return;
        }
        SquadCommandType current = decoded.status()
                == SquadLedgerModeCodec.DecodeStatus.VALID
                        ? decoded.mode()
                        : SquadLedgerModeCodec.DEFAULT_MODE;
        SquadCommandType selected = decoded.status()
                == SquadLedgerModeCodec.DecodeStatus.CORRUPT
                        ? SquadLedgerModeCodec.DEFAULT_MODE
                        : current.next();
        SquadLedgerModeCodec.writeTo(ledger.getOrCreateTag(), selected);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable(
                        "message.villagerguardgunners.squad_command.mode_selected",
                        Component.translatable(selected.translationKey()))
                .withStyle(decoded.status() == SquadLedgerModeCodec.DecodeStatus.CORRUPT
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.GOLD), true);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.45F, 1.0F);
    }

    @Nullable
    private static SquadCommandType selectedMode(Player player, ItemStack ledger) {
        DecodeResult binding = SquadLedgerBindingCodec.readFrom(ledger.getTag());
        if (!binding.isValid()) {
            player.displayClientMessage(bindingFailure(binding), true);
            player.playSound(SoundEvents.VILLAGER_NO, 0.45F, 0.8F);
            return null;
        }
        SquadLedgerModeCodec.DecodeResult decoded =
                SquadLedgerModeCodec.readFrom(ledger.getTag());
        if (decoded.status() == SquadLedgerModeCodec.DecodeStatus.BLANK) {
            return SquadLedgerModeCodec.DEFAULT_MODE;
        }
        if (decoded.status() == SquadLedgerModeCodec.DecodeStatus.VALID) {
            return decoded.mode();
        }
        Component failure = decoded.status()
                == SquadLedgerModeCodec.DecodeStatus.UNSUPPORTED_VERSION
                        ? Component.translatable(
                                "message.villagerguardgunners.squad_command.unsupported_mode",
                                decoded.storedVersion())
                        : Component.translatable(
                                "message.villagerguardgunners.squad_command.corrupt_mode");
        player.displayClientMessage(
                failure.copy().withStyle(ChatFormatting.RED),
                true);
        player.playSound(SoundEvents.VILLAGER_NO, 0.45F, 0.8F);
        return null;
    }

    private static Component bindingFailure(DecodeResult binding) {
        return switch (binding.status()) {
            case BLANK -> Component.translatable(
                    "message.villagerguardgunners.squad_command.blank");
            case CORRUPT -> Component.translatable(
                    "message.villagerguardgunners.squad_command.corrupt");
            case UNSUPPORTED_VERSION -> Component.translatable(
                    "message.villagerguardgunners.squad_command.unsupported",
                    binding.storedVersion());
            case VALID -> Component.empty();
        };
    }

    private static BlockPos projectedPosition(Player player) {
        double range = Math.min(
                64.0D,
                ModConfigs.GUARD_GUNNERS.squads.airCommandRange.get());
        HitResult hit = player.pick(range, 1.0F, false);
        return BlockPos.containing(hit.getLocation());
    }

    private static void reportDispatch(Player player, DispatchResult result) {
        Component message = switch (result.status()) {
            case SUCCESS -> Component.translatable(
                    "message.villagerguardgunners.squad_command.dispatched",
                    Component.translatable(result.type().translationKey()),
                    result.squadName(),
                    result.applied(),
                    result.unauthorized(),
                    result.capped());
            case BLANK_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.squad_command.blank");
            case CORRUPT_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.squad_command.corrupt");
            case UNSUPPORTED_VERSION -> Component.translatable(
                    "message.villagerguardgunners.squad_command.unsupported",
                    result.storedVersion());
            case REGISTRY_DISABLED -> Component.translatable(
                    "message.villagerguardgunners.squad_command.registry_disabled");
            case SQUAD_NOT_REGISTERED -> Component.translatable(
                    "message.villagerguardgunners.squad_command.missing_squad");
            case POSITION_REQUIRED -> Component.translatable(
                    "message.villagerguardgunners.squad_command.position_required");
            case TARGET_REQUIRED -> Component.translatable(
                    "message.villagerguardgunners.squad_command.target_required");
            case INVALID_TARGET -> Component.translatable(
                    "message.villagerguardgunners.squad_command.invalid_target");
            case OUT_OF_RANGE -> Component.translatable(
                    "message.villagerguardgunners.squad_command.out_of_range");
            case NOT_LEDGER, INVALID_PLAYER, INVALID_REQUEST -> Component.translatable(
                    "message.villagerguardgunners.squad_command.failed");
        };
        boolean success = result.status() == DispatchStatus.SUCCESS;
        player.displayClientMessage(message.copy().withStyle(success
                        ? (result.applied() > 0
                                ? ChatFormatting.GREEN
                                : ChatFormatting.YELLOW)
                        : ChatFormatting.RED),
                true);
        player.playSound(success
                        ? SoundEvents.BOOK_PAGE_TURN
                        : SoundEvents.VILLAGER_NO,
                0.45F,
                success ? 1.1F : 0.8F);
    }

    private static void reportRouteEdit(Player player, EditResult result) {
        Component routeState = Component.translatable(result.executable()
                ? "message.villagerguardgunners.patrol_route.state.ready"
                : "message.villagerguardgunners.patrol_route.state.draft");
        Component message = switch (result.status()) {
            case WAYPOINT_ADDED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.added",
                    result.squadName(),
                    result.waypointCount(),
                    routeState);
            case WAYPOINT_REMOVED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.removed",
                    result.squadName(),
                    result.waypointCount(),
                    routeState);
            case ROUTE_CLEARED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.cleared",
                    result.squadName());
            case ROUTE_DRAFT, ROUTE_READY -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.status",
                    result.squadName(),
                    result.dimensionId() == null ? "" : result.dimensionId().toString(),
                    result.waypointCount(),
                    result.revision(),
                    routeState);
            case NO_ROUTE -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.none",
                    result.squadName());
            case UNCHANGED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.unchanged",
                    result.squadName());
            case BLANK_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.squad_command.blank");
            case CORRUPT_LEDGER -> Component.translatable(
                    "message.villagerguardgunners.squad_command.corrupt");
            case UNSUPPORTED_VERSION -> Component.translatable(
                    "message.villagerguardgunners.squad_command.unsupported",
                    result.storedVersion());
            case REGISTRY_DISABLED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.registry_disabled");
            case SQUAD_NOT_REGISTERED -> Component.translatable(
                    "message.villagerguardgunners.squad_command.missing_squad");
            case ROUTE_FULL -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.full",
                    SquadPatrolRoute.MAXIMUM_WAYPOINTS);
            case WRONG_DIMENSION -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.wrong_dimension",
                    result.dimensionId() == null ? "" : result.dimensionId().toString());
            case INVALID_WAYPOINT -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.invalid_waypoint",
                    SquadPatrolRoute.MAXIMUM_SEGMENT_DISTANCE);
            case INVALID_DESTINATION -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.invalid_destination");
            case DESTINATION_UNLOADED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.unloaded");
            case OUTSIDE_WORLD -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.outside_world");
            case OUT_OF_RANGE -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.out_of_range");
            case REVISION_EXHAUSTED -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.revision_exhausted");
            case INVALID_REQUEST -> Component.translatable(
                    "message.villagerguardgunners.patrol_route.failed");
        };
        boolean positive = result.changed() || result.informational();
        if (result.informational()) {
            com.gamergaming.villagerguardgunners.command.VillageCoverageCommands.ledgerSummary(player);
            com.gamergaming.villagerguardgunners.command.VillagePopulationCommands.ledgerSummary(player);
        }
        player.displayClientMessage(message.copy().withStyle(
                        result.changed()
                                ? ChatFormatting.GREEN
                                : positive ? ChatFormatting.GOLD : ChatFormatting.RED),
                true);
        player.playSound(
                positive ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.VILLAGER_NO,
                0.45F,
                result.changed() ? 1.15F : positive ? 1.0F : 0.8F);
    }

    private static void appendMode(
            List<Component> tooltip,
            @Nullable SquadCommandType mode) {
        if (mode == null) {
            tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.mode_corrupt")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.mode",
                        Component.translatable(mode.translationKey()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.cycle")
                .withStyle(ChatFormatting.GRAY));
        if (mode.isPatrolRouteEditor()) {
            tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.route_add")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.route_remove")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                            "item.villagerguardgunners.squad_command_ledger.tooltip.route_inspect")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.villagerguardgunners.squad_command_ledger.tooltip.issue")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
