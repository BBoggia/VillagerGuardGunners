package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class QuartermasterServiceGameTests {
    private static final String TEMPLATE = "villagerguardgunners/gametest_arena";
    private record Fixture(Guard guard, QuartermasterBlockEntity station, Villager operator, FakePlayer owner) implements AutoCloseable {
        @Override public void close() { operator.discard(); }
    }
    private static Fixture fixture(GameTestHelper h) {
        var level = h.getLevel(); level.setDayTime(3000);
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, ModBlocks.QUARTERMASTER.get().defaultBlockState(), 3);
        var station = (QuartermasterBlockEntity) level.getBlockEntity(pos);
        var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "EquipmentServiceTest"));
        owner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5); station.placedBy(owner);
        Guard guard = GuardEntityType.GUARD.get().create(level); guard.setPos(owner.position()); guard.setNoAi(true); guard.setOwnerId(owner.getUUID());
        var data = IGunOperator.fromLivingEntity(guard).getDataHolder(); data.drawTimestamp = -1; data.shootTimestamp = -1; data.meleePrepTickCount = -1;
        var state = GuardEquipmentServiceAccess.get(guard); state.policy(GuardServiceState.Policy.PROFILE); state.profile(ResourceLocation.parse("villagerguardgunners:rifleman"));
        Villager operator = new Villager(EntityType.VILLAGER, level); operator.setPos(owner.position());
        operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
        operator.refreshBrain(level); operator.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), pos)); level.addFreshEntity(operator); station.operator = operator.getUUID();
        level.getPoiManager().take(type -> type.value() == ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),
                (type, point) -> point.equals(pos), pos, 1);

        return new Fixture(guard, station, operator, owner);
    }
    private static ItemStack gun(String id) { var stack = GunItemBuilder.create().setId(ResourceLocation.parse("tacz:" + id)).build(); stack.getOrCreateTag().putString("ServiceEvidence", UUID.randomUUID().toString()); return stack; }
    private static ServicePlan plan(Fixture f) { return ServicePlanner.plan(f.guard, f.station, QuartermasterServiceRuntime.sources(f.station)); }
    private static void ready(GameTestHelper h, Fixture f) {
        h.assertTrue(ServiceEligibility.station(f.station) == ServiceStatus.READY, "Station gate: " + ServiceEligibility.station(f.station));
        h.assertTrue(ServiceEligibility.guard(f.guard) == ServiceStatus.READY, "Guard gate: " + ServiceEligibility.guard(f.guard));
    }
    private static void supplied(GameTestHelper h, ServicePlan p) { h.assertTrue(p != null, "No plan"); h.assertTrue(ServiceTransactions.transfer(p) == ServiceStatus.SUPPLIED, "Transfer rejected: " + ServiceEligibility.guard(p.guard()) + "/" + ServiceEligibility.station(p.station())); }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void physicalGunMovesOnceWithExactNbt(GameTestHelper h) {
        try (var f = fixture(h)) { ready(h, f); ItemStack stack = gun("ak47"); f.station.inventory().setStackInSlot(0, stack.copy());
        var p = plan(f); supplied(h, p); h.assertTrue(ItemStack.matches(stack, f.guard.getMainHandItem()) && f.station.inventory().getStackInSlot(0).isEmpty(), "Gun changed or duplicated");
        h.assertTrue(ServiceTransactions.transfer(p) == ServiceStatus.RETRY, "Replayed transfer"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void manualPolicyNeverPlansEquipment(GameTestHelper h) {
        try (var f = fixture(h)) { GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.MANUAL); f.station.inventory().setStackInSlot(0, gun("ak47"));
        h.assertTrue(plan(f) == null && f.guard.getMainHandItem().isEmpty(), "Manual guard was equipped"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void uniqueStockCannotBeReservedByTwoGuards(GameTestHelper h) {
        try (var f = fixture(h)) { ready(h, f); f.station.inventory().setStackInSlot(0, gun("ak47")); var first = plan(f);
        h.assertTrue(QuartermasterServiceRuntime.reserve(first), "First reservation failed");
        Guard other = GuardEntityType.GUARD.get().create(h.getLevel()); other.setOwnerId(f.owner.getUUID()); other.setPos(f.guard.position());
        var state = GuardEquipmentServiceAccess.get(other); state.policy(GuardServiceState.Policy.PROFILE); state.profile(ResourceLocation.parse("villagerguardgunners:rifleman"));
        h.assertTrue(ServicePlanner.plan(other, f.station, QuartermasterServiceRuntime.sources(f.station)) == null, "Second guard reserved unique gun");
        QuartermasterServiceRuntime.cancel(f.guard, ServiceStatus.RETRY); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void protectedEquipmentRequiresExplicitReplacement(GameTestHelper h) {
        try (var f = fixture(h)) { ItemStack old = new ItemStack(Items.DIAMOND_SWORD); old.setHoverName(net.minecraft.network.chat.Component.literal("Player keepsake")); f.guard.setItemSlot(EquipmentSlot.MAINHAND, old.copy());
        f.station.inventory().setStackInSlot(0, gun("ak47")); h.assertTrue(plan(f) == null, "Protected gear replaced");
        GuardEquipmentServiceAccess.get(f.guard).replaceExisting(true); supplied(h, plan(f));
        h.assertTrue(ItemStack.matches(old, f.station.inventory().getStackInSlot(27)), "Exact old gear not returned"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void fullReturnsNeverRemoveOldEquipment(GameTestHelper h) {
        try (var f = fixture(h)) { f.guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); GuardEquipmentServiceAccess.get(f.guard).replaceExisting(true);
        f.station.inventory().setStackInSlot(0, gun("ak47")); for (int i = 27; i < 36; i++) f.station.inventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        h.assertTrue(plan(f) == null && f.guard.getMainHandItem().is(Items.IRON_SWORD) && !f.station.inventory().getStackInSlot(0).isEmpty(), "Full returns lost old gear"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void changedStockAndDestinationInvalidatePlan(GameTestHelper h) {
        try (var f = fixture(h)) { f.station.inventory().setStackInSlot(0, gun("ak47")); var p = plan(f);
        f.station.inventory().getStackInSlot(0).getOrCreateTag().putInt("Changed", 1); h.assertTrue(!ServiceTransactions.valid(p), "Changed source accepted");
        var next = plan(f); f.guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); h.assertTrue(!ServiceTransactions.valid(next), "Changed destination accepted"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void absentChildAndProfessionLossStopService(GameTestHelper h) {
        try (var f = fixture(h)) { ready(h, f); f.operator.setAge(-100); h.assertTrue(ServiceEligibility.station(f.station) == ServiceStatus.OPERATOR_UNAVAILABLE, "Child operator accepted");
        f.operator.setAge(0); f.operator.setVillagerData(f.operator.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE)); h.assertTrue(ServiceEligibility.station(f.station) == ServiceStatus.OPERATOR_UNAVAILABLE, "Wrong profession accepted");
        f.station.operator = null; h.assertTrue(ServiceEligibility.station(f.station) == ServiceStatus.OPERATOR_UNAVAILABLE, "Absent operator accepted"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void workHoursAndNativeBoltSuspendTransfers(GameTestHelper h) {
        try (var f = fixture(h)) { f.station.inventory().setStackInSlot(0, gun("ak47")); var p = plan(f);
        h.getLevel().setDayTime(12000); h.assertTrue(!ServiceTransactions.valid(p), "Night service accepted"); h.getLevel().setDayTime(3000);
        f.guard.setItemSlot(EquipmentSlot.MAINHAND, gun("m4a1")); IGunOperator.fromLivingEntity(f.guard).getDataHolder().isBolting = true;
        h.assertTrue(ServiceEligibility.guard(f.guard) == ServiceStatus.NATIVE_ACTION, "Native bolt ignored"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void policyAndStationAccessChangesCancelReservations(GameTestHelper h) {
        try (var f = fixture(h)) { f.station.inventory().setStackInSlot(0, gun("ak47")); var p = plan(f);
        GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.RESTOCK); h.assertTrue(!ServiceTransactions.valid(p), "Changed policy accepted");
        GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.PROFILE); f.station.changeSetting(f.owner, 3); h.assertTrue(!ServiceTransactions.valid(p), "Changed access accepted"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void ammoAndMedicalTopUpsConsumeOnlyPhysicalStock(GameTestHelper h) {
        try (var f = fixture(h)) { f.guard.setItemSlot(EquipmentSlot.MAINHAND, gun("ak47")); GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.RESTOCK);
        var ammo = AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(23).build(); f.station.inventory().setStackInSlot(0, ammo.copy());
        supplied(h, plan(f)); h.assertTrue(GuardAmmoAccess.getAmmoHandler(f.guard).getStackInSlot(0).getCount() == 23 && f.station.inventory().getStackInSlot(0).isEmpty(), "Ammo conservation failed");
        GuardMedicalAccess.get(f.guard).inventory().setStackInSlot(0, new ItemStack(ModItems.BANDAGE.get(), 1)); f.station.inventory().setStackInSlot(1, new ItemStack(ModItems.BANDAGE.get(), 7));
        supplied(h, plan(f)); h.assertTrue(GuardMedicalAccess.get(f.guard).inventory().getStackInSlot(0).getCount() == 8 && f.station.inventory().getStackInSlot(1).isEmpty(), "Medical conservation failed"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void interruptedOwnershipLocksCachedTransport(GameTestHelper h) {
        try (var f = fixture(h)) { var stock = gun("ak47"); f.station.inventory().setStackInSlot(0, stock.copy());
        var receipt = GuardEquipmentServiceAccess.get(f.guard).receipt;
        GuardAmmoAccess.getAmmoHandler(f.guard).setStackInSlot(0, AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(3).build());
        var cached = f.guard.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
        h.assertTrue(ServiceJournal.get(h.getLevel().getServer()).begin(f.station.serviceReceipt(), receipt), "Intent failed");
        h.assertTrue(!ServiceTransactions.valid(plan(f)), "Old owner accepted after intent");
        h.assertTrue(cached.getStackInSlot(6).isEmpty() && cached.extractItem(6, 3, false).isEmpty()
                && GuardAmmoAccess.getAmmoHandler(f.guard).getStackInSlot(0).getCount() == 3, "Cached transport bypassed quarantine");
        h.assertTrue(f.guard.getMainHandItem().isEmpty() && ItemStack.matches(stock, f.station.inventory().getStackInSlot(0)), "Intent moved physical stock"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void guardPolicyAndReceiptRoundTrip(GameTestHelper h) {
        try (var f = fixture(h)) { GuardEquipmentServiceAccess.get(f.guard).replaceExisting(true); CompoundTag saved = new CompoundTag(); f.guard.saveWithoutId(saved);
        Guard restored = GuardEntityType.GUARD.get().create(h.getLevel()); restored.load(saved); var state = GuardEquipmentServiceAccess.get(restored);
        h.assertTrue(state.readable() && state.policy() == GuardServiceState.Policy.PROFILE && state.replaceExisting(), "Service policy failed entity round trip"); h.succeed();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void configuredSidearmUsesPhysicalSlotAndKeepsPrimary(GameTestHelper h) {
        try (var f = fixture(h)) { ItemStack primary = gun("ak47"); f.guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        ItemStack sidearm = gun("glock_17"); f.station.inventory().setStackInSlot(0, sidearm.copy());
        var p = plan(f); h.assertTrue(p != null && p.targetSlot() == 1, "Sidearm was not selected"); supplied(h, p);
        h.assertTrue(ItemStack.matches(primary, f.guard.getMainHandItem()) && ItemStack.matches(sidearm, GuardSidearmAccess.getConfiguredSidearmInventory(f.guard).getStackInSlot(0)), "Sidearm swap changed ownership"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void acceptableGunDoesNotChurnEquivalentStock(GameTestHelper h) {
        try (var f = fixture(h)) { ItemStack primary = gun("ak47"); f.guard.setItemSlot(EquipmentSlot.MAINHAND, primary.copy());
        GuardEquipmentServiceAccess.get(f.guard).replaceExisting(true); f.station.inventory().setStackInSlot(0, gun("m4a1"));
        h.assertTrue(plan(f) == null && ItemStack.matches(primary, f.guard.getMainHandItem()), "Equivalent gun churned"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void failedSourceMutationRollsBackAndQuarantines(GameTestHelper h) {
        try (var f = fixture(h)) { ItemStack stock = gun("ak47"); f.station.inventory().setStackInSlot(0, stock.copy()); var base = plan(f);
        var faulty = new net.minecraftforge.items.ItemStackHandler(27) {
            boolean fail = true;
            @Override public ItemStack getStackInSlot(int slot) { return f.station.inventory().getStackInSlot(slot); }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                f.station.inventory().setStackInSlot(slot, stack);
                if (fail) { fail = false; throw new IllegalStateException("Injected post-mutation failure"); }
            }
        };
        var source = new ServiceSource(f.station, faulty, f.station.serviceReceipt(), 27);
        var p = new ServicePlan(base.operation(), base.guard(), base.station(), base.placement(), source, base.sourceSlot(), base.targetSlot(), base.returnSlot(), base.stockBefore(), base.replacement(), base.guardBefore(), base.profileRevision(), base.policy(), base.profile(), base.replaceExisting(), base.sourceRevision(), base.guardRevision(), base.expiresAt());
        h.assertTrue(ServiceTransactions.transfer(p) == ServiceStatus.RECOVERY_REQUIRED, "Failure escaped recovery");
        h.assertTrue(f.guard.getMainHandItem().isEmpty() && ItemStack.matches(stock, f.station.inventory().getStackInSlot(0)), "Rollback failed item conservation");
        h.assertTrue(f.station.serviceReceipt().locked() && GuardEquipmentServiceAccess.get(f.guard).receipt.locked(), "Failed owners not quarantined"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void linkedArmorySuppliesAmmoAndUnlinkCancelsPlan(GameTestHelper h) {
        try (var f = fixture(h)) { var pos = f.station.getBlockPos().east(2); h.getLevel().setBlock(pos, ModBlocks.ARMORY.get().defaultBlockState(), 3);
        var armory = (com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity) h.getLevel().getBlockEntity(pos);
        QuartermasterServiceRuntime.loaded(armory); f.station.changeSetting(f.owner, 4);
        h.assertTrue(f.station.linkedArmories().contains(pos), "Nearby armory not linked");
        f.guard.setItemSlot(EquipmentSlot.MAINHAND, gun("ak47")); GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.RESTOCK);
        armory.getInventory().setStackInSlot(0, AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(9).build());
        var p = plan(f); h.assertTrue(p != null && p.source().block() == armory, "Linked stock not selected");
        f.station.changeSetting(f.owner, 5); h.assertTrue(!ServiceTransactions.valid(p), "Unlinked source remained valid");
        f.station.changeSetting(f.owner, 4); supplied(h, plan(f)); h.assertTrue(armory.getInventory().getStackInSlot(0).isEmpty() && GuardAmmoAccess.getAmmoHandler(f.guard).getStackInSlot(0).getCount() == 9, "Linked transfer lost ammo"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 400, batch = "quartermaster_travel")
    public static void optedInGuardTravelsAndResumesAfterService(GameTestHelper h) {
        var f = fixture(h); f.station.inventory().setStackInSlot(0, gun("ak47"));
        var start = f.station.getBlockPos().east(10);
        for (int x = -2; x <= 12; x++) for (int z = -2; z <= 2; z++) {
            var floor = f.station.getBlockPos().offset(x, -1, z); h.getLevel().setBlock(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 3; y++) if (!floor.above(y).equals(f.station.getBlockPos())) h.getLevel().setBlock(floor.above(y), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        f.guard.targetSelector.removeAllGoals(goal -> true); f.guard.setTarget(null); f.guard.setInvulnerable(true);
        f.guard.setPos(start.getX() + .5, start.getY(), start.getZ() + .5); f.guard.setNoAi(false); h.getLevel().addFreshEntity(f.guard);
        h.onEachTick(() -> {
            f.guard.setTarget(null); f.guard.setLastHurtByMob(null);
            f.operator.setPos(f.owner.position()); f.operator.setInvulnerable(true);
            f.operator.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
            f.operator.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            f.operator.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(h.getLevel().dimension(), f.station.getBlockPos()));
            f.operator.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.WORK);
        });
        h.succeedWhen(() -> {
            h.assertTrue(!f.guard.getMainHandItem().isEmpty(), "Guard did not travel to service: " + GuardEquipmentServiceAccess.get(f.guard).status + " position=" + f.guard.position() + " station=" + f.station.getBlockPos() + " navigationDone=" + f.guard.getNavigation().isDone());
            h.assertTrue(f.station.inventory().getStackInSlot(0).isEmpty(), "Travel issued a copy");
            h.assertTrue(QuartermasterServiceRuntime.plan(f.guard) == null, "Service reservation not released");
            f.close(); f.guard.discard();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void reloadInvalidatesReservationsAndRejectsMalformedRegistry(GameTestHelper h) {
        try (var f = fixture(h)) { f.station.inventory().setStackInSlot(0, gun("ak47")); var p = plan(f); var profiles = EquipmentProfiles.snapshot();
        long before = EquipmentProfiles.revision(); EquipmentProfileTestAccess.apply(java.util.Map.of(), "Injected malformed reload");
        h.assertTrue(EquipmentProfiles.revision() == before && EquipmentProfiles.snapshot().equals(profiles), "Bad reload replaced profiles");
        EquipmentProfileTestAccess.apply(profiles, null); h.assertTrue(!ServiceTransactions.valid(p), "Reload retained stale reservation"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void profileControlsCycleAndRoleChangesNeverOptIn(GameTestHelper h) {
        try (var f = fixture(h)) { var profiles = EquipmentProfiles.snapshot(); var state = GuardEquipmentServiceAccess.get(f.guard);
        state.policy(GuardServiceState.Policy.MANUAL); GuardRoleAccess.setRoleId(f.guard, ResourceLocation.parse("villagerguardgunners:generalist"));
        h.assertTrue(state.policy() == GuardServiceState.Policy.MANUAL, "Role assignment opted guard into service"); var changed = new java.util.HashMap<>(profiles);
        var extra = ResourceLocation.parse("villagerguardgunners:zz_fixture"); changed.put(extra, profiles.values().iterator().next());
        try {
            EquipmentProfileTestAccess.apply(changed, null);
            h.assertTrue(ServiceControls.change(f.owner, f.guard, ServiceControls.PROFILE_BUTTON) && state.profile().equals(extra), "Profile cycle failed");
            h.assertTrue(state.policy() == GuardServiceState.Policy.MANUAL, "Profile selection opted guard into service");
            h.assertTrue(!ServiceControls.change(new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Unauthorized")), f.guard, ServiceControls.POLICY_BUTTON), "Unauthorized service change");
        } finally { EquipmentProfileTestAccess.apply(profiles, null); }
        h.succeed();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void replacementArmoryDoesNotInheritRemovedOwnerReceipt(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(8, 2, 8)); h.getLevel().setBlock(pos, ModBlocks.ARMORY.get().defaultBlockState(), 3);
        var first = (com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity) h.getLevel().getBlockEntity(pos);
        var journal = ServiceJournal.get(h.getLevel().getServer()); var old = first.serviceReceipt();
        h.assertTrue(journal.begin(old, new ServiceReceipt()), "Could not write old ownership"); old.revision(1);
        h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(pos, ModBlocks.ARMORY.get().defaultBlockState(), 3);
        var replacement = (com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(!replacement.serviceReceipt().id().equals(old.id()) && journal.verify(replacement.serviceReceipt()), "Fresh Armory inherited old recovery lock"); h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void replacementCannotNestPortableContainersInReturns(GameTestHelper h) {
        try (var f = fixture(h)) {
            f.guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHULKER_BOX));
            GuardEquipmentServiceAccess.get(f.guard).replaceExisting(true); f.station.inventory().setStackInSlot(0, gun("ak47"));
            h.assertTrue(plan(f) == null && f.guard.getMainHandItem().is(Items.SHULKER_BOX), "Equipment service nested a forbidden container"); h.succeed();
        }
    }
    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, setupTicks = 50)
    public static void stationSuppliesMedicWhoRevivesOriginalArmedPatient(GameTestHelper h) {
        boolean downed = ModConfigs.GUARD_GUNNERS.medical.downed.get();
        boolean transport = ModConfigs.GUARD_GUNNERS.medical.transport.get();
        try (var f = fixture(h)) {
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);
            ModConfigs.GUARD_GUNNERS.medical.transport.accept(false);
            f.guard.setNoAi(false);
            GuardMedicAccess.get(f.guard).medic(true);
            GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.PROFILE);
            f.station.inventory().setStackInSlot(0, new ItemStack(ModItems.BANDAGE.get(), 2));
            supplied(h, plan(f));
            Guard patient = GuardEntityType.GUARD.get().create(h.getLevel());
            try {
                patient.setOwnerId(f.owner.getUUID());
                patient.setPos(f.guard.position().add(1, 0, 0));
                var originalGun = gun("ak47");
                patient.setItemSlot(EquipmentSlot.MAINHAND, originalGun.copy());
                var identity = patient.getUUID();
                patient.hurt(h.getLevel().damageSources().generic(), 1000);
                h.assertTrue(com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.casualty(patient), "Lethal injury was not downed");
                for (int dose = 0; dose < 2; dose++) {
                    GuardMedicalAccess.get(patient).readyAt(0);
                    h.assertTrue(com.gamergaming.villagerguardgunners.medical.MedicRuntime.reserve(f.guard, patient), "Medic could not reserve casualty for dose " + dose);
                    var lease = com.gamergaming.villagerguardgunners.medical.MedicRuntime.lease(f.guard);
                    com.gamergaming.villagerguardgunners.medical.MedicRuntime.treat(lease);
                    h.assertTrue(lease.preparedAt >= 0, "Channel did not start");
                    lease.preparedAt -= 40;
                    com.gamergaming.villagerguardgunners.medical.MedicRuntime.treat(lease);
                }
                h.assertTrue(GuardLifeAccess.get(patient).stage() == com.gamergaming.villagerguardgunners.medical.GuardLifeState.Stage.RECOVERING,
                        "Two physical doses did not revive patient");
                h.assertTrue(identity.equals(patient.getUUID()) && ItemStack.matches(originalGun, patient.getMainHandItem()), "Revival changed identity or gun");
                h.assertTrue(f.station.inventory().getStackInSlot(0).isEmpty() && GuardMedicalAccess.get(f.guard).inventory().getStackInSlot(0).isEmpty(), "Station/medic dose conservation failed");
                CompoundTag saved = new CompoundTag(); patient.saveWithoutId(saved);
                Guard restored = GuardEntityType.GUARD.get().create(h.getLevel());
                try {
                    restored.load(saved);
                    h.assertTrue(identity.equals(restored.getUUID()) && ItemStack.matches(originalGun, restored.getMainHandItem())
                            && GuardLifeAccess.get(restored).stage() == GuardLifeAccess.get(patient).stage(), "Combined recovery save lost state");
                } finally { restored.discard(); }
            } finally {
                com.gamergaming.villagerguardgunners.medical.MedicRuntime.cancel(f.guard, com.gamergaming.villagerguardgunners.medical.MedicState.Status.INTERRUPTED);
                patient.discard(); f.guard.discard();
            }
        } finally {
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(downed);
            ModConfigs.GUARD_GUNNERS.medical.transport.accept(transport);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void operatorLossInvalidatesMedicalRestockWithoutMovingDoses(GameTestHelper h) {
        try (var f = fixture(h)) {
            GuardMedicAccess.get(f.guard).medic(true);
            GuardEquipmentServiceAccess.get(f.guard).policy(GuardServiceState.Policy.PROFILE);
            var doses = new ItemStack(ModItems.BANDAGE.get(), 7);
            doses.getOrCreateTag().putString("Batch", "release-validation");
            f.station.inventory().setStackInSlot(0, doses.copy());
            var pending = plan(f);
            h.assertTrue(pending != null && QuartermasterServiceRuntime.reserve(pending), "No pending medical restock");
            f.operator.setVillagerData(f.operator.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
            h.assertTrue(ServiceTransactions.transfer(pending) != ServiceStatus.SUPPLIED, "Former operator supplied medic");
            QuartermasterServiceRuntime.cancel(f.guard, ServiceStatus.RETRY);
            h.assertTrue(ItemStack.matches(doses, f.station.inventory().getStackInSlot(0)) && GuardMedicalAccess.get(f.guard).empty(), "Interrupted restock moved doses");
            f.operator.setVillagerData(f.operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
            supplied(h, plan(f));
            h.assertTrue(ItemStack.matches(doses, GuardMedicalAccess.get(f.guard).inventory().getStackInSlot(0)) && f.station.inventory().getStackInSlot(0).isEmpty(), "Restored service lost exact supplies");
        }
        h.succeed();
    }

}
