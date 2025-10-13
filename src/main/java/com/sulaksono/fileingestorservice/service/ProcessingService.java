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

    private final EmbeddingService            embeddingService;
    private final FileEmbeddingRepository     repository;

    public ProcessingService(EmbeddingService embeddingService, FileEmbeddingRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    @Async("asyncExecutor")
    @Transactional
    public void processAsync(Path filePath, String module) {
        try {
            File file = filePath.toFile();
            FileType type = FileTypeResolver.resolve(file.getName());

            String text = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                log.warn("Empty content in {} – skipping", file.getName());
                return;
            }

            /* ------- build embedding payload with header --------------- */
            String header = """
                    ### path: %s
                    ### type: %s
                    ### module: %s
                    ###
                    """.formatted(filePath.toString(), type, module);
            String payload = header + text;

            float[] vector = embeddingService.generateEmbedding(payload);

            repository.save(new FileEmbedding(
                    file.getName(),
                    filePath.toString(),
                    module,
                    type,
                    vector,
                    text));

            log.info("Stored embedding for {} (module={})", file.getName(), module);
        } catch (Exception e) {
            log.error("Failed to process {}", filePath, e);
        } finally {
            try { FileUtils.forceDelete(filePath.toFile()); }
            catch (IOException ex) { log.warn("Unable to delete {}", filePath, ex); }
        }
    }
}