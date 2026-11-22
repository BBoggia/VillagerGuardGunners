package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class CareRegressionGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";

    @GameTest(templateNamespace = "minecraft", template = ARENA)
    public static void crowdedEarlyCellsCannotHideLaterPatients(GameTestHelper h) throws Exception { search(h, false); }

    @GameTest(templateNamespace = "minecraft", template = ARENA)
    public static void crowdedSingleCellCannotHideLaterPatients(GameTestHelper h) throws Exception { search(h, true); }

    private static void search(GameTestHelper h, boolean sameCell) throws Exception {
        List<Guard> actors = new ArrayList<>();
        try {
            Guard source = actor(h, actors, 40008, 40008);
            for (int i = 0; i < 65; i++) actor(h, actors, sameCell ? 40008 : 39984, sameCell ? 40008 : 39984);
            Guard patient = actor(h, actors, 40009, 40008); patient.setHealth(2);
            // Refresh the injury index after changing health.
            MedicRuntime.joined(new EntityJoinLevelEvent(patient, h.getLevel()));
            var method = MedicRuntime.class.getDeclaredMethod("nearby", Guard.class, boolean.class);
            method.setAccessible(true);
            boolean found = false;
            for (int attempt = 0; attempt < 30; attempt++) {
                var result = (List<?>) method.invoke(null, source, true);
                h.assertTrue(result.size() <= MedicRuntime.CANDIDATE_BUDGET, "Search exceeded candidate budget");
                found |= result.contains(patient);
            }
            h.assertTrue(found, "Healthy guards permanently hid a nearby patient beyond the scan budget");
        } finally {
            for (Guard actor : actors) { MedicRuntime.left(new EntityLeaveLevelEvent(actor, h.getLevel())); actor.discard(); }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA)
    public static void medicSearchersKeepIndependentProgressAndDiscardDistantCursors(GameTestHelper h) throws Exception {
        List<Guard> actors = new ArrayList<>();
        try {
            Guard first = actor(h, actors, 40008, 40008), second = actor(h, actors, 40008, 40008);
            for (int i = 0; i < 125; i++) actor(h, actors, 40008, 40008);
            Guard patient = actor(h, actors, 40009, 40008); patient.setHealth(2);
            MedicRuntime.joined(new EntityJoinLevelEvent(patient, h.getLevel()));
            var method = MedicRuntime.class.getDeclaredMethod("nearby", Guard.class, boolean.class); method.setAccessible(true);
            boolean firstFound = false, secondFound = false;
            for (int i = 0; i < 30; i++) {
                firstFound |= ((List<?>) method.invoke(null, first, true)).contains(patient);
                secondFound |= ((List<?>) method.invoke(null, second, true)).contains(patient);
            }
            h.assertTrue(firstFound && secondFound, "One medic's queries starved another medic's search");
            first.setPos(50008, 70, 50008); method.invoke(null, first, true);
            h.assertTrue(com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(first).searchAfter.isEmpty(), "Moving guard retained distant search cursors");
        } finally {
            for (Guard actor : actors) { MedicRuntime.left(new EntityLeaveLevelEvent(actor, h.getLevel())); actor.discard(); }
        }
        h.succeed();
    }

    private static Guard actor(GameTestHelper h, List<Guard> actors, double x, double z) {
        Guard guard = GuardEntityType.GUARD.get().create(h.getLevel());
        guard.setUUID(new UUID(40000, actors.size() + 1)); guard.setPos(x, 70, z); actors.add(guard);
        MedicRuntime.joined(new EntityJoinLevelEvent(guard, h.getLevel())); return guard;
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA)
    public static void downingImmediatelyRemovesBuildingDutyCandidate(GameTestHelper h) {
        boolean previous = ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get();
        ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(true);
        try (var f = new CareFixture(h)) {
            int before = VillageDefenseStaffingRuntime.diagnostics(h.getLevel()).loadedGuards();
            VillageDefenseStaffingRuntime.synchronizeGuard(f.guard);
            h.assertTrue(VillageDefenseStaffingRuntime.diagnostics(h.getLevel()).loadedGuards() == before + 1, "Active guard was not registered");
            f.down(h);
            h.assertTrue(VillageDefenseStaffingRuntime.diagnostics(h.getLevel()).loadedGuards() == before, "Downed guard retained its duty roster entry");
            VillageDefenseStaffingRuntime.synchronizeGuard(f.guard);
            h.assertTrue(VillageDefenseStaffingRuntime.diagnostics(h.getLevel()).loadedGuards() == before, "Casualty was readmitted to duty");
        } finally { ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(previous); }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA)
    public static void priorHealingCooldownDoesNotBlockFirstAid(GameTestHelper h) {
        try (var f = new CareFixture(h)) {
            GuardMedicalAccess.get(f.guard).readyAt(h.getLevel().getGameTime() + 1200);
            f.down(h);
            h.assertTrue(f.treat() == MedicalTreatment.Result.HEALED, "Earlier healing cooldown prevented emergency stabilization");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA)
    public static void longestValidCooldownLeavesTimeForSecondDose(GameTestHelper h) {
        var original = TreatmentProfiles.snapshot(); var registry = new TreatmentProfiles();
        var manager = h.getLevel().getServer().getResourceManager();
        try (var f = new CareFixture(h)) {
            var profile = new TreatmentProfile(ResourceLocation.parse("villagerguardgunners:bandage"), null, 4, 1200, null);
            registry.apply(new TreatmentProfiles.Prepared(Map.of(ResourceLocation.parse("villagerguardgunners:slow"), profile), null), manager, InactiveProfiler.INSTANCE);
            h.assertTrue(TreatmentProfiles.resolve(f.player.getMainHandItem()).cooldownTicks() == 1200, "Slow profile did not load");
            f.down(h); h.assertTrue(f.treat() == MedicalTreatment.Result.HEALED, "First dose failed");
            long cooldown = GuardMedicalAccess.get(f.guard).readyAt() - h.getLevel().getGameTime();
            h.assertTrue(GuardLifeAccess.get(f.guard).remaining() >= cooldown + 1200, "Stabilization expires before a full second-dose window after cooldown");
        } finally { registry.apply(new TreatmentProfiles.Prepared(original, null), manager, InactiveProfiler.INSTANCE); }
        h.succeed();
    }

    private static final class CareFixture implements AutoCloseable {
        final Guard guard; final FakePlayer player;
        final boolean previous = ModConfigs.GUARD_GUNNERS.medical.downed.get();
        CareFixture(GameTestHelper h) {
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);
            guard = GuardEntityType.GUARD.get().create(h.getLevel());
            var pos = h.absolutePos(new BlockPos(8, 2, 8)); guard.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "CareRegression"));
            player.setPos(guard.position().add(1, 0, 0)); player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1000));
            guard.setOwnerId(player.getUUID()); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BANDAGE.get(), 4));
        }
        void down(GameTestHelper h) {
            guard.setHealth(2); guard.invulnerableTime = 0; guard.hurt(h.getLevel().damageSources().generic(), 100);
            h.assertTrue(GuardLifeRuntime.casualty(guard), "Fixture did not become downed");
        }
        MedicalTreatment.Result treat() { return MedicalTreatment.fromHand(player, guard, InteractionHand.MAIN_HAND); }
        public void close() {
            VillageDefenseStaffingRuntime.onEntityLeave(new EntityLeaveLevelEvent(guard, guard.level()));
            MedicRuntime.left(new EntityLeaveLevelEvent(guard, guard.level())); guard.discard();
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(previous);
        }
    }
}
