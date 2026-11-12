package com.gamergaming.villagerguardgunners.compat.pointblank;

import com.gamergaming.villagerguardgunners.combat.GuardShotEnvelope;
import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedGunBackend;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedGunStats;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Fires Point Blank hit-scan guns for a guard through the mod's own trace and
 * damage code. Launchers are reported as unreadable, so a guard holding one
 * never fires it.
 */
final class PointBlankGunBackend implements ManagedGunBackend {
    private final PointBlankAccess access;

    PointBlankGunBackend(PointBlankAccess access) {
        this.access = access;
    }

    @Override
    public Optional<ManagedGunStats> stats(ItemStack gun) {
        PointBlankAccess.Facts facts = access.facts(gun).orElse(null);
        if (facts == null || facts.launcher() || facts.capacity() < 1 || facts.capacity() == Integer.MAX_VALUE) {
            return Optional.empty();
        }
        int interval = PointBlankTraits.fireIntervalMillis(facts.rpm());
        double range = PointBlankTraits.hitScanRange(facts.range(), facts.inaccuracy());
        if (interval < 1 || Double.isNaN(range)) {
            return Optional.empty();
        }
        // A hit scan is modelled as a projectile that covers its whole range in one tick.
        GuardShotEnvelope.Ballistics ballistics = new GuardShotEnvelope.Ballistics(
                range, 1, 0.0D, 0.0D,
                PointBlankTraits.safetyInaccuracy(facts.inaccuracy()),
                Vec3.ZERO, 0.0D, Integer.MAX_VALUE);
        return Optional.of(new ManagedGunStats(
                interval,
                facts.automatic(),
                facts.capacity(),
                PointBlankTraits.reloadTicks(facts.reloadMillis()),
                facts.capacity(),
                Math.max(1, facts.pellets()),
                (float) range,
                false,
                0.0D,
                ballistics,
                false));
    }

    @Override
    public int loaded(ItemStack gun) {
        return Math.max(0, access.loaded(gun));
    }

    @Override
    public void setLoaded(ItemStack gun, int rounds) {
        access.setLoaded(gun, Math.max(0, rounds));
    }

    @Override
    public void prepare(ItemStack gun) {
        access.prepare(gun);
    }

    @Override
    public Outcome fire(Guard guard, ItemStack gun, GuardShotAim aim, Predicate<Entity> mayHit) {
        PointBlankAccess.Facts facts = access.facts(gun).orElse(null);
        if (facts == null || facts.launcher()) {
            return Outcome.FAILED;
        }
        double range = PointBlankTraits.hitScanRange(facts.range(), facts.inaccuracy());
        if (Double.isNaN(range)) {
            return Outcome.FAILED;
        }
        // Same origin, direction, range, and spread the ally check was just run against.
        return access.fireHitScan(guard, gun, aim.projectileOrigin(), aim.direction(), range,
                Math.max(1, facts.pellets()), facts.inaccuracy(), guard.getRandom().nextLong(), mayHit)
                ? Outcome.FIRED
                : Outcome.FAILED;
    }
}
