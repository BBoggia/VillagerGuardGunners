package com.gamergaming.villagerguardgunners.combat;

import javax.annotation.Nullable;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves the explosion state that TaCZ will apply when it constructs a
 * kinetic bullet.
 *
 * <p>TaCZ invokes its runtime explosion properties even when neither the base
 * gun data nor an attachment supplies an explosion block. In that case it
 * starts with a disabled, zero-radius default which a gun script may override.
 * Keeping that detail here prevents policy, standoff, and natural-loadout
 * checks from disagreeing with the projectile that TaCZ actually creates.</p>
 */
public final class TaCZExplosionResolver {
    private static final ExplosionData DEFAULT_EXPLOSION_DATA =
            new ExplosionData(false, 0.0F, 0.0F, false, 30.0F, false);

    private TaCZExplosionResolver() {
    }

    public static Traits resolve(
            LivingEntity shooter,
            ItemStack gunStack,
            IGun gun,
            GunData gunData,
            @Nullable AttachmentCacheProperty cache) {
        ExplosionData explosion = resolveBaseExplosionFromGun(gunData, cache);
        Boolean enabled = gun.modifyProperty(
                IGunOperator.fromLivingEntity(shooter).getDataHolder(),
                gunStack,
                shooter,
                GunProperties.RuntimeOnly.EXPLODE_ENABLED,
                Boolean.class,
                explosion.isExplode());
        if (enabled == null) {
            throw new IllegalStateException("TaCZ gun resolved an invalid explosion flag");
        }
        if (!enabled) {
            return Traits.NONE;
        }

        Float radius = gun.modifyProperty(
                IGunOperator.fromLivingEntity(shooter).getDataHolder(),
                gunStack,
                shooter,
                GunProperties.RuntimeOnly.EXPLOSION_RADIUS,
                Float.class,
                explosion.getRadius());
        if (radius == null || !Float.isFinite(radius)) {
            throw new IllegalStateException("TaCZ gun resolved an invalid explosion radius");
        }
        return new Traits(true, Math.max(0.0F, radius));
    }

    static ExplosionData resolveBaseExplosionFromGun(
            GunData gunData,
            @Nullable AttachmentCacheProperty cache) {
        ExplosionData attachmentExplosion = cache == null
                ? null
                : cache.getCache(GunProperties.EXPLOSION);
        ExplosionData baseExplosion = gunData.getBulletData() == null
                ? null
                : gunData.getBulletData().getExplosionData();
        return resolveBaseExplosion(attachmentExplosion, baseExplosion);
    }

    static ExplosionData resolveBaseExplosion(
            @Nullable ExplosionData attachmentExplosion,
            @Nullable ExplosionData baseExplosion) {
        if (attachmentExplosion != null) {
            return attachmentExplosion;
        }
        return baseExplosion == null ? DEFAULT_EXPLOSION_DATA : baseExplosion;
    }

    public record Traits(boolean enabled, float radius) {
        public static final Traits NONE = new Traits(false, 0.0F);

        public Traits {
            if (!Float.isFinite(radius) || radius < 0.0F) {
                throw new IllegalArgumentException("Explosion radius must be finite and non-negative");
            }
            if (!enabled && radius != 0.0F) {
                throw new IllegalArgumentException("A disabled explosion cannot have a radius");
            }
        }
    }
}
