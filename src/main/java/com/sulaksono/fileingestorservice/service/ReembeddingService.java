package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Re-generates embeddings when metadata (e.g. deprecated flag) changes.
 * Runs async so the admin endpoint returns immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReembeddingService {

    private final FileEmbeddingRepository repo;
    private final EmbeddingService embedSvc;

    @Async("asyncExecutor")
    @Transactional
    public void reembedModule(String module) {
        log.info("event=reembed_start reembedId={} module={}", MDC.get("requestId"), module);
        try {
            List<FileEmbedding> rows = repo.findByModule(module);
            log.debug("event=row_fetch reembedId={} module={} rows={}", MDC.get("requestId"), module, rows.size());

            rows.forEach(this::reembedRow);

            log.info("event=reembed_complete reembedId={} module={} rows={}", MDC.get("requestId"), module, rows.size());
        } catch (Exception e) {
            log.error("event=reembed_error reembedId={} module={} error={}", MDC.get("requestId"), module, e, e);
            throw e;
        }
    }

    private void reembedRow(FileEmbedding row) {
        try {
            String header = """
            ### path: %s
            ### type: %s
            ### module: %s
            ### deprecated: %s
            ###
            """.formatted(row.getPath(), row.getFileType(), row.getModule(), row.isDeprecated());

            float[] vec = embedSvc.generateEmbedding(header + row.getContent());
            row.setEmbedding(vec);

            log.debug("event=row_reembedded reembedId={} fileName={}", MDC.get("requestId"), row.getFileName());
        } catch (Exception e) {
            log.error("event=row_reembed_error reembedId={} fileName={} error={}",
                    MDC.get("requestId"), row.getFileName(), e, e);
        }
    }
}