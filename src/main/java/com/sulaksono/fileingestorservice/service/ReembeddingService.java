package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Re-generates embeddings when metadata changes, for example deprecated flag updates.
 *
 * Runs asynchronously so the admin endpoint can return immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReembeddingService {

    private static final String MDC_REQUEST_ID = "requestId";

    private final FileEmbeddingRepository repo;
    private final EmbeddingService embedSvc;

    @Async("asyncExecutor")
    @Transactional
    public void reembedModule(String module) {
        String requestId = MDC.get(MDC_REQUEST_ID);

        validateModule(module);

        log.info(
                "event=reembed_start requestId={} module={}",
                requestId,
                module
        );

        try {
            List<FileEmbedding> rows = safeRows(repo.findByModule(module));

            log.debug(
                    "event=row_fetch requestId={} module={} rows={}",
                    requestId,
                    module,
                    rows.size()
            );

            for (FileEmbedding row : rows) {
                reembedRow(requestId, row);
            }

            log.info(
                    "event=reembed_complete requestId={} module={} rows={}",
                    requestId,
                    module,
                    rows.size()
            );
        } catch (Exception e) {
            log.error(
                    "event=reembed_error requestId={} module={} message={}",
                    requestId,
                    module,
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }

    private void validateModule(String module) {
        if (!StringUtils.hasText(module)) {
            throw new IllegalArgumentException("module cannot be blank");
        }
    }

    private List<FileEmbedding> safeRows(List<FileEmbedding> rows) {
        return rows == null ? Collections.emptyList() : rows;
    }

    private void reembedRow(String requestId, FileEmbedding row) {
        if (row == null) {
            log.warn("event=row_reembed_skipped requestId={} reason=null_row", requestId);
            return;
        }

        try {
            String content = row.getContent() == null ? "" : row.getContent();

            String header = """
                    ### path: %s
                    ### type: %s
                    ### module: %s
                    ### deprecated: %s
                    ###
                    """.formatted(
                    row.getPath(),
                    row.getFileType(),
                    row.getModule(),
                    row.isDeprecated()
            );

            float[] vector = embedSvc.generateEmbedding(header + content);

            row.setEmbedding(vector);
            repo.save(row);

            log.debug(
                    "event=row_reembedded requestId={} fileName={} path={}",
                    requestId,
                    row.getFileName(),
                    row.getPath()
            );
        } catch (Exception e) {
            log.error(
                    "event=row_reembed_error requestId={} fileName={} message={}",
                    requestId,
                    row.getFileName(),
                    e.getMessage(),
                    e
            );
        }
    }
}