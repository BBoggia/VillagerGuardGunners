package shotconflict;

import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.bus.api.Event;

public final class HookCounters {
    public static final AtomicLong SCHEDULED = new AtomicLong();
    public static final AtomicLong CALLBACKS = new AtomicLong();
    public static final AtomicLong INSERTIONS = new AtomicLong();
    public static final AtomicLong ACCEPTED = new AtomicLong();

    public static Map<String, Long> snapshot() {
        return Map.of("scheduled", SCHEDULED.get(), "callbacks", CALLBACKS.get(),
                "insertions", INSERTIONS.get(), "accepted", ACCEPTED.get());
    }

    /** Published on every insertion attempt, including attempts rejected by the level. */
    public static final class ProjectileEvent extends Event {
        public final EntityKineticBullet bullet;
        public ProjectileEvent(EntityKineticBullet bullet) { this.bullet = bullet; }
    }
}
