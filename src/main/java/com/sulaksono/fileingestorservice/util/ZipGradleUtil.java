package com.sulaksono.fileingestorservice.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipGradleUtil {

    private ZipGradleUtil() {}

    private static final Pattern ROOT_PROJECT_NAME =
            Pattern.compile("(?m)^\\s*rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");

    private static final Pattern VERSION_EQUALS =
            Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");
    private static final Pattern VERSION_GROOVY =
            Pattern.compile("(?m)^\\s*version\\s+['\"]([^'\"]+)['\"]\\s*$");

    public static String extractRootProjectName(InputStream zipStream) {
        return extractFromEntry(zipStream, entryBase ->
                        entryBase.equals("settings.gradle") || entryBase.equals("settings.gradle.kts"),
                content -> firstMatch(ROOT_PROJECT_NAME, content));
    }

    public static String extractVersion(InputStream zipStream) {
        return extractFromEntry(zipStream, entryBase ->
                        entryBase.equals("build.gradle") || entryBase.equals("build.gradle.kts"),
                content -> firstNonBlank(
                        firstMatch(VERSION_EQUALS, content),
                        firstMatch(VERSION_GROOVY, content)
                ));
    }

    private interface EntryNamePredicate { boolean test(String baseName); }
    private interface ContentMapper { String map(String content); }

    private static String extractFromEntry(InputStream zipStream, EntryNamePredicate predicate, ContentMapper mapper) {
        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String base = Path.of(entry.getName()).getFileName().toString().toLowerCase();
                if (!predicate.test(base)) continue;

                String content = readEntryAsString(zin, 1024 * 1024); // 1MB cap
                return mapper.map(content);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String readEntryAsString(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0, r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > maxBytes) break;
            out.write(buf, 0, r);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String firstMatch(Pattern p, String s) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}