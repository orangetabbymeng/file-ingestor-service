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
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoint for receiving individual files or ZIP batches.
 */
@RestController
@RequestMapping("/api/files")
@Validated
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String FROM_PACKAGE   = "from-package";
    private static final String DEFAULT_MODULE = "undefined";
    private static final String ZIP_SUFFIX     = ".zip";

    private final FileStorageService storage;
    private final ProcessingService processor;

    public FileUploadController(FileStorageService storage, ProcessingService processor) {
        this.storage   = storage;
        this.processor = processor;
    }

    @Operation(summary = "Upload one or many files or ZIP archives")
    @PostMapping(
            value    = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasAnyRole('embedding-user','embedding-admin','assistant-admin')")
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("files") @NotEmpty MultipartFile[] files,
            @RequestPart(name = "module",       required = false) String module,
            @RequestPart(name = "fileVersion",  required = false) String fileVersion,
            @RequestPart(name = "repoCloneUrl", required = false) String repoCloneUrl,
            @RequestPart(name = "repoRef",      required = false) String repoRef,
            @RequestPart(name = "pathInRepo",   required = false) String pathInRepo) {

        final String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            log.info("event=upload_start requestId={} module={} version={} fileCount={}",
                    requestId, module, fileVersion, files.length);

            List<String> accepted = new ArrayList<>();
            List<String> rejected = new ArrayList<>();

            for (MultipartFile file : files) {
                handleFile(file, module, fileVersion, repoCloneUrl, repoRef, pathInRepo,
                        accepted, rejected, requestId);
            }

            log.info("event=upload_complete requestId={} acceptedCount={} rejectedCount={}",
                    requestId, accepted.size(), rejected.size());

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new UploadResponse(accepted, rejected));
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    // ---------- per-file pipeline ----------

    private void handleFile(MultipartFile file,
                            String module, String fileVersion,
                            String repoCloneUrl, String repoRef, String pathInRepo,
                            List<String> accepted, List<String> rejected,
                            String requestId) {

        final String original = file.getOriginalFilename();
        log.debug("event=file_received requestId={} fileName={} size={}",
                requestId, original, file.getSize());

        String rejection = validate(file, original, fileVersion);
        if (rejection != null) {
            log.warn("event=file_rejected requestId={} reason=\"{}\"", requestId, rejection);
            rejected.add(rejection);
            return;
        }

        boolean isZip = isZip(original);
        log.debug("event=file_validated requestId={} fileName={} isZip={}",
                requestId, original, isZip);

        try {
            if (isZip) {
                handleZip(file, original, module, fileVersion,
                        repoCloneUrl, repoRef, pathInRepo, accepted, rejected, requestId);
            } else {
                handleRegular(file, original, module, fileVersion,
                        repoCloneUrl, repoRef, pathInRepo, accepted, requestId);
            }
        } catch (Exception e) {
            log.error("event=file_failure requestId={} fileName={} error={}",
                    requestId, original, e.getMessage(), e);
            rejected.add(original + " (" + e.getMessage() + ")");
        }
    }

    private void handleRegular(MultipartFile file, String original,
                               String module, String fileVersion,
                               String repoCloneUrl, String repoRef, String pathInRepo,
                               List<String> accepted, String requestId) throws IOException {
        Path stored = storage.save(file);
        log.debug("event=file_saved requestId={} path={}", requestId, stored);

        String effectiveModule = StringUtils.hasText(module) && !FROM_PACKAGE.equalsIgnoreCase(module)
                ? module
                : DEFAULT_MODULE;

        processor.processAsync(stored, effectiveModule, fileVersion, repoCloneUrl, repoRef, pathInRepo);
        accepted.add(original);
        log.info("event=file_accepted requestId={} fileName={}", requestId, original);
    }

    private void handleZip(MultipartFile file, String original,
                           String module, String fileVersion,
                           String repoCloneUrl, String repoRef, String pathInRepo,
                           List<String> accepted, List<String> rejected,
                           String requestId) throws IOException {

        Path zipPath = storage.save(file);
        log.debug("event=zip_saved requestId={} path={}", requestId, zipPath);

        Path extractDir = null;
        try {
            String derivedModule  = resolveZipModule(module, zipPath);
            String derivedVersion = resolveZipVersion(fileVersion, zipPath);
            log.debug("event=zip_metadata requestId={} derivedModule={} derivedVersion={}",
                    requestId, derivedModule, derivedVersion);

            if (!StringUtils.hasText(derivedVersion)) {
                rejected.add(original + " (missing version in package and request)");
                return;
            }

            Path baseDir = Optional.ofNullable(zipPath.getParent()).orElse(Path.of("."));
            extractDir   = Files.createTempDirectory(baseDir, "unzipped-");

            ZipUtil.unzip(zipPath, extractDir).stream()
                    .filter(Files::isRegularFile)
                    .filter(p -> FileTypeResolver.resolve(p.getFileName().toString()) != FileType.UNKNOWN)
                    .forEach(p -> processor.processAsync(p, derivedModule, derivedVersion,
                            repoCloneUrl, repoRef, pathInRepo));

            accepted.add(original);
            log.info("event=zip_file_accepted requestId={} fileName={}", requestId, original);
        } finally {
            deleteQuietly(zipPath);
            deleteDirQuietly(extractDir);
        }
    }

    // ---------- validation ----------

    private String validate(MultipartFile file, String originalName, String requestedVersion) {
        if (!StringUtils.hasText(originalName)) return "Unnamed file";
        if (file.isEmpty())                     return originalName + " (empty)";

        boolean isZip = isZip(originalName);
        if (!isZip && !StringUtils.hasText(requestedVersion)) {
            return originalName + " (missing fileVersion)";
        }
        if (!isZip && FileTypeResolver.resolve(originalName) == FileType.UNKNOWN) {
            return originalName + " (unsupported type)";
        }
        return null;
    }

    private static boolean isZip(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX);
    }

    // ---------- zip metadata resolution ----------

    private String resolveZipModule(String requestedModule, Path zipPath) {
        boolean fromPackage = FROM_PACKAGE.equalsIgnoreCase(requestedModule);
        if (StringUtils.hasText(requestedModule) && !fromPackage) {
            return requestedModule;
        }

        String fromPom = readFromZip(zipPath, ZipPomUtil::extractArtifactId);
        if (StringUtils.hasText(fromPom)) return fromPom;

        String fromGradle = readFromZip(zipPath, ZipGradleUtil::extractRootProjectName);
        return StringUtils.hasText(fromGradle) ? fromGradle : DEFAULT_MODULE;
    }

    private String resolveZipVersion(String requestedVersion, Path zipPath) {
        String fromPom = readFromZip(zipPath, ZipPomUtil::extractVersion);
        if (StringUtils.hasText(fromPom)) return fromPom;

        String fromGradle = readFromZip(zipPath, ZipGradleUtil::extractVersion);
        if (StringUtils.hasText(fromGradle)) return fromGradle;

        return StringUtils.hasText(requestedVersion) ? requestedVersion : null;
    }

    @FunctionalInterface
    private interface ZipReader {
        String read(InputStream in) throws IOException;
    }

    private String readFromZip(Path zipPath, ZipReader reader) {
        try (InputStream in = Files.newInputStream(zipPath)) {
            return reader.read(in);
        } catch (IOException e) {
            log.warn("event=zip_inspect_failed requestId={} zip={} msg={}",
                    MDC.get(MDC_REQUEST_ID), zipPath.getFileName(), e.getMessage());
            return null;
        }
    }

    // ---------- cleanup ----------

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            if (Files.deleteIfExists(path)) {
                log.debug("event=temp_deleted requestId={} path={}", MDC.get(MDC_REQUEST_ID), path);
            }
        } catch (IOException ex) {
            log.warn("event=temp_delete_failed requestId={} path={} msg={}",
                    MDC.get(MDC_REQUEST_ID), path, ex.getMessage());
        }
    }

    private void deleteDirQuietly(Path dir) {
        if (dir == null) return;
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ex) {
            log.warn("event=temp_dir_delete_failed requestId={} path={} msg={}",
                    MDC.get(MDC_REQUEST_ID), dir, ex.getMessage());
        }
    }

    public record UploadResponse(List<String> accepted, List<String> rejected) { }
}