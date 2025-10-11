package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;

/**
 * Stores raw uploads on the local file system so processing can be retried independently.
 */
@Service
public class FileStorageService {

    private final Path rootDir;

    public FileStorageService(StorageProperties props) {
        this.rootDir = Paths.get(props.getLocation()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(rootDir);
    }

    public Path save(MultipartFile file) throws IOException {
        String base = FilenameUtils.getBaseName(file.getOriginalFilename());
        String ext  = FilenameUtils.getExtension(file.getOriginalFilename());
        assert ext != null;
        String generated = base + "-" + System.nanoTime() + (ext.isBlank() ? "" : "." + ext);

        Path destination = rootDir.resolve(generated);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }
}