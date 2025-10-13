package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.service.FileStorageService;
import com.sulaksono.fileingestorservice.service.ProcessingService;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import com.sulaksono.fileingestorservice.util.ZipUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
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
    @PostMapping(
            value    = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("files")  @NotEmpty MultipartFile[] files,
            @RequestPart("module") @NotBlank  String        module) {

        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (MultipartFile file : files) {

            String original = file.getOriginalFilename();
            if (!StringUtils.hasText(original)) {
                rejected.add("Unnamed file (empty filename)");
                continue;
            }

            if (file.isEmpty()) {
                rejected.add(original + " (empty)");
                continue;
            }

            boolean isZip = original.toLowerCase().endsWith(".zip");

            if (!isZip && FileTypeResolver.resolve(original) == FileType.UNKNOWN) {
                rejected.add(original + " (unsupported type)");
                continue;
            }

            try {
                if (isZip) {
                    Path zipPath = storage.save(file);                       // persist the ZIP
                    ZipUtil.unzip(file, zipPath.getParent()).stream()
                            .filter(p -> FileTypeResolver.resolve(p.getFileName().toString()) != FileType.UNKNOWN)
                            .forEach(p -> processor.processAsync(p, module)); // async per entry
                    accepted.add(original);
                } else {
                    Path stored = storage.save(file);
                    processor.processAsync(stored, module);                  // async
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
}