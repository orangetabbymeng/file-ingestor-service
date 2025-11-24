package com.sulaksono.fileingestorservice.util;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipPomUtil {

    private ZipPomUtil() { }

    /**
     * Returns the first <artifactId> found in a POM stored in the ZIP, or null
     * if no readable POM is present.
     */
    public static String extractArtifactId(InputStream zipStream) {
        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = Path.of(entry.getName()).getFileName().toString().toLowerCase();
                if (!name.equals("pom.xml") && !name.endsWith(".pom")) continue;

                MavenXpp3Reader reader = new MavenXpp3Reader();
                Model model           = reader.read(zin, false);
                return firstNonNull(model.getArtifactId(),
                        model.getParent() != null ? model.getParent().getArtifactId() : null);
            }
        } catch (Exception ignored) { /* just fall through and return null */ }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
