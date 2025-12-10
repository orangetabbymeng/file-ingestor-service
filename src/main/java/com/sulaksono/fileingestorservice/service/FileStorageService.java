package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;

/**
 * Stores raw uploads on the local file system so processing can be retried independently.
 */
@Getter
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path rootDir;

    public FileStorageService(StorageProperties props) {
        this.rootDir = Paths.get(props.getLocation()).toAbsolutePath().normalize();
        log.debug("event=constructor rootDir={}", this.rootDir);
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(rootDir);
        log.info("event=storage_init rootDir={}", rootDir);
    }

    public Path save(MultipartFile file) throws IOException {

        String requestId = MDC.get("requestId");
        String original  = file.getOriginalFilename();

        log.debug("event=file_save_start requestId={} originalName={} size={}",
                requestId, original, file.getSize());

        try {
            String base = FilenameUtils.getBaseName(original);
            String ext  = FilenameUtils.getExtension(original);
            assert ext != null;
            String generated = base + "-" + System.nanoTime() + (ext.isBlank() ? "" : "." + ext);

            Path destination = rootDir.resolve(generated);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("event=file_saved requestId={} storedName={} path={}",
                    requestId, generated, destination);

            return destination;
        } catch (IOException e) {
            log.error("event=file_save_error requestId={} originalName={} error={}",
                    requestId, original, e, e);
            throw e;
        }

    }
}