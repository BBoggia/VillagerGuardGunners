package com.gamergaming.villagerguardgunners.gametest.combat;

import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

/** Test-only access using the development runtime's mapped field names. */
final class NavigationTimeoutGameTestAccess {
    private NavigationTimeoutGameTestAccess() { }

    static void prime(PathNavigation navigation, Vec3i node, long elapsed, double limit, long checkedAt) {
        set(navigation, "timeoutCachedNode", node);
        set(navigation, "timeoutTimer", elapsed);
        set(navigation, "timeoutLimit", limit);
        set(navigation, "lastTimeoutCheck", checkedAt);
    }

    static long elapsed(PathNavigation navigation) {
        try { return field("timeoutTimer").getLong(navigation); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void set(PathNavigation navigation, String name, Object value) {
        try { field(name).set(navigation, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static java.lang.reflect.Field field(String name) throws NoSuchFieldException {
        var field = PathNavigation.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
