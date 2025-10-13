package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Converts stored files into embeddings and persists them.
 */
@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private final EmbeddingService embeddingService;
    private final FileEmbeddingRepository repository;

    public ProcessingService(EmbeddingService embeddingService,
                             FileEmbeddingRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    @Async("asyncExecutor")
    @Transactional
    public void processAsync(Path filePath) {
        try {
            File file = filePath.toFile();
            FileType type = FileTypeResolver.resolve(file.getName());

            String text = extractContent(file);
            if (text == null || text.isBlank()) {
                log.warn("No text extracted from {} – skipping.", file.getName());
                return;
            }

            float[] vector = embeddingService.generateEmbedding(text);
            repository.save(new FileEmbedding(
                    file.getName(), type, vector, text));
            log.debug("Stored embedding content {}", text);
            log.info("Stored embedding for {}", file.getName());
        } catch (Exception e) {
            log.error("Failed to process {}", filePath, e);
        } finally {
            // Remove temporary file regardless of outcome
            try {
                FileUtils.forceDelete(filePath.toFile());
            } catch (IOException ex) {
                log.warn("Unable to delete temp {}", filePath, ex);
            }
        }
    }

    /* simple text extraction – can be extended per file type */
    private String extractContent(File file) throws IOException {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }
}
