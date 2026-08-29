package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Central, server-authoritative ally classification used by every guard-gun
 * safety layer. Mod integrations may register an allocation-free provider
 * during common setup for relationships that entity tags and teams cannot
 * express.
 */
public final class GuardAllyProtection {
    public static final TagKey<EntityType<?>> GUARD_ALLIES = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    VillagerGuardGunners.MODID,
                    "guard_allies"));

    private static volatile List<RegisteredProvider> providers = List.of();

    private GuardAllyProtection() {
    }

    /**
     * Registers a positive-only compatibility provider. Providers cannot
     * override explicit exclusions or remove built-in owner/guard protection.
     * Duplicate IDs are rejected so integration ordering cannot change the
     * result silently.
     */
    public static synchronized void registerProvider(
            ResourceLocation id,
            AllyProtectionProvider provider) {
        register(id, provider);
    }

    /**
     * Registers a provider that can later be removed by closing the returned
     * handle. This is intended for bounded integrations and test fixtures;
     * normal mod integrations should use {@link #registerProvider} during
     * common setup and keep their provider for the lifetime of the server.
     */
    public static synchronized ProviderRegistration registerScopedProvider(
            ResourceLocation id,
            AllyProtectionProvider provider) {
        RegisteredProvider registered = register(id, provider);
        return () -> unregister(registered);
    }

    private static RegisteredProvider register(
            ResourceLocation id,
            AllyProtectionProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (providers.stream().anyMatch(registered -> registered.id().equals(id))) {
            throw new IllegalArgumentException("Duplicate guard ally provider: " + id);
        }
        RegisteredProvider registered = new RegisteredProvider(id, provider);
        List<RegisteredProvider> updated = new ArrayList<>(providers);
        updated.add(registered);
        updated.sort(Comparator.comparing(entry -> entry.id().toString()));
        providers = List.copyOf(updated);
        return registered;
    }

    private static synchronized void unregister(RegisteredProvider registered) {
        if (!providers.contains(registered)) {
            return;
        }
        List<RegisteredProvider> updated = new ArrayList<>(providers);
        updated.remove(registered);
        providers = List.copyOf(updated);
    }

    public static boolean isProtected(Guard shooter, Entity entity) {
        return classify(shooter, entity).protectedEntity();
    }

    /** Returns a descriptive, allocation-free reason suitable for diagnostics. */
    public static ProtectionReason classify(Guard shooter, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return ProtectionReason.NOT_LIVING;
        }
        if (living == shooter) {
            return ProtectionReason.SHOOTER;
        }
        if (living instanceof Guard) {
            return ProtectionReason.GUARD;
        }

        UUID ownerId = shooter.getOwnerId();
        if (ownerId != null && ownerId.equals(living.getUUID())) {
            return ProtectionReason.OWNER;
        }

        VillagerGuardGunnersConfig.AllySettings settings =
                ModConfigs.GUARD_GUNNERS.safety.allies;
        ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
        if (entityTypeId != null && settings.excludedEntities.get().contains(entityTypeId)) {
            return ProtectionReason.EXPLICIT_EXCLUSION;
        }
        if (entityTypeId != null
                && settings.additionalProtectedEntities.get().contains(entityTypeId)) {
            return ProtectionReason.EXPLICIT_ADDITION;
        }
        if (living.getType().is(GUARD_ALLIES)) {
            return ProtectionReason.ENTITY_TYPE_TAG;
        }
        if (living instanceof AbstractVillager) {
            return ProtectionReason.VILLAGER;
        }
        if (living instanceof IronGolem) {
            return ProtectionReason.IRON_GOLEM;
        }
        if (settings.protectOwnedEntities.get()
                && living instanceof OwnableEntity ownable
                && ownable.getOwnerUUID() != null) {
            return ProtectionReason.OWNED_ENTITY;
        }
        if (settings.protectAlliedEntities.get()
                && shooter.getTeam() != null
                && shooter.getTeam() == living.getTeam()) {
            return ProtectionReason.SCOREBOARD_TEAM;
        }
        if (settings.protectAlliedEntities.get() && shooter.isAlliedTo(living)) {
            return ProtectionReason.VANILLA_ALLIANCE;
        }

        for (RegisteredProvider registered : providers) {
            try {
                if (registered.provider().isProtected(shooter, living)) {
                    return ProtectionReason.COMPATIBILITY_PROVIDER;
                }
            } catch (RuntimeException exception) {
                // Third-party integration must never crash combat or add hot-
                // path logging. The on-demand diagnostic reason still makes
                // this fail-closed result observable to an administrator.
                return ProtectionReason.PROVIDER_FAILURE;
            }
        }
        return ProtectionReason.NOT_PROTECTED;
    }

    @FunctionalInterface
    public interface AllyProtectionProvider {
        boolean isProtected(Guard shooter, LivingEntity candidate);
    }

    @FunctionalInterface
    public interface ProviderRegistration extends AutoCloseable {
        @Override
        void close();
    }

    public enum ProtectionReason {
        NOT_LIVING(false),
        EXPLICIT_EXCLUSION(false),
        NOT_PROTECTED(false),
        SHOOTER(true),
        GUARD(true),
        OWNER(true),
        EXPLICIT_ADDITION(true),
        ENTITY_TYPE_TAG(true),
        VILLAGER(true),
        IRON_GOLEM(true),
        OWNED_ENTITY(true),
        SCOREBOARD_TEAM(true),
        VANILLA_ALLIANCE(true),
        COMPATIBILITY_PROVIDER(true),
        PROVIDER_FAILURE(true);

        private final boolean protectedEntity;

        ProtectionReason(boolean protectedEntity) {
            this.protectedEntity = protectedEntity;
        }

        public boolean protectedEntity() {
            return protectedEntity;
        }
    }

    private record RegisteredProvider(
            ResourceLocation id,
            AllyProtectionProvider provider) {
    }
}
