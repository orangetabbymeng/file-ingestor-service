package com.sulaksono.fileingestorservice.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipGradleUtil {

    private static final int MAX_ENTRY_BYTES = 1024 * 1024;

    // Fixed: Added ['\"] to match opening quote, and ([^'\"]+) to capture the value
    private static final Pattern ROOT_PROJECT_NAME =
            Pattern.compile("(?m)^\\s*rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");

    private static final Pattern VERSION_EQUALS =
            Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");

    private static final Pattern VERSION_GROOVY =
            Pattern.compile("(?m)^\\s*version\\s+['\"]([^'\"]+)['\"]\\s*$");

    private ZipGradleUtil() {
    }

    public static String extractRootProjectName(InputStream zipStream) {
        return extractFromEntry(
                zipStream,
                entryBase -> entryBase.equals("settings.gradle")
                        || entryBase.equals("settings.gradle.kts"),
                content -> firstMatch(ROOT_PROJECT_NAME, content)
        );
    }

    public static String extractVersion(InputStream zipStream) {
        return extractFromEntry(
                zipStream,
                entryBase -> entryBase.equals("build.gradle")
                        || entryBase.equals("build.gradle.kts"),
                content -> firstNonBlank(
                        firstMatch(VERSION_EQUALS, content),
                        firstMatch(VERSION_GROOVY, content)
                )
        );
    }

    private interface EntryNamePredicate {
        boolean test(String baseName);
    }

    private interface ContentMapper {
        String map(String content);
    }

    private static String extractFromEntry(
            InputStream zipStream,
            EntryNamePredicate predicate,
            ContentMapper mapper) {

        if (zipStream == null) {
            return null;
        }

        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry entry;

            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zin.closeEntry();
                    continue;
                }

                String baseName = baseName(entry.getName());

                if (!predicate.test(baseName)) {
                    zin.closeEntry();
                    continue;
                }

                String content = readEntryAsString(zin, MAX_ENTRY_BYTES);
                String result = mapper.map(content);

                zin.closeEntry();

                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private static String baseName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return "";
        }

        String normalized = entryName.replace('\\', '/');

        return Paths.get(normalized)
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);
    }

    private static String readEntryAsString(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            int remaining = maxBytes - total;

            if (remaining <= 0) {
                break;
            }

            int bytesToWrite = Math.min(read, remaining);
            out.write(buffer, 0, bytesToWrite);
            total += bytesToWrite;

            if (read > remaining) {
                break;
            }
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    private static String firstMatch(Pattern pattern, String content) {
        if (content == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(content);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}