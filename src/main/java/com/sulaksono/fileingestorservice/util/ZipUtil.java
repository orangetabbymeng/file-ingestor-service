package com.sulaksono.fileingestorservice.util;

import com.sulaksono.fileingestorservice.model.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Extracts ZIP archives in a secure (zip-slip safe) manner with basic guardrails.
 *
 * Guardrails (defaults):
 * - skips unsupported file types (FileType.UNKNOWN) -> logs "file skipped"
 * - skips entries larger than MAX_ENTRY_BYTES      -> logs "file skipped"
 * - stops after MAX_ENTRIES and/or MAX_TOTAL_BYTES
 *
 * Note: defaults are intentionally conservative to avoid embedding huge low-signal files.
 * Tune the constants if needed.
 */
public final class ZipUtil {

    private static final Logger log = LoggerFactory.getLogger(ZipUtil.class);

    /** Per-entry uncompressed byte limit (e.g., skip huge TXT). */
    public static final long MAX_ENTRY_BYTES = 512L * 1024; // 256 KB

    /** Total uncompressed bytes limit across extracted entries (zip bomb guardrail). */
    public static final long MAX_TOTAL_BYTES = 10L * 1024 * 1024; // 10 MB

    /** Max number of entries we will consider (zip bomb guardrail). */
    public static final int MAX_ENTRIES = 10_000;

    private ZipUtil() { }

    public static List<Path> unzip(Path zipPath, Path targetDir) throws IOException {
        return unzip(zipPath, targetDir, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES, MAX_ENTRIES, true);
    }

    public static List<Path> unzip(MultipartFile zip, Path targetDir) throws IOException {
        try (InputStream is = zip.getInputStream()) {
            return unzip(is, targetDir);
        }
    }

    public static List<Path> unzip(Path zipPath,
                                   Path targetDir,
                                   long maxEntryBytes,
                                   long maxTotalBytes,
                                   int maxEntries,
                                   boolean skipUnsupportedTypes) throws IOException {

        String requestId = MDC.get("requestId");
        Files.createDirectories(targetDir);

        long totalExtractedBytes = 0L;
        int extractedCount = 0;
        int skippedCount = 0;
        boolean stoppedByLimit = false;

        List<Path> extracted = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                if (extractedCount + skippedCount >= maxEntries) {
                    stoppedByLimit = true;
                    log.warn("event=zip_extract_stopped requestId={} zip={} reason=max_entries limit={}",
                            requestId, zipPath.getFileName(), maxEntries);
                    break;
                }

                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String entryName = sanitizeEntryName(entry.getName());
                Path resolved = resolveZipSlipSafe(targetDir, entryName);

                if (skipUnsupportedTypes) {
                    FileType type = FileTypeResolver.resolve(resolved.getFileName().toString());
                    if (type == FileType.UNKNOWN) {
                        skippedCount++;
                        log.info("event=zip_entry_skipped requestId={} zip={} entry={} reason=unsupported_type",
                                requestId, zipPath.getFileName(), entryName);
                        continue;
                    }
                }

                long declaredSize = entry.getSize(); // may be -1
                if (declaredSize > 0 && declaredSize > maxEntryBytes) {
                    skippedCount++;
                    log.info("event=zip_entry_skipped requestId={} zip={} entry={} reason=too_large declaredSize={} limit={}",
                            requestId, zipPath.getFileName(), entryName, declaredSize, maxEntryBytes);
                    continue;
                }

                long remainingTotal = maxTotalBytes - totalExtractedBytes;
                if (remainingTotal <= 0) {
                    stoppedByLimit = true;
                    log.warn("event=zip_extract_stopped requestId={} zip={} reason=max_total_bytes limit={}",
                            requestId, zipPath.getFileName(), maxTotalBytes);
                    break;
                }

                Files.createDirectories(resolved.getParent());

                Path tmp = resolved.getParent().resolve(resolved.getFileName() + ".part-" + UUID.randomUUID());
                long written;

                try (InputStream entryIn = zipFile.getInputStream(entry)) {
                    written = copyWithLimits(entryIn, tmp, maxEntryBytes, remainingTotal);
                } catch (EntryTooLargeException e) {
                    skippedCount++;
                    deleteQuietly(tmp);
                    log.info("event=zip_entry_skipped requestId={} zip={} entry={} reason=too_large limit={}",
                            requestId, zipPath.getFileName(), entryName, maxEntryBytes);
                    continue;
                } catch (TotalLimitReachedException e) {
                    stoppedByLimit = true;
                    deleteQuietly(tmp);
                    log.warn("event=zip_extract_stopped requestId={} zip={} reason=max_total_bytes limit={}",
                            requestId, zipPath.getFileName(), maxTotalBytes);
                    break;
                }

                // success: move into place
                Files.move(tmp, resolved, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                extracted.add(resolved);
                extractedCount++;
                totalExtractedBytes += written;
            }
        }

        log.info("event=zip_extract_complete requestId={} zip={} extractedCount={} skippedCount={} totalExtractedBytes={} stoppedByLimit={}",
                requestId, zipPath.getFileName(), extractedCount, skippedCount, totalExtractedBytes, stoppedByLimit);

        return extracted;
    }

    private static List<Path> unzip(InputStream is,
                                    Path targetDir) throws IOException {

        String requestId = MDC.get("requestId");
        Files.createDirectories(targetDir);

        long totalExtractedBytes = 0L;
        int extractedCount = 0;
        int skippedCount = 0;
        boolean stoppedByLimit = false;

        List<Path> extracted = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                if (extractedCount + skippedCount >= ZipUtil.MAX_ENTRIES) {
                    stoppedByLimit = true;
                    log.warn("event=zip_extract_stopped requestId={} reason=max_entries limit={}", requestId, ZipUtil.MAX_ENTRIES);
                    break;
                }

                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = sanitizeEntryName(entry.getName());
                Path resolved = resolveZipSlipSafe(targetDir, entryName);

                FileType type = FileTypeResolver.resolve(resolved.getFileName().toString());
                if (type == FileType.UNKNOWN) {
                    skippedCount++;
                    log.info("event=zip_entry_skipped requestId={} entry={} reason=unsupported_type", requestId, entryName);
                    drainEntry(zis);
                    zis.closeEntry();
                    continue;
                }

                long declaredSize = entry.getSize();
                if (declaredSize > ZipUtil.MAX_ENTRY_BYTES) {
                    skippedCount++;
                    log.info("event=zip_entry_skipped requestId={} entry={} reason=too_large declaredSize={} limit={}",
                            requestId, entryName, declaredSize, ZipUtil.MAX_ENTRY_BYTES);
                    drainEntry(zis);
                    zis.closeEntry();
                    continue;
                }

                long remainingTotal = ZipUtil.MAX_TOTAL_BYTES - totalExtractedBytes;
                if (remainingTotal <= 0) {
                    stoppedByLimit = true;
                    log.warn("event=zip_extract_stopped requestId={} reason=max_total_bytes limit={}", requestId, ZipUtil.MAX_TOTAL_BYTES);
                    break;
                }

                Files.createDirectories(resolved.getParent());

                Path tmp = resolved.getParent().resolve(resolved.getFileName() + ".part-" + UUID.randomUUID());
                try {
                    long written = copyWithLimits(zis, tmp, ZipUtil.MAX_ENTRY_BYTES, remainingTotal);
                    Files.move(tmp, resolved, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                    extracted.add(resolved);
                    extractedCount++;
                    totalExtractedBytes += written;
                } catch (EntryTooLargeException e) {
                    skippedCount++;
                    deleteQuietly(tmp);
                    log.info("event=zip_entry_skipped requestId={} entry={} reason=too_large limit={}", requestId, entryName, ZipUtil.MAX_ENTRY_BYTES);
                    drainEntry(zis);
                } catch (TotalLimitReachedException e) {
                    stoppedByLimit = true;
                    deleteQuietly(tmp);
                    log.warn("event=zip_extract_stopped requestId={} reason=max_total_bytes limit={}", requestId, ZipUtil.MAX_TOTAL_BYTES);
                    break;
                } finally {
                    zis.closeEntry();
                }
            }
        }

        log.info("event=zip_extract_complete requestId={} extractedCount={} skippedCount={} totalExtractedBytes={} stoppedByLimit={}",
                requestId, extractedCount, skippedCount, totalExtractedBytes, stoppedByLimit);

        return extracted;
    }

    private static String sanitizeEntryName(String name) {
        if (name == null) return "";
        String n = name.replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        return n;
    }

    private static Path resolveZipSlipSafe(Path targetDir, String entryName) throws IOException {
        Path resolved = targetDir.resolve(entryName).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new IOException("Zip-Slip attack detected: " + entryName);
        }
        return resolved;
    }

    private static long copyWithLimits(InputStream in,
                                       Path outFile,
                                       long maxEntryBytes,
                                       long maxTotalBytesRemaining) throws IOException {
        long written = 0L;
        byte[] buf = new byte[8192];

        try (var out = Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int r;
            while ((r = in.read(buf)) != -1) {
                if (written + r > maxEntryBytes) {
                    throw new EntryTooLargeException();
                }
                if (written + r > maxTotalBytesRemaining) {
                    throw new TotalLimitReachedException();
                }
                out.write(buf, 0, r);
                written += r;
            }
        }

        return written;
    }

    private static void drainEntry(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) {
            log.debug("event=draining_buffer");
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    private static final class EntryTooLargeException extends RuntimeException { }
    private static final class TotalLimitReachedException extends RuntimeException { }
}