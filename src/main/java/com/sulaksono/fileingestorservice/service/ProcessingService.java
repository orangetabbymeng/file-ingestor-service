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
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private static final String MDC_REQUEST_ID = "requestId";
    private static final int MODEL_TOKEN_LIMIT = 8191;
    private static final String DEFAULT_REPO_REF = "master";

    private final EmbeddingService embeddingService;
    private final FileEmbeddingRepository embeddingRepository;
    private final CanonicalFileRepository canonicalFileRepository;
    private final VectorProperties vecProps;
    private final FileStorageService storage;

    @Async("asyncExecutor")
    @Transactional
    public void processAsync(
            Path filePath,
            String module,
            String moduleVersion,
            String repoCloneUrl,
            String repoRef,
            String pathInRepo) {

        String requestId = MDC.get(MDC_REQUEST_ID);

        log.debug(
                "event=processing_start requestId={} file={} module={} version={}",
                requestId,
                filePath,
                module,
                moduleVersion
        );

        try {
            validateInput(filePath, moduleVersion);

            String fileName = filePath.getFileName().toString();
            FileType type = FileTypeResolver.resolve(fileName);

            String rawText = Files.readString(filePath, StandardCharsets.UTF_8);

            log.debug(
                    "event=file_read requestId={} file={} size={}chars",
                    requestId,
                    filePath,
                    rawText.length()
            );

            if (rawText.isBlank()) {
                log.info(
                        "event=processing_skipped requestId={} file={} reason=empty_content",
                        requestId,
                        filePath
                );
                return;
            }

            String trimmedPath = resolveTrimmedPath(filePath);

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

            chunks = applyMaxChunksLimit(
                    requestId,
                    trimmedPath,
                    chunks,
                    vecProps.getMaxChunksPerFile()
            );

            log.debug(
                    "event=token_split requestId={} file={} chunks={} window={} overlap={}",
                    requestId,
                    filePath,
                    chunks.size(),
                    vecProps.getChunkSizeTokens(),
                    vecProps.getChunkOverlapTokens()
            );

            persistEmbeddings(
                    requestId,
                    fileName,
                    trimmedPath,
                    module,
                    moduleVersion,
                    type,
                    chunks,
                    canonical
            );

            log.info(
                    "event=processing_complete requestId={} file={} module={} chunks={}",
                    requestId,
                    trimmedPath,
                    module,
                    chunks.size()
            );

        } catch (Exception e) {
            log.error(
                    "event=processing_error requestId={} file={} message={}",
                    requestId,
                    filePath,
                    e.getMessage(),
                    e
            );
        } finally {
            deleteLocalFileQuietly(requestId, filePath);
        }
    }

    private void validateInput(Path filePath, String moduleVersion) {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath cannot be null");
        }

        if (!StringUtils.hasText(moduleVersion)) {
            throw new IllegalArgumentException("moduleVersion cannot be blank");
        }
    }

    private String resolveTrimmedPath(Path filePath) {
        Path rootDir = storage.getRootDir();

        Path relative;
        if (rootDir != null && filePath.toAbsolutePath().normalize().startsWith(rootDir.toAbsolutePath().normalize())) {
            relative = rootDir.toAbsolutePath().normalize()
                    .relativize(filePath.toAbsolutePath().normalize());
        } else {
            relative = filePath.getFileName();
        }

        return trimDepth(relative.normalize(), vecProps.getIncludePathDepth());
    }

    private CanonicalFile upsertCanonicalFile(
            String requestId,
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
        canonical.setRepoRef(StringUtils.hasText(repoRef) ? repoRef : DEFAULT_REPO_REF);
        canonical.setPathInRepo(pathInRepo);
        canonical.setContent(rawText);
        canonical.setDeprecated(false);

        CanonicalFile saved = canonicalFileRepository.save(canonical);

        log.info(
                "event=canonical_file_upserted requestId={} path={} module={} version={} id={}",
                requestId,
                trimmedPath,
                module,
                moduleVersion,
                saved.getId()
        );

        return saved;
    }

    private List<String> applyMaxChunksLimit(
            String requestId,
            String trimmedPath,
            List<String> chunks,
            int maxChunks) {

        int originalChunks = chunks.size();

        if (maxChunks > 0 && originalChunks > maxChunks) {
            log.info(
                    "event=file_truncated requestId={} file={} originalChunks={} processedChunks={} skippedChunks={}",
                    requestId,
                    trimmedPath,
                    originalChunks,
                    maxChunks,
                    originalChunks - maxChunks
            );

            return new ArrayList<>(chunks.subList(0, maxChunks));
        }

        return chunks;
    }

    private void persistEmbeddings(
            String requestId,
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
                    """.formatted(trimmedPath, type, module, idx + 1, total);

            float[] vector = embeddingService.generateEmbedding(header + chunk);

            embeddingRepository.save(new FileEmbedding(
                    fileName,
                    trimmedPath,
                    module,
                    idx,
                    total,
                    type,
                    vector,
                    chunk,
                    moduleVersion,
                    canonicalFile
            ));

            log.info(
                    "event=chunk_persisted requestId={} file={} chunkIdx={}/{}",
                    requestId,
                    fileName,
                    idx + 1,
                    total
            );
        }
    }

    private void deleteLocalFileQuietly(String requestId, Path filePath) {
        if (filePath == null) {
            return;
        }

        try {
            if (Files.deleteIfExists(filePath)) {
                log.debug(
                        "event=file_delete requestId={} file={} result=deleted",
                        requestId,
                        filePath
                );
            } else {
                log.debug(
                        "event=file_delete requestId={} file={} result=not_found",
                        requestId,
                        filePath
                );
            }
        } catch (Exception e) {
            log.debug(
                    "event=file_delete_failed requestId={} file={} message={}",
                    requestId,
                    filePath,
                    e.getMessage()
            );
        }
    }

    private List<String> splitIntoChunks(String text, int configuredWindow, int configuredOverlap) {
        int window = normalizeWindow(configuredWindow);
        int overlap = normalizeOverlap(configuredOverlap, window);

        int totalTokens = TokenizerUtil.countTokens(text);

        if (totalTokens <= window) {
            return List.of(text);
        }

        List<String> out = new ArrayList<>();
        int start = 0;

        while (start < totalTokens) {
            int end = Math.min(start + window, totalTokens);
            out.add(TokenizerUtil.slice(text, start, end));

            if (end == totalTokens) {
                break;
            }

            start = end - overlap;
        }

        return out;
    }

    private int normalizeWindow(int configuredWindow) {
        if (configuredWindow <= 0) {
            throw new IllegalStateException("chunkSizeTokens must be greater than 0");
        }

        return Math.min(configuredWindow, MODEL_TOKEN_LIMIT);
    }

    private int normalizeOverlap(int configuredOverlap, int window) {
        if (configuredOverlap < 0) {
            throw new IllegalStateException("chunkOverlapTokens cannot be negative");
        }

        if (configuredOverlap >= window) {
            throw new IllegalStateException("chunkOverlapTokens must be smaller than chunkSizeTokens");
        }

        return configuredOverlap;
    }

    private String trimDepth(Path p, int depth) {
        if (p == null || p.getNameCount() == 0) {
            return "";
        }

        if (depth <= 0) {
            return p.getFileName().toString();
        }

        int parts = p.getNameCount();
        int from = Math.max(0, parts - depth);

        return p.subpath(from, parts)
                .toString()
                .replace('\\', '/');
    }
}