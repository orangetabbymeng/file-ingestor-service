package com.sulaksono.fileingestorservice.service;

import com.sulaksono.fileingestorservice.config.VectorProperties;
import com.sulaksono.fileingestorservice.model.CanonicalFile;
import com.sulaksono.fileingestorservice.model.FileEmbedding;
import com.sulaksono.fileingestorservice.model.FileType;
import com.sulaksono.fileingestorservice.repository.CanonicalFileRepository;
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
 * Reads a file, stores canonical (full) metadata/content once, then splits into token windows (if needed),
 * embeds each chunk and stores one row per chunk.
 *
 * Notes:
 * - This implementation avoids relying on JPA "save" as an upsert with a random UUID id.
 * - It does a find-then-update using the NULL-safe lookup.
 * - It also links each FileEmbedding row to the CanonicalFile via canonicalFile FK.
 */
@Service
@RequiredArgsConstructor
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private static final int MODEL_TOKEN_LIMIT = 8191;

    private final EmbeddingService embeddingService;
    private final FileEmbeddingRepository embeddingRepository;
    private final CanonicalFileRepository canonicalFileRepository;
    private final VectorProperties vecProps;
    private final FileStorageService storage;

    @Async("asyncExecutor")
    @Transactional
    public void processAsync(Path filePath, String module, String moduleVersion, String repoCloneUrl, String repoRef, String pathInRepo) {
        String requestId = MDC.get("requestId");
        log.debug("event=processing_start requestId={} file={} module={} version={}",
                requestId, filePath, module, moduleVersion);

        try {
            String fileName = filePath.getFileName().toString();
            FileType type = FileTypeResolver.resolve(fileName);

            String rawText = Files.readString(filePath, StandardCharsets.UTF_8);
            log.debug("event=file_read requestId={} file={} size={}bytes",
                    requestId, filePath, rawText.length());

            if (rawText.isBlank()) {
                log.info("event=processing_skipped requestId={} file={} reason=empty_content",
                        requestId, filePath);
                return;
            }

            Path relative = storage.getRootDir().relativize(filePath).normalize();
            String trimmedPath = trimDepth(relative, vecProps.getIncludePathDepth());

            CanonicalFile canonical = upsertCanonicalFile(
                    requestId,
                    fileName,
                    trimmedPath,
                    module,
                    moduleVersion,
                    type,
                    repoCloneUrl,
                    repoRef,
                    pathInRepo,
                    rawText
            );

            List<String> chunks = splitIntoChunks(
                    rawText,
                    vecProps.getChunkSizeTokens(),
                    vecProps.getChunkOverlapTokens()
            );

            int originalChunks = chunks.size();
            int maxChunks = vecProps.getMaxChunksPerFile();

            if (maxChunks > 0 && originalChunks > maxChunks) {
                log.info("event=file_truncated requestId={} file={} originalChunks={} processedChunks={} skippedChunks={}",
                        requestId, trimmedPath, originalChunks, maxChunks, (originalChunks - maxChunks));
                chunks = new ArrayList<>(chunks.subList(0, maxChunks));
            }

            log.debug("event=token_split requestId={} file={} chunks={} window={} overlap={}",
                    requestId, filePath, chunks.size(),
                    vecProps.getChunkSizeTokens(), vecProps.getChunkOverlapTokens());

            persistEmbeddings(requestId, fileName, trimmedPath, module, moduleVersion, type, chunks, canonical);

            log.info("event=processing_complete requestId={} file={} module={} chunks={}",
                    requestId, trimmedPath, module, chunks.size());

        } catch (Exception e) {
            log.error("event=processing_error requestId={} file={} error={}",
                    requestId, filePath, e.getMessage(), e);
        } finally {
            deleteLocalFileQuietly(requestId, filePath);
        }
    }

    private CanonicalFile upsertCanonicalFile(String requestId,
                                              String fileName,
                                              String trimmedPath,
                                              String module,
                                              String moduleVersion,
                                              FileType type,
                                              String repoCloneUrl,
                                              String repoRef,
                                              String pathInRepo,
                                              String rawText) {

        CanonicalFile canonical = canonicalFileRepository
                .findByModuleAndModuleVersionAndPathNullSafe(module, moduleVersion, trimmedPath)
                .orElseGet(CanonicalFile::new);

        canonical.setFileName(fileName);
        canonical.setPath(trimmedPath);
        canonical.setModule(module);
        canonical.setModuleVersion(moduleVersion);
        canonical.setFileType(type);

        canonical.setRepoCloneUrl(repoCloneUrl);
        canonical.setRepoRef((repoRef == null || repoRef.isBlank()) ? "master" : repoRef);
        canonical.setPathInRepo(pathInRepo);

        // cache the full file content (agent can still prefer repo)
        canonical.setContent(rawText);
        canonical.setDeprecated(false);

        CanonicalFile saved = canonicalFileRepository.save(canonical);

        log.info("event=canonical_file_upserted requestId={} path={} module={} version={} id={}",
                requestId, trimmedPath, module, moduleVersion, saved.getId());

        return saved;
    }

    private void persistEmbeddings(String requestId,
                                   String fileName,
                                   String trimmedPath,
                                   String module,
                                   String moduleVersion,
                                   FileType type,
                                   List<String> chunks,
                                   CanonicalFile canonicalFile) {

        int total = chunks.size();

        for (int idx = 0; idx < total; idx++) {
            String chunk = chunks.get(idx);

            String header = """
                 ### path: %s
                 ### type: %s
                 ### module: %s
                 ### chunk: %d/%d
                 ### deprecated: false
                 ###
                 """.formatted(trimmedPath, type, module, idx, total);

            float[] vector = embeddingService.generateEmbedding(header + chunk);

            embeddingRepository.save(new FileEmbedding(
                    fileName,
                    trimmedPath,
                    module,
                    idx, total,
                    type,
                    vector,
                    chunk,
                    moduleVersion,
                    canonicalFile
            ));

            log.info("event=chunk_persisted requestId={} file={} chunkIdx={}/{}",
                    requestId, fileName, idx + 1, total);
        }
    }

    private void deleteLocalFileQuietly(String requestId, Path filePath) {
        try {
            if (Files.deleteIfExists(filePath)) {
                log.debug("event=file_delete requestId={} file={} result=deleted", requestId, filePath);
            } else {
                log.debug("event=file_delete requestId={} file={} result=not_found", requestId, filePath);
            }
        } catch (Exception e) {
            log.debug("event=file_delete_failed requestId={} file={} msg={}",
                    requestId, filePath, e.getMessage());
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
        int from = Math.max(0, parts - depth);
        return p.subpath(from, parts).toString().replace('\\', '/');
    }
}