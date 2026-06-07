package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import com.sulaksono.fileingestorservice.util.ZipGradleUtil;
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
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasAnyRole('embedding-user','embedding-admin','assistant-admin')")
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("files") @NotEmpty MultipartFile[] files,
            @RequestPart(name = "module", required = false) String module,
            @RequestPart(name = "fileVersion", required = false) String fileVersion,
            @RequestPart(name = "repoCloneUrl",required = false) String repoCloneUrl,
            @RequestPart(name = "repoRef", required = false) String repoRef,
            @RequestPart(name = "pathInRepo", required = false) String pathInRepo) {

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

                String rejectionReason = validate(file, original, fileVersion);
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
                            final String derivedModule = resolveZipModule(module, zipPath);
                            final String derivedVersion = resolveZipVersion(fileVersion, zipPath);
                            log.debug("event=zip_metadata requestId={} derivedModule={} derivedVersion={}",
                                    requestId, derivedModule, derivedVersion);

                            if (!StringUtils.hasText(derivedVersion)) {
                                rejected.add(original + " (missing version in package and request)");
                                continue;
                            }

                            Path baseDir = Optional.ofNullable(zipPath.getParent()).orElse(Path.of("."));

                            Path extractDir = Files.createTempDirectory(baseDir, "unzipped-");

                            ZipUtil.unzip(zipPath, extractDir).stream()
                                    .filter(Files::isRegularFile)
                                    .filter(p -> FileTypeResolver.resolve(p.getFileName().toString()) != FileType.UNKNOWN)
                                    .forEach(p -> processor.processAsync(p, derivedModule, derivedVersion, repoCloneUrl, repoRef, pathInRepo));

                            accepted.add(original);
                            log.info("event=zip_file_accepted requestId={} fileName={}", requestId, original);
                        } finally {
                            deleteQuietly(zipPath);
                        }
                    } else {                                          // regular file
                        Path stored = storage.save(file);
                        log.debug("event=file_saved requestId={} path={}", requestId, stored);
                        String effectiveModule = resolveModuleForFile(module, false, null);
                        processor.processAsync(stored, effectiveModule, fileVersion, repoCloneUrl, repoRef, pathInRepo);
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

    public record UploadResponse(List<String> accepted, List<String> rejected) {
    }

    private String validate(MultipartFile file, String originalName, String requestedVersion) {

        if (!StringUtils.hasText(originalName)) return "Unnamed file";
        if (file.isEmpty()) return originalName + " (empty)";

        boolean isZip = originalName.toLowerCase().endsWith(".zip");

        // Non-zip: must have a version
        if (!isZip && !StringUtils.hasText(requestedVersion)) {
            return originalName + " (missing fileVersion)";
        }

        if (!isZip && FileTypeResolver.resolve(originalName) == FileType.UNKNOWN) {
            return originalName + " (unsupported type)";
        }

        return null;
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

    private String resolveModuleVersion(String requestedVersion, Path zipPath) {
        String fallback = StringUtils.hasText(requestedVersion) ? requestedVersion : null;

        try (InputStream in = Files.newInputStream(zipPath)) {
            String version = ZipPomUtil.extractVersion(in);
            return StringUtils.hasText(version) ? version : fallback;
        } catch (IOException e) {
            return fallback;
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

    private String resolveModuleForFile(String requestedModule, boolean isZip, Path zipPath) {

        boolean deriveFromPackage = "from-package".equalsIgnoreCase(requestedModule);

        // Rule 1: if provided AND not "from-package" -> use it
        if (StringUtils.hasText(requestedModule) && !deriveFromPackage) {
            return requestedModule;
        }

        // Rule 2: if not zip -> "undefined" (or derive from filename if you want)
        if (!isZip) {
            return "undefined";
        }

        // Rule 3: zip + (module missing OR from-package) -> derive from package
        try (InputStream in = Files.newInputStream(zipPath)) {
            String art = ZipPomUtil.extractArtifactId(in); // extend later to Gradle/composer/pyproject/Cargo/csproj
            return (art != null && !art.isBlank()) ? art : "undefined";
        } catch (IOException e) {
            return "undefined";
        }
    }

    private String resolveZipModule(String requestedModule, Path zipPath) {
        boolean fromPackage = "from-package".equalsIgnoreCase(requestedModule);

        // if explicitly provided and not from-package -> use it
        if (StringUtils.hasText(requestedModule) && !fromPackage) return requestedModule;

        // derive from ZIP (pom first, then gradle)
        String fromPom;
        try (InputStream in = Files.newInputStream(zipPath)) {
            fromPom = ZipPomUtil.extractArtifactId(in);
        } catch (IOException e) {
            fromPom = null;
        }
        if (StringUtils.hasText(fromPom)) return fromPom;

        String fromGradle;
        try (InputStream in = Files.newInputStream(zipPath)) {
            fromGradle = ZipGradleUtil.extractRootProjectName(in);
        } catch (IOException e) {
            fromGradle = null;
        }
        return StringUtils.hasText(fromGradle) ? fromGradle : "undefined";
    }

    private String resolveZipVersion(String requestedVersion, Path zipPath) {
        // derive from ZIP (pom first, then gradle), fallback to request version
        String fromPom;
        try (InputStream in = Files.newInputStream(zipPath)) {
            fromPom = ZipPomUtil.extractVersion(in);
        } catch (IOException e) {
            fromPom = null;
        }
        if (StringUtils.hasText(fromPom)) return fromPom;

        String fromGradle;
        try (InputStream in = Files.newInputStream(zipPath)) {
            fromGradle = ZipGradleUtil.extractVersion(in);
        } catch (IOException e) {
            fromGradle = null;
        }
        if (StringUtils.hasText(fromGradle)) return fromGradle;

        return StringUtils.hasText(requestedVersion) ? requestedVersion : null;
    }
}