package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.repository.CanonicalFileRepository;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.service.ReembeddingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administrative API for managing generated embeddings.
 *
 * Supports:
 * - soft delete / restore by module
 * - hard delete by module
 */
@RestController
@RequestMapping("/api/embeddings")
@RequiredArgsConstructor
@Validated
public class EmbeddingAdminController {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingAdminController.class);

    private static final String MDC_REQUEST_ID = "requestId";

    private final FileEmbeddingRepository embeddingRepository;
    private final CanonicalFileRepository canonicalFileRepository;
    private final ReembeddingService reembed;

    /**
     * Soft delete or restore all embeddings for a module.
     *
     * This marks embeddings as deprecated or active again, then triggers
     * re-embedding for the affected module.
     */
    @PatchMapping("/module/{module}")
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasAnyRole('embedding-user','embedding-admin','assistant-admin')")
    public ResponseEntity<String> markDeprecated(
            @PathVariable @NotBlank String module,
            @RequestParam(defaultValue = "true") boolean deprecated) {

        final String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);

        log.info(
                "event=mark_deprecated_start requestId={} module={} deprecated={}",
                requestId,
                module,
                deprecated
        );

        try {
            int rows = embeddingRepository.markDeprecatedByModule(module, deprecated);

            log.debug(
                    "event=db_update requestId={} module={} rowsAffected={}",
                    requestId,
                    module,
                    rows
            );

            log.debug(
                    "event=reembed_trigger requestId={} module={}",
                    requestId,
                    module
            );

            reembed.reembedModule(module);

            log.info(
                    "event=mark_deprecated_complete requestId={} module={} rowsAffected={}",
                    requestId,
                    module,
                    rows
            );

            return ResponseEntity.ok("updated: " + rows);
        } catch (Exception e) {
            log.error(
                    "event=mark_deprecated_error requestId={} module={} message={}",
                    requestId,
                    module,
                    e.getMessage(),
                    e
            );
            throw e;
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /**
     * Hard delete all embeddings and canonical file records for a module.
     *
     * This operation is irreversible.
     */
    @DeleteMapping("/module/{module}")
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasAnyRole('embedding-user','embedding-admin','assistant-admin')")
    @Transactional
    public ResponseEntity<String> hardDelete(
            @PathVariable @NotBlank String module) {

        final String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);

        log.info(
                "event=hard_delete_start requestId={} module={}",
                requestId,
                module
        );

        try {
            embeddingRepository.deleteByModule(module);
            canonicalFileRepository.deleteByModule(module);

            log.info(
                    "event=hard_delete_complete requestId={} module={}",
                    requestId,
                    module
            );

            return ResponseEntity.ok("deleted all rows for module: " + module);
        } catch (Exception e) {
            log.error(
                    "event=hard_delete_error requestId={} module={} message={}",
                    requestId,
                    module,
                    e.getMessage(),
                    e
            );
            throw e;
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}