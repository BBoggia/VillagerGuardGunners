package com.gamergaming.villagerguardgunners.compat.tacz;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/** Optional TaCZ APIs resolved once, without linking older installations to 1.2.0 classes. */
public final class TaCZRuntimeCompatibility {
    private TaCZRuntimeCompatibility() {
    }

    /** TaCZ 1.2.0 owns compression, fragmentation, and client cache publication. */
    public static boolean hasNativeChunkedSync() {
        return SyncSupport.NATIVE_CHUNKING;
    }

    /** Use the same clock as TaCZ's draw, firing, and base timestamps. */
    public static long gunTimeMillis() {
        MethodHandle clock = ClockSupport.NOW_MILLIS;
        if (clock == null) {
            return System.currentTimeMillis();
        }
        try {
            return (long) clock.invokeExact();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            // Never switch clock domains after TaCZ has initialized its timestamps.
            throw new IllegalStateException("Could not read TaCZ's gun clock", failure);
        }
    }

    private static final class SyncSupport {
        private static final boolean NATIVE_CHUNKING = detectNativeChunking();

        private static boolean detectNativeChunking() {
            try {
                Class<?> packet = Class.forName("com.tacz.guns.network.message.ServerMessageSyncGunPack",
                        false, TaCZRuntimeCompatibility.class.getClassLoader());
                var factory = packet.getMethod("createMessages", Map.class);
                return Modifier.isStatic(factory.getModifiers())
                        && List.class.isAssignableFrom(factory.getReturnType());
            } catch (NoSuchMethodException legacyPacket) {
                return false;
            } catch (ClassNotFoundException missingDependency) {
                throw new IllegalStateException("TaCZ's gun-pack sync packet is unavailable", missingDependency);
            }
        }
    }

    private static final class ClockSupport {
        private static final MethodHandle NOW_MILLIS = resolveClock();

        private static MethodHandle resolveClock() {
            Class<?> clock;
            try {
                clock = Class.forName("com.tacz.guns.util.time.GunTime", false,
                        TaCZRuntimeCompatibility.class.getClassLoader());
            } catch (ClassNotFoundException legacyClock) {
                return null;
            }
            try {
                return MethodHandles.publicLookup().findStatic(clock, "nowMillis", MethodType.methodType(long.class));
            } catch (NoSuchMethodException | IllegalAccessException incompatibleClock) {
                throw new IllegalStateException("TaCZ's gun clock API is incompatible", incompatibleClock);
            }
        }
    }
}
