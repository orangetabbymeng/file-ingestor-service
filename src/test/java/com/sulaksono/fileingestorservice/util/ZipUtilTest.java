package com.sulaksono.fileingestorservice.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipUtilTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------
    // Path-based unzip
    // -----------------------------------------------------------------

    @Test
    void unzip_pathVariant_shouldExtractSupportedFiles() throws Exception {
        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("Demo.java", "class Demo {}"));
            entries.add(entry("Notes.txt", "some notes"));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(zip, extractDir);

        assertThat(extracted).hasSize(2);
        assertThat(extracted)
                .allSatisfy(p -> assertThat(p).exists());
    }

    @Test
    void unzip_pathVariant_shouldSkipUnsupportedFileTypes() throws Exception {
        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("Demo.java", "class Demo {}"));
            entries.add(entry("photo.xyz", "binary-ish content"));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(zip, extractDir);

        assertThat(extracted).hasSize(1);

        assertThat(extracted.get(0).getFileName().toString())
                .isEqualTo("Demo.java");
    }

    @Test
    void unzip_pathVariant_shouldIgnoreDirectoryEntries() throws Exception {
        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(directoryEntry("src/"));
            entries.add(entry("src/Demo.java", "class Demo {}"));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(zip, extractDir);

        assertThat(extracted).hasSize(1);

        assertThat(extracted.get(0).getFileName().toString())
                .isEqualTo("Demo.java");
    }

    @Test
    void unzip_pathVariant_shouldRejectZipSlipEntries() throws Exception {
        Path zip = zipInTempDir("evil.zip", entries -> {
            entries.add(entry("../../evil.java", "class Evil {}"));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        assertThatThrownBy(() -> ZipUtil.unzip(zip, extractDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Zip-Slip");
    }

    @Test
    void unzip_pathVariant_shouldSkipEntriesLargerThanMaxEntryBytes() throws Exception {
        long maxEntryBytes = 10;
        long maxTotalBytes = 1024;
        int maxEntries = 100;

        String largeContent = "x".repeat(100);

        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("Small.java", "class S {}"));
            entries.add(entry("Big.java", largeContent));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(
                zip,
                extractDir,
                maxEntryBytes,
                maxTotalBytes,
                maxEntries,
                true
        );

        assertThat(extracted).hasSize(1);

        assertThat(extracted.get(0).getFileName().toString())
                .isEqualTo("Small.java");
    }

    @Test
    void unzip_pathVariant_shouldStopWhenMaxEntriesReached() throws Exception {
        long maxEntryBytes = 1024;
        long maxTotalBytes = 1024 * 1024;
        int maxEntries = 2;

        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("A.java", "class A {}"));
            entries.add(entry("B.java", "class B {}"));
            entries.add(entry("C.java", "class C {}"));
            entries.add(entry("D.java", "class D {}"));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(
                zip,
                extractDir,
                maxEntryBytes,
                maxTotalBytes,
                maxEntries,
                true
        );

        assertThat(extracted).hasSizeLessThanOrEqualTo(maxEntries);
    }

    @Test
    void unzip_pathVariant_shouldStopWhenMaxTotalBytesReached() throws Exception {
        long maxEntryBytes = 1024;
        long maxTotalBytes = 20;
        int maxEntries = 100;

        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("A.java", "x".repeat(15)));
            entries.add(entry("B.java", "x".repeat(15)));
            entries.add(entry("C.java", "x".repeat(15)));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(
                zip,
                extractDir,
                maxEntryBytes,
                maxTotalBytes,
                maxEntries,
                true
        );

        assertThat(extracted).hasSizeLessThan(3);

        long totalBytes = 0;
        for (Path p : extracted) {
            totalBytes += Files.size(p);
        }

        assertThat(totalBytes).isLessThanOrEqualTo(maxTotalBytes);
    }

    @Test
    void unzip_pathVariant_whenSkipUnsupportedFalse_shouldExtractUnknownTypes() throws Exception {
        long maxEntryBytes = 1024;
        long maxTotalBytes = 1024 * 1024;
        int maxEntries = 100;

        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("photo.xyz", "unknown-type-content"));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(
                zip,
                extractDir,
                maxEntryBytes,
                maxTotalBytes,
                maxEntries,
                false
        );

        assertThat(extracted).hasSize(1);

        assertThat(extracted.get(0).getFileName().toString())
                .isEqualTo("photo.xyz");
    }

    @Test
    void unzip_pathVariant_shouldPreserveFileContent() throws Exception {
        String original = "public class Demo { }";

        Path zip = zipInTempDir("archive.zip", entries -> {
            entries.add(entry("Demo.java", original));
        });

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(zip, extractDir);

        assertThat(extracted).hasSize(1);

        String actual = Files.readString(extracted.get(0));

        assertThat(actual).isEqualTo(original);
    }

    // -----------------------------------------------------------------
    // MultipartFile-based unzip
    // -----------------------------------------------------------------

    @Test
    void unzip_multipartVariant_shouldExtractSupportedFiles() throws Exception {
        byte[] zipBytes = zipBytes(entries -> {
            entries.add(entry("Demo.java", "class Demo {}"));
            entries.add(entry("Other.txt", "hello"));
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "archive.zip",
                "application/zip",
                zipBytes
        );

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(file, extractDir);

        assertThat(extracted).hasSize(2);

        assertThat(extracted)
                .allSatisfy(p -> assertThat(p).exists());
    }

    @Test
    void unzip_multipartVariant_shouldSkipUnsupportedTypes() throws Exception {
        byte[] zipBytes = zipBytes(entries -> {
            entries.add(entry("Demo.java", "class Demo {}"));
            entries.add(entry("weird.xyz", "unknown"));
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "archive.zip",
                "application/zip",
                zipBytes
        );

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        List<Path> extracted = ZipUtil.unzip(file, extractDir);

        assertThat(extracted).hasSize(1);

        assertThat(extracted.get(0).getFileName().toString())
                .isEqualTo("Demo.java");
    }

    @Test
    void unzip_multipartVariant_shouldRejectZipSlip() throws Exception {
        byte[] zipBytes = zipBytes(entries -> {
            entries.add(entry("../../evil.java", "class Evil {}"));
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evil.zip",
                "application/zip",
                zipBytes
        );

        Path extractDir = Files.createDirectory(tempDir.resolve("out"));

        assertThatThrownBy(() -> ZipUtil.unzip(file, extractDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Zip-Slip");
    }

    // -----------------------------------------------------------------
    // Constructor coverage
    // -----------------------------------------------------------------

    @Test
    void constructor_shouldBePrivate() throws Exception {
        Constructor<ZipUtil> constructor =
                ZipUtil.class.getDeclaredConstructor();

        assertThat(constructor.canAccess(null)).isFalse();

        constructor.setAccessible(true);
        ZipUtil instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    @FunctionalInterface
    private interface EntryBuilder {
        void build(List<ZipEntrySpec> entries);
    }

    private record ZipEntrySpec(String name, boolean directory, byte[] content) {
    }

    private static ZipEntrySpec entry(String name, String content) {
        return new ZipEntrySpec(
                name,
                false,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ZipEntrySpec directoryEntry(String name) {
        String normalized = name.endsWith("/") ? name : name + "/";
        return new ZipEntrySpec(normalized, true, new byte[0]);
    }

    private Path zipInTempDir(String zipFileName, EntryBuilder builder) throws IOException {
        Path zipPath = tempDir.resolve(zipFileName);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            List<ZipEntrySpec> specs = new java.util.ArrayList<>();
            builder.build(specs);

            for (ZipEntrySpec spec : specs) {
                writeSpec(zos, spec);
            }
        }

        return zipPath;
    }

    private static byte[] zipBytes(EntryBuilder builder) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            List<ZipEntrySpec> specs = new java.util.ArrayList<>();
            builder.build(specs);

            for (ZipEntrySpec spec : specs) {
                writeSpec(zos, spec);
            }
        }

        return bytes.toByteArray();
    }

    private static void writeSpec(ZipOutputStream zos, ZipEntrySpec spec) throws IOException {
        ZipEntry entry = new ZipEntry(spec.name());
        zos.putNextEntry(entry);

        if (!spec.directory()) {
            zos.write(spec.content());
        }

        zos.closeEntry();
    }
}