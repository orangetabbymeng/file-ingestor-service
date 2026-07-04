package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setup() throws Exception {

        StorageProperties props = new StorageProperties();
        props.setLocation(tempDir.toString());

        service = new FileStorageService(props);
        service.init();
    }

    @Test
    void save_shouldStoreFile() throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "demo.txt",
                        "text/plain",
                        "hello world".getBytes());

        Path stored = service.save(file);

        assertThat(stored).exists();
        assertThat(Files.readString(stored))
                .isEqualTo("hello world");
    }

    @Test
    void save_shouldPreserveExtension() throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "demo.java",
                        "text/plain",
                        "class Test {}".getBytes());

        Path stored = service.save(file);

        assertThat(stored.getFileName().toString())
                .endsWith(".java");
    }

    @Test
    void save_shouldGenerateUniqueFileNames() throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "duplicate.txt",
                        "text/plain",
                        "abc".getBytes());

        Path first = service.save(file);
        Path second = service.save(file);

        assertThat(first)
                .isNotEqualTo(second);

        assertThat(first).exists();
        assertThat(second).exists();
    }

    @Test
    void save_fileWithoutExtension_shouldStillStore() throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "README",
                        "text/plain",
                        "test".getBytes());

        Path stored = service.save(file);

        assertThat(stored).exists();
        assertThat(stored.getFileName().toString())
                .doesNotContain("..");
    }

    @Test
    void save_nullFilename_shouldThrow() {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        null,
                        "text/plain",
                        "test".getBytes());

        assertThatThrownBy(() -> service.save(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("File name");
    }
}