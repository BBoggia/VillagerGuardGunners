package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModItems;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class TreatmentReloadGameTests {
    @GameTest(templateNamespace = "minecraft", template = "villagerguardgunners/gametest_arena")
    public static void badReloadRetainsRegistryAndRemovedProfilesPreserveSupplies(GameTestHelper h) {
        var registry = new TreatmentProfiles(); var original = TreatmentProfiles.snapshot();
        var manager = h.getLevel().getServer().getResourceManager(); var profiler = InactiveProfiler.INSTANCE;
        var bandage = new ItemStack(ModItems.BANDAGE.get(), 7);
        h.assertTrue(TreatmentProfiles.resolve(bandage) != null, "Default bandage profile missing");
        var guard = GuardEntityType.GUARD.get().create(h.getLevel()); var pouch = new MedicalStorage(guard);
        pouch.inventory().setStackInSlot(2, bandage.copy()); var saved = pouch.save();
        try {
            registry.apply(new TreatmentProfiles.Prepared(Map.of(), "bad JSON"), manager, profiler);
            h.assertTrue(TreatmentProfiles.snapshot().equals(original), "Malformed reload replaced active profiles");
            var invalid = new TreatmentProfile(ResourceLocation.withDefaultNamespace("air"), null, 4, 40, null);
            registry.apply(new TreatmentProfiles.Prepared(Map.of(ResourceLocation.withDefaultNamespace("bad"), invalid), null), manager, profiler);
            h.assertTrue(TreatmentProfiles.snapshot().equals(original), "Invalid item replaced active profiles");
            var existing = original.values().iterator().next();
            registry.apply(new TreatmentProfiles.Prepared(Map.of(ResourceLocation.withDefaultNamespace("a"), existing, ResourceLocation.withDefaultNamespace("b"), existing), null), manager, profiler);
            h.assertTrue(TreatmentProfiles.snapshot().equals(original), "Duplicate selector published");
            registry.apply(new TreatmentProfiles.Prepared(Map.of(), null), manager, profiler);
            h.assertTrue(TreatmentProfiles.resolve(bandage) == null, "Removed profile still active");
            pouch.load(saved);
            h.assertTrue(pouch.readable() && pouch.inventory().extractItem(2, 7, false).getCount() == 7, "Removed profile trapped or deleted supplies");
        } finally { registry.apply(new TreatmentProfiles.Prepared(original, null), manager, profiler); }
        h.succeed();
    }
}
