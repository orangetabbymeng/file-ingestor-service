package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        List<FileEmbedding> rows = repo.findByModule(module);
        rows.forEach(this::reembedRow);
    }

    private void reembedRow(FileEmbedding row) {
        String header = """
            ### path: %s
            ### type: %s
            ### module: %s
            ### deprecated: %s
            ###
            """.formatted(row.getPath(), row.getFileType(), row.getModule(), row.isDeprecated());

        float[] vec = embedSvc.generateEmbedding(header + row.getContent());
        row.setEmbedding(vec);
        log.debug("Re-embedded {}", row.getFileName());
    }
}