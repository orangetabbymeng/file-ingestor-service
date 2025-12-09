package com.sulaksono.fileingestorservice.util;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Utility for extracting information from a {@code pom.xml} contained in a ZIP file.
 */
public final class ZipPomUtil {

    private ZipPomUtil() {}

    public static String extractArtifactId(InputStream zipStream) {
        return extractPomField(zipStream, Field.ARTIFACT_ID);
    }

    public static String extractVersion(InputStream zipStream) {
        return extractPomField(zipStream, Field.VERSION);
    }

    private enum Field { ARTIFACT_ID, VERSION }

    private static String extractPomField(InputStream zipStream, Field field) {
        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = Path.of(entry.getName()).getFileName().toString().toLowerCase();
                if (!name.equals("pom.xml") && !name.endsWith(".pom")) continue;

                MavenXpp3Reader reader = new MavenXpp3Reader();
                Model model           = reader.read(zin, false);

                return switch (field) {
                    case ARTIFACT_ID -> firstNonNull(model.getArtifactId(),
                            model.getParent() != null ? model.getParent().getArtifactId() : null);
                    case VERSION -> firstNonNull(model.getVersion(),
                            model.getParent() != null ? model.getParent().getVersion() : null);
                };
            }
        } catch (Exception ignored) { /* fall through and return null */ }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}