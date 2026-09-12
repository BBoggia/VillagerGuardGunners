package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.entities.Guard;

/** Administrative inspection and empty-owner acknowledgement; never replays or deletes stock. */
public final class ServiceCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var root = Commands.literal("equipment-service").requires(source -> source.hasPermission(2));
        for (boolean recover : new boolean[]{false, true}) root.then(Commands.literal(recover ? "recover-empty" : "inspect")
                .then(Commands.literal("guard").then(Commands.argument("guard", EntityArgument.entity()).executes(context -> {
                    var entity = EntityArgument.getEntity(context, "guard");
                    if (!(entity instanceof Guard guard)) return fail(context.getSource(), "Select a guard.");
                    boolean empty = GuardEquipmentServiceAccess.get(guard).known() && ServiceEquipment.snapshot(guard).stream().allMatch(stack -> stack.isEmpty()) && guard.getOffhandItem().isEmpty()
                            && com.gamergaming.villagerguardgunners.api.GuardSidearmAccess.getState(guard) == com.gamergaming.villagerguardgunners.sidearm.SidearmState.PRIMARY_ACTIVE
                            && com.gamergaming.villagerguardgunners.api.GuardMedicalAccess.get(guard).readable();
                    int result = run(context.getSource(), GuardEquipmentServiceAccess.get(guard).receipt, recover, empty);
                    if (recover && result == 1) { QuartermasterServiceRuntime.cancel(guard, ServiceStatus.MANUAL); GuardEquipmentServiceAccess.get(guard).policy(GuardServiceState.Policy.MANUAL); }
                    return result;
                })))
                .then(Commands.literal("block").then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context -> {
                    var pos = BlockPosArgument.getLoadedBlockPos(context, "position");
                    var block = context.getSource().getLevel().getBlockEntity(pos);
                    ServiceReceipt receipt; IItemHandler inventory;
                    if (block instanceof QuartermasterBlockEntity station) { receipt = station.serviceReceipt(); inventory = station.inventory(); }
                    else if (block instanceof ArmoryBlockEntity armory) { receipt = armory.serviceReceipt(); inventory = armory.getInventory(); }
                    else return fail(context.getSource(), "Select a loaded Quartermaster station or Village Armory.");
                    boolean empty = true; for (int slot = 0; slot < inventory.getSlots(); slot++) empty &= inventory.getStackInSlot(slot).isEmpty();
                    int result = run(context.getSource(), receipt, recover, empty);
                    if (recover && result == 1) block.setChanged();
                    return result;
                }))));
        return root;
    }
    private static int run(CommandSourceStack source, ServiceReceipt receipt, boolean recover, boolean empty) {
        var journal = ServiceJournal.get(source.getServer());
        if (recover && (!empty || !journal.recoverEmpty(receipt))) return fail(source, "Recovery requires an empty inventory, a known receipt, and a readable service journal. Reconcile the physical items from a backup before retrying; this command never deletes or recreates items.");
        source.sendSuccess(() -> Component.literal("Equipment service owner=" + receipt.id() + " saved=" + receipt.revision()
                + " expected=" + journal.expected(receipt) + " locked=" + receipt.locked() + " journalHealthy=" + journal.healthy()), false);
        return 1;
    }
    private static int fail(CommandSourceStack source, String message) { source.sendFailure(Component.literal(message)); return 0; }
}
