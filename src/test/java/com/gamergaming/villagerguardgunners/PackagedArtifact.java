package com.gamergaming.villagerguardgunners;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/** Checks the built artifact instead of looking for filenames in a Gradle validation list. */
final class PackagedArtifact {
    private PackagedArtifact() { }

    static String entries() throws IOException {
        try (var zip = new ZipFile(System.getProperty("villagerguardgunners.artifact"))) {
            return zip.stream().map(entry -> entry.getName()).collect(Collectors.joining("\n"));
        }
    }
}
