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
import org.slf4j.MDC;
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
import java.util.*;

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

        final String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.info("event=upload_start requestId={} module={} version={} fileCount={}",
                requestId, module, fileVersion, files.length);

        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        try {
            for (MultipartFile file : files) {

                String original = file.getOriginalFilename();
                log.debug("event=file_received requestId={} fileName={} size={}",
                        requestId, original, file.getSize());

                String rejectionReason = validate(file, original);
                if (rejectionReason != null) {
                    log.warn("event=file_rejected requestId={} reason=\"{}\"",
                            requestId, rejectionReason);
                    rejected.add(rejectionReason);
                    continue;
                }

                boolean isZip = Optional.ofNullable(original)
                        .map(n -> n.toLowerCase(Locale.ROOT).endsWith(".zip"))
                        .orElse(false);

                log.debug("event=file_validated requestId={} fileName={} isZip={}",
                        requestId, original, isZip);

                try {
                    if (isZip) {
                        Path zipPath = storage.save(file);             // persist the ZIP
                        log.debug("event=zip_saved requestId={} path={}", requestId, zipPath);

                        try {
                            final String derivedModule  = resolveModuleName(module, zipPath);
                            final String derivedVersion = resolveModuleVersion(zipPath);
                            log.debug("event=zip_metadata requestId={} derivedModule={} derivedVersion={}",
                                    requestId, derivedModule, derivedVersion);

                            ZipUtil.unzip(file, zipPath.getParent()).stream()
                                    .filter(p -> FileTypeResolver.resolve(p.getFileName().toString()) != FileType.UNKNOWN)
                                    .forEach(p -> {
                                        log.debug("event=dispatch_async requestId={} file={}", requestId, p);
                                        processor.processAsync(p, derivedModule, derivedVersion);
                                    });

                            accepted.add(original);
                            log.info("event=zip_file_accepted requestId={} fileName={}", requestId, original);
                        } finally {
                            deleteQuietly(zipPath);
                        }
                    } else {                                          // regular file
                        Path stored = storage.save(file);
                        log.debug("event=file_saved requestId={} path={}", requestId, stored);
                        processor.processAsync(stored, module, fileVersion);
                        accepted.add(original);
                        log.info("event=file_accepted requestId={} fileName={}", requestId, original);
                    }
                } catch (Exception e) {
                    log.error("event=file_failure requestId={} fileName={} error={}",
                            requestId, original, e, e);
                    rejected.add(original + " (" + e.getMessage() + ")");
                }
            }

            UploadResponse body = new UploadResponse(accepted, rejected);
            log.info("event=upload_complete requestId={} acceptedCount={} rejectedCount={}",
                    requestId, accepted.size(), rejected.size());

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        } finally {
            MDC.remove("requestId");   // clean up to avoid leaking correlation IDs
        }
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
            String resolved = (art != null && !art.isBlank()) ? art : requestedModule;
            log.debug("event=module_name_resolved requestId={} zip={} resolvedModule={}",
                    MDC.get("requestId"), zipPath.getFileName(), resolved);
            return resolved;
        } catch (IOException e) {
            log.warn("event=pom_inspect_failed requestId={} zip={} fallbackModule={}",
                    MDC.get("requestId"), zipPath.getFileName(), requestedModule, e);
            return requestedModule;
        }
    }

    private String resolveModuleVersion(Path zipPath) {
        try (InputStream in = Files.newInputStream(zipPath)) {
            String version = ZipPomUtil.extractVersion(in);
            log.debug("event=module_version_resolved requestId={} zip={} version={}",
                    MDC.get("requestId"), zipPath.getFileName(), version);
            return version;
        } catch (IOException e) {
            log.debug("event=version_extract_failed requestId={} zip={} msg={}",
                    MDC.get("requestId"), zipPath.getFileName(), e.getMessage());
            return null;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                log.debug("event=temp_deleted requestId={} path={}", MDC.get("requestId"), path);
            }
        } catch (IOException ex) {
            log.warn("event=temp_delete_failed requestId={} path={} msg={}",
                    MDC.get("requestId"), path, ex.getMessage());
        }
    }
}