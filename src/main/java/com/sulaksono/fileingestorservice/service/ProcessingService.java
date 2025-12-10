package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.config.VectorProperties;
import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.repository.FileEmbeddingRepository;
import com.sulaksono.fileingestorservice.util.FileTypeResolver;
import com.sulaksono.fileingestorservice.util.TokenizerUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a file, splits it into token windows if it exceeds the model limit,
 * embeds each chunk and stores one row per chunk.
 */
@Service
@RequiredArgsConstructor
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private static final int MODEL_TOKEN_LIMIT = 8191;

    private final EmbeddingService         embeddingService;
    private final FileEmbeddingRepository  repository;
    private final VectorProperties         vecProps;
    private final FileStorageService       storage;

    @Async("asyncExecutor")
    @Transactional
    public void processAsync(Path filePath, String module, String moduleVersion) {
        String requestId = MDC.get("requestId");
        log.debug("event=processing_start requestId={} file={} module={} version={}",
                requestId, filePath, module, moduleVersion);

        try {
            FileType type    = FileTypeResolver.resolve(filePath.getFileName().toString());

            String   rawText = Files.readString(filePath, StandardCharsets.UTF_8);
            log.debug("event=file_read requestId={} file={} size={}bytes",
                    requestId, filePath, rawText.length());

            if (rawText.isBlank()) {
                log.info("event=processing_skipped requestId={} file={} reason=empty_content",
                        requestId, filePath);
                return;
            }

            /* -------- derive trimmed (relative) path ------------------ */
            Path   relative = storage.getRootDir().relativize(filePath).normalize();
            String trimmed  = trimDepth(relative, vecProps.getIncludePathDepth());

            /* -------- split into token windows ------------------------ */
            List<String> chunks = splitIntoChunks(rawText,
                    vecProps.getChunkSizeTokens(),
                    vecProps.getChunkOverlapTokens());

            log.debug("event=token_split requestId={} file={} chunks={} window={} overlap={}",
                    requestId, filePath, chunks.size(),
                    vecProps.getChunkSizeTokens(), vecProps.getChunkOverlapTokens());

            for (int idx = 0; idx < chunks.size(); idx++) {
                String chunk = chunks.get(idx);

                String header = """
                 ### path: %s
                 ### type: %s
                 ### module: %s
                 ### chunk: %d/%d
                 ### deprecated: false
                 ###
                 """.formatted(trimmed, type, module, idx, chunks.size());

                float[] vector = embeddingService.generateEmbedding(header + chunk);

                repository.save(new FileEmbedding(
                        filePath.getFileName().toString(), // fileName
                        trimmed,                           // path
                        module,
                        idx, chunks.size(),
                        type,
                        vector,
                        chunk,
                        moduleVersion
                ));

                log.info("event=chunk_persisted requestId={} file={} chunkIdx={}/{}",
                        requestId, filePath.getFileName(), idx + 1, chunks.size());
            }

            log.info("event=processing_complete requestId={} file={} module={} chunks={}",
                    requestId, trimmed, module, chunks.size());

        } catch (Exception e) {
            log.error("event=processing_error requestId={} file={} error={}",
                    requestId, filePath, e, e);
        } finally {
            try {
                if (Files.deleteIfExists(filePath)) {
                    log.debug("event=file_delete requestId={} file={} result=deleted",
                            requestId, filePath);
                } else {
                    log.debug("event=file_delete requestId={} file={} result=not_found",
                            requestId, filePath);
                }
            } catch (Exception e) {
                log.debug("event=file_delete_failed requestId={} file={} msg={}",
                        requestId, filePath, e.getMessage());
            }
        }
    }

    private List<String> splitIntoChunks(String text, int window, int overlap) {
        int totalTokens = TokenizerUtil.countTokens(text);
        if (totalTokens <= MODEL_TOKEN_LIMIT) return List.of(text);

        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < totalTokens) {
            int end = Math.min(start + window, totalTokens);
            out.add(TokenizerUtil.slice(text, start, end));
            if (end == totalTokens) break;
            start = end - overlap;
        }
        return out;
    }

    private String trimDepth(Path p, int depth) {
        if (depth <= 0) return p.getFileName().toString();
        int parts = p.getNameCount();
        int from  = Math.max(0, parts - depth);
        return p.subpath(from, parts).toString().replace('\\', '/');
    }
}