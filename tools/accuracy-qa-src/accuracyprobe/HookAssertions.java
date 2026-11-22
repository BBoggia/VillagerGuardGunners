package accuracyprobe;

import java.util.Map;

/** No compile-time dependency on the competing addon: the same observer runs both matrices. */
final class HookAssertions {
    @SuppressWarnings("unchecked")
    static Map<String, Long> snapshot() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("vgg.shotqa.conflict")) return Map.of();
        return (Map<String, Long>) Class.forName("shotconflict.HookCounters")
                .getMethod("snapshot").invoke(null);
    }

    static void check(Map<String, Long> before, int events, int attempts, int successes)
            throws ReflectiveOperationException {
        if (before.isEmpty()) return;
        var after = snapshot();
        long scheduled = after.get("scheduled") - before.get("scheduled");
        long callbacks = after.get("callbacks") - before.get("callbacks");
        long insertions = after.get("insertions") - before.get("insertions");
        long accepted = after.get("accepted") - before.get("accepted");
        if (events > 0 && (scheduled == 0 || callbacks < events))
            throw new AssertionError("Competing scheduler was bypassed: " + after);
        if (insertions != attempts || accepted != successes)
            throw new AssertionError("Competing insertion hook bypassed/duplicated: " + after);
    }
}
