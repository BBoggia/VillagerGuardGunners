import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/** Use the same Maven range semantics as Forge, including compound ranges. */
class CheckVersions {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--select-embedded")) {
            if ((args.length - 1) % 2 != 0) throw new IllegalArgumentException("Expected version/range pairs");
            int chosen = -1;
            for (int i = 1; i < args.length; i += 2) {
                var candidate = new DefaultArtifactVersion(args[i]);
                boolean allowed = true;
                for (int j = 2; j < args.length; j += 2) {
                    var range = VersionRange.createFromVersionSpec(args[j]);
                    if (range.hasRestrictions() && !range.containsVersion(candidate)) allowed = false;
                }
                if (allowed && (chosen < 0 || candidate.compareTo(new DefaultArtifactVersion(args[chosen])) > 0)) chosen = i;
            }
            if (chosen < 0) throw new IllegalArgumentException("No available embedded version satisfies all requested ranges");
            System.out.println((chosen - 1) / 2);
            return;
        }
        if (args.length % 3 != 0) throw new IllegalArgumentException("Expected label/version/range triples");
        for (int i = 0; i < args.length; i += 3) {
            String range = args[i + 2];
            if (range.isBlank() || range.equals("*")) continue;
            var parsed = VersionRange.createFromVersionSpec(range);
            // Bare recommended versions are not restrictions in Forge.
            if (parsed.hasRestrictions() && !parsed.containsVersion(new DefaultArtifactVersion(args[i + 1]))) {
                System.err.println(args[i] + ": " + args[i + 1] + " is outside " + range);
                System.exit(1);
            }
        }
        System.out.println("Validated " + args.length / 3 + " dependency and loader ranges");
    }
}
