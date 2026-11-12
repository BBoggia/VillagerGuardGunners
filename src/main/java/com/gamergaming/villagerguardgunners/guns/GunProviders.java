package com.gamergaming.villagerguardgunners.guns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.world.item.ItemStack;

/**
 * Registry of the gun mods available in this installation.
 *
 * <p>Providers register during mod construction and are never removed.
 * Lookups run in registration order, so resolution is deterministic.</p>
 */
public final class GunProviders {
    private static final Registry REGISTRY = new Registry();

    private GunProviders() {
    }

    public static void register(GunProvider provider) {
        REGISTRY.register(provider);
    }

    public static List<GunProvider> all() {
        return REGISTRY.all();
    }

    public static Optional<GunProvider> byId(String id) {
        return REGISTRY.byId(id);
    }

    /** The provider that owns the stack as a gun, if any. */
    public static Optional<GunProvider> find(ItemStack stack) {
        return REGISTRY.find(stack);
    }

    public static boolean isGun(ItemStack stack) {
        return REGISTRY.find(stack).isPresent();
    }

    public static boolean isAmmo(ItemStack stack) {
        return REGISTRY.isAmmo(stack);
    }

    /** True for single rounds from any provider, never for an ammo container. */
    public static boolean isLooseAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (GunProvider provider : all()) {
            if (provider.isLooseAmmo(stack)) {
                return true;
            }
        }
        return false;
    }

    /** The provider that owns the stack as loose ammo or an ammo container, if any. */
    public static Optional<GunProvider> findAmmoOwner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        for (GunProvider provider : all()) {
            if (provider.isAmmoItem(stack)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /** Provider whose driver serves a guard that holds no recognized gun. */
    public static GunProvider fallback() {
        return REGISTRY.fallback();
    }

    /** Instance form of the registry, separate from the static facade so its rules are testable. */
    static final class Registry {
        private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");

        // Replaced, never mutated: mod construction may run on a loader worker thread.
        private volatile List<GunProvider> providers = List.of();

        synchronized void register(GunProvider provider) {
            Objects.requireNonNull(provider, "provider");
            String id = provider.id();
            if (id == null || !VALID_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid gun provider id: " + id);
            }
            for (GunProvider existing : providers) {
                if (existing.id().equals(id)) {
                    throw new IllegalStateException("Gun provider already registered: " + id);
                }
            }
            List<GunProvider> updated = new ArrayList<>(providers);
            updated.add(provider);
            providers = List.copyOf(updated);
        }

        List<GunProvider> all() {
            return providers;
        }

        Optional<GunProvider> byId(String id) {
            for (GunProvider provider : providers) {
                if (provider.id().equals(id)) {
                    return Optional.of(provider);
                }
            }
            return Optional.empty();
        }

        Optional<GunProvider> find(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return Optional.empty();
            }
            for (GunProvider provider : providers) {
                if (provider.isGun(stack)) {
                    return Optional.of(provider);
                }
            }
            return Optional.empty();
        }

        boolean isAmmo(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            for (GunProvider provider : providers) {
                if (provider.isAmmoItem(stack)) {
                    return true;
                }
            }
            return false;
        }

        GunProvider fallback() {
            List<GunProvider> current = providers;
            if (current.isEmpty()) {
                throw new IllegalStateException("No gun provider is registered");
            }
            return current.get(0);
        }
    }
}
