package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.config.VectorProperties;
import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts stored files into embeddings and persists them.
 */
@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private final EmbeddingService embeddingService;
    private final FileEmbeddingRepository repository;
    private final VectorProperties vecProps;
    private final FileStorageService storage;

    public ProcessingService(EmbeddingService embeddingService, FileEmbeddingRepository repository, VectorProperties vecProps, FileStorageService storage) {
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.vecProps = vecProps;
        this.storage = storage;
    }

    @Async("asyncExecutor")
    @Transactional
    public void processAsync(Path filePath, String module) {

        try {
            FileType type = FileTypeResolver.resolve(filePath.getFileName().toString());
            String   text = Files.readString(filePath, StandardCharsets.UTF_8);
            if (text.isBlank()) return;

            Path relative = storage.getRootDir().relativize(filePath).normalize();
            String trimmed = trimDepth(relative, vecProps.getIncludePathDepth());

            String header = """
                    ### path: %s
                    ### type: %s
                    ### module: %s
                    ### deprecated: false
                    ###
                    """.formatted(trimmed, type, module);

            float[] vector = embeddingService.generateEmbedding(header + text);

            repository.save(new FileEmbedding(
                    filePath.getFileName().toString(),   // fileName
                    trimmed,                             // path
                    module,
                    type,
                    vector,
                    text,
                    false));

            log.info("Stored embedding for {} (module={})", trimmed, module);

        } catch (Exception e) {
            log.error("Failed to process {}", filePath, e);
        } finally {
            try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
        }
    }

    /* helper ----------------------------------------------------------- */
    private String trimDepth(Path p, int depth) {
        if (depth <= 0) return p.getFileName().toString();

        int parts = p.getNameCount();
        int from  = Math.max(0, parts - depth);
        return p.subpath(from, parts).toString().replace('\\', '/');
    }
}