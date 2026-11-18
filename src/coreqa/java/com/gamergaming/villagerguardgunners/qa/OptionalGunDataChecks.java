package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.quartermaster.trade.QuartermasterTrades;
import com.gamergaming.villagerguardgunners.quartermaster.trade.TradeDefinition;
import com.gamergaming.villagerguardgunners.quartermaster.trade.TradeDefinitions;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCandidate;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCompatibilityAnalyzer;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutIssuance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutPools;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Checks the data this mod ships for one optional gun mod against the
 * installed mod: every bundled loadout gun can be issued, every bundled trade
 * can be built, and data for gun mods that are not installed is left out.
 */
final class OptionalGunDataChecks {
    private static final Set<String> OPTIONAL_GUN_MODS = Set.of("cgm", "pointblank");

    private OptionalGunDataChecks() {
    }

    /** @return a summary for the pass line; throws with a reason on the first failure */
    static String run(ServerLevel level, String mod) {
        int loadoutGuns = 0;
        ResourceLocation issuedPool = null;
        ResourceLocation issuedGun = null;
        for (var pool : NaturalGuardLoadoutPools.snapshot().entrySet()) {
            for (var entry : pool.getValue().entries()) {
                String namespace = entry.gunId().getNamespace();
                require(!OPTIONAL_GUN_MODS.contains(namespace) || namespace.equals(mod),
                        "Pool " + pool.getKey() + " kept an entry for a gun mod that is not installed: " + entry.gunId());
                if (!namespace.equals(mod)) continue;
                var analysis = NaturalGuardLoadoutCompatibilityAnalyzer.analyze(null, entry.gunId(),
                        NaturalGuardLoadoutCompatibilityAnalyzer.ExplosivePolicy.REPORT);
                require(analysis.compatible(), "Bundled loadout gun " + entry.gunId() + " in " + pool.getKey()
                        + " cannot be issued: " + analysis.status());
                require(analysis.explosionState() == NaturalGuardLoadoutCompatibilityAnalyzer.ExplosionState.NON_EXPLOSIVE,
                        "Bundled loadout gun " + entry.gunId() + " is " + analysis.explosionState());
                loadoutGuns++;
                issuedPool = pool.getKey();
                issuedGun = entry.gunId();
            }
        }
        require(loadoutGuns > 0, "No bundled loadout entries were kept for " + mod);

        // One real issuance, as a naturally spawned guard would get it.
        Guard guard = GuardEntityType.GUARD.get().create(level);
        require(guard != null, "Guard creation failed");
        var result = NaturalGuardLoadoutIssuance.issue(guard, new NaturalGuardLoadoutCandidate(
                issuedPool, issuedGun, NaturalGuardDifficulty.NORMAL, NaturalGuardSpawnSource.COMMAND), false);
        require(result.issued(), "Issuing " + issuedGun + " failed: " + result.status());
        ItemStack held = guard.getMainHandItem();
        require(GunProviders.find(held).map(provider -> provider.id().equals(mod)).orElse(false),
                "Issued gun is not owned by the " + mod + " provider");
        GuardAmmoSummary.Snapshot summary = GuardAmmoSummary.capture(held, GuardAmmoAccess.getAmmoHandler(guard));
        require(summary.isReady() && summary.loadedAmmo() > 0 && summary.loadedAmmo() == summary.magazineCapacity(),
                "Issued gun is not fully loaded: " + summary.loadedAmmo() + "/" + summary.magazineCapacity());
        require(summary.hasCompatibleReserve() && result.primaryAmmunitionItems() > 0,
                "Issued guard has no usable reserve ammo");
        require(result.provenance() != null, "Issued gun carries no loadout record");
        // Every mode must issue a complete optional-provider loadout. Unsupported
        // creative supplies fall back to the same finite ammo, not a failed gun.
        var originalMode = com.gamergaming.villagerguardgunners.ammunition.GuardInfiniteAmmo.mode();
        try {
            for (var mode : com.gamergaming.villagerguardgunners.ammunition.InfiniteAmmoMode.values()) {
                com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(mode);
                Guard sample = GuardEntityType.GUARD.get().create(level);
                try {
                    var issuance = NaturalGuardLoadoutIssuance.issue(sample, new NaturalGuardLoadoutCandidate(
                            issuedPool, issuedGun, NaturalGuardDifficulty.NORMAL, NaturalGuardSpawnSource.COMMAND), false);
                    require(issuance.issued(), "Loadout failed in " + mode + ": " + issuance.status());
                } finally { sample.discard(); }
            }
        } finally {
            com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(originalMode);
        }
        var provider = GunProviders.find(held).orElseThrow();
        ItemStack ammo = provider.createAmmo(provider.describe(held).orElseThrow().ammoId(), 16).orElseThrow();
        var handler = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < handler.getSlots(); slot++) handler.setStackInSlot(slot, ItemStack.EMPTY);
        require(provider.captureAmmo(held, null).reserveSource() == GuardAmmoSummary.ReserveSource.PHYSICAL,
                "Null reserve hid the gun's physical feeding model");
        require(provider.captureAmmo(held, handler).reserveSource() == GuardAmmoSummary.ReserveSource.PHYSICAL,
                "Empty reserve hid the gun's physical feeding model");
        require(com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition
                .assessPhysicalSupply(guard, ammo).compatible(), "Empty guard refused compatible supplies");
        var control = com.gamergaming.villagerguardgunners.guns.GuardWeaponControlAccess.control(guard).orElseThrow();
        control.startAiming();
        require(control.controllerDiagnostics().weaponDataReady(), "Ready optional gun has unavailable diagnostics");
        var driver = control.observedDriver();
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        require(!control.controllerDiagnostics().weaponDataReady(), "Empty hand reported a stale controller");
        require(control.driverFor(mod).orElseThrow() == driver, "Inspection replaced the cached controller");
        require(provider.isAiming(guard), "Inspection interrupted the previous controller");
        // The first real ownership update, not the diagnostic read, performs cleanup.
        control.active();
        require(!provider.isAiming(guard), "Unequipping a gun left its managed driver aiming");
        guard.discard();

        int trades = 0;
        for (var trade : TradeDefinitions.snapshot().entrySet()) {
            TradeDefinition.Stack output = trade.getValue().output();
            if (output == null) continue;
            String namespace = output.id().getNamespace();
            if (!namespace.equals(mod)) {
                if (OPTIONAL_GUN_MODS.contains(namespace)) {
                    require(QuartermasterTrades.stack(output, level.registryAccess()).isEmpty(),
                            "Trade " + trade.getKey() + " built an item for a gun mod that is not installed");
                }
                continue;
            }
            ItemStack stack = QuartermasterTrades.stack(output, level.registryAccess());
            require(!stack.isEmpty(), "Bundled trade " + trade.getKey() + " cannot build " + output.id());
            if (output.type() == TradeDefinition.Kind.GUN) {
                require(GunProviders.isGun(stack), "Trade " + trade.getKey() + " does not sell a gun");
                require(GuardAmmoSummary.capture(stack, null).loadedAmmo() == 0, "Trade " + trade.getKey() + " sells a loaded gun");
            } else {
                require(GunProviders.isAmmo(stack) && stack.getCount() == output.count(),
                        "Trade " + trade.getKey() + " does not sell the declared ammo");
            }
            trades++;
        }
        require(trades > 0, "No bundled trades were found for " + mod);
        return "loadoutGuns=" + loadoutGuns + " issued=" + issuedGun + " trades=" + trades;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
