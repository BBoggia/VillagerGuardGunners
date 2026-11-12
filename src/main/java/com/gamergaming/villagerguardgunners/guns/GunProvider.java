package com.gamergaming.villagerguardgunners.guns;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * One supported gun mod.
 *
 * <p>Code outside a provider's own package never imports that gun mod. It asks
 * {@link GunProviders} which provider owns a stack and works through this
 * interface and {@link WeaponDriver}.</p>
 */
public interface GunProvider {
    /** Lower-case identifier used in data files, saves, and diagnostics. */
    String id();

    /** True when this provider's mod owns the stack as a gun. Never mutates the stack. */
    boolean isGun(ItemStack stack);

    /** True for this mod's loose ammo and ammo containers. Never mutates the stack. */
    boolean isAmmoItem(ItemStack stack);

    /** Identity of a gun stack, or empty when it is not this provider's gun or cannot be read. */
    Optional<GunRef> ref(ItemStack gunStack);

    /**
     * Looks the gun up in its mod's definitions. Empty when the stack is not
     * this provider's gun, its identity cannot be read, or no definition is
     * loaded for it. Never throws.
     */
    Optional<GunDescriptor> describe(ItemStack gunStack);

    /**
     * Whether the gun fires explosive rounds, evaluated in isolation from the
     * weapon the guard currently holds. Empty when that cannot be determined;
     * callers must then treat the gun as unsafe.
     */
    Optional<Boolean> isExplosive(Guard guard, ItemStack gunStack);

    /**
     * Identity of the ammo in a loose stack or container, when the mod names
     * one. Malformed stack data may throw a {@link RuntimeException}.
     */
    default Optional<ResourceLocation> ammoId(ItemStack ammoStack) {
        return Optional.empty();
    }

    /**
     * True when the stack supplies ammo that the mod's loaded definitions
     * recognize. Stricter than {@link #isUsableSupply}: generated loot uses it
     * to drop entries from packs that are not installed.
     */
    default boolean isKnownUsableSupply(ItemStack stack) {
        return isUsableSupply(stack);
    }

    /**
     * Judges one candidate stack as ammo for a gun. Both stacks are non-empty.
     * Implementations probe copies and must never mutate either live stack.
     */
    AmmoCompatibility.Assessment assessAmmo(ItemStack gunStack, ItemStack candidate);

    /** True for single rounds and stacks of them, never for a container of ammo. */
    default boolean isLooseAmmo(ItemStack stack) {
        return isAmmoItem(stack);
    }

    /** True when the stack still supplies ammo: loose rounds, or a container that is not used up. */
    default boolean isUsableSupply(ItemStack stack) {
        return isAmmoItem(stack);
    }

    /** True for a reusable ammo container with nothing left in it. Guards drop these to make room. */
    default boolean isSpentAmmoContainer(ItemStack stack) {
        return false;
    }

    /**
     * Describes the gun's magazine, feed, and reserves. The stack is this
     * provider's gun and is non-empty. Unreadable gun data is reported in the
     * snapshot's status, never thrown.
     */
    GuardAmmoSummary.Snapshot captureAmmo(ItemStack gunStack, @Nullable IItemHandler reserveHandler);

    /** A never-emptying ammo source for the gun, for mods that have one. */
    default Optional<ItemStack> creativeSupply(ItemStack gunStack) {
        return Optional.empty();
    }

    /**
     * True when the mod poses arms only for player models, so a guard holding
     * one of its guns needs this mod's shared shouldered pose. Read on the client.
     */
    default boolean usesGenericHeldPose() {
        return false;
    }

    /**
     * Creates the driver that operates this provider's guns for one guard.
     * Called at most once per guard; the driver lives as long as the guard's
     * combat goal.
     */
    WeaponDriver createDriver(Guard guard);

    /** Server-side setup for a newly constructed guard, before it holds anything. */
    default void onGuardCreated(Guard guard) {
    }

    /** Cancels an in-progress reload and lowers the weapon. Safe when the guard holds no gun. */
    default void interrupt(Guard guard) {
    }

    /** Discards queued follow-up actions, such as a pending bolt cycle or melee, after {@link #interrupt}. */
    default void clearPendingActions(Guard guard) {
    }

    /** True while this provider has the guard's weapon raised to fire. */
    default boolean isAiming(Guard guard) {
        return false;
    }

    /**
     * True while this provider is mid-action for the guard: reloading, cycling,
     * drawing, charging, or cooling down. Unreadable state must report busy, so
     * logistics never interrupts it.
     */
    default boolean isWeaponBusy(Guard guard) {
        return false;
    }
}
