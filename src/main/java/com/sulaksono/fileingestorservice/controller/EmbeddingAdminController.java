package com.sulaksono.fileingestorservice.controller;

import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.service.ReembeddingService;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/embeddings")
@RequiredArgsConstructor
public class EmbeddingAdminController {

    private final FileEmbeddingRepository repo;
    private final ReembeddingService reembed;

    /* ------------------------------------------------------------------
       SOFT delete or restore a whole module
       ------------------------------------------------------------------ */
    @PatchMapping("/module/{module}")
    public ResponseEntity<?> markDeprecated(@PathVariable String module,
                                            @RequestParam(defaultValue = "true") boolean deprecated) {

        int rows = repo.markDeprecatedByModule(module, deprecated);
        reembed.reembedModule(module);            // async regeneration
        return ResponseEntity.ok("updated: " + rows);
    }

    /* ------------------------------------------------------------------
       HARD delete – physical removal (irreversible)
       ------------------------------------------------------------------ */
    @DeleteMapping("/module/{module}")
    @Transactional
    public ResponseEntity<?> hardDelete(@PathVariable @NotBlank String module) {
        repo.deleteByModule(module);
        return ResponseEntity.ok("deleted all rows for module: " + module);
    }
}