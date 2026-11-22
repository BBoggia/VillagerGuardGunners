package com.gamergaming.villagerguardgunners.combat;

import net.minecraft.world.phys.Vec3;

/** Safety-relevant native properties resolved once, before this round spends ammo or gains heat. */
public record GuardShotProperties(float lifeSeconds, float gravity, float friction,
                                  boolean explosive, float explosionRadius, float explosionDelay) {
    public GuardShotProperties {
        if (!Float.isFinite(lifeSeconds) || !Float.isFinite(gravity) || !Float.isFinite(friction)
                || !Float.isFinite(explosionRadius) || !Float.isFinite(explosionDelay)) {
            throw new IllegalArgumentException("Non-finite projectile property");
        }
    }

    /** Raw floats preserve TaCZ's constructor rounding and clamping exactly. Null means unrelated property. */
    public <T> T nativeValue(String name, Class<T> type) {
        Object value = switch (name) {
            case "bullet_life" -> lifeSeconds;
            case "bullet_gravity" -> gravity;
            case "bullet_friction" -> friction;
            case "explode_enabled" -> explosive;
            case "explosion_radius" -> explosionRadius;
            case "explosion_delay" -> explosionDelay;
            default -> null;
        };
        return value == null ? null : type.cast(value);
    }

    public GuardShotEnvelope.Ballistics ballistics(float speed, float spread, Vec3 inheritedVelocity) {
        int delay = (int) (explosionDelay * 20.0F);
        int fuse = !explosive || delay < 0 ? Integer.MAX_VALUE : Math.max(1, delay);
        return new GuardShotEnvelope.Ballistics(speed, Math.max(1, (int) (lifeSeconds * 20.0F)),
                Math.max(0, gravity), Math.max(0, friction), spread, inheritedVelocity,
                explosive ? Math.max(0, explosionRadius) * 2.0D : 0, fuse);
    }
}
