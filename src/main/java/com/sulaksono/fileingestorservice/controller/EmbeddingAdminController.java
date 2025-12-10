package com.sulaksono.fileingestorservice.controller;

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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/embeddings")
@RequiredArgsConstructor
public class EmbeddingAdminController {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingAdminController.class);

    private final FileEmbeddingRepository repo;
    private final ReembeddingService reembed;

    /* ------------------------------------------------------------------
       SOFT delete or restore a whole module
       ------------------------------------------------------------------ */
    @PreAuthorize("hasRole('DEPRECATE')")
    @SecurityRequirement(name = "basicAuth")
    @PatchMapping("/module/{module}")
    public ResponseEntity<?> markDeprecated(@PathVariable String module,
                                            @RequestParam(defaultValue = "true") boolean deprecated) {

        final String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.info("event=mark_deprecated_start requestId={} module={} deprecated={}",
                requestId, module, deprecated);

        try {
            int rows = repo.markDeprecatedByModule(module, deprecated);
            log.debug("event=db_update requestId={} module={} rowsAffected={}",
                    requestId, module, rows);

            log.debug("event=reembed_trigger requestId={} module={}", requestId, module);
            reembed.reembedModule(module);

            log.info("event=mark_deprecated_complete requestId={} module={} rowsAffected={}",
                    requestId, module, rows);
            return ResponseEntity.ok("updated: " + rows);
        } catch (Exception e) {
            log.error("event=mark_deprecated_error requestId={} module={} error={}",
                    requestId, module, e, e);
            throw e;
        } finally {
            MDC.remove("requestId");
        }
    }

    /* ------------------------------------------------------------------
       HARD delete – physical removal (irreversible)
       ------------------------------------------------------------------ */
    @DeleteMapping("/module/{module}")
    @SecurityRequirement(name = "basicAuth")
    @PreAuthorize("hasRole('DELETE')")
    @Transactional
    public ResponseEntity<?> hardDelete(@PathVariable @NotBlank String module) {
        final String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        log.info("event=hard_delete_start requestId={} module={}", requestId, module);

        try {
            repo.deleteByModule(module);
            log.info("event=hard_delete_complete requestId={} module={}", requestId, module);
            return ResponseEntity.ok("deleted all rows for module: " + module);
        } catch (Exception e) {
            log.error("event=hard_delete_error requestId={} module={} error={}",
                    requestId, module, e, e);
            throw e;
        } finally {
            MDC.remove("requestId");
        }
    }
}