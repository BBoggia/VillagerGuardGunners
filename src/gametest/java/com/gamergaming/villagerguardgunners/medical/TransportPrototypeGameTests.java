package com.gamergaming.villagerguardgunners.medical;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class TransportPrototypeGameTests {
    @GameTest(templateNamespace="minecraft", template="villagerguardgunners/gametest_arena")
    public static void nativePassengerKeepsOneOriginalInventoryThroughSaveAndCarrierRemoval(GameTestHelper h) {
        Guard carrier = GuardEntityType.GUARD.get().create(h.getLevel());
        Guard patient = GuardEntityType.GUARD.get().create(h.getLevel());
        net.minecraft.world.entity.Entity loaded = null;
        try {
            patient.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertTrue(patient.startRiding(carrier, true), "Native guard passenger was rejected");
            h.assertTrue(carrier.getControllingPassenger() == patient, "Ordinary passenger controls were changed");
            carrier.setPos(10, 70, 10); carrier.positionRider(patient);
            h.assertTrue(patient.getVehicle() == carrier && patient.distanceToSqr(carrier) < 16, "Passenger did not follow carrier");
            var root = new CompoundTag(); h.assertTrue(carrier.save(root), "Carrier could not save");
            h.assertTrue(root.getList("Passengers", 10).size() == 1, "Passenger was not saved exactly once");
            loaded = EntityType.loadEntityRecursive(root, h.getLevel(), entity -> entity);
            h.assertTrue(loaded != null && loaded.getPassengers().size() == 1, "Nested passenger did not restore");
            Guard restored = (Guard) loaded.getFirstPassenger();
            h.assertTrue(restored.getUUID().equals(patient.getUUID()) && restored.getMainHandItem().is(Items.IRON_SWORD), "Passenger identity or inventory changed");
            carrier.discard();
            h.assertTrue(patient.isAlive() && !patient.isRemoved() && !patient.isPassenger(), "Carrier removal removed or trapped its passenger");
        } finally {
            if (loaded != null) { for (var passenger : java.util.List.copyOf(loaded.getPassengers())) passenger.discard(); loaded.discard(); }
            carrier.discard(); patient.discard();
        }
        h.succeed();
    }
}
