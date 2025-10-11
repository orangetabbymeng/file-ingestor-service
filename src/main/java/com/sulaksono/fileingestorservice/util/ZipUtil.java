package com.sulaksono.fileingestorservice.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts ZIP archives in a secure (zip-slip safe) manner.
 */
public final class ZipUtil {

    private ZipUtil() { }

    public static List<Path> unzip(MultipartFile zip, Path targetDir) throws IOException {
        List<Path> extracted = new ArrayList<>();

        try (InputStream is = zip.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir))
                    throw new IOException("Zip-Slip attack detected: " + entry.getName());

                Files.createDirectories(resolved.getParent());
                Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                extracted.add(resolved);
                zis.closeEntry();
            }
        }
        return extracted;
    }
}
