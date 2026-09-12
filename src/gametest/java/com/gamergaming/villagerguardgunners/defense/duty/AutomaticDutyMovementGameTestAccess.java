package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Test-only live adapter for the package-private assignment seam. */
public final class AutomaticDutyMovementGameTestAccess {
    private AutomaticDutyMovementGameTestAccess() {
    }

    public static void verifyLoadedPostReturn(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean originalEnabled = ModConfigs.GUARD_GUNNERS.enabled.get();
        boolean originalAutomaticDuties =
                ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get();
        double originalDutySpeed =
                ModConfigs.GUARD_GUNNERS.villageDefense.dutyMovementSpeed.get();
        ModConfigs.GUARD_GUNNERS.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(true);
        ModConfigs.GUARD_GUNNERS.villageDefense.dutyMovementSpeed.accept(1.1D);
        BlockPos destination = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -6; x <= 6; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(destination.offset(x, -1, z),
                        Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(destination.offset(x, 0, z),
                        Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(destination.offset(x, 1, z),
                        Blocks.AIR.defaultBlockState(), 3);
            }
        }

        Guard guard = GuardEntityType.GUARD.get().create(level);
        helper.assertTrue(guard != null, "Could not create the duty-movement guard");
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        guard.moveTo(
                destination.getX() + 4.5D,
                destination.getY(),
                destination.getZ() + 0.5D);

        UUID buildingId = UUID.fromString("6d4d537a-468b-4aeb-9e51-6b575299810f");
        VillageDefenseDutySlot slot = new VillageDefenseDutySlot(
                VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                VillageDefenseDutyIds.slotId(buildingId, "live_return"),
                buildingId,
                level.dimension().location(),
                "live_return",
                destination,
                Direction.EAST,
                VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        VillageDefenseDutyAssignment assignment = new VillageDefenseDutyAssignment(
                UUID.fromString("4f2bcf6c-d02d-4c74-b71d-ed049021231f"),
                slot,
                guard.getUUID(),
                level.getGameTime(),
                level.getGameTime() + 6000L);
        GuardAutomaticDutyMovementController controller =
                new GuardAutomaticDutyMovementController(guard, ignored -> assignment);
        guard.goalSelector.addGoal(0, new ControllerGoal(controller));
        level.addFreshEntity(guard);

        Runnable restore = () -> {
            ModConfigs.GUARD_GUNNERS.enabled.accept(originalEnabled);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(
                    originalAutomaticDuties);
            ModConfigs.GUARD_GUNNERS.villageDefense.dutyMovementSpeed.accept(
                    originalDutySpeed);
        };
        helper.runAfterDelay(75, restore);
        helper.succeedWhen(() -> {
            boolean arrived = GuardAutomaticDutyMovementController.atPosition(
                    guard.getX(), guard.getY(), guard.getZ(), destination);
            helper.assertTrue(arrived,
                    "The isolated guard did not return to its loaded duty cell; position="
                            + guard.blockPosition()
                            + ", state="
                            + controller.snapshot());
            restore.run();
            guard.discard();
        });
    }

    public static void verifyBlockedPostIsReleasedUntilRepaired(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean originalEnabled = ModConfigs.GUARD_GUNNERS.enabled.get();
        boolean originalAutomaticDuties =
                ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get();
        ModConfigs.GUARD_GUNNERS.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(true);
        BlockPos destination = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(destination.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(destination, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(destination.above(), Blocks.AIR.defaultBlockState(), 3);

        Guard guard = GuardEntityType.GUARD.get().create(level);
        helper.assertTrue(guard != null, "Could not create the blocked-post guard");
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        guard.moveTo(
                destination.getX() + 2.5D,
                destination.getY(),
                destination.getZ() + 0.5D);

        UUID buildingId = UUID.fromString("7652d73a-90c8-48c3-a1aa-a13c92f71d20");
        VillageDefenseDutySlot slot = new VillageDefenseDutySlot(
                VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                VillageDefenseDutyIds.slotId(buildingId, "blocked_repair"),
                buildingId,
                level.dimension().location(),
                "blocked_repair",
                destination,
                Direction.NORTH,
                VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        VillageDefenseDutyAssignment assignment = new VillageDefenseDutyAssignment(
                UUID.fromString("af27805b-3f16-4342-a604-acd87e75e963"),
                slot,
                guard.getUUID(),
                level.getGameTime(),
                VillageDefenseDutyAssignment.NO_SHIFT_END);
        GuardAutomaticDutyMovementController blockedController =
                new GuardAutomaticDutyMovementController(guard, ignored -> assignment);

        try {
            VillageDefenseStaffingRuntime.SlotScreen blockedScreen =
                    VillageDefenseStaffingRuntime.screenSlots(level, List.of(slot));
            helper.assertTrue(
                    blockedScreen.usableSlots().isEmpty()
                            && blockedScreen.invalidLoadedSlot(),
                    "Staffing retained a physically blocked loaded duty slot");
            helper.assertTrue(
                    !blockedController.canStart()
                            && blockedController.snapshot().status()
                                    == GuardAutomaticDutyMovementController.MovementStatus
                                            .INVALID_ASSIGNMENT,
                    "Movement accepted a physically blocked loaded duty slot");

            level.setBlock(destination, Blocks.AIR.defaultBlockState(), 3);
            VillageDefenseStaffingRuntime.SlotScreen repairedScreen =
                    VillageDefenseStaffingRuntime.screenSlots(level, List.of(slot));
            GuardAutomaticDutyMovementController repairedController =
                    new GuardAutomaticDutyMovementController(guard, ignored -> assignment);
            helper.assertTrue(
                    repairedScreen.usableSlots().equals(List.of(slot))
                            && !repairedScreen.invalidLoadedSlot(),
                    "Staffing did not restore a repaired duty slot");
            helper.assertTrue(
                    repairedController.canStart(),
                    "Movement did not accept the repaired duty slot");
            helper.succeed();
        } finally {
            ModConfigs.GUARD_GUNNERS.enabled.accept(originalEnabled);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(
                    originalAutomaticDuties);
            guard.discard();
        }
    }

    private static final class ControllerGoal extends Goal {
        private final GuardAutomaticDutyMovementController controller;

        private ControllerGoal(GuardAutomaticDutyMovementController controller) {
            this.controller = controller;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return controller.canStart();
        }

        @Override
        public boolean canContinueToUse() {
            return controller.shouldContinue();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            controller.start();
        }

        @Override
        public void tick() {
            controller.tick();
        }

        @Override
        public void stop() {
            controller.stopForHigherPriorityGoal();
        }
    }
}
