package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import com.sulaksono.fileingestorservice.util.ZipPomUtil;
import com.sulaksono.fileingestorservice.util.ZipUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * REST endpoint for receiving individual files or ZIP batches.
 */
@RestController
@RequestMapping("/api/files")
@Validated
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileStorageService storage;
    private final ProcessingService processor;

    public FileUploadController(FileStorageService storage, ProcessingService processor) {
        this.storage = storage;
        this.processor = processor;
    }

    @Operation(summary = "Upload one or many files or ZIP archives")
    @PostMapping(
            value    = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "basicAuth")
    @PreAuthorize("hasRole('UPLOAD')")
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("files")  @NotEmpty MultipartFile[] files,
            @RequestPart("module") @NotBlank String module,
            @RequestPart("fileVersion") String fileVersion) {

        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (MultipartFile file : files) {

            String original = file.getOriginalFilename();

            String rejectionReason = validate(file, original);
            if (rejectionReason != null) {
                rejected.add(rejectionReason);
                continue;
            }

            boolean isZip = Optional.ofNullable(original)
                    .map(n -> n.toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .orElse(false);

            try {
                if (isZip) {
                    Path zipPath = storage.save(file);             // persist the ZIP
                    try {
                        final String derivedModule = resolveModuleName(module, zipPath);
                        final String derivedVersion = resolveModuleVersion(zipPath);

                        ZipUtil.unzip(file, zipPath.getParent()).stream()
                                .filter(p -> FileTypeResolver.resolve(p.getFileName().toString()) != FileType.UNKNOWN)
                                .forEach(p -> processor.processAsync(p, derivedModule, derivedVersion));

                        accepted.add(original);
                    } finally {
                        deleteQuietly(zipPath);
                    }
                } else {                                          // regular file
                    Path stored = storage.save(file);
                    processor.processAsync(stored, module, fileVersion);
                    accepted.add(original);
                }
            } catch (Exception e) {
                log.error("Upload failed for {}", original, e);
                rejected.add(original + " (" + e.getMessage() + ")");
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new UploadResponse(accepted, rejected));
    }

    public record UploadResponse(List<String> accepted, List<String> rejected) { }

    private String validate(MultipartFile file, String originalName) {

        if (!StringUtils.hasText(originalName)) {
            return "Unnamed file";
        }
        if (file.isEmpty()) {
            return originalName + " (empty)";
        }

        boolean isZip = originalName.toLowerCase().endsWith(".zip");
        if (!isZip && FileTypeResolver.resolve(originalName) == FileType.UNKNOWN) {
            return originalName + " (unsupported type)";
        }
        return null;  // accept
    }

    private String resolveModuleName(String requestedModule, Path zipPath) {
        if (!"from-package".equalsIgnoreCase(requestedModule)) {
            return requestedModule;
        }
        try (InputStream in = Files.newInputStream(zipPath)) {
            String art = ZipPomUtil.extractArtifactId(in);
            return (art != null && !art.isBlank()) ? art : requestedModule;
        } catch (IOException e) {
            log.warn("Could not inspect POM inside {} – falling back to {}", zipPath, requestedModule, e);
            return requestedModule;
        }
    }

    private String resolveModuleVersion(Path zipPath) {
        try (InputStream in = Files.newInputStream(zipPath)) {
            return ZipPomUtil.extractVersion(in);
        } catch (IOException e) {
            log.debug("Could not extract version from {} – {}", zipPath, e.getMessage());
            return null;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                log.debug("Deleted temporary file {}", path);
            }
        } catch (IOException ex) {
            log.warn("Unable to delete file {} – {}", path, ex.getMessage());
        }
    }
}