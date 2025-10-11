package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import com.sulaksono.fileingestorservice.util.ZipUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    @PostMapping(value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestPart("files") @NotEmpty MultipartFile[] files) {

        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (MultipartFile f : files) {

            /* -------------------------------------------------------------
             * 1. Validate filename presence (avoid NPE on toLowerCase())   */
            /* ----------------------------------------------------------- */
            String originalName = f.getOriginalFilename();
            if (!StringUtils.hasText(originalName)) {
                rejected.add("Unnamed file (empty filename)");
                continue;
            }

            /* -------------------------------------------------------------
             * 2. Reject empty files early                                 */
            /* ----------------------------------------------------------- */
            if (f.isEmpty()) {
                rejected.add(originalName + " (empty)");
                continue;
            }

            boolean isZip = originalName.toLowerCase().endsWith(".zip");

            /* -------------------------------------------------------------
             * 3. Extension whitelist (ignore unsupported direct uploads)   */
            /* ----------------------------------------------------------- */
            if (!isZip && FileTypeResolver.resolve(originalName) == FileType.UNKNOWN) {
                rejected.add(originalName + " (unsupported type)");
                continue;
            }

            try {
                if (isZip) {
                    Path zipPath = storage.save(f);              // persist ZIP itself

                    /* Extract & filter unsupported entries */
                    ZipUtil.unzip(f, zipPath.getParent()).stream()
                            .filter(p -> FileTypeResolver.resolve(p.getFileName().toString()) != FileType.UNKNOWN)
                            .forEach(processor::processAsync);

                    accepted.add(originalName);
                } else {
                    Path stored = storage.save(f);
                    processor.processAsync(stored);
                    accepted.add(originalName);
                }
            } catch (Exception e) {
                log.error("Upload failed for {}", originalName, e);
                rejected.add(originalName + " (" + e.getMessage() + ")");
            }
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new UploadResponse(accepted, rejected));
    }

    /* Public record to avoid “exposed outside its defined visibility” warning */
    public record UploadResponse(List<String> accepted, List<String> rejected) { }
}