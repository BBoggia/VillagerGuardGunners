package com.gamergaming.villagerguardgunners.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import tallestegg.guardvillagers.entities.Guard;

final class DriverSwitchTest {
    @Test
    void fallbackDriverExistsFromConstruction() {
        Fixture fixture = new Fixture();

        DriverSwitch drivers = fixture.newSwitch();

        assertSame(fixture.driver("first"), drivers.current());
        assertEquals(List.of("create:first"), fixture.events);
    }

    @Test
    void unclaimedStackDeactivatesButRetainsTheCurrentDriver() {
        Fixture fixture = new Fixture();
        DriverSwitch drivers = fixture.newSwitch();
        drivers.select(Optional.of(fixture.second));
        fixture.events.clear();

        WeaponDriver selected = drivers.select(Optional.empty());

        assertSame(fixture.driver("second"), selected);
        assertEquals(List.of("deactivate:second"), fixture.events);
    }

    @Test
    void sameProviderIsNotStoppedOrRecreated() {
        Fixture fixture = new Fixture();
        DriverSwitch drivers = fixture.newSwitch();
        fixture.events.clear();

        WeaponDriver selected = drivers.select(Optional.of(fixture.first));

        assertSame(fixture.driver("first"), selected);
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void changingProviderStopsTheOutgoingDriverAfterCreatingTheNewOne() {
        Fixture fixture = new Fixture();
        DriverSwitch drivers = fixture.newSwitch();
        fixture.events.clear();

        WeaponDriver selected = drivers.select(Optional.of(fixture.second));

        assertSame(fixture.driver("second"), selected);
        assertSame(selected, drivers.current());
        assertEquals(List.of("create:second", "deactivate:first"), fixture.events);
    }

    @Test
    void eachProviderKeepsOneDriverAcrossRepeatedSwitches() {
        Fixture fixture = new Fixture();
        DriverSwitch drivers = fixture.newSwitch();
        WeaponDriver firstDriver = drivers.current();
        WeaponDriver secondDriver = drivers.select(Optional.of(fixture.second));
        fixture.events.clear();

        assertSame(firstDriver, drivers.select(Optional.of(fixture.first)));
        assertSame(secondDriver, drivers.select(Optional.of(fixture.second)));
        assertEquals(List.of("deactivate:second", "deactivate:first"), fixture.events);
    }

    @Test
    void failedDriverCreationLeavesTheCurrentDriverUntouched() {
        Fixture fixture = new Fixture();
        DriverSwitch drivers = fixture.newSwitch();
        fixture.failCreation = true;
        fixture.events.clear();

        assertThrows(IllegalStateException.class, () -> drivers.select(Optional.of(fixture.second)));

        assertSame(fixture.driver("first"), drivers.current());
        assertTrue(fixture.events.isEmpty(), "The outgoing driver must not be stopped for a switch that failed");
    }

    @Test
    void providerThatCreatesNoDriverIsRejected() {
        FakeProvider provider = new FakeProvider("first");

        assertThrows(NullPointerException.class, () -> new DriverSwitch(provider, ignored -> null));
    }

    @Test
    void createdDriversCanBeFoundByType() {
        Fixture fixture = new Fixture();
        DriverSwitch drivers = fixture.newSwitch();

        assertSame(fixture.driver("first"), drivers.driver(WeaponDriver.class).orElseThrow());
        assertTrue(drivers.driver(GuardWeaponControl.class).isEmpty());
    }

    private static final class Fixture {
        final FakeProvider first = new FakeProvider("first");
        final FakeProvider second = new FakeProvider("second");
        final List<String> events = new ArrayList<>();
        final Map<String, WeaponDriver> created = new HashMap<>();
        boolean failCreation;

        DriverSwitch newSwitch() {
            Function<GunProvider, WeaponDriver> factory = provider -> {
                if (failCreation) {
                    throw new IllegalStateException("creation failed");
                }
                events.add("create:" + provider.id());
                WeaponDriver driver = recordingDriver(provider.id());
                created.put(provider.id(), driver);
                return driver;
            };
            return new DriverSwitch(first, factory);
        }

        WeaponDriver driver(String providerId) {
            return created.get(providerId);
        }

        /** Records the calls the switch makes; every other driver method is unused here. */
        private WeaponDriver recordingDriver(String providerId) {
            return new WeaponDriver() {
                @Override public void deactivate() { events.add("deactivate:" + providerId); }
                @Override public void startAiming() { throw new AssertionError(); }
                @Override public void stopAiming() { throw new AssertionError(); }
                @Override public void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire, BooleanSupplier burstSafetyCheck, boolean aimAtTarget) { throw new AssertionError(); }
                @Override public WeaponReadiness captureReadiness() { throw new AssertionError(); }
                @Override public boolean hasCurrentWeaponData(ItemStack heldStack) { throw new AssertionError(); }
                @Override public boolean isCurrentWeaponDryConfirmed() { throw new AssertionError(); }
                @Override public GuardCombatStatus.WeaponActivity getPlayerFacingActivity() { throw new AssertionError(); }
                @Override public double getPreferredApproachDistance() { throw new AssertionError(); }
                @Override public double getRetreatDistance() { throw new AssertionError(); }
                @Override public int getRequiredVisibleTicks() { throw new AssertionError(); }
                @Override public double getAimToleranceDegrees() { throw new AssertionError(); }
                @Override public double getMinimumFiringRange() { throw new AssertionError(); }
                @Override public float getMaximumFiringRange() { throw new AssertionError(); }
                @Override public boolean hasValidFiringWindow() { throw new AssertionError(); }
                @Override public boolean canFireAtDistanceSquared(double distanceSquared) { throw new AssertionError(); }
                @Override public boolean isCurrentWeaponExplosive() { throw new AssertionError(); }
                @Override public boolean isWeaponAllowedByOrders() { throw new AssertionError(); }
                @Override public Vec3 intentionalAimPoint(LivingEntity target) { throw new AssertionError(); }
                @Override public GuardShotSafety.Result currentShotSafety(LivingEntity target) { throw new AssertionError(); }
                @Override public GuardShotSafety.Result lastShotSafety() { throw new AssertionError(); }
                @Override public LivingEntity lastShotTarget() { throw new AssertionError(); }
                @Override public long lastShotGameTick() { throw new AssertionError(); }
                @Override public boolean isEquipmentServiceSafe() { throw new AssertionError(); }
                @Override public boolean isCoverageMovementSafe() { throw new AssertionError(); }
                @Override public boolean isSidearmSwapSafe() { throw new AssertionError(); }
                @Override public boolean isCurrentWeaponSidearmCompatible() { throw new AssertionError(); }
                @Override public void prepareForSidearmTransition() { throw new AssertionError(); }
                @Override public void refreshAfterSidearmTransaction() { throw new AssertionError(); }
            };
        }
    }

    private record FakeProvider(String id) implements GunProvider {
        @Override
        public boolean isGun(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isAmmoItem(ItemStack stack) {
            return false;
        }

        @Override
        public Optional<GunRef> ref(ItemStack gunStack) {
            return Optional.empty();
        }

        @Override
        public Optional<GunDescriptor> describe(ItemStack gunStack) {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> isExplosive(Guard guard, ItemStack gunStack) {
            return Optional.empty();
        }

        @Override
        public com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility.Assessment assessAmmo(
                ItemStack gunStack, ItemStack candidate) {
            throw new AssertionError("Not an ammo test");
        }

        @Override
        public com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.Snapshot captureAmmo(
                ItemStack gunStack, net.minecraftforge.items.IItemHandler reserveHandler) {
            throw new AssertionError("Not an ammo test");
        }

        @Override
        public WeaponDriver createDriver(Guard guard) {
            throw new AssertionError("The switch must create drivers through its factory");
        }
    }
}
