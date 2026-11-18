package com.gamergaming.villagerguardgunners.guns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import tallestegg.guardvillagers.common.entities.Guard;

final class GunProvidersTest {
    @Test
    void providersAreListedInRegistrationOrder() {
        GunProviders.Registry registry = new GunProviders.Registry();
        GunProvider first = new NamedProvider("first");
        GunProvider second = new NamedProvider("second");

        registry.register(first);
        registry.register(second);

        assertEquals(List.of(first, second), registry.all());
        assertSame(first, registry.fallback());
        assertSame(second, registry.byId("second").orElseThrow());
        assertTrue(registry.byId("missing").isEmpty());
    }

    @Test
    void duplicateProviderIdIsRejected() {
        GunProviders.Registry registry = new GunProviders.Registry();
        registry.register(new NamedProvider("tacz"));

        assertThrows(IllegalStateException.class, () -> registry.register(new NamedProvider("tacz")));
        assertEquals(1, registry.all().size());
    }

    @Test
    void providerIdsMustBeSafeForDataFilesAndSaves() {
        GunProviders.Registry registry = new GunProviders.Registry();

        for (String id : new String[] {"", "TaCZ", "point blank", "cgm:guns", "pb-j"}) {
            assertThrows(IllegalArgumentException.class, () -> registry.register(new NamedProvider(id)), id);
        }
        assertThrows(IllegalArgumentException.class, () -> registry.register(new NamedProvider(null)));
        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void emptyRegistryHasNoFallbackAndClaimsNothing() {
        GunProviders.Registry registry = new GunProviders.Registry();

        // With no gun mod installed, guards get a provider that owns nothing and never fires.
        assertEquals("none", registry.fallback().id());
        assertFalse(registry.fallback().isGun(null));
        assertTrue(registry.all().isEmpty(), "The stand-in is never listed as a registered provider");
        assertTrue(registry.find(null).isEmpty());
        assertFalse(registry.isAmmo(null));
    }

    @Test
    void registeredListCannotBeModifiedByCallers() {
        GunProviders.Registry registry = new GunProviders.Registry();
        registry.register(new NamedProvider("first"));

        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }

    private record NamedProvider(String id) implements GunProvider {
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
                ItemStack gunStack, net.neoforged.neoforge.items.IItemHandler reserveHandler) {
            throw new AssertionError("Not an ammo test");
        }

        @Override
        public WeaponDriver createDriver(Guard guard) {
            throw new AssertionError("Registration must not create drivers");
        }
    }
}
